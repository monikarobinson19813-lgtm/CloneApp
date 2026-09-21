package com.cloneapp.ca

import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.cloneapp.core.VirtualProviderAuthorityRouter

/**
 * CA-side provider bridge for virtual authorities.
 *
 * Callers operate on a CA virtual authority that is unique per virtual user. Before Android
 * dispatch, the bridge resolves that authority to the guest's physical provider authority and
 * injects the virtual-user segment required by the v0.1 controlled provider contract.
 */
class VirtualProviderDispatcher(
    private val contentResolver: ContentResolver,
) {
    fun virtualUri(
        packageName: String,
        virtualUserId: Int,
        physicalAuthority: String,
        vararg pathSegments: String,
    ): Uri {
        val route = VirtualProviderAuthorityRouter.route(
            packageName = packageName,
            virtualUserId = virtualUserId,
            physicalAuthority = physicalAuthority,
        )

        return Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(route.virtualAuthority)
            .apply {
                pathSegments.forEach { segment ->
                    require(segment.isNotBlank()) { "Provider path segments must not be blank" }
                    appendPath(segment)
                }
            }
            .build()
    }

    fun resolveForDispatch(virtualUri: Uri): Uri {
        require(virtualUri.scheme == ContentResolver.SCHEME_CONTENT) {
            "Expected content URI: $virtualUri"
        }

        val virtualAuthority = requireNotNull(virtualUri.authority) {
            "Virtual provider URI has no authority: $virtualUri"
        }
        val route = requireNotNull(VirtualProviderAuthorityRouter.resolve(virtualAuthority)) {
            "Unrecognized CA virtual provider authority: $virtualAuthority"
        }

        return Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(route.physicalAuthority)
            .appendPath("vusers")
            .appendPath(route.virtualUserId.toString())
            .apply {
                virtualUri.pathSegments.forEach { appendPath(it) }
                encodedQuery(virtualUri.encodedQuery)
                fragment(virtualUri.fragment)
            }
            .build()
    }

    fun query(
        virtualUri: Uri,
        projection: Array<String>? = null,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = null,
    ): Cursor? = contentResolver.query(
        resolveForDispatch(virtualUri),
        projection,
        selection,
        selectionArgs,
        sortOrder,
    )

    fun insert(virtualUri: Uri, values: ContentValues): Uri? =
        contentResolver.insert(resolveForDispatch(virtualUri), values)

    fun delete(
        virtualUri: Uri,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
    ): Int = contentResolver.delete(
        resolveForDispatch(virtualUri),
        selection,
        selectionArgs,
    )
}
