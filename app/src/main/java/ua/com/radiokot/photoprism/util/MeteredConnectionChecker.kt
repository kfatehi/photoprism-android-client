package ua.com.radiokot.photoprism.util

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.getSystemService

/**
 * Tells whether the active connection is metered,
 * so that heavy optional downloads can be avoided.
 */
class MeteredConnectionChecker(
    private val context: Context,
) {
    /**
     * **true** if the active connection is metered
     * or there is no way to tell, **false** otherwise.
     */
    val isConnectionMetered: Boolean
        get() = context.getSystemService<ConnectivityManager>()
            ?.isActiveNetworkMetered
            ?: true
}
