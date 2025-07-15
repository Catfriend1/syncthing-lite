package net.syncthing.lite.library

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import net.syncthing.java.bep.connectionactor.ConnectionInfo
import net.syncthing.java.bep.folder.FolderBrowser
import net.syncthing.java.bep.folder.FolderStatus
import net.syncthing.java.client.SyncthingClient
import net.syncthing.java.core.beans.DeviceId
import net.syncthing.java.core.configuration.Configuration
import java.util.concurrent.atomic.AtomicBoolean

/**
 * This class helps when using the library.
 * It's required to start and stop it to make the callbacks fire (or stop to fire).
 *
 * It's possible to do multiple start and stop cycles with one instance of this class.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryHandler(private val context: Context) {

    companion object {
        private const val TAG = "LibraryHandler"
        private val handler = Handler(Looper.getMainLooper())
    }

    val libraryManager = DefaultLibraryManager.with(context)
    private val isStarted = AtomicBoolean(false)
    private val isListeningPortTakenInternal = MutableLiveData<Boolean>().apply { postValue(false) }
    private val indexUpdateCompleteMessages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    private val folderStatusList = MutableSharedFlow<List<FolderStatus>>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val connectionStatus = MutableStateFlow<Map<DeviceId, ConnectionInfo>>(emptyMap())
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

        libraryManager.startLibraryUsage { libraryInstance ->

            isListeningPortTakenInternal.postValue(libraryInstance.isListeningPortTaken)
            onLibraryLoaded(this)

            val client = libraryInstance.syncthingClient

            client.discoveryHandler.registerMessageFromUnknownDeviceListener(internalMessageFromUnknownDeviceListener)

            job = Job()

            CoroutineScope(job + Dispatchers.IO).launch {
                libraryInstance.syncthingClient.indexHandler
                    .subscribeToOnFullIndexAcquiredEvents()
                    .collect {
                        indexUpdateCompleteMessages.emit(it)
                    }
            }

            CoroutineScope(job + Dispatchers.IO).launch {
                libraryInstance.folderBrowser
                    .folderInfoAndStatusStream()
                    .consumeEach {
                        folderStatusList.emit(it)
                    }
            }

            CoroutineScope(job + Dispatchers.IO).launch {
                libraryInstance.syncthingClient
                    .subscribeToConnectionStatus()
                    .collect {
                        connectionStatus.emit(it)
                    }
            }
        }
    }

    fun stop() {
        if (!isStarted.getAndSet(false)) {
            throw IllegalStateException("already stopped")
        }

        job.cancel()

        CoroutineScope(Dispatchers.IO).launch {
            syncthingClient {
                try {
                    it.discoveryHandler.unregisterMessageFromUnknownDeviceListener(internalMessageFromUnknownDeviceListener)
                } catch (e: IllegalArgumentException) {
                    // ignored
                }
            }
        }

        libraryManager.stopLibraryUsage()
    }

    /*
     * The callback is executed asynchronously.
     * As soon as it returns, there is no guarantee about the availability of the library
     */
    suspend fun library(callback: suspend (Configuration, SyncthingClient, FolderBrowser) -> Unit) {
        withContext(Dispatchers.IO) {
            val completion = CompletableDeferred<Unit>()

            libraryManager.startLibraryUsage { instance ->
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        callback(instance.configuration, instance.syncthingClient, instance.folderBrowser)
                    } finally {
                        libraryManager.stopLibraryUsage()
                        completion.complete(Unit)
                    }
                }
            }

            completion.await()
        }
    }

    fun syncthingClient(callback: suspend (SyncthingClient) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            library { _, s, _ -> callback(s) }
        }
    }

    /**
     * Enhanced version of syncthingClient that ensures connection is available before executing callback.
     * This method automatically triggers reconnection if devices are not connected and waits for connection.
     */
    suspend fun syncthingClientWithConnection(callback: suspend (SyncthingClient) -> Unit) {
        withContext(Dispatchers.IO) {
            library { configuration, syncthingClient, _ ->
                // Check if we have any connected devices
                val connectionStatus = syncthingClient.subscribeToConnectionStatus().value
                val configuredDevices = configuration.peers
                val connectedDevices = configuredDevices.filter { device ->
                    val connection = connectionStatus[device.deviceId]
                    connection?.status == net.syncthing.java.bep.connectionactor.ConnectionStatus.Connected
                }
                
                Log.d(TAG, "syncthingClientWithConnection: ${connectedDevices.size} of ${configuredDevices.size} devices connected")
                
                // If no devices are connected, trigger reconnection and wait
                if (connectedDevices.isEmpty() && configuredDevices.isNotEmpty()) {
                    Log.d(TAG, "No devices connected, triggering reconnection attempts")
                    
                    try {
                        // Try to reconnect to all configured devices
                        configuredDevices.forEach { device ->
                            Log.d(TAG, "Attempting to reconnect to device: ${device.deviceId.deviceId.substring(0, 8)}")
                            try {
                                syncthingClient.reconnect(device.deviceId)
                            } catch (e: Exception) {
                                Log.d(TAG, "Reconnect failed for device ${device.deviceId.deviceId.substring(0, 8)}: ${e.message}")
                            }
                        }
                        
                        // Also trigger discovery for devices without addresses
                        try {
                            syncthingClient.retryDiscovery()
                        } catch (e: Exception) {
                            Log.d(TAG, "Discovery retry failed: ${e.message}")
                        }
                        
                        // Wait for at least one connection to be established
                        Log.d(TAG, "Waiting for connection to be established...")
                        val maxWaitMs = 30000L // 30 seconds timeout
                        val startTime = System.currentTimeMillis()
                        
                        while (System.currentTimeMillis() - startTime < maxWaitMs) {
                            delay(1000) // Check every second
                            
                            try {
                                val currentStatus = syncthingClient.subscribeToConnectionStatus().value
                                val currentConnected = configuredDevices.filter { device ->
                                    val connection = currentStatus[device.deviceId]
                                    connection?.status == net.syncthing.java.bep.connectionactor.ConnectionStatus.Connected
                                }
                                
                                if (currentConnected.isNotEmpty()) {
                                    Log.d(TAG, "Connection established to ${currentConnected.size} device(s)")
                                    break
                                }
                            } catch (e: Exception) {
                                Log.d(TAG, "Connection status check failed: ${e.message}")
                                // Continue waiting
                            }
                        }
                        
                        // Final check - proceed even if no connection (let the operation fail with proper error)
                        try {
                            val finalStatus = syncthingClient.subscribeToConnectionStatus().value
                            val finalConnected = configuredDevices.filter { device ->
                                val connection = finalStatus[device.deviceId]
                                connection?.status == net.syncthing.java.bep.connectionactor.ConnectionStatus.Connected
                            }
                            
                            if (finalConnected.isEmpty()) {
                                Log.w(TAG, "No connections established after waiting ${maxWaitMs}ms - proceeding anyway")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Final connection check failed: ${e.message} - proceeding anyway")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error during connection setup: ${e.message}", e)
                        // Continue with callback execution even if reconnection fails
                    }
                }
                
                // Execute the callback
                try {
                    callback(syncthingClient)
                } catch (e: Exception) {
                    Log.e(TAG, "Callback execution failed: ${e.message}", e)
                    throw e
                }
            }
        }
    }

    fun configuration(callback: suspend (Configuration) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            library { c, _, _ -> callback(c) }
        }
    }

    fun folderBrowser(callback: suspend (FolderBrowser) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            library { _, _, f -> callback(f) }
        }
    }

    // these listeners are called at the UI Thread
    // there is no need to unregister because they removed from the library when close is called
    fun registerMessageFromUnknownDeviceListener(listener: (DeviceId) -> Unit) {
        messageFromUnknownDeviceListeners.add(listener)
    }

    fun unregisterMessageFromUnknownDeviceListener(listener: (DeviceId) -> Unit) {
        messageFromUnknownDeviceListeners.remove(listener)
    }

    fun subscribeToOnFullIndexAcquiredEvents() = indexUpdateCompleteMessages.asSharedFlow()
    fun subscribeToFolderStatusList() = folderStatusList.asSharedFlow()
    fun subscribeToConnectionStatus() = connectionStatus.asStateFlow()
    
    fun retryDiscoveryForDevicesWithoutAddresses() {
        Log.v(TAG, "retryDiscoveryForDevicesWithoutAddresses() called")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Additional logging to help debug discovery issues
                libraryManager.withLibrary { library ->
                    val devices = library.configuration.peers
                    Log.d(TAG, "LibraryHandler found ${devices.size} configured devices for discovery")
                    devices.forEach { device ->
                        Log.d(TAG, "LibraryHandler device for discovery: ${device.deviceId.deviceId.substring(0, 8)}...")
                    }
                }
                
                Log.v(TAG, "Calling syncthingClient.retryDiscovery()")
                syncthingClient { it.retryDiscovery() }
                Log.v(TAG, "syncthingClient.retryDiscovery() completed")
            } catch (e: Exception) {
                Log.e(TAG, "Error in retryDiscoveryForDevicesWithoutAddresses()", e)
            }
        }
    }

    fun disableDiscovery() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.v(TAG, "Calling syncthingClient.disableDiscovery()")
                syncthingClient { it.disableDiscovery() }
                Log.v(TAG, "syncthingClient.disableDiscovery() completed")
            } catch (e: Exception) {
                Log.e(TAG, "Error in disableDiscovery()", e)
            }
        }
    }

    fun enableLocalDiscovery() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.v(TAG, "Calling syncthingClient.enableLocalDiscovery()")
                syncthingClient { it.enableLocalDiscovery() }
                Log.v(TAG, "syncthingClient.enableLocalDiscovery() completed")
            } catch (e: Exception) {
                Log.e(TAG, "Error in enableLocalDiscovery()", e)
            }
        }
    }

    fun disableLocalDiscovery() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.v(TAG, "Calling syncthingClient.disableLocalDiscovery()")
                syncthingClient { it.disableLocalDiscovery() }
                Log.v(TAG, "syncthingClient.disableLocalDiscovery() completed")
            } catch (e: Exception) {
                Log.e(TAG, "Error in disableLocalDiscovery()", e)
            }
        }
    }

    fun enableGlobalDiscovery() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.v(TAG, "Calling syncthingClient.enableGlobalDiscovery()")
                syncthingClient { it.enableGlobalDiscovery() }
                Log.v(TAG, "syncthingClient.enableGlobalDiscovery() completed")
            } catch (e: Exception) {
                Log.e(TAG, "Error in enableGlobalDiscovery()", e)
            }
        }
    }

    fun disableGlobalDiscovery() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.v(TAG, "Calling syncthingClient.disableGlobalDiscovery()")
                syncthingClient { it.disableGlobalDiscovery() }
                Log.v(TAG, "syncthingClient.disableGlobalDiscovery() completed")
            } catch (e: Exception) {
                Log.e(TAG, "Error in disableGlobalDiscovery()", e)
            }
        }
    }
}
