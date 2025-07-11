package net.syncthing.lite.library

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import java.util.concurrent.Executors
import net.syncthing.java.bep.index.browser.DirectoryListing

/**
 * This class manages the access to an LibraryInstance
 *
 * Users can get an instance with startLibraryUsage()
 * If they are done with it, the should call stopLibraryUsage()
 * After this, it's NOT safe to continue using the received LibraryInstance
 *
 * Every call to startLibraryUsage should be followed by an call to stopLibraryUsage,
 * even if the callback was not called yet. It can still be called, so users should watch out.
 *
 * All listeners are executed at the UI Thread (except the synchronousInstanceCreator)
 *
 * The userCounterListener is always called before the isRunningListener
 *
 * The listeners are called for all changes, nothing is skipped or batched
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LibraryManager(
    val synchronousInstanceCreator: () -> LibraryInstance,
    val userCounterListener: (Int) -> Unit = {},
    val isRunningListener: (isRunning: Boolean) -> Unit = {}
) {
    companion object {
        private val handler = Handler(Looper.getMainLooper())
        private const val TAG = "LibraryManager"
    }

    // this must be a SingleThreadExecutor to avoid race conditions
    // only this Thread should access instance and userCounter
    private val startStopExecutor = Executors.newSingleThreadExecutor()

    private val instanceStream = MutableStateFlow<LibraryInstance?>(null)
    private var userCounter = 0

    fun startLibraryUsage(callback: (LibraryInstance) -> Unit) {
        // Log.d(TAG, "startLibraryUsage - called")
        startStopExecutor.submit {
            // Log.d(TAG, "startLibraryUsage - in executor thread")
            val newUserCounter = ++userCounter
            // Log.d(TAG, "startLibraryUsage - incremented userCounter: $newUserCounter")
            handler.post { 
                // Log.d(TAG, "startLibraryUsage - calling userCounterListener on handler thread with value $newUserCounter")
                userCounterListener(newUserCounter)
            }

            if (instanceStream.value == null) {
                instanceStream.value = synchronousInstanceCreator()
                handler.post { isRunningListener(true) }
            } else {
                // Log.d(TAG, "startLibraryUsage - instance already exists")
            }

            handler.post { 
                // Log.d(TAG, "startLibraryUsage - invoking callback with instance: ${instanceStream.value}")
                callback(instanceStream.value!!)
            }
        }
    }

    suspend fun startLibraryUsageCoroutine(): LibraryInstance {
        // Log.d(TAG, "startLibraryUsageCoroutine - called")
        return suspendCoroutine { continuation ->
            // Log.d(TAG, "startLibraryUsageCoroutine - inside suspendCoroutine")
            startLibraryUsage { instance ->
                // Log.d(TAG, "startLibraryUsageCoroutine - startLibraryUsage callback with instance: $instance")
                continuation.resume(instance)
            }
        }
    }

    suspend fun <T> withLibrary(action: suspend (LibraryInstance) -> T): T {
        // Log.d(TAG, "withLibrary - called")
        val instance = startLibraryUsageCoroutine()
        // Log.d(TAG, "withLibrary - obtained instance: $instance")
        return try {
            action(instance)
        } finally {
            // Log.d(TAG, "withLibrary - finally: calling stopLibraryUsage")
            stopLibraryUsage()
        }
    }

    fun stopLibraryUsage() {
        // Log.d(TAG, "stopLibraryUsage - called")
        startStopExecutor.submit {
            // Log.d(TAG, "stopLibraryUsage - in executor thread")
            val newUserCounter = --userCounter
            // Log.d(TAG, "stopLibraryUsage - decremented userCounter: $newUserCounter")

            if (newUserCounter < 0) {
                userCounter = 0
                Log.e(TAG, "stopLibraryUsage - tried to stop usage when no users exist")
                throw IllegalStateException("can not stop library usage if there are 0 users")
            }

            handler.post { 
                // Log.d(TAG, "stopLibraryUsage - calling userCounterListener on handler thread with value $newUserCounter")
                userCounterListener(newUserCounter)
            }
        }
    }

    fun shutdownIfThereAreZeroUsers(listener: (wasShutdownPerformed: Boolean) -> Unit = {}) {
        // Log.d(TAG, "shutdownIfThereAreZeroUsers - called")
        startStopExecutor.submit {
            // Log.d(TAG, "shutdownIfThereAreZeroUsers - in executor thread")
            if (userCounter == 0) {
                // Log.d(TAG, "shutdownIfThereAreZeroUsers - userCounter == 0, shutting down instance")
                runBlocking { 
                    // Log.d(TAG, "shutdownIfThereAreZeroUsers - running shutdown on instanceStream.value")
                    instanceStream.value?.shutdown()
                }
                instanceStream.value = null
                handler.post { 
                    // Log.d(TAG, "shutdownIfThereAreZeroUsers - calling isRunningListener(false) on handler thread")
                    isRunningListener(false)
                }
                handler.post { 
                    // Log.d(TAG, "shutdownIfThereAreZeroUsers - calling listener(true) on handler thread")
                    listener(true)
                }
            } else {
                // Log.d(TAG, "shutdownIfThereAreZeroUsers - userCounter != 0 ($userCounter), not shutting down")
                handler.post { 
                    // Log.d(TAG, "shutdownIfThereAreZeroUsers - calling listener(false) on handler thread")
                    listener(false)
                }
            }
        }
    }

    fun streamDirectoryListing(folder: String, path: String): ReceiveChannel<DirectoryListing> =
        CoroutineScope(Dispatchers.IO).produce {
            var job = Job()
            // Log.d(TAG, "streamDirectoryListing - started for folder=$folder, path=$path")

            instanceStream.collect { instance ->
                // Log.d(TAG, "streamDirectoryListing - instanceStream collected new value: $instance")
                job.cancel()
                job = Job()

                if (instance != null) {
                    // Log.d(TAG, "streamDirectoryListing - launching async job for indexBrowser")
                    async(job) {
                        instance.indexBrowser.streamDirectoryListing(folder, path).consumeEach {
                            // Log.d(TAG, "streamDirectoryListing - sending DirectoryListing: $it")
                            send(it)
                        }
                    }
                }
            }
        }
}
