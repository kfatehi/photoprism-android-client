package ua.com.radiokot.photoprism.features.gallery.view.model

import ua.com.radiokot.photoprism.R

enum class GalleryItemScale(
    /**
     * Multiplier for the default list item min size
     *
     * @see R.dimen.list_item_gallery_media_min_size
     */
    val factor: Float,

    /**
     * Size of the thumbnails loaded for this scale.
     * Thumbnails of the exact same size are reused by the media viewer,
     * as they are likely to be cached.
     */
    val thumbnailSizePx: Int,
) {
    TINY(0.5f, 100),
    SMALL(0.75f, 250),
    NORMAL(1f, 250),
    LARGE(1.5f, 500),
    HUGE(2f, 500),
    ;
}
