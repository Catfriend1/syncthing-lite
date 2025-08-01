package net.syncthing.lite.activities

import androidx.lifecycle.Observer
import android.content.Context
import android.content.Intent
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
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import net.syncthing.java.core.beans.DeviceId
import net.syncthing.lite.R
import net.syncthing.lite.activities.QRScannerActivity
import net.syncthing.lite.adapters.IntroFragmentAdapter
import net.syncthing.lite.databinding.ActivityIntroBinding
import net.syncthing.lite.databinding.FragmentIntroOneBinding
import net.syncthing.lite.databinding.FragmentIntroThreeBinding
import net.syncthing.lite.databinding.FragmentIntroTwoBinding
import net.syncthing.lite.fragments.SyncthingFragment
import net.syncthing.lite.utils.Util
import java.io.IOException

/**
 * Shown when a user first starts the app. Shows some info and helps the user to add their first
 * device and folder.
 */
class IntroActivity : SyncthingActivity() {

    companion object {
        private const val ENABLE_TEST_DATA: Boolean = true
        private const val TEST_DEVICE_ID: String = "ELQBG5X-NNNR7JC-NB7P7HF-AAZRSWD-ODAETQG-6OBQZRJ-7V2E7J6-KNMXNQL"
        private const val TAG = "IntroActivity"
    }

    private lateinit var binding: ActivityIntroBinding
    private lateinit var adapter: IntroFragmentAdapter
    private val pageIndicators = mutableListOf<ImageView>()

    /**
     * Initialize fragments and library parameters.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityIntroBinding.inflate(layoutInflater).also { setContentView(it.root) }
        
        adapter = IntroFragmentAdapter(this)
        binding.viewPager.adapter = adapter
        
        setupNavigationButtons()
        setupPageIndicators()
        setupViewPager()
    }

    private fun setupNavigationButtons() {
        binding.btnSkip.setOnClickListener { onDonePressed() }
        binding.btnNext.setOnClickListener { 
            val currentPosition = binding.viewPager.currentItem
            
            // If we're on page 1 (device ID page), validate and import the device ID before proceeding
            if (currentPosition == 1) {
                val fragment = supportFragmentManager.findFragmentByTag("f$currentPosition") as? IntroFragmentTwo
                if (fragment?.validateAndImportDeviceId() == false) {
                    return@setOnClickListener // Don't proceed if validation failed
                }
            }
            
            if (currentPosition < adapter.itemCount - 1) {
                binding.viewPager.currentItem = currentPosition + 1
            } else {
                onDonePressed()
            }
        }
        binding.btnDone.setOnClickListener { onDonePressed() }
    }

    private fun setupPageIndicators() {
        for (i in 0 until adapter.itemCount) {
            val indicator = ImageView(this)
            val size = 12.dpToPx()
            val params = LinearLayout.LayoutParams(size, size)
            params.setMargins(4.dpToPx(), 0, 4.dpToPx(), 0)
            indicator.layoutParams = params
            indicator.setImageResource(R.drawable.ic_circle_outline)
            indicator.setColorFilter(ContextCompat.getColor(this, android.R.color.white))
            binding.pageIndicators.addView(indicator)
            pageIndicators.add(indicator)
        }
        updatePageIndicators(0)
    }

    private fun setupViewPager() {
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updatePageIndicators(position)
                updateNavigationButtons(position)
                
                // Validate current page if it's the device ID page (but don't import yet)
                if (position == 1) {
                    val fragment = supportFragmentManager.findFragmentByTag("f$position") as? IntroFragmentTwo
                    fragment?.let {
                        binding.btnNext.isEnabled = it.isDeviceIdValidForNavigation()
                    }
                }
            }
        })
    }

    private fun updatePageIndicators(position: Int) {
        pageIndicators.forEachIndexed { index, indicator ->
            if (index == position) {
                indicator.setImageResource(R.drawable.ic_circle_filled)
            } else {
                indicator.setImageResource(R.drawable.ic_circle_outline)
            }
        }
    }

    private fun updateNavigationButtons(position: Int) {
        when (position) {
            adapter.itemCount - 1 -> {
                // Last page - show Done button
                binding.btnNext.visibility = View.GONE
                binding.btnDone.visibility = View.VISIBLE
            }
            else -> {
                // Other pages - show Next button
                binding.btnNext.visibility = View.VISIBLE
                binding.btnDone.visibility = View.GONE
            }
        }
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    fun onDonePressed() {
        getSharedPreferences("default", Context.MODE_PRIVATE).edit {
            putBoolean(MainActivity.PREF_IS_FIRST_START, false)
        }
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    fun enableNextButton(enabled: Boolean) {
        binding.btnNext.isEnabled = enabled
    }

    /**
     * Display some simple welcome text.
     */
    class IntroFragmentOne : SyncthingFragment() {
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

            libraryHandler.isListeningPortTaken.observe(viewLifecycleOwner, Observer { isPortTaken ->
                val visibility = if (isPortTaken) View.VISIBLE else View.GONE
                binding.listeningPortTakenTitle.visibility = visibility
                binding.listeningPortTakenMessage.visibility = visibility
            })

            return binding.root
        }
    }

    /**
     * Display device ID entry field and QR scanner option.
     */
    class IntroFragmentTwo : SyncthingFragment() {

        private lateinit var binding: FragmentIntroTwoBinding
        private var qrCodeLauncher: ActivityResultLauncher<Intent>? = null

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            // Register the activity result launcher in onCreate
            qrCodeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == android.app.Activity.RESULT_OK) {
                    val scanResult = result.data?.getStringExtra(QRScannerActivity.SCAN_RESULT)
                    if (scanResult != null && scanResult.isNotBlank()) {
                        binding.enterDeviceId.deviceId.setText(scanResult)
                        binding.enterDeviceId.deviceIdHolder.isErrorEnabled = false
                        (activity as? IntroActivity)?.enableNextButton(true)
                    }
                }
            }
        }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            binding = FragmentIntroTwoBinding.inflate(inflater, container, false)
            binding.enterDeviceId.scanQrCode.setOnClickListener {
                qrCodeLauncher?.let { launcher ->
                    val intent = Intent(requireContext(), QRScannerActivity::class.java)
                    launcher.launch(intent)
                }
            }
            binding.enterDeviceId.scanQrCode.setImageResource(R.drawable.ic_qr_code_white_24dp)

            // Add text watcher to validate device ID (but don't import yet)
            binding.enterDeviceId.deviceId.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val isValid = isDeviceIdValidForNavigation()
                    (activity as? IntroActivity)?.enableNextButton(isValid)
                }
            })

            if (ENABLE_TEST_DATA) {
                binding.enterDeviceId.deviceId.setText(TEST_DEVICE_ID)
                binding.enterDeviceId.deviceIdHolder.isErrorEnabled = false
            }

            return binding.root
        }

        /**
         * Validates the device ID format without importing it.
         * Used for navigation button enable/disable.
         */
        fun isDeviceIdValidForNavigation(): Boolean {
            return try {
                val deviceId = binding.enterDeviceId.deviceId.text.toString()
                if (deviceId.isBlank()) return false
                DeviceId(deviceId) // Just validate format, don't import
                binding.enterDeviceId.deviceIdHolder.isErrorEnabled = false
                true
            } catch (e: Exception) {
                binding.enterDeviceId.deviceIdHolder.error = getString(R.string.invalid_device_id)
                false
            }
        }

        /**
         * Validates and imports the device ID. Called when user clicks Next.
         * Returns true if successful, false if validation failed.
         */
        fun validateAndImportDeviceId(): Boolean {
            return try {
                val deviceId = binding.enterDeviceId.deviceId.text.toString()
                if (deviceId.isBlank()) return false
                Util.importDeviceId(libraryHandler.libraryManager, requireContext(), deviceId, { })
                binding.enterDeviceId.deviceIdHolder.isErrorEnabled = false
                true
            } catch (e: IOException) {
                binding.enterDeviceId.deviceIdHolder.error = getString(R.string.invalid_device_id)
                false
            }
        }

        /**
         * Checks if the entered device ID is valid. If yes, imports it and returns true. If not,
         * sets an error on the textview and returns false.
         * @deprecated Use isDeviceIdValidForNavigation() for UI validation and validateAndImportDeviceId() for import
         */
        fun isDeviceIdValid(): Boolean {
            return try {
                val deviceId = binding.enterDeviceId.deviceId.text.toString()
                if (deviceId.isBlank()) return false
                Util.importDeviceId(libraryHandler.libraryManager, requireContext(), deviceId, { })
                binding.enterDeviceId.deviceIdHolder.isErrorEnabled = false
                true
            } catch (e: IOException) {
                binding.enterDeviceId.deviceIdHolder.error = getString(R.string.invalid_device_id)
                false
            }
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
                                    (activity as? IntroActivity)?.enableNextButton(true)

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
    class IntroFragmentThree : SyncthingFragment() {

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            val binding = FragmentIntroThreeBinding.inflate(inflater, container, false)

            launch {
                val ownDeviceId = libraryHandler.libraryManager.withLibrary { it.configuration.localDeviceId.deviceId }

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
                        (activity as IntroActivity?)?.onDonePressed()
                    }
                }
            }

            return binding.root
        }
    }
}
