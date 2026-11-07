package com.hinnka.mycamera.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.Rect
import android.os.Build
import com.google.gson.GsonBuilder
import com.hinnka.mycamera.gallery.MediaMetadata
import com.hinnka.mycamera.gallery.db.GalleryMediaStore
import com.hinnka.mycamera.raw.RawHdrRenderResult
import com.hinnka.mycamera.utils.PLog
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel

/**
 * `photos/<id>/checkpoint_*`: a `:render` RAW render saved right after the RAW render step, so a
 * retry after a low-memory kill continues at the JPEG instead of repeating the RAW render.
 * [SDR_FILE] and [HDR_FILE] are the bitmaps' raw pixel bytes ([Bitmap.copyPixelsToBuffer] order),
 * written and read through memory-mapped files so no second full-size copy lands on the heap.
 * [INFO_FILE], written last, holds every other [RawHdrRenderResult] field and the merged metadata.
 */
object RawRenderCheckpoint {
    private const val TAG = "RawRenderCheckpoint"
    private const val VERSION = 1
    const val SDR_FILE = "checkpoint_sdr.f16"
    const val HDR_FILE = "checkpoint_hdr.f16"
    const val INFO_FILE = "checkpoint.json"

    // Room for the checkpoint plus the JPEG, HDR outputs and export that follow it.
    private const val FREE_SPACE_FACTOR = 3
    private val gson = GsonBuilder().serializeSpecialFloatingPointValues().create()

    data class Restored(val rawResult: RawHdrRenderResult, val metadata: MediaMetadata)

    private data class Plane(
        val width: Int,
        val height: Int,
        val config: String,
        val colorSpace: String,
        val hasAlpha: Boolean,
        val premultiplied: Boolean,
        val bytes: Long,
    )

    private data class Info(
        val version: Int,
        val photoId: String,
        val sdr: Plane,
        val hdr: Plane?,
        val rawInputWidth: Int,
        val rawInputHeight: Int,
        /** left, top, right, bottom */
        val outputSourceBounds: IntArray?,
        val outputRotation: Int,
        val effectiveDefaultCrop: IntArray?,
        /** In the [GalleryMediaStore.RENDER_RESULT_FILE] form. */
        val metadata: String,
    )

    /**
     * Saves [result] and [metadata] (merged, as the steps after the RAW render read it). Returns
     * false, leaving no checkpoint, when it cannot; the render then just goes on without one.
     */
    fun write(context: Context, photoId: String, result: RawHdrRenderResult, metadata: MediaMetadata): Boolean {
        val dir = RenderJob.photoDir(context, photoId)
        delete(context, photoId)
        val sdr = planeOf(result.sdrBitmap)
        val hdr = result.hdrReferenceBitmap?.let(::planeOf)
        if (sdr == null || (result.hdrReferenceBitmap != null && hdr == null)) {
            PLog.w(TAG, "Not checkpointing $photoId: unsupported bitmap (sdr=$sdr, hdr=$hdr)")
            return false
        }
        val bytes = sdr.bytes + (hdr?.bytes ?: 0L)
        val free = dir.usableSpace
        if (free < FREE_SPACE_FACTOR * bytes) {
            PLog.w(TAG, "Not checkpointing $photoId: ${free shr 20} MB free, needs ${(FREE_SPACE_FACTOR * bytes) shr 20} MB")
            return false
        }
        val startedAt = System.nanoTime()
        return try {
            writePixels(result.sdrBitmap, File(dir, SDR_FILE))
            result.hdrReferenceBitmap?.let { writePixels(it, File(dir, HDR_FILE)) }
            val info = Info(
                VERSION, photoId, sdr, hdr, result.rawInputWidth, result.rawInputHeight,
                result.outputSourceBounds?.toArray(), result.outputRotation, result.effectiveDefaultCrop?.toArray(),
                GalleryMediaStore.encodeRenderMetadata(context, photoId, metadata),
            )
            RenderJob.writeAtomically(File(dir, INFO_FILE)) { it.writeText(gson.toJson(info)) }
            PLog.i(TAG, "Checkpoint of $photoId written: bytes=$bytes took=${(System.nanoTime() - startedAt) / 1_000_000}ms")
            true
        } catch (e: Exception) {
            PLog.e(TAG, "Checkpoint of $photoId failed; going on without it", e)
            delete(context, photoId)
            false
        }
    }

    /** The checkpoint of [photoId] as bitmaps, or null when it is missing or invalid, which deletes it. */
    fun read(context: Context, photoId: String): Restored? {
        val dir = RenderJob.photoDir(context, photoId)
        val startedAt = System.nanoTime()
        var sdr: Bitmap? = null
        var hdr: Bitmap? = null
        return try {
            val info = checkNotNull(gson.fromJson(File(dir, INFO_FILE).readText(), Info::class.java)) { "empty info" }
            check(info.version == VERSION && info.photoId == photoId) { "version ${info.version} of ${info.photoId}" }
            sdr = readPixels(info.sdr, File(dir, SDR_FILE))
            hdr = info.hdr?.let { readPixels(it, File(dir, HDR_FILE)) }
            val result = RawHdrRenderResult(
                sdrBitmap = sdr,
                hdrReferenceBitmap = hdr,
                rawInputWidth = info.rawInputWidth,
                rawInputHeight = info.rawInputHeight,
                outputSourceBounds = info.outputSourceBounds?.toRect(),
                outputRotation = info.outputRotation,
                effectiveDefaultCrop = info.effectiveDefaultCrop?.toRect(),
            )
            val metadata = GalleryMediaStore.decodeRenderMetadata(info.metadata)
            PLog.i(
                TAG,
                "Checkpoint of $photoId restored: bytes=${info.sdr.bytes + (info.hdr?.bytes ?: 0L)} " +
                    "took=${(System.nanoTime() - startedAt) / 1_000_000}ms",
            )
            Restored(result, metadata)
        } catch (e: Exception) {
            PLog.w(TAG, "Checkpoint of $photoId missing or invalid (${e.message}); deleting it and rendering from the DNG")
            sdr?.recycle()
            hdr?.recycle()
            delete(context, photoId)
            null
        }
    }

    fun delete(context: Context, photoId: String) {
        val dir = RenderJob.photoDir(context, photoId)
        listOf(SDR_FILE, HDR_FILE, INFO_FILE).forEach { name ->
            File(dir, name).delete()
            File(dir, "$name.tmp").delete()
        }
    }

    private fun planeOf(bitmap: Bitmap): Plane? {
        if (bitmap.isRecycled || bitmap.config == Bitmap.Config.HARDWARE) return null
        // The raw bytes carry no gain map; the RAW render never attaches one.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && bitmap.hasGainmap()) return null
        val config = bitmap.config ?: return null
        val colorSpace = ColorSpace.Named.entries.firstOrNull { ColorSpace.get(it) == bitmap.colorSpace } ?: return null
        return Plane(
            bitmap.width, bitmap.height, config.name, colorSpace.name,
            bitmap.hasAlpha(), bitmap.isPremultiplied, bitmap.byteCount.toLong(),
        )
    }

    private fun writePixels(bitmap: Bitmap, target: File) {
        RenderJob.writeAtomically(target) { temp ->
            RandomAccessFile(temp, "rw").use { file ->
                val mapped = file.channel.map(FileChannel.MapMode.READ_WRITE, 0, bitmap.byteCount.toLong())
                bitmap.copyPixelsToBuffer(mapped)
                // Clean pages can be dropped under memory pressure; dirty ones must be written first.
                mapped.force()
            }
        }
    }

    private fun readPixels(plane: Plane, file: File): Bitmap {
        check(file.length() == plane.bytes) { "${file.name} is ${file.length()} bytes, expected ${plane.bytes}" }
        val bitmap = Bitmap.createBitmap(
            plane.width, plane.height, Bitmap.Config.valueOf(plane.config), plane.hasAlpha,
            ColorSpace.get(ColorSpace.Named.valueOf(plane.colorSpace)),
        )
        try {
            check(bitmap.byteCount.toLong() == plane.bytes) { "${file.name}: bitmap is ${bitmap.byteCount} bytes" }
            RandomAccessFile(file, "r").use {
                bitmap.copyPixelsFromBuffer(it.channel.map(FileChannel.MapMode.READ_ONLY, 0, plane.bytes))
            }
            bitmap.isPremultiplied = plane.premultiplied
        } catch (e: Exception) {
            bitmap.recycle()
            throw e
        }
        return bitmap
    }

    private fun Rect.toArray() = intArrayOf(left, top, right, bottom)

    private fun IntArray.toRect(): Rect {
        check(size == 4) { "rect of $size values" }
        return Rect(this[0], this[1], this[2], this[3])
    }
}
