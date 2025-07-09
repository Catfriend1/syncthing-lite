package net.syncthing.lite.library

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
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

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.ObsoleteCoroutinesApi::class)
class LibraryHandler(context: Context) {

    companion object {
        private const val TAG = "LibraryHandler"
        private val handler = Handler(Looper.getMainLooper())
    }

    val libraryManager = DefaultLibraryManager.with(context)

    private val isStarted = AtomicBoolean(false)
    private val isListeningPortTakenInternal = MutableLiveData<Boolean>().apply { postValue(false) }
    val isListeningPortTaken: LiveData<Boolean> = isListeningPortTakenInternal

    private val indexUpdateCompleteMessages = BroadcastChannel<String>(capacity = 16)
    private val folderStatusList = BroadcastChannel<List<FolderStatus>>(capacity = Channel.CONFLATED)
    private val connectionStatus = ConflatedBroadcastChannel<Map<DeviceId, ConnectionInfo>>()

    private val messageFromUnknownDeviceListeners = HashSet<(DeviceId) -> Unit>()
    private val internalMessageFromUnknownDeviceListener: (DeviceId) -> Unit = { deviceId ->
        handler.post {
            messageFromUnknownDeviceListeners.forEach { listener -> listener(deviceId) }
        }
    }

    private var job: Job = Job()
    private lateinit var scope: CoroutineScope

    fun start(onLibraryLoaded: (LibraryHandler) -> Unit = {}) {
        if (isStarted.getAndSet(true)) {
            throw IllegalStateException("already started")
        }

        libraryManager.startLibraryUsage { libraryInstance ->
            isListeningPortTakenInternal.value = libraryInstance.isListeningPortTaken
            onLibraryLoaded(this)

            val client = libraryInstance.syncthingClient
            client.discoveryHandler.registerMessageFromUnknownDeviceListener(internalMessageFromUnknownDeviceListener)

            job = Job()
            scope = CoroutineScope(job + Dispatchers.IO)

            scope.launch {
                libraryInstance.syncthingClient.indexHandler.subscribeToOnFullIndexAcquiredEvents().consumeEach {
                    indexUpdateCompleteMessages.send(it)
                }
            }

            scope.launch {
                libraryInstance.folderBrowser.folderInfoAndStatusStream().consumeEach {
                    folderStatusList.send(it)
                }
            }

            scope.launch {
                libraryInstance.syncthingClient.subscribeToConnectionStatus().consumeEach {
                    connectionStatus.send(it)
                }
            }
        }
    }

    fun stop() {
        if (isStarted.getAndSet(false).not()) {
            throw IllegalStateException("already stopped")
        }

        job.cancel() // 🧹 cancel all coroutines

        syncthingClient {
            try {
                it.discoveryHandler.unregisterMessageFromUnknownDeviceListener(internalMessageFromUnknownDeviceListener)
            } catch (e: IllegalArgumentException) {
                // ignored — can happen if listener wasn't registered
            }
        }

        libraryManager.stopLibraryUsage()
    }

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
