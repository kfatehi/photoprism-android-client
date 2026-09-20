package ua.com.radiokot.photoprism.features.gallery.data.model

/**
 * Size of the high resolution image preview
 * loaded in the viewer on demand.
 *
 * @param sizePx value to be passed to
 * [ua.com.radiokot.photoprism.features.gallery.logic.MediaPreviewUrlFactory.getImagePreviewUrl]
 * to get a preview of this size.
 */
enum class HdPreviewSize(val sizePx: Int) {
    FIT_2048(2048),
    FIT_3840(3840),
    FIT_4096(4096),
    ;
}
