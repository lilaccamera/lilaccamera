package com.hinnka.mycamera.gallery

import com.hinnka.mycamera.render.RenderLook

/**
 * A shot and its versions (re-renders, the camera's own JPEG) share [STACK_ID_PROPERTY] in their
 * [MediaMetadata.customProperties], set to the original photo's id; a photo without it is its own stack.
 * Built in memory from the gallery list, which is sorted newest first.
 */
class PhotoStacks private constructor(
    /** One photo per stack, the newest version, in gallery order. */
    val tiles: List<MediaData>,
    private val versionsByStack: Map<String, List<MediaData>>,
) {
    /** The versions of [photo]'s stack, oldest first. */
    fun versionsOf(photo: MediaData): List<MediaData> = versionsByStack[stackIdOf(photo)] ?: listOf(photo)

    fun tileIndexOf(photo: MediaData): Int = stackIdOf(photo).let { id -> tiles.indexOfFirst { stackIdOf(it) == id } }

    companion object {
        const val STACK_ID_PROPERTY = "stackId"
        /** Marks the full-size JPEG the camera delivered beside a Pro RAW, saved as is. */
        const val CAMERA_JPEG_PROPERTY = "cameraJpeg"
        /** The shot's stars, "1" to "5"; absent is unrated. Every version of a stack carries the same. */
        const val RATING_PROPERTY = "rating"
        /** "true" while the shot is protected from deletion; every version of a stack carries the same. */
        const val PROTECTED_PROPERTY = "protected"

        fun of(photos: List<MediaData>): PhotoStacks {
            val groups = photos.groupBy(::stackIdOf)
            return PhotoStacks(groups.values.map { it.first() }, groups.mapValues { it.value.asReversed() })
        }

        fun stackIdOf(photo: MediaData): String = stackIdOf(photo.id, photo.metadata)

        fun stackIdOf(photoId: String, metadata: MediaMetadata?): String =
            metadata?.customProperties?.get(STACK_ID_PROPERTY) ?: photoId

        fun isCameraJpeg(metadata: MediaMetadata?): Boolean =
            metadata?.customProperties?.get(CAMERA_JPEG_PROPERTY) == "true"

        fun ratingOf(metadata: MediaMetadata?): Int =
            metadata?.customProperties?.get(RATING_PROPERTY)?.toIntOrNull()?.coerceIn(0, 5) ?: 0

        fun isProtected(metadata: MediaMetadata?): Boolean =
            metadata?.customProperties?.get(PROTECTED_PROPERTY) == "true"

        /**
         * [properties] with the stack membership, rating and protection of [target]: pasted settings never move a
         * photo into another stack, nor copy the source's stars or protection.
         */
        fun keepStackOf(target: Map<String, String>, properties: Map<String, String>): Map<String, String> =
            (properties - STACK_KEYS) + target.filterKeys { it in STACK_KEYS }

        private val STACK_KEYS = setOf(
            STACK_ID_PROPERTY, CAMERA_JPEG_PROPERTY, RenderLook.LABEL_PROPERTY, RATING_PROPERTY, PROTECTED_PROPERTY,
        )
    }
}
