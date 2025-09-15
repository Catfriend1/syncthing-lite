package net.syncthing.lite.activities

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.get
import androidx.core.view.size
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import net.syncthing.lite.R
import net.syncthing.lite.databinding.ActivityMainBinding
import net.syncthing.lite.dialogs.DeviceIdDialogFragment
import net.syncthing.lite.fragments.DevicesFragment
import net.syncthing.lite.fragments.FoldersFragment
import net.syncthing.lite.fragments.SettingsFragment
import net.syncthing.lite.viewmodels.ConnectionStatusViewModel
import net.syncthing.lite.views.CircularBadgeView

class MainActivity : SyncthingActivity() {

    companion object {
        const val PREF_IS_FIRST_START = "IS_FIRST_START"
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private var drawerToggle: ActionBarDrawerToggle? = null
    
    // ViewModel for managing device connection status
    private val connectionStatusViewModel: ConnectionStatusViewModel by viewModels()
    private var deviceBadge: CircularBadgeView? = null
    private var isObservingConnectionStatus = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("default", Context.MODE_PRIVATE)
        if (prefs.getBoolean(PREF_IS_FIRST_START, true)) {
            startActivity(Intent(this, IntroActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater).also { setContentView(it.root) }

        ViewCompat.setOnApplyWindowInsetsListener(binding.drawerLayout) { _, insets ->
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            binding.navigation.setPadding(
                binding.navigation.paddingLeft,
                systemBarsInsets.top,
                binding.navigation.paddingRight,
                binding.navigation.paddingBottom
            )

            insets
        }

        drawerToggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, R.string.app_name, R.string.app_name
        )
        binding.drawerLayout.addDrawerListener(drawerToggle!!)
        binding.navigation.setNavigationItemSelectedListener { onNavigationItemSelectedListener(it) }

        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    /**
     * Sync the toggle state and fragment after onRestoreInstanceState has occurred.
     */
    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        drawerToggle?.syncState()
        val menu = binding.navigation.menu
        val selection = (0 until menu.size)
            .map { menu[it] }
            .find { it.isChecked }
            ?: menu[0]
        onNavigationItemSelectedListener(selection)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        drawerToggle?.onConfigurationChanged(newConfig)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_actionbar, menu)
        
        // Retrieve and reference the Badge-View from the ActionBar menu
        val badgeItem = menu.findItem(R.id.action_device_badge)
        deviceBadge = badgeItem?.actionView as? CircularBadgeView
        
        // Start observing connection status if library is already loaded
        if (libraryHandler != null) {
            startObservingConnectionStatus()
        }
        
        return true
    }
    
    override fun onLibraryLoaded() {
        super.onLibraryLoaded()
        
        // Initialize ViewModel with LibraryHandler
        connectionStatusViewModel.initialize(libraryHandler)
        
        // Start observing if badge view is already initialized
        startObservingConnectionStatus()
    }
    
    private fun startObservingConnectionStatus() {
        // Only start observing if both badge and library are available and not already observing
        if (deviceBadge != null && libraryHandler != null && !isObservingConnectionStatus) {
            isObservingConnectionStatus = true
            // Observe StateFlow and update badge accordingly
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    connectionStatusViewModel.connectedDeviceCount.collect { count ->
                        deviceBadge?.setDeviceCount(count)
                    }
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app icon touch event
        return if (drawerToggle?.onOptionsItemSelected(item) == true) {
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    private fun onNavigationItemSelectedListener(menuItem: MenuItem): Boolean {
        Log.v(TAG, "Navigation item selected: ${menuItem.title}")
        when (menuItem.itemId) {
            R.id.folders -> setContentFragment(FoldersFragment())
            R.id.devices -> setContentFragment(DevicesFragment())
            R.id.settings -> setContentFragment(SettingsFragment())
            R.id.device_id -> DeviceIdDialogFragment().show(supportFragmentManager)
            R.id.clear_index -> AlertDialog.Builder(this)
                .setTitle(getString(R.string.clear_cache_and_index_title))
                .setMessage(getString(R.string.clear_cache_and_index_body))
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(resources.getText(R.string.yes)) { _, _ -> cleanCacheAndIndex() }
                .setNegativeButton(resources.getText(R.string.no), null)
                .show()
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun setContentFragment(fragment: Fragment) {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.content_frame, fragment)
            .commit()
    }

    private fun cleanCacheAndIndex() {
        Log.v(TAG, "cleanCacheAndIndex() called")
        launch {
            libraryHandler.libraryManager.withLibrary {
                it.syncthingClient.clearCacheAndIndex()
            }
        }
    }
}
