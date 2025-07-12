package net.syncthing.lite.library

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException

class CacheFileProvider: ContentProvider() {

    override fun onCreate() = true

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        throw NotImplementedError()
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        throw NotImplementedError()
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        throw NotImplementedError()
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val ctx = requireContextCompat()
        // When creating CacheFileProviderUrl from a URI, we pass the application's package name.
        // This package name serves as the base for the dynamic Authority.
        val url = CacheFileProviderUrl.fromUri(uri, ctx.packageName)
        val file = url.getFile(ctx)

        val resultProjection = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val resultCursor = MatrixCursor(resultProjection)

        if (file.exists()) {
            val builder = resultCursor.newRow()

            for (row in resultProjection) {
                when (row) {
                    OpenableColumns.DISPLAY_NAME -> builder.add(url.filename)
                    OpenableColumns.SIZE -> builder.add(file.length())
                    else -> builder.add(null)
                }
            }
        }

        return resultCursor
    }

    override fun getType(uri: Uri): String {
        val ctx = requireContextCompat()
        return CacheFileProviderUrl.fromUri(uri, ctx.packageName).mimeType
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode == "r") {
            val ctx = requireContextCompat()
            val url = CacheFileProviderUrl.fromUri(uri, ctx.packageName)
            val file = url.getFile(ctx)

            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } else {
            throw IOException("illegal mode")
        }
    }

    private fun requireContextCompat(): Context =
        context ?: throw IllegalStateException("Context is not available")
}

data class CacheFileProviderUrl(
        val pathInCacheDirectory: String,
        val filename: String,
        val mimeType: String,
        val applicationId: String
) {
    companion object {
        private const val PATH = "path"
        private const val FILENAME = "filename"
        private const val MIME_TYPE = "mimeType"
        private const val AUTHORITY_SUFFIX = ".fileprovider"

        /**
         * Creates a CacheFileProviderUrl from a received URI.
         * Validates if the URI's authority matches the expected authority of the current app.
         * @param uri The incoming URI.
         * @param currentApplicationId The package name (applicationId) of the current app.
         */
        fun fromUri(uri: Uri, currentApplicationId: String): CacheFileProviderUrl {
            // The expected authority is formed from the package name and the suffix.
            val expectedAuthority = currentApplicationId + AUTHORITY_SUFFIX
            // Check if the URI's authority matches the expected authority.
            if (uri.authority != expectedAuthority) {
                throw IllegalArgumentException("Invalid authority: ${uri.authority}. Expected: $expectedAuthority")
            }

            return CacheFileProviderUrl(
                pathInCacheDirectory = uri.getQueryParameter(PATH) ?: throw IllegalArgumentException("Missing path"),
                filename = uri.getQueryParameter(FILENAME) ?: throw IllegalArgumentException("Missing filename"),
                mimeType = uri.getQueryParameter(MIME_TYPE) ?: throw IllegalArgumentException("Missing mimeType"),
                applicationId = currentApplicationId
            )
        }

        /**
         * Creates a CacheFileProviderUrl from a File.
         * Retrieves the package name (applicationId) directly from the provided Context.
         * @param file The file for which the URL should be created.
         * @param filename The file name.
         * @param mimeType The MIME type of the file.
         * @param context The Context to access the package name and cache directory.
         */
        fun fromFile(file: File, filename: String, mimeType: String, context: Context) = CacheFileProviderUrl(
                filename = filename,
                mimeType = mimeType,
                pathInCacheDirectory = file.toRelativeString(context.externalCacheDir ?: throw IllegalStateException("No external cache dir")),
                applicationId = context.packageName
        )
    }

    /**
     * Creates the serialized URI for this CacheFileProviderUrl.
     * The Authority is dynamically formed from the stored applicationId.
     */
    val serialized: Uri by lazy {
        Uri.Builder()
                .scheme("content")
                .authority(applicationId + AUTHORITY_SUFFIX) // Uses the stored applicationId for the Authority
                .appendQueryParameter(PATH, pathInCacheDirectory)
                .appendQueryParameter(FILENAME, filename)
                .appendQueryParameter(MIME_TYPE, mimeType)
                .build()
    }

    fun getFile(context: Context): File {
        return File(context.externalCacheDir ?: throw IllegalStateException("No external cache dir"), pathInCacheDirectory)
    }
}
