package net.syncthing.lite.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.github.paolorotolo.appintro.AppIntro
import com.github.paolorotolo.appintro.ISlidePolicy
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import net.syncthing.java.core.beans.DeviceId
import net.syncthing.lite.R
import net.syncthing.lite.databinding.FragmentIntroOneBinding
import net.syncthing.lite.databinding.FragmentIntroThreeBinding
import net.syncthing.lite.databinding.FragmentIntroTwoBinding
import net.syncthing.lite.fragments.SyncthingFragment
import net.syncthing.lite.utils.FragmentIntentIntegrator
import net.syncthing.lite.utils.Util
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.intentFor
import java.io.IOException

@OptIn(kotlinx.coroutines.ObsoleteCoroutinesApi::class)
class IntroActivity : AppIntro() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        addSlide(IntroFragmentOne())
        addSlide(IntroFragmentTwo())
        addSlide(IntroFragmentThree())

        val typedValue = TypedValue()
        theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
        setSeparatorColor(ContextCompat.getColor(this, typedValue.resourceId))

        showSkipButton(true)
        isProgressButtonEnabled = true
        pager.isPagingEnabled = false
    }

    override fun onSkipPressed(currentFragment: Fragment) {
        onDonePressed(currentFragment)
    }

    override fun onDonePressed(currentFragment: Fragment) {
        defaultSharedPreferences.edit().putBoolean(MainActivity.PREF_IS_FIRST_START, false).apply()
        startActivity(intentFor<MainActivity>())
        finish()
    }

    class IntroFragmentOne : SyncthingFragment() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            launch {
                libraryHandler.libraryManager.withLibrary { library ->
                    library.configuration.update { oldConfig ->
                        oldConfig.copy(localDeviceName = Util.getDeviceName())
                    }

                    library.configuration.persistLater()
                }
            }
        }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            val binding = FragmentIntroOneBinding.inflate(inflater, container, false)

            libraryHandler.isListeningPortTaken.observe(viewLifecycleOwner, Observer {
                binding.listeningPortTaken = it
            })

            return binding.root
        }
    }

    class IntroFragmentTwo : SyncthingFragment(), ISlidePolicy {

        private lateinit var binding: FragmentIntroTwoBinding
        private val addedDeviceIds = HashSet<DeviceId>()

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            binding = DataBindingUtil.inflate(inflater, R.layout.fragment_intro_two, container, false)
            binding.enterDeviceId.scanQrCode.setOnClickListener {
                FragmentIntentIntegrator(this@IntroFragmentTwo).initiateScan()
            }
            binding.enterDeviceId.scanQrCode.setImageResource(R.drawable.ic_qr_code_white_24dp)
            return binding.root
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
            val scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent)
            if (scanResult?.contents != null && scanResult.contents.isNotBlank()) {
                binding.enterDeviceId.deviceId.setText(scanResult.contents)
                binding.enterDeviceId.deviceIdHolder.isErrorEnabled = false
            }
        }

        fun isDeviceIdValid(): Boolean {
            return try {
                val deviceId = binding.enterDeviceId.deviceId.text.toString()
                Util.importDeviceId(libraryHandler.libraryManager, requireContext(), deviceId) {}
                true
            } catch (e: IOException) {
                binding.enterDeviceId.deviceId.error = getString(R.string.invalid_device_id)
                false
            }
        }

        override fun isPolicyRespected() = isDeviceIdValid()

        override fun onUserIllegallyRequestedNextPage() {}

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

        private val onDeviceFound: (DeviceId) -> Unit = { deviceId ->
            if (addedDeviceIds.add(deviceId)) {
                binding.foundDevices.addView(
                    Button(context).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        text = deviceId.deviceId
                        setOnClickListener {
                            binding.enterDeviceId.deviceId.setText(deviceId.deviceId)
                            binding.enterDeviceId.deviceIdHolder.isErrorEnabled = false
                            binding.scroll.scrollTo(0, 0)
                        }
                    }
                )
            }
        }
    }

    class IntroFragmentThree : SyncthingFragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            val binding = FragmentIntroThreeBinding.inflate(inflater, container, false)

            launch {
                val ownDeviceId = libraryHandler.libraryManager.withLibrary { it.configuration.localDeviceId }

                libraryHandler.subscribeToConnectionStatus().consumeEach {
                    val desc = if (it.values.any { conn -> conn.addresses.isNotEmpty() }) {
                        val html = getString(R.string.intro_page_three_description, "<b>$ownDeviceId</b>")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                            Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
                        else
                            Html.fromHtml(html)
                    } else {
                        Html.fromHtml(getString(R.string.intro_page_three_searching_device))
                    }
                    binding.description.text = desc
                }
            }

            launch {
                libraryHandler.subscribeToFolderStatusList().consumeEach {
                    if (it.isNotEmpty()) {
                        (activity as? IntroActivity)?.onDonePressed(this@IntroFragmentThree)
                    }
                }
            }

            return binding.root
        }
    }
}
