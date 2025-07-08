package net.syncthing.lite.library

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import net.syncthing.java.bep.connectionactor.ConnectionInfo
import net.syncthing.java.bep.folder.FolderBrowser
import net.syncthing.java.bep.folder.FolderStatus
import net.syncthing.java.client.SyncthingClient
import net.syncthing.java.core.beans.DeviceId
import net.syncthing.java.core.configuration.Configuration
import org.jetbrains.anko.doAsync
import java.util.concurrent.atomic.AtomicBoolean

/**
 * This class helps when using the library.
 * It's required to start and stop it to make the callbacks fire (or stop to fire).
 *
 * It's possible to do multiple start and stop cycles with one instance of this class.
 */
@OptIn(ExperimentalCoroutinesApi::class, ObsoleteCoroutinesApi::class)
class LibraryHandler(context: Context) {

    companion object {
        private const val TAG = "LibraryHandler"
        private val handler = Handler(Looper.getMainLooper())
    }

    val libraryManager = DefaultLibraryManager.with(context)
    private val isStarted = AtomicBoolean(false)
    private val isListeningPortTakenInternal = MutableLiveData<Boolean>().apply { postValue(false) }
    private val indexUpdateCompleteMessages = BroadcastChannel<String>(capacity = 16)
    private val folderStatusList = BroadcastChannel<List<FolderStatus>>(capacity = Channel.CONFLATED)
    private val connectionStatus = ConflatedBroadcastChannel<Map<DeviceId, ConnectionInfo>>()
    private var job: Job = Job()

    val isListeningPortTaken: LiveData<Boolean> = isListeningPortTakenInternal

    private val messageFromUnknownDeviceListeners = HashSet<(DeviceId) -> Unit>()
    private val internalMessageFromUnknownDeviceListener: (DeviceId) -> Unit = { deviceId ->
        handler.post {
            messageFromUnknownDeviceListeners.forEach { listener -> listener(deviceId) }
        }
    }

    fun start(onLibraryLoaded: (LibraryHandler) -> Unit = {}) {
        if (isStarted.getAndSet(true)) {
            throw IllegalStateException("already started")
        }

        Log.d(TAG, "Calling startLibraryUsage...")

        libraryManager.startLibraryUsage { libraryInstance ->
            Log.d(TAG, "startLibraryUsage callback entered")

            isListeningPortTakenInternal.value = libraryInstance.isListeningPortTaken
            onLibraryLoaded(this)

            val client = libraryInstance.syncthingClient
            Log.d(TAG, "Registering unknown device listener...")
            client.discoveryHandler.registerMessageFromUnknownDeviceListener(internalMessageFromUnknownDeviceListener)

            job = Job()

            CoroutineScope(job).launch {
                Log.d(TAG, "Launching index subscription...")
                libraryInstance.syncthingClient.indexHandler.subscribeToOnFullIndexAcquiredEvents().consumeEach {
                    Log.d(TAG, "Received index update: $it")
                    indexUpdateCompleteMessages.send(it)
                }
            }

            CoroutineScope(job).launch {
                Log.d(TAG, "Launching folder status stream...")
                libraryInstance.folderBrowser.folderInfoAndStatusStream().consumeEach {
                    Log.d(TAG, "Received folder status update")
                    folderStatusList.send(it)
                }
            }

            CoroutineScope(job).launch {
                Log.d(TAG, "Launching connection status stream...")
                libraryInstance.syncthingClient.subscribeToConnectionStatus().consumeEach {
                    Log.d(TAG, "Received connection status update")
                    connectionStatus.send(it)
                }
            }
        }
    }

    fun stop() {
        if (!isStarted.getAndSet(false)) {
            throw IllegalStateException("already stopped")
        }

        Log.d(TAG, "Stopping library usage...")
        job.cancel()

        syncthingClient {
            try {
                Log.d(TAG, "Unregistering unknown device listener")
                it.discoveryHandler.unregisterMessageFromUnknownDeviceListener(internalMessageFromUnknownDeviceListener)
            } catch (e: IllegalArgumentException) {
                // ignored, no idea why this is thrown
                Log.w(TAG, "Failed to unregister listener", e)
            }
        }

        libraryManager.stopLibraryUsage()
    }

    /*
     * The callback is executed asynchronously.
     * As soon as it returns, there is no guarantee about the availability of the library
     */
    fun library(callback: (Configuration, SyncthingClient, FolderBrowser) -> Unit) {
        libraryManager.startLibraryUsage {
            doAsync {
                try {
                    callback(it.configuration, it.syncthingClient, it.folderBrowser)
                } finally {
                    libraryManager.stopLibraryUsage()
                }
            }
        }
    }

    fun syncthingClient(callback: (SyncthingClient) -> Unit) {
        library { _, s, _ -> callback(s) }
    }

    fun configuration(callback: (Configuration) -> Unit) {
        library { c, _, _ -> callback(c) }
    }

    fun folderBrowser(callback: (FolderBrowser) -> Unit) {
        library { _, _, f -> callback(f) }
    }

    // these listeners are called at the UI Thread
    // there is no need to unregister because they removed from the library when close is called
    fun registerMessageFromUnknownDeviceListener(listener: (DeviceId) -> Unit) {
        messageFromUnknownDeviceListeners.add(listener)
    }

    fun unregisterMessageFromUnknownDeviceListener(listener: (DeviceId) -> Unit) {
        messageFromUnknownDeviceListeners.remove(listener)
    }

    fun subscribeToOnFullIndexAcquiredEvents() = indexUpdateCompleteMessages.openSubscription()
    fun subscribeToFolderStatusList() = folderStatusList.openSubscription()
    fun subscribeToConnectionStatus() = connectionStatus.openSubscription()
}
