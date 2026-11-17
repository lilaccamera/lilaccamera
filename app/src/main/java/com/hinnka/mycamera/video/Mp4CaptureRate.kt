package com.hinnka.mycamera.video

import android.system.Os
import java.io.FileDescriptor
import java.nio.ByteBuffer

/**
 * Marks an MP4 with its capture rate the way Android's own recorder does for high-frame-rate video: the key
 * `com.android.capture.fps` (a float) in `moov/meta` (QuickTime `mdta` keys). MediaMuxer cannot write it, but it does
 * write that `meta` box (com.android.version and others) and leaves a `free` box after `moov`; the key is added in
 * place, `moov` growing into the `free` box (or at the end of the file when `moov` comes last), so no sample offset moves.
 */
object Mp4CaptureRate {
    private const val KEY = "com.android.capture.fps"
    private const val TYPE_FLOAT32_BE = 23

    private class Box(val type: String, val offset: Long, val size: Long)

    /** False when the file's layout does not allow the change; the file is then left as it was. */
    fun write(fd: FileDescriptor, fps: Float): Boolean {
        val fileSize = Os.fstat(fd).st_size
        val top = boxes(fileSize) { offset, length -> read(fd, offset, length) }
        val moovIndex = top.indexOfFirst { it.type == "moov" }.takeIf { it >= 0 } ?: return false
        val moovBox = top[moovIndex]
        if (moovBox.size > Int.MAX_VALUE) return false
        val moov = read(fd, moovBox.offset, moovBox.size.toInt())
        val grown = addKey(moov, fps) ?: return false
        val delta = grown.size - moov.size
        val next = top.getOrNull(moovIndex + 1)
        val freeHeader: ByteArray? = when {
            next == null -> null
            next.type == "free" && next.size == delta.toLong() -> null
            next.type == "free" && next.size >= delta + 8L ->
                ByteBuffer.allocate(8).putInt((next.size - delta).toInt()).put("free".toByteArray()).array()
            else -> return false
        }
        write(fd, moovBox.offset, grown)
        if (freeHeader != null) write(fd, moovBox.offset + grown.size, freeHeader)
        return true
    }

    /** moov with the key appended to its meta's keys and ilst, every enclosing size raised; null without a meta. */
    private fun addKey(moov: ByteArray, fps: Float): ByteArray? {
        val meta = children(moov, 8, moov.size).firstOrNull { it.type == "meta" } ?: return null
        val metaChildren = children(moov, meta.offset.toInt() + 8, (meta.offset + meta.size).toInt())
        val keys = metaChildren.firstOrNull { it.type == "keys" } ?: return null
        val ilst = metaChildren.firstOrNull { it.type == "ilst" } ?: return null
        val count = ByteBuffer.wrap(moov, keys.offset.toInt() + 12, 4).int
        val keyName = KEY.toByteArray(Charsets.US_ASCII)
        val keyEntry = ByteBuffer.allocate(8 + keyName.size)
            .putInt(8 + keyName.size).put("mdta".toByteArray()).put(keyName).array()
        val item = ByteBuffer.allocate(32)
            .putInt(32).putInt(count + 1) // item type: the key's 1-based index
            .putInt(24).put("data".toByteArray()).putInt(TYPE_FLOAT32_BE).putInt(0).putFloat(fps)
            .array()
        val keysEnd = (keys.offset + keys.size).toInt()
        val ilstEnd = (ilst.offset + ilst.size).toInt()
        if (keysEnd > ilst.offset) return null // keys after ilst: not the layout MediaMuxer writes
        val out = ByteBuffer.allocate(moov.size + keyEntry.size + item.size)
        out.put(moov, 0, keysEnd).put(keyEntry).put(moov, keysEnd, ilstEnd - keysEnd).put(item)
            .put(moov, ilstEnd, moov.size - ilstEnd)
        val result = out.array()
        val sizes = ByteBuffer.wrap(result)
        fun grow(offset: Long, by: Int) = sizes.putInt(offset.toInt(), sizes.getInt(offset.toInt()) + by)
        val delta = keyEntry.size + item.size
        grow(0, delta) // moov
        grow(meta.offset, delta)
        grow(keys.offset, keyEntry.size)
        sizes.putInt(keys.offset.toInt() + 12, count + 1)
        grow(ilst.offset + keyEntry.size, item.size) // ilst moved by the key entry
        return result
    }

    private fun children(data: ByteArray, from: Int, to: Int): List<Box> {
        val list = ArrayList<Box>()
        var at = from
        while (at + 8 <= to) {
            val size = ByteBuffer.wrap(data, at, 4).int.toLong() and 0xffffffffL
            if (size < 8 || at + size > to) break
            list += Box(String(data, at + 4, 4, Charsets.US_ASCII), at.toLong(), size)
            at += size.toInt()
        }
        return list
    }

    private fun boxes(fileSize: Long, read: (Long, Int) -> ByteArray): List<Box> {
        val list = ArrayList<Box>()
        var at = 0L
        while (at + 8 <= fileSize) {
            val header = read(at, 16.coerceAtMost((fileSize - at).toInt()))
            val buffer = ByteBuffer.wrap(header)
            var size = buffer.int.toLong() and 0xffffffffL
            val type = String(header, 4, 4, Charsets.US_ASCII)
            if (size == 1L && header.size >= 16) size = ByteBuffer.wrap(header, 8, 8).long
            if (size == 0L) size = fileSize - at
            if (size < 8 || at + size > fileSize) break
            list += Box(type, at, size)
            at += size
        }
        return list
    }

    private fun read(fd: FileDescriptor, offset: Long, length: Int): ByteArray {
        val bytes = ByteArray(length)
        var done = 0
        while (done < length) {
            val n = Os.pread(fd, bytes, done, length - done, offset + done)
            if (n <= 0) break
            done += n
        }
        return bytes
    }

    private fun write(fd: FileDescriptor, offset: Long, bytes: ByteArray) {
        var done = 0
        while (done < bytes.size) done += Os.pwrite(fd, bytes, done, bytes.size - done, offset + done)
    }
}
