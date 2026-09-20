package ua.com.radiokot.photoprism.features.gallery.data.storage

import io.reactivex.rxjava3.subjects.BehaviorSubject
import ua.com.radiokot.photoprism.features.gallery.data.model.HdPreviewSize
import ua.com.radiokot.photoprism.features.gallery.data.model.RawSharingMode
import ua.com.radiokot.photoprism.features.gallery.view.model.GalleryItemScale
import ua.com.radiokot.photoprism.features.gallery.data.model.GalleryItemsOrder

interface GalleryPreferences {
    val itemScale: BehaviorSubject<GalleryItemScale>
    val livePhotosAsImages: BehaviorSubject<Boolean>
    val rawSharingMode: BehaviorSubject<RawSharingMode>

    /**
     * Size of the high resolution image preview
     * loaded in the viewer on demand.
     */
    val hdPreviewSize: BehaviorSubject<HdPreviewSize>

    /**
     * Whether to load the high resolution image preview automatically
     * when the connection is not metered.
     */
    val autoLoadHdOnUnmetered: BehaviorSubject<Boolean>

    fun getItemsOrderBySearchQuery(searchQuery: String?): BehaviorSubject<GalleryItemsOrder>
}
