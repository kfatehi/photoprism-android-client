package ua.com.radiokot.photoprism.features.viewer.view

import android.graphics.Rect

/**
 * A view which initially shows a lightweight preview
 * and can load a high resolution one on demand,
 * controlled by its own floating button.
 */
interface HdLoadableView {
    /**
     * Sets whether the HD button may be shown.
     * It is only actually shown when there is something to load.
     */
    fun setHdButtonVisibilityAllowed(isAllowed: Boolean)

    /**
     * Extends the HD button margins with the given fullscreen insets.
     * May be called multiple times with the same value.
     */
    fun applyHdButtonInsets(insets: Rect)
}
