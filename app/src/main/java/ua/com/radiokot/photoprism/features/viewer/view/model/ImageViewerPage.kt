package ua.com.radiokot.photoprism.features.viewer.view.model

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.Size
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import okhttp3.Call
import org.koin.core.component.KoinScopeComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import ua.com.radiokot.photoprism.R
import ua.com.radiokot.photoprism.databinding.PagerItemMediaViewerImageBinding
import ua.com.radiokot.photoprism.di.DI_SCOPE_SESSION
import ua.com.radiokot.photoprism.di.HttpClient
import ua.com.radiokot.photoprism.di.IMAGE_HTTP_CLIENT
import ua.com.radiokot.photoprism.extension.fadeVisibility
import ua.com.radiokot.photoprism.extension.hardwareOr565
import ua.com.radiokot.photoprism.extension.kLogger
import ua.com.radiokot.photoprism.features.gallery.data.model.GalleryMedia
import ua.com.radiokot.photoprism.features.viewer.view.HdLoadableView
import ua.com.radiokot.photoprism.features.viewer.view.MediaViewerPageViewHolder
import ua.com.radiokot.photoprism.features.viewer.view.ZoomablePhotoView
import ua.com.radiokot.photoprism.features.viewer.view.ZoomableView
import kotlin.math.abs

/**
 * @param previewUrl URL of the preview shown once the page is opened.
 * @param hdPreviewUrl URL of the high resolution preview to be loaded on demand,
 * or null if [previewUrl] is the best available.
 * @param isHdLoadedAutomatically whether to start loading [hdPreviewUrl]
 * without waiting for the user action.
 */
class ImageViewerPage(
    val previewUrl: String,
    val hdPreviewUrl: String?,
    val isHdLoadedAutomatically: Boolean,
    val imageViewSize: Size,
    thumbnailUrl: String,
    source: GalleryMedia?,
) : MediaViewerPage(thumbnailUrl, source) {
    override val type: Int
        get() = R.id.pager_item_media_viewer_image

    override val layoutRes: Int
        get() = R.layout.pager_item_media_viewer_image

    override fun getViewHolder(v: View): ViewHolder =
        ViewHolder(PagerItemMediaViewerImageBinding.bind(v))

    class ViewHolder(
        val view: PagerItemMediaViewerImageBinding,
    ) : MediaViewerPageViewHolder<ImageViewerPage>(view.root),
        KoinScopeComponent,
        HdLoadableView,
        ZoomableView by ZoomablePhotoView(view.photoView) {

        override val scope: Scope
            get() = getKoin().getScope(DI_SCOPE_SESSION)

        private val log = kLogger("ImageViewerPage")
        private val picasso: Picasso by inject()

        /**
         * The very client Picasso loads images with,
         * required to actually abort the HD loading on cancel.
         */
        private val imageHttpClient: HttpClient by inject(named(IMAGE_HTTP_CLIENT))

        private var isLoadingFinished = false
        private var imageViewSize: Size = Size(0, 0)
        private var hdPreviewUrl: String? = null
        private var hdLoadState: HdLoadState = HdLoadState.UNAVAILABLE
        private var isHdButtonVisibilityAllowed = true
        private var appliedHdButtonInsets: Rect? = null

        /**
         * Picasso only keeps a weak reference to the target,
         * so it must be kept here for the duration of the loading.
         */
        private var hdTarget: Target? = null

        private val rememberedSuppMatrix = Matrix()
        private var rememberedAspectRatio: Float? = null

        private val imageLoadingCallback = object : Callback {
            override fun onSuccess() {
                view.progressIndicator.hide()
                // The preview has the correct aspect ratio,
                // so the cropped backdrop is no longer needed.
                view.backdropImageView.isVisible = false
                isLoadingFinished = true
                onContentPresented()
            }

            override fun onError(e: Exception?) {
                view.progressIndicator.hide()
                view.errorTextView.visibility = View.VISIBLE
                isLoadingFinished = true
                onContentPresented()
            }
        }

        init {
            // Not throttled: cancelling right after starting the loading
            // must work without a delay.
            view.hdButton.setOnClickListener {
                when (hdLoadState) {
                    HdLoadState.IDLE ->
                        loadHd()

                    HdLoadState.LOADING ->
                        cancelHdLoading()

                    else ->
                        Unit
                }
            }
        }

        override fun bindView(item: ImageViewerPage, payloads: List<Any>) {
            super.bindView(item, payloads)

            cancelHdRequest()

            view.progressIndicator.show()
            view.errorTextView.visibility = View.GONE
            isLoadingFinished = false
            imageViewSize = item.imageViewSize
            hdPreviewUrl = item.hdPreviewUrl

            setHdLoadState(
                if (item.hdPreviewUrl == null)
                    HdLoadState.UNAVAILABLE
                else
                    HdLoadState.IDLE,
                animateButton = false,
            )

            // The cropped thumbnail is likely to be cached by the gallery grid,
            // hence it is shown without a network wait while the preview is loading.
            // It is only worth it when the preview is a small one.
            view.backdropImageView.isVisible = item.hdPreviewUrl != null
            if (item.hdPreviewUrl != null) {
                picasso
                    .load(item.thumbnailUrl)
                    .hardwareOr565()
                    .noFade()
                    .into(view.backdropImageView)
            }

            picasso
                .load(item.previewUrl)
                .hardwareOr565()
                // Picasso deferred fit is no good when we want to resize the image
                // considering the zoom factor, so the zoom actually makes sense.
                .resize(item.imageViewSize.width, item.imageViewSize.height)
                .centerInside()
                .onlyScaleDown()
                .into(view.photoView, imageLoadingCallback)

            if (item.isHdLoadedAutomatically) {
                loadHd()
            }
        }

        private fun loadHd() {
            val url = hdPreviewUrl
                ?: return

            if (hdLoadState != HdLoadState.IDLE) {
                return
            }

            log.debug {
                "loadHd(): loading:" +
                        "\nurl=$url"
            }

            setHdLoadState(HdLoadState.LOADING)

            // The image is set manually rather than by Picasso,
            // so the zoom and pan can be captured right before the swap
            // and restored right after it.
            val target = object : Target {
                override fun onBitmapLoaded(bitmap: Bitmap, from: Picasso.LoadedFrom?) {
                    hdTarget = null
                    rememberZoomAndPan()
                    view.photoView.setImageBitmap(bitmap)
                    restoreZoomAndPan()
                    setHdLoadState(HdLoadState.LOADED)
                }

                override fun onBitmapFailed(e: Exception?, errorDrawable: Drawable?) {
                    hdTarget = null

                    log.debug(e) {
                        "loadHd(): hd_loading_failed"
                    }

                    // Keep the shown preview and let the user try again.
                    setHdLoadState(HdLoadState.IDLE)
                }

                override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
                    // The preview is already shown, nothing to prepare.
                }
            }
            hdTarget = target

            picasso
                .load(url)
                .hardwareOr565()
                .resize(imageViewSize.width, imageViewSize.height)
                .centerInside()
                .onlyScaleDown()
                .into(target)
        }

        private fun cancelHdLoading() {
            if (hdLoadState != HdLoadState.LOADING) {
                return
            }

            log.debug {
                "cancelHdLoading(): cancelling"
            }

            cancelHdRequest()

            setHdLoadState(HdLoadState.IDLE)
        }

        /**
         * Stops the HD loading and aborts the HTTP call it may be blocked on.
         * The shown preview is not affected.
         */
        private fun cancelHdRequest() {
            hdTarget?.also(picasso::cancelRequest)
            hdTarget = null

            val url = hdPreviewUrl
                ?: return

            // Picasso loads images synchronously on its own threads,
            // so the call is among the running ones.
            imageHttpClient.dispatcher.runningCalls()
                .filter { it.request().url.toString() == url }
                .forEach(Call::cancel)
        }

        private fun setHdLoadState(
            state: HdLoadState,
            animateButton: Boolean = true,
        ) {
            hdLoadState = state

            with(view.hdButton) {
                setIconResource(
                    if (state == HdLoadState.LOADING)
                        R.drawable.ic_close
                    else
                        R.drawable.ic_hd
                )
                contentDescription = context.getString(
                    if (state == HdLoadState.LOADING)
                        R.string.cancel
                    else
                        R.string.load_hd
                )
            }
            view.hdButtonProgress.isVisible = state == HdLoadState.LOADING

            updateHdButtonVisibility(animate = animateButton)
        }

        override fun setHdButtonVisibilityAllowed(isAllowed: Boolean) {
            isHdButtonVisibilityAllowed = isAllowed
            updateHdButtonVisibility(animate = true)
        }

        private fun updateHdButtonVisibility(animate: Boolean) {
            val isVisible = isHdButtonVisibilityAllowed
                    && (hdLoadState == HdLoadState.IDLE || hdLoadState == HdLoadState.LOADING)

            with(view.hdButtonLayout) {
                if (animate) {
                    clearAnimation()
                    fadeVisibility(isVisible)
                } else {
                    clearAnimation()
                    alpha = 1f
                    this.isVisible = isVisible
                }
            }
        }

        override fun applyHdButtonInsets(insets: Rect) {
            if (appliedHdButtonInsets == insets) {
                return
            }

            val previousInsets = appliedHdButtonInsets
            appliedHdButtonInsets = Rect(insets)

            view.hdButtonLayout.updateLayoutParams<MarginLayoutParams> {
                topMargin += insets.top - (previousInsets?.top ?: 0)
                rightMargin += insets.right - (previousInsets?.right ?: 0)
            }
        }

        /**
         * Remembers the current zoom and pan of the photo view.
         */
        private fun rememberZoomAndPan() {
            rememberedAspectRatio = view.photoView.drawable?.aspectRatio
            view.photoView.getSuppMatrix(rememberedSuppMatrix)
        }

        /**
         * Restores the zoom and pan remembered by [rememberZoomAndPan].
         *
         * The supplementary matrix is applied on top of the base one,
         * which fits the image into the view. As both the preview and the HD image
         * have the same aspect ratio, their base matrices are identical,
         * hence applying the remembered supplementary matrix
         * keeps the visible area exactly the same.
         */
        private fun restoreZoomAndPan() {
            val rememberedAspectRatio = this.rememberedAspectRatio
                ?: return
            val newAspectRatio = view.photoView.drawable?.aspectRatio
                ?: return

            if (abs(newAspectRatio - rememberedAspectRatio) > ASPECT_RATIO_TOLERANCE) {
                log.warn {
                    "restoreZoomAndPan(): resetting_as_aspect_ratio_changed:" +
                            "\nremembered=$rememberedAspectRatio," +
                            "\nnew=$newAspectRatio"
                }

                return
            }

            view.photoView.setSuppMatrix(rememberedSuppMatrix)
        }

        override fun attachToWindow(item: ImageViewerPage) {
            // If attached without re-binding (swipe to a previous page)
            // and the loading is finished, call the content presentation callback.
            if (isLoadingFinished) {
                onContentPresented()
            }
        }

        override fun unbindView(item: ImageViewerPage) {
            cancelHdRequest()
            picasso.cancelRequest(view.photoView)
            picasso.cancelRequest(view.backdropImageView)
            hdPreviewUrl = null
            setHdLoadState(HdLoadState.UNAVAILABLE, animateButton = false)
        }

        private val Drawable.aspectRatio: Float?
            get() =
                if (intrinsicWidth > 0 && intrinsicHeight > 0)
                    intrinsicWidth.toFloat() / intrinsicHeight
                else
                    null

        private enum class HdLoadState {
            /**
             * There is nothing to load, the shown preview is the best available.
             */
            UNAVAILABLE,

            /**
             * The HD preview can be loaded.
             */
            IDLE,

            /**
             * The HD preview is being loaded.
             */
            LOADING,

            /**
             * The HD preview is shown.
             */
            LOADED,
            ;
        }

        private companion object {
            private const val ASPECT_RATIO_TOLERANCE = 0.01f
        }
    }
}
