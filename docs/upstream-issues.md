# Upstream-ready issue drafts

These are the upstream-facing versions of the two feature requests tracked on this fork:

- Fork issue A -> <https://github.com/kfatehi/photoprism-android-client/issues/1>
- Fork issue B -> <https://github.com/kfatehi/photoprism-android-client/issues/2>

Nothing here has been posted to
[Radiokot/photoprism-android-client](https://github.com/Radiokot/photoprism-android-client).
They are drafts, kept separate from the fork-internal issues so they can be filed upstream
(or turned into a discussion thread) unchanged if and when that is wanted.

---

## Issue 1 - Progressive full-screen image viewer (thumbnail first, full-res on demand)

**Type:** feature request / performance

### What happens now

Browsing the timeline is fast, but opening an item is noticeably slower, and on a slow or
remote library it can take several seconds before anything appears.

The grid loads square tiles (`tile_100` / `tile_224` / `tile_500`, by item scale) which are
small and get cached. The viewer instead requests a preview sized for the zoomed viewport:

- `MediaViewerActivity.initPager()` sets `imageViewSize` to the window size multiplied by
  `1.5` ("Image view size is the window size multiplied by a zoom factor")
- `MediaViewerPage.fromGalleryMedia()` passes `max(width, height)` to
  `MediaPreviewUrlFactory.getImagePreviewUrl()`
- `PhotoPrismMediaPreviewUrlFactory` maps that onto a `fit_*` size

On a 1080x2400 phone that is `1620x3600` -> `fit_3840`: a multi-megabyte JPEG that must be
downloaded and decoded before the page shows anything but a spinner, even when a thumbnail
of the same photo is already in the cache.

### Suggested change

Make the image page progressive, and make the expensive download an explicit step:

1. **Show something immediately.** Paint the page with a small, aspect-correct preview
   (`fit_720`, which is usually a single fast round trip, and is free when it is already in
   the OkHttp cache from a previous visit). Zoom and pan should be live on it at once.
   The already-cached square `tile_*` thumbnail can additionally be used as an instant
   center-cropped backdrop; it cannot be the zoomable layer, since `tile_*` previews are
   square crops rather than aspect-correct images.
2. **Load the high-resolution version on demand**, via a floating button over the image with
   three states: idle ("load HD"), loading (circular progress, tappable to cancel, where
   cancelling actually aborts the HTTP request), and hidden once loaded.
3. **Swap seamlessly.** When the full image arrives, put it in the same `PhotoView` while
   preserving the current zoom scale and center point, mapped into the new image's
   coordinate space, so the only perceptible change is sharpness.

### Why it may be worth doing upstream

- It turns the viewer's worst-case latency into a user-controlled action instead of an
  unavoidable wait, which matters most on exactly the setups PhotoPrism users tend to have
  (self-hosted, sometimes behind a slow uplink or a VPN).
- It reduces bandwidth for the common case of glancing through photos, which is also
  friendly to metered connections.
- It is contained: `ImageViewerPage`, `MediaViewerPage`, `MediaViewerActivity`,
  `PhotoPrismMediaPreviewUrlFactory` and one layout.

### Possible options / open questions for the maintainer

- Is the `1.5` zoom factor on `imageViewSize` worth roughly 4x the bytes, or would
  `fit_2048` as the HD default be a better trade?
- A preference for the HD size (`fit_2048` / `fit_2560` / `fit_4096` / original), and a
  preference to auto-load HD on unmetered connections, would make the behaviour opt-in for
  users who prefer today's semantics.
- `ImageViewerPage` is shared with the slideshow, which should keep its current behaviour
  and must not show the button.
- Must not regress: swiping, videos, live photos, panoramas, slideshow,
  archive/delete/share/favorite from the viewer, and rotation.

---

## Issue 2 - Bulk uploader with verified upload and bulk local delete

**Type:** feature request

### Context

The gallery can already import files into a library - by sharing them to the app, and,
since 1.46.0, by selecting them from the main screen. What it does not offer is a bulk
path: select a large number of items from the device gallery, upload them reliably in the
background, confirm the server really has them, and then free the space on the phone.

The README is explicit that syncing is out of scope and recommends Autosync, which is a
fair position. This request is deliberately *not* sync: it is a one-directional, explicitly
triggered "move these off my phone" operation, which is a different (and much narrower)
problem than continuous two-way synchronisation. The closest existing reference point is
the iOS "PhotoPrism Uploader".

### Suggested change

1. **Bulk selection** from the device gallery (system photo picker / `MediaStore`), designed
   for hundreds of items rather than a handful.
2. **Background upload with `WorkManager`**, so the transfer survives the app being
   backgrounded or killed, retries with backoff, shows progress in a notification, and
   keeps its queue across reboots (`androidx.work` + `work-rxjava3` and Room are already
   dependencies). This should reuse the existing import code path in `features/importt`
   rather than introducing a second uploader.
3. **Verification by content hash.** PhotoPrism stores a SHA-1 per file, so after uploading,
   the client can compute the local file's SHA-1 and confirm the server reports a file with
   the identical hash. Only then is the item considered verified.
4. **Bulk delete of verified local originals**, as a separate, explicit user action: on
   Android 11+ use `MediaStore.createDeleteRequest()` so the whole batch is confirmed in a
   single system dialog, with a graceful fallback on older versions (the app supports
   `minSdk 21`).

### Safety properties that should hold

- Verification is by hash, not by a 2xx response.
- If verification cannot be completed - server unreachable, file not indexed yet, hash
  missing - the item is not verified and not deletable.
- Deletion is never automatic and never part of the upload step.
- Only local device copies are deleted; nothing is removed from the library.

### Why it may be worth doing upstream

- "My phone is full" is the moment users most need certainty that the server has the file,
  and a hash check is the only answer that is actually trustworthy.
- It builds on machinery the app already has (import, WorkManager, Room, Retrofit, Koin,
  RxJava3) rather than adding a new subsystem.
- It stays inside the project's stated scope boundary: a user-initiated bulk transfer, not
  background sync.

### Open questions for the maintainer

- Is this within the scope the project wants to own, given the explicit "not a sync app"
  position? If not, a "verify my library has these files" tool without the upload half may
  still be useful on its own.
- Should verification be offered standalone, for files uploaded by other means?
- How should partially-verified batches be presented so that the delete action stays
  obviously safe?
