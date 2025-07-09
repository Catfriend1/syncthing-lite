package net.syncthing.lite.library

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.syncthing.java.client.SyncthingClient
import net.syncthing.java.core.configuration.Configuration
import net.syncthing.java.core.exception.ExceptionReport
import net.syncthing.java.repository.EncryptedTempRepository
import net.syncthing.repository.android.SqliteIndexRepository
import net.syncthing.repository.android.TempDirectoryLocalRepository
import net.syncthing.repository.android.database.RepositoryDatabase
import java.io.File
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException

/**
 * This class is used internally to access the syncthing-java library.
 * There should be never more than 1 instance of this class.
 * It cannot be recycled. After shutdown, create a new instance.
 * Creation and shutdown are synchronous — keep them off the UI thread.
 */
class LibraryInstance(
    context: Context,
    private val exceptionReportHandler: (ExceptionReport) -> Unit
) {
    companion object {
        private const val LOG_TAG = "LibraryInstance"

        /**
         * Check if listening port for local discovery is taken by another app.
         */
        private fun checkIsListeningPortTaken(): Boolean {
            return try {
                DatagramSocket(21027, InetAddress.getByName("0.0.0.0")).close()
                false
            } catch (e: SocketException) {
                Log.w(LOG_TAG, e)
                true
            }
        }
    }

    // 🌐 Stable coroutine scope for main-thread reporting
    private val mainScope = MainScope()

    private val tempRepository = EncryptedTempRepository(
        TempDirectoryLocalRepository(
            File(context.filesDir, "temp_repository")
        )
    )

    val isListeningPortTaken = checkIsListeningPortTaken() // must come first
    val configuration = Configuration(configFolder = context.filesDir)

    val syncthingClient = SyncthingClient(
        configuration = configuration,
        repository = SqliteIndexRepository(
            database = RepositoryDatabase.with(context),
            closeDatabaseOnClose = false,
            clearTempStorageHook = { tempRepository.deleteAllTempData() }
        ),
        tempRepository = tempRepository,
        exceptionReportHandler = { ex ->
            Log.w(
                LOG_TAG,
                "${ex.component}\n${ex.detailsReadableString}\n${Log.getStackTraceString(ex.exception)}"
            )

            // 💡 Stable launch via stored MainScope
            mainScope.launch(Dispatchers.Main) {
                exceptionReportHandler(ex)
            }
        }
    )

    val folderBrowser = syncthingClient.indexHandler.folderBrowser
    val indexBrowser = syncthingClient.indexHandler.indexBrowser

    suspend fun shutdown() {
        syncthingClient.close()
        configuration.persistNow()
        mainScope.cancel() // 🧼 Cleanup
    }
}
