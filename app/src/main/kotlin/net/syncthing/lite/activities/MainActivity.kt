package net.syncthing.lite.activities

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import androidx.core.view.GravityCompat
import android.view.MenuItem
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.core.view.get
import androidx.core.view.size
import kotlinx.coroutines.launch
import net.syncthing.lite.R
import net.syncthing.lite.databinding.ActivityMainBinding
import net.syncthing.lite.dialogs.DeviceIdDialogFragment
import net.syncthing.lite.fragments.DevicesFragment
import net.syncthing.lite.fragments.FoldersFragment
import net.syncthing.lite.fragments.SettingsFragment

class MainActivity : SyncthingActivity() {

    companion object {
        const val PREF_IS_FIRST_START = "IS_FIRST_START"
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private var drawerToggle: ActionBarDrawerToggle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MainActivity onCreate() called")

        val prefs = getSharedPreferences("default", Context.MODE_PRIVATE)
        if (prefs.getBoolean(PREF_IS_FIRST_START, true)) {
            Log.d(TAG, "MainActivity first start detected, launching IntroActivity")
            startActivity(Intent(this, IntroActivity::class.java))
            finish()
            return
        }

        Log.d(TAG, "MainActivity not first start, proceeding with normal initialization")
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        drawerToggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, R.string.app_name, R.string.app_name
        )
        binding.drawerLayout.addDrawerListener(drawerToggle!!)
        binding.navigation.setNavigationItemSelectedListener { onNavigationItemSelectedListener(it) }

        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onLibraryLoaded() {
        Log.d(TAG, "MainActivity onLibraryLoaded() called")
        super.onLibraryLoaded()
        
        // Additional connection establishment for MainActivity
        // This is critical for the IntroActivity -> MainActivity transition
        Log.d(TAG, "MainActivity triggering additional connection establishment")
        triggerImmediateConnectionAttempt()
    }

    /**
     * Sync the toggle state and fragment after onRestoreInstanceState has occurred.
     */
    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        Log.d(TAG, "MainActivity onPostCreate() called")

        drawerToggle?.syncState()
        val menu = binding.navigation.menu
        val selection = (0 until menu.size)
            .map { menu[it] }
            .find { it.isChecked }
            ?: menu[0]
        Log.d(TAG, "MainActivity setting initial navigation selection: ${selection.title}")
        onNavigationItemSelectedListener(selection)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        drawerToggle?.onConfigurationChanged(newConfig)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app icon touch event
        return if (drawerToggle?.onOptionsItemSelected(item) == true) {
            true
        // Handle your other action bar items...
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    private fun onNavigationItemSelectedListener(menuItem: MenuItem): Boolean {
        Log.d(TAG, "MainActivity navigation item selected: ${menuItem.title}")
        when (menuItem.itemId) {
            R.id.folders -> {
                Log.d(TAG, "MainActivity switching to FoldersFragment")
                setContentFragment(FoldersFragment())
            }
            R.id.devices -> {
                Log.d(TAG, "MainActivity switching to DevicesFragment")
                setContentFragment(DevicesFragment())
            }
            R.id.settings -> {
                Log.d(TAG, "MainActivity switching to SettingsFragment")
                setContentFragment(SettingsFragment())
            }
            R.id.device_id -> {
                Log.d(TAG, "MainActivity showing DeviceIdDialogFragment")
                DeviceIdDialogFragment().show(supportFragmentManager)
            }
            R.id.clear_index -> {
                Log.d(TAG, "MainActivity showing clear cache dialog")
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.clear_cache_and_index_title))
                    .setMessage(getString(R.string.clear_cache_and_index_body))
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(resources.getText(R.string.yes)) { _, _ -> cleanCacheAndIndex() }
                    .setNegativeButton(resources.getText(R.string.no), null)
                    .show()
            }
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun setContentFragment(fragment: Fragment) {
        Log.d(TAG, "MainActivity setContentFragment() called with ${fragment.javaClass.simpleName}")
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.content_frame, fragment)
            .commit()
    }

    private fun cleanCacheAndIndex() {
        Log.d(TAG, "MainActivity cleanCacheAndIndex() called")
        launch {
            libraryHandler.libraryManager.withLibrary {
                it.syncthingClient.clearCacheAndIndex()
            }
        }
    }
}
