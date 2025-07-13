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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import net.syncthing.java.core.beans.DeviceId
import net.syncthing.lite.R
import net.syncthing.lite.activities.QRScannerActivity
import net.syncthing.lite.databinding.FragmentIntroOneBinding
import net.syncthing.lite.databinding.FragmentIntroThreeBinding
import net.syncthing.lite.databinding.FragmentIntroTwoBinding
import net.syncthing.lite.fragments.SyncthingFragment
import net.syncthing.lite.library.LibraryHandler
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

    /**
     * Initialize fragments and library parameters.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        // Start the shared LibraryHandler
        sharedLibraryHandler.start()
    }

    override fun onStop() {
        super.onStop()
        // Stop the shared LibraryHandler
        sharedLibraryHandler.stop()
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
         * Checks if the entered device ID is valid. If yes, imports it (only once) and returns true. If not,
         * sets an error on the textview and returns false.
         */
        fun isDeviceIdValid(): Boolean {
            return try {
                val deviceId = binding.enterDeviceId.deviceId.text.toString()
                // Just validate the device ID format first
                DeviceId(deviceId.uppercase(Locale.getDefault()))
                
                // Only import once
                if (!hasImportedDevice) {
                    Util.importDeviceId(libraryHandler.libraryManager, requireContext(), deviceId) {
                        hasImportedDevice = true
                    }
                }
                true
            } catch (e: Exception) {
                binding.enterDeviceId.deviceId.error = getString(R.string.invalid_device_id)
                false
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

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            val binding = FragmentIntroThreeBinding.inflate(inflater, container, false)

            launch {
                val ownDeviceId = libraryHandler.libraryManager.withLibrary { it.configuration.localDeviceId }

                libraryHandler.subscribeToConnectionStatus().collect {
                    if (it.values.find { it.addresses.isNotEmpty() } != null) {
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
                }
            }

            launch {
                libraryHandler.subscribeToFolderStatusList().collect {
                    if (it.isNotEmpty()) {
                        (activity as IntroActivity?)?.onDonePressed(null)
                    }
                }
            }

            return binding.root
        }

        override fun onResume() {
            super.onResume()
            // Trigger connection attempts when this fragment becomes active
            launch(Dispatchers.IO) {
                try {
                    libraryHandler.libraryManager.withLibrary { library ->
                        // Ensure discovery and connection processes are active
                        library.syncthingClient.connectToNewlyAddedDevices()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "onResume::launch", e)
                }
            }
        }
    }
}
