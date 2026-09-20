package ua.com.radiokot.photoprism.features.viewer.view

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
     * Places the HD button, which must be kept away
     * from the toolbar and the system bars.
     */
    fun setHdButtonMargins(
        topPx: Int,
        endPx: Int,
    )
}
