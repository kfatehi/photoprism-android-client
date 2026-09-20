package ua.com.radiokot.photoprism.features.viewer.view.model

import android.util.Size
import com.mikepenz.fastadapter.items.AbstractItem
import ua.com.radiokot.photoprism.features.gallery.data.model.GalleryMedia
import ua.com.radiokot.photoprism.features.gallery.data.model.Viewable
import ua.com.radiokot.photoprism.features.gallery.logic.MediaPreviewUrlFactory
import ua.com.radiokot.photoprism.features.gallery.view.model.GalleryMediaTypeResources
import ua.com.radiokot.photoprism.features.viewer.view.MediaViewerPageViewHolder
import kotlin.math.max

// MediaViewerPageDiffCallback must be updated when adding new page types.
sealed class MediaViewerPage(
    val thumbnailUrl: String,
    val source: GalleryMedia?,
) : AbstractItem<MediaViewerPageViewHolder<out MediaViewerPage>>() {

    // Used in MediaViewerPageDiffCallback for item equality.
    override var identifier: Long
        get() = (thumbnailUrl + type).hashCode().toLong()
        set(_) = error("Don't override my value")

    companion object {
        private const val FADE_END_PLAYBACK_DURATION_MS_SHORT =
            400L + FadeEndLivePhotoViewerPage.FADE_DURATION_MS
        private const val FADE_END_PLAYBACK_DURATION_MS_LONG =
            1000L + FadeEndLivePhotoViewerPage.FADE_DURATION_MS
        private const val THUMBNAIL_SIZE_PX = 500

        /**
         * Size of the preview shown immediately when progressive loading is on.
         * Small enough to arrive fast, yet aspect-correct unlike the thumbnails.
         */
        private const val PROGRESSIVE_PREVIEW_SIZE_PX = 720

        /**
         * @param progressiveImageLoading non-null to show a small preview immediately
         * and load the high resolution one on demand.
         */
        fun fromGalleryMedia(
            source: GalleryMedia,
            imageViewSize: Size,
            livePhotosAsImages: Boolean,
            borderlessVideo: Boolean,
            canOpenPanoramas: Boolean,
            previewUrlFactory: MediaPreviewUrlFactory,
            progressiveImageLoading: ProgressiveImageLoading? = null,
            thumbnailSizePx: Int = THUMBNAIL_SIZE_PX,
        ): MediaViewerPage {
            return when {
                source.media is GalleryMedia.TypeData.Live
                        && source.media.fullDurationMs != null -> {

                    if (livePhotosAsImages) {
                        return imageViewerPage(
                            source = source,
                            imageViewSize = imageViewSize,
                            previewUrlFactory = previewUrlFactory,
                            progressiveImageLoading = progressiveImageLoading,
                            thumbnailSizePx = thumbnailSizePx,
                        )
                    }

                    val videoPreviewStartMs: Long? =
                        when (source.media.kind) {
                            GalleryMedia.TypeData.Live.Kind.SAMSUNG ->
                                (source.media.fullDurationMs - FADE_END_PLAYBACK_DURATION_MS_SHORT)
                                    .coerceAtLeast(0)

                            GalleryMedia.TypeData.Live.Kind.APPLE ->
                                (source.media.fullDurationMs / 2 - FADE_END_PLAYBACK_DURATION_MS_SHORT)
                                    .coerceAtLeast(0)

                            GalleryMedia.TypeData.Live.Kind.GOOGLE ->
                                (source.media.fullDurationMs - FADE_END_PLAYBACK_DURATION_MS_LONG)
                                    .coerceAtLeast(0)

                            else ->
                                null
                        }

                    val videoPreviewEndMs: Long? =
                        when (source.media.kind) {
                            GalleryMedia.TypeData.Live.Kind.APPLE ->
                                (source.media.fullDurationMs / 2)
                                    .coerceAtLeast(0)

                            else ->
                                null
                        }

                    FadeEndLivePhotoViewerPage(
                        photoPreviewUrl = previewUrlFactory.getImagePreviewUrl(
                            previewHash = source.hash,
                            sizePx = max(
                                imageViewSize.width,
                                imageViewSize.height
                            )
                        ),
                        videoPreviewUrl = previewUrlFactory.getVideoPreviewUrl(
                            galleryMedia = source,
                        ),
                        videoPreviewStartMs = videoPreviewStartMs,
                        videoPreviewEndMs = videoPreviewEndMs,
                        imageViewSize = imageViewSize,
                        thumbnailUrl = previewUrlFactory.getThumbnailUrl(
                            thumbnailHash = source.hash,
                            sizePx = THUMBNAIL_SIZE_PX,
                        ),
                        source = source,
                    )
                }

                source.media is Viewable.AsImage
                        && source.panoramaProjection != null ->
                    Panorama2DPreviewViewerPage(
                        needsOpenPanoramaButton = canOpenPanoramas,
                        projection = source.panoramaProjection,
                        previewUrl = previewUrlFactory.getImagePreviewUrl(
                            previewHash = source.hash,
                            sizePx = max(
                                imageViewSize.width,
                                imageViewSize.height
                            )
                        ),
                        thumbnailUrl = previewUrlFactory.getThumbnailUrl(
                            thumbnailHash = source.hash,
                            sizePx = THUMBNAIL_SIZE_PX,
                        ),
                        source = source,
                    )

                source.media is Viewable.AsVideo ->
                    VideoViewerPage(
                        previewUrl = previewUrlFactory.getVideoPreviewUrl(
                            galleryMedia = source,
                        ),
                        isLooped = source.media is GalleryMedia.TypeData.Live
                                || source.media is GalleryMedia.TypeData.Animated,
                        needsVideoControls = source.media is GalleryMedia.TypeData.Video,
                        isVideoBorderless = borderlessVideo,
                        thumbnailUrl = previewUrlFactory.getThumbnailUrl(
                            thumbnailHash = source.hash,
                            sizePx = THUMBNAIL_SIZE_PX,
                        ),
                        source = source,
                    )

                source.media is Viewable.AsImage ->
                    imageViewerPage(
                        source = source,
                        imageViewSize = imageViewSize,
                        previewUrlFactory = previewUrlFactory,
                        progressiveImageLoading = progressiveImageLoading,
                        thumbnailSizePx = thumbnailSizePx,
                    )

                else ->
                    unsupported(source, previewUrlFactory)
            }
        }

        private fun imageViewerPage(
            source: GalleryMedia,
            imageViewSize: Size,
            previewUrlFactory: MediaPreviewUrlFactory,
            progressiveImageLoading: ProgressiveImageLoading?,
            thumbnailSizePx: Int,
        ) = ImageViewerPage(
            previewUrl = previewUrlFactory.getImagePreviewUrl(
                previewHash = source.hash,
                sizePx =
                    if (progressiveImageLoading != null)
                        PROGRESSIVE_PREVIEW_SIZE_PX
                    else
                        max(
                            imageViewSize.width,
                            imageViewSize.height
                        )
            ),
            hdPreviewUrl = progressiveImageLoading?.let { progressive ->
                previewUrlFactory.getImagePreviewUrl(
                    previewHash = source.hash,
                    sizePx = progressive.hdSizePx,
                )
            },
            isHdLoadedAutomatically = progressiveImageLoading?.isAutomatic == true,
            imageViewSize = imageViewSize,
            // The thumbnail of the very size the grid loads is shown as a backdrop,
            // so it must be requested with the very same size to hit the cache.
            thumbnailUrl = previewUrlFactory.getThumbnailUrl(
                thumbnailHash = source.hash,
                sizePx = thumbnailSizePx,
            ),
            source = source,
        )

        fun unsupported(
            source: GalleryMedia,
            previewUrlFactory: MediaPreviewUrlFactory,
        ) = UnsupportedNoticePage(
            mediaTypeIcon = GalleryMediaTypeResources.getIcon(source.media.typeName),
            mediaTypeName = GalleryMediaTypeResources.getName(source.media.typeName),
            thumbnailUrl = previewUrlFactory.getThumbnailUrl(
                thumbnailHash = source.hash,
                sizePx = THUMBNAIL_SIZE_PX,
            ),
            source = source,
        )
    }

    /**
     * @param hdSizePx size of the high resolution preview to be loaded on demand.
     * @param isAutomatic whether to load it without waiting for the user action.
     */
    class ProgressiveImageLoading(
        val hdSizePx: Int,
        val isAutomatic: Boolean,
    )
}
