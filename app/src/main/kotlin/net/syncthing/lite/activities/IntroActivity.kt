package net.syncthing.lite.activities

import androidx.lifecycle.Observer
import android.content.Context
import android.content.Intent
import androidx.databinding.DataBindingUtil
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AppCompatActivity
import com.github.appintro.AppIntro
import com.github.appintro.SlidePolicy
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import net.syncthing.java.core.beans.DeviceId
import net.syncthing.java.core.beans.DeviceInfo
import net.syncthing.java.bep.connectionactor.ConnectionInfo
import net.syncthing.lite.R
import net.syncthing.lite.activities.QRScannerActivity
import net.syncthing.lite.databinding.FragmentIntroOneBinding
import net.syncthing.lite.databinding.FragmentIntroThreeBinding
import net.syncthing.lite.databinding.FragmentIntroTwoBinding
import net.syncthing.lite.fragments.SyncthingFragment
import net.syncthing.lite.library.LibraryHandler
import net.syncthing.lite.library.ConnectionManager
import net.syncthing.lite.utils.Util
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.CoroutineContext

/**
 * Shown when a user first starts the app. Shows some info and helps the user to add their first
 * device and folder.
 */
class IntroActivity : AppIntro() {

    companion object {
        private const val ENABLE_TEST_DATA: Boolean = true
        private const val TEST_DEVICE_ID: String = "RF2FVSV-DGNA7O7-UM2N4IU-YB6S6CA-5YXBHSV-BGS3M53-PVCCOA4-FHTQOQC"
        private const val TAG = "IntroActivity"
    }

    // Shared LibraryHandler instance across all fragments
    private val sharedLibraryHandler: LibraryHandler by lazy {
        LibraryHandler(context = this@IntroActivity)
    }
    
    private lateinit var connectionManager: ConnectionManager
    private var isStarted = false
    private var currentSlidePosition = 0 // Track current slide position
    private var isSlideThreeActive = false // Track if slide 3 is active

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        connectionManager = ConnectionManager(sharedLibraryHandler, lifecycleScope, TAG)
        
        addSlide(IntroFragmentOne())
        addSlide(IntroFragmentTwo())
        addSlide(IntroFragmentThree())

        val typedValue = TypedValue()
        theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
        setColorDoneText(ContextCompat.getColor(this, typedValue.resourceId))
        isSkipButtonEnabled = true
        isSystemBackButtonLocked = true
        isWizardMode = false
    }

    override fun onStart() {
        super.onStart()
        isStarted = true
        
        sharedLibraryHandler.start {
            // Start connection manager with condition that discovery only runs on slide 3
            connectionManager.start { isOnSlideThree() }
        }
    }

    override fun onStop() {
        super.onStop()
        isStarted = false
        
        connectionManager.stop()
        sharedLibraryHandler.stop()
    }

    override fun onSlideChanged(oldFragment: Fragment?, newFragment: Fragment?) {
        super.onSlideChanged(oldFragment, newFragment)
        
        // Track the current slide position
        when (newFragment) {
            is IntroFragmentOne -> {
                currentSlidePosition = 0
                isSlideThreeActive = false
                sharedLibraryHandler.disableLocalDiscovery()
                sharedLibraryHandler.disableGlobalDiscovery()
            }
            is IntroFragmentTwo -> {
                currentSlidePosition = 1
                isSlideThreeActive = false
                sharedLibraryHandler.enableLocalDiscovery()
                sharedLibraryHandler.disableGlobalDiscovery()
            }
            is IntroFragmentThree -> {
                currentSlidePosition = 2
                isSlideThreeActive = true
                importDeviceIdOnSlideThree()
                sharedLibraryHandler.enableLocalDiscovery()
                sharedLibraryHandler.enableGlobalDiscovery()
            }
        }
    }

    override fun onSkipPressed(currentFragment: Fragment?) {
        onDonePressed(currentFragment)
    }

    override fun onDonePressed(currentFragment: Fragment?) {
        getSharedPreferences("default", Context.MODE_PRIVATE).edit {
            putBoolean(MainActivity.PREF_IS_FIRST_START, false)
        }
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    /**
     * Check if we're currently on slide 3 (IntroFragmentThree)
     */
    private fun isOnSlideThree(): Boolean {
        return isSlideThreeActive
    }

    /**
     * Trigger immediate discovery and connection (called when new devices are added)
     * For IntroActivity, we only trigger discovery if we're on slide 3
     */
    fun triggerImmediateConnectionAttempt() {
        Log.v(TAG, "IntroActivity triggerImmediateConnectionAttempt() called")
        lifecycleScope.launch {
            connectionManager.resetRetryDelay()
            
            // Enable discovery based on current slide
            if (currentSlidePosition >= 1) { // Slide 2 or later
                sharedLibraryHandler.enableLocalDiscovery()
                
                // Only enable global discovery if we're on slide 3 (and device ID is imported)
                if (isOnSlideThree()) {
                    sharedLibraryHandler.enableGlobalDiscovery()
                    connectionManager.tryConnectToAllDevices()
                } else {
                    // On slide 2, only use local discovery and connect to devices with addresses
                    sharedLibraryHandler.disableGlobalDiscovery()
                    connectionManager.tryConnectToAllDevices()
                }
            } else {
                // On slide 1, disable all discovery
                sharedLibraryHandler.disableLocalDiscovery()
                sharedLibraryHandler.disableGlobalDiscovery()
            }
        }
    }

    /**
     * Import device ID when transitioning to slide 3
     */
    private fun importDeviceIdOnSlideThree() {
        Log.v(TAG, "IntroActivity importDeviceIdOnSlideThree() called")
        
        // Find the IntroFragmentTwo to get the device ID
        val fragmentTwo = supportFragmentManager.fragments.find { it is IntroFragmentTwo } as? IntroFragmentTwo
        if (fragmentTwo != null) {
            // Check if device ID is valid and import it
            if (fragmentTwo.isDeviceIdValidForImport()) {
                Log.d(TAG, "IntroActivity device ID is valid, importing now")
                fragmentTwo.importDeviceId()
            } else {
                Log.w(TAG, "IntroActivity device ID is not valid, cannot import")
            }
        } else {
            Log.w(TAG, "IntroActivity could not find IntroFragmentTwo, cannot import device ID")
        }
    }

    /**
     * Special method to trigger discovery when IntroFragmentThree is displayed
     */
    fun triggerDiscoveryOnSlideThree() {
        Log.v(TAG, "IntroActivity triggerDiscoveryOnSlideThree() called")
        lifecycleScope.launch {
            connectionManager.resetRetryDelay()
            
            // First ensure local and global discovery are enabled
            sharedLibraryHandler.enableLocalDiscovery()
            sharedLibraryHandler.enableGlobalDiscovery()
            
            // First ensure we have devices configured
            val devices = sharedLibraryHandler.libraryManager.withLibrary { it.configuration.peers }
            if (devices.isEmpty()) {
                Log.w(TAG, "IntroActivity triggerDiscoveryOnSlideThree no devices configured, cannot trigger discovery")
                return@launch
            }

            // Trigger discovery with additional logging
            Log.v(TAG, "IntroActivity triggering discovery for ${devices.size} devices")
            connectionManager.tryConnectToAllDevices()
        }
    }

    /**
     * Base class for IntroActivity fragments that uses shared LibraryHandler
     */
    abstract class IntroSyncthingFragment : Fragment(), CoroutineScope {
        protected val libraryHandler: LibraryHandler
            get() = (activity as IntroActivity).sharedLibraryHandler
        
        private val job = Job()
        
        override val coroutineContext: CoroutineContext
            get() = job + Dispatchers.Main
        
        override fun onDestroy() {
            super.onDestroy()
            job.cancel()
        }
    }

    /**
     * Display some simple welcome text.
     */
    class IntroFragmentOne : IntroSyncthingFragment() {
        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            launch(Dispatchers.IO) {
                try {
                    libraryHandler.libraryManager.withLibrary { library ->
                        library.configuration.update { oldConfig ->
                            oldConfig.copy(localDeviceName = Util.getDeviceName())
                        }
                        library.configuration.persistLater()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "onViewCreated::launch", e)
                }
            }
        }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            val binding = FragmentIntroOneBinding.inflate(inflater, container, false)

            libraryHandler.isListeningPortTaken.observe(viewLifecycleOwner, Observer { binding.listeningPortTaken = it })

            return binding.root
        }
    }

    /**
     * Display device ID entry field and QR scanner option.
     */
    class IntroFragmentTwo : IntroSyncthingFragment(), SlidePolicy {

        private lateinit var binding: FragmentIntroTwoBinding
        private var qrCodeLauncher: ActivityResultLauncher<Intent>? = null
        private var hasImportedDevice = false

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            // Register the activity result launcher in onCreate
            qrCodeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == android.app.Activity.RESULT_OK) {
                    val scanResult = result.data?.getStringExtra(QRScannerActivity.SCAN_RESULT)
                    if (scanResult != null && scanResult.isNotBlank()) {
                        binding.enterDeviceId.deviceId.setText(scanResult)
                        binding.enterDeviceId.deviceIdHolder.isErrorEnabled = false
                        hasImportedDevice = false
                    }
                }
            }
        }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            binding = DataBindingUtil.inflate(inflater, R.layout.fragment_intro_two, container, false)
            binding.enterDeviceId.scanQrCode.setOnClickListener {
                qrCodeLauncher?.let { launcher ->
                    val intent = Intent(requireContext(), QRScannerActivity::class.java)
                    launcher.launch(intent)
                }
            }
            binding.enterDeviceId.scanQrCode.setImageResource(R.drawable.ic_qr_code_white_24dp)

            // Reset import flag when device ID text changes
            binding.enterDeviceId.deviceId.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    hasImportedDevice = false
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })

            if (ENABLE_TEST_DATA) {
                binding.enterDeviceId.deviceId.setText(TEST_DEVICE_ID)
                binding.enterDeviceId.deviceIdHolder.isErrorEnabled = false
            }

            return binding.root
        }

        /**
         * Checks if the entered device ID is valid. Does NOT import it.
         */
        fun isDeviceIdValid(): Boolean {
            return try {
                val deviceId = binding.enterDeviceId.deviceId.text.toString()
                // Just validate the device ID format
                DeviceId(deviceId.uppercase(Locale.getDefault()))
                binding.enterDeviceId.deviceIdHolder.isErrorEnabled = false
                true
            } catch (e: Exception) {
                Log.e(TAG, "IntroFragmentTwo device ID validation failed", e)
                binding.enterDeviceId.deviceId.error = getString(R.string.invalid_device_id)
                false
            }
        }

        /**
         * Checks if the entered device ID is valid for import (not already imported)
         */
        fun isDeviceIdValidForImport(): Boolean {
            return isDeviceIdValid() && !hasImportedDevice
        }

        /**
         * Imports the device ID if it's valid and not already imported
         */
        fun importDeviceId() {
            try {
                val deviceId = binding.enterDeviceId.deviceId.text.toString()
                
                // Validate the device ID format first
                DeviceId(deviceId.uppercase(Locale.getDefault()))
                
                // Only import once
                if (!hasImportedDevice) {
                    Log.d(TAG, "IntroFragmentTwo importing device ID")
                    Util.importDeviceId(libraryHandler.libraryManager, requireContext(), deviceId) {
                        Log.d(TAG, "IntroFragmentTwo device ID imported successfully")
                        hasImportedDevice = true
                    }
                } else {
                    Log.w(TAG, "IntroFragmentTwo device ID already imported, skipping")
                }
            } catch (e: Exception) {
                Log.e(TAG, "IntroFragmentTwo device ID import failed", e)
                binding.enterDeviceId.deviceId.error = getString(R.string.invalid_device_id)
            }
        }

        override val isPolicyRespected: Boolean
            get() = isDeviceIdValid()

        override fun onUserIllegallyRequestedNextPage() {
            // nothing to do, but some user feedback would be nice
        }

        private val addedDeviceIds = HashSet<DeviceId>()

        override fun onResume() {
            super.onResume()

            binding.foundDevices.removeAllViews()
            addedDeviceIds.clear()

            libraryHandler.registerMessageFromUnknownDeviceListener(onDeviceFound)
        }

        override fun onPause() {
            super.onPause()

            libraryHandler.unregisterMessageFromUnknownDeviceListener(onDeviceFound)
        }

        private val onDeviceFound: (DeviceId) -> Unit = {
            deviceId ->

                if (addedDeviceIds.add(deviceId)) {
                    binding.foundDevices.addView(
                            Button(context).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT
                                )
                                text = deviceId.deviceId

                                setOnClickListener {
                                    binding.enterDeviceId.deviceId.setText(deviceId.deviceId)
                                    binding.enterDeviceId.deviceIdHolder.isErrorEnabled = false
                                    hasImportedDevice = false

                                    binding.scroll.scrollTo(0, 0)
                                }
                            }
                    )
                }
        }
    }

    /**
     * Waits until remote device connects with new folder.
     */
    class IntroFragmentThree : IntroSyncthingFragment() {

        private lateinit var binding: FragmentIntroThreeBinding

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            binding = FragmentIntroThreeBinding.inflate(inflater, container, false)

            launch {
                Log.v(TAG, "IntroFragmentThree starting connection status monitoring")
                val ownDeviceId = libraryHandler.libraryManager.withLibrary { it.configuration.localDeviceId }

                libraryHandler.subscribeToConnectionStatus().collect { connectionInfo ->
                    val devices = libraryHandler.libraryManager.withLibrary { it.configuration.peers }

                    val hasConnectedDevice = connectionInfo.values.find { it.addresses.isNotEmpty() } != null

                    if (hasConnectedDevice) {
                        val desc = activity?.getString(R.string.intro_page_three_description, "<b>$ownDeviceId</b>")
                        val spanned = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            Html.fromHtml(desc, Html.FROM_HTML_MODE_LEGACY)
                        } else {
                            @Suppress("DEPRECATION")
                            Html.fromHtml(desc)
                        }
                        binding.description.text = spanned
                    } else {
                        binding.description.text = getString(R.string.intro_page_three_searching_device)
                    }
                    
                    // Update discovery status display
                    updateDiscoveryStatus(devices, connectionInfo)
                }
            }

            launch {
                libraryHandler.subscribeToFolderStatusList().collect {
                    if (it.isNotEmpty()) {
                        Log.v(TAG, "IntroFragmentThree folders found, finishing intro")
                        (activity as IntroActivity?)?.onDonePressed(null)
                    }
                }
            }

            return binding.root
        }

        private fun updateDiscoveryStatus(devices: Set<DeviceInfo>, connectionInfo: Map<DeviceId, ConnectionInfo>) {
            Log.v(TAG, "IntroFragmentThree updateDiscoveryStatus() called with ${devices.size} devices")
            
            if (devices.isEmpty()) {
                // No devices added yet, show searching
                binding.discoveryStatusText.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_search, 0, 0, 0)
                binding.discoveryStatusText.text = getString(R.string.discovery_status_searching)
                return
            }

            // Check the discovery status of all devices
            val devicesWithAddresses = devices.filter { device ->
                val connection = connectionInfo[device.deviceId] ?: ConnectionInfo.empty
                connection.addresses.isNotEmpty()
            }
            
            val devicesWithoutAddresses = devices.filter { device ->
                val connection = connectionInfo[device.deviceId] ?: ConnectionInfo.empty
                connection.addresses.isEmpty()
            }

            Log.v(TAG, "IntroFragmentThree devices with addresses: ${devicesWithAddresses.size}, without addresses: ${devicesWithoutAddresses.size}")

            when {
                devicesWithAddresses.isNotEmpty() -> {
                    // At least one device has addresses - show success
                    binding.discoveryStatusText.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.checkbox_on_background, 0, 0, 0)
                    if (devicesWithAddresses.size == 1) {
                        val connection = connectionInfo[devicesWithAddresses.first().deviceId]
                        val address = connection?.addresses?.firstOrNull()?.address ?: "unknown"
                        binding.discoveryStatusText.text = getString(R.string.discovery_status_found, address)
                    } else {
                        binding.discoveryStatusText.text = getString(R.string.discovery_status_found, "${devicesWithAddresses.size} addresses")
                    }
                }
                devicesWithoutAddresses.isNotEmpty() -> {
                    // All devices have no addresses - show error
                    binding.discoveryStatusText.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_delete, 0, 0, 0)
                    binding.discoveryStatusText.text = getString(R.string.discovery_status_not_found)
                }
                else -> {
                    // Still searching
                    binding.discoveryStatusText.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_search, 0, 0, 0)
                    binding.discoveryStatusText.text = getString(R.string.discovery_status_searching)
                }
            }
        }

        override fun onResume() {
            super.onResume()
            
            // Wait a bit to ensure the library is fully loaded before triggering discovery
            lifecycleScope.launch {
                // Enable local and global discovery first
                libraryHandler.enableLocalDiscovery()
                libraryHandler.enableGlobalDiscovery()
                
                // Check if we have devices configured
                val devices = libraryHandler.libraryManager.withLibrary { it.configuration.peers }
                Log.d(TAG, "IntroFragmentThree found ${devices.size} configured devices")
                
                if (devices.isNotEmpty()) {
                    (activity as? IntroActivity)?.triggerDiscoveryOnSlideThree()
                } else {
                    Log.w(TAG, "IntroFragmentThree no devices configured, cannot trigger discovery")
                }
            }
        }
    }
}
