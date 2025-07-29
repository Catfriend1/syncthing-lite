package net.syncthing.java.core.configuration

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.syncthing.java.core.beans.DeviceId
import net.syncthing.java.core.beans.DeviceInfo
import net.syncthing.java.core.beans.FolderInfo
import net.syncthing.java.core.security.KeystoreHandler
import org.bouncycastle.util.encoders.Base64
import net.syncthing.java.core.utils.Logger
import net.syncthing.java.core.utils.LoggerFactory
import java.io.File
import java.io.StringReader
import java.io.StringWriter
import java.net.InetAddress
import java.util.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class Configuration(
    configFolder: File,
    val clientName: String,
    val clientVersion: String
) {
    private val modifyLock = Mutex()
    private val saveLock = Mutex()
    private val configChannel = MutableStateFlow<Config?>(null)

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val configFile = File(configFolder, ConfigFileName)
    val databaseFolder = File(configFolder, DatabaseFolderName)

    private var isSaved = true

    init {
        configFolder.mkdirs()
        databaseFolder.mkdirs()
        assert(configFolder.isDirectory && configFolder.canWrite(), { "Invalid config folder $configFolder" })

        if (!configFile.exists()) {
            var localDeviceName = InetAddress.getLocalHost().hostName
            if (localDeviceName.isEmpty() || localDeviceName == "localhost") {
                localDeviceName = "syncthing-lite"
            }
            val localDeviceId = KeystoreHandler.Loader().generateKeystore(configFolder).deviceId
            isSaved = false
            configChannel.value = Config(peers = setOf(), folders = setOf(),
                            localDeviceName = localDeviceName,
                            localDeviceId = localDeviceId,
                            customDiscoveryServers = emptySet(),
                            useDefaultDiscoveryServers = true
                    )
            runBlocking { persistNow() }
        } else {
            configChannel.value = Config.parse(JsonReader(StringReader(configFile.readText())))
        }
        logger.debug("Loaded Configuration: {}.", configChannel.value!!)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(Configuration::class.java)
        private const val ConfigFileName = "config.json"
        private const val DatabaseFolderName = "database"
    }

    val instanceId = Math.abs(Random().nextLong())

    val localDeviceId: DeviceId
        get() = DeviceId(configChannel.value!!.localDeviceId)

    val discoveryServers: Set<DiscoveryServer>
        get() = configChannel.value!!.let { config ->
            config.customDiscoveryServers + (if (config.useDefaultDiscoveryServers) DiscoveryServer.defaultDiscoveryServers else emptySet())
        }

    val peerIds: Set<DeviceId>
        get() = configChannel.value!!.peers.map { it.deviceId }.toSet()

    val localDeviceName: String
        get() = configChannel.value!!.localDeviceName

    val folders: Set<FolderInfo>
        get() = configChannel.value!!.folders

    val peers: Set<DeviceInfo>
        get() = configChannel.value!!.peers

    fun getConfigFolder(): File = configFile.parentFile

    suspend fun update(operation: suspend (Config) -> Config): Boolean {
        modifyLock.withLock {
            val oldConfig = configChannel.value!!
            val newConfig = operation(oldConfig)

            if (oldConfig != newConfig) {
                configChannel.emit(newConfig)
                isSaved = false

                return true
            } else {
                return false
            }
        }
    }

    suspend fun persistNow() {
        persist()
    }

    fun persistLater() {
        coroutineScope.launch { persist() }
    }

    private suspend fun persist() {
        saveLock.withLock {
            val (config1, isConfig1Saved) = modifyLock.withLock { configChannel.value!! to isSaved }

            if (isConfig1Saved) {
                return
            }

            logger.info("Writing config to {}.", configFile)

            configFile.writeText(
                    StringWriter().apply {
                        JsonWriter(this).apply {
                            setIndent("  ")

                            config1.serialize(this)
                        }
                    }.toString()
            )

            modifyLock.withLock {
                if (config1 === configChannel.value!!) {
                    isSaved = true
                }
            }
        }
    }

    fun shutdown() {
        coroutineScope.cancel()
    }

    fun subscribe() = configChannel.asStateFlow()

    override fun toString() = "Configuration(peers=$peers, folders=$folders, localDeviceName=$localDeviceName, " +
            "localDeviceId=${localDeviceId.deviceId}, discoveryServers=$discoveryServers, instanceId=$instanceId, " +
            "configFile=$configFile, databaseFolder=$databaseFolder)"
}
