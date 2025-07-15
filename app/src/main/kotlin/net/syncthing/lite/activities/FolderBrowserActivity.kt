package net.syncthing.lite.activities

import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import androidx.databinding.DataBindingUtil
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import kotlinx.coroutines.*
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import net.syncthing.java.bep.index.browser.DirectoryContentListing
import net.syncthing.java.bep.index.browser.DirectoryListing
import net.syncthing.java.bep.index.browser.DirectoryNotFoundListing
import net.syncthing.java.bep.index.browser.IndexBrowser
import net.syncthing.java.core.beans.FileInfo
import net.syncthing.java.core.utils.PathUtils
import net.syncthing.lite.R
import net.syncthing.lite.adapters.FolderContentsAdapter
import net.syncthing.lite.adapters.FolderContentsListener
import net.syncthing.lite.databinding.ActivityFolderBrowserBinding
import net.syncthing.lite.dialogs.EnableFolderSyncForNewDeviceDialog
import net.syncthing.lite.dialogs.FileMenuDialogFragment
import net.syncthing.lite.dialogs.FileUploadDialog
import net.syncthing.lite.dialogs.ReconnectIssueDialogFragment
import net.syncthing.lite.dialogs.downloadfile.DownloadFileDialogFragment
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback

@OptIn(ExperimentalCoroutinesApi::class)
class FolderBrowserActivity : SyncthingActivity() {

    companion object {
        private const val STATUS_PATH = "path"
        const val EXTRA_FOLDER_NAME = "folder_name"
    }

    private lateinit var folder: String
    private lateinit var uploadFileLauncher: ActivityResultLauncher<Intent>
    private var currentUploadDialog: FileUploadDialog? = null

    private val path = MutableStateFlow("")
    private val listing = MutableStateFlow<DirectoryListing?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!goUp()) {
                    finish()
                }
            }
        })

        uploadFileLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                libraryHandler.syncthingClient { syncthingClient ->
                    val currentPath = path.value
                    result.data?.data?.let { uri ->
                        currentUploadDialog?.cleanup() // cleanup any existing dialog
                        currentUploadDialog = FileUploadDialog(
                            this@FolderBrowserActivity,
                            syncthingClient,
                            uri,
                            folder,
                            currentPath,
                            { 
                                // cleanup the dialog reference after successful upload
                                currentUploadDialog = null
                            }
                        )
                        currentUploadDialog?.show()
                    }
                }
            }
        }

        val binding: ActivityFolderBrowserBinding = DataBindingUtil.setContentView(this, R.layout.activity_folder_browser)
        val adapter = FolderContentsAdapter()

        binding.listView.adapter = adapter
        binding.mainListViewUploadHereButton.setOnClickListener {
            uploadFileLauncher.launch(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
            )
        }

        adapter.listener = object : FolderContentsListener {
            override fun onItemClicked(fileInfo: FileInfo) {
                if (fileInfo.isDirectory()) {
                    path.value = fileInfo.path
                } else {
                    DownloadFileDialogFragment.newInstance(fileInfo).show(supportFragmentManager)
                }
            }

            override fun onItemLongClicked(fileInfo: FileInfo): Boolean {
                return if (fileInfo.type == FileInfo.FileType.FILE) {
                    FileMenuDialogFragment.newInstance(fileInfo).show(supportFragmentManager)
                    true
                } else false
            }
        }

        ReconnectIssueDialogFragment.showIfNeeded(this)

        folder = intent.getStringExtra(EXTRA_FOLDER_NAME) ?: throw IllegalArgumentException("Missing folder name")
        path.value = if (savedInstanceState == null) IndexBrowser.ROOT_PATH else savedInstanceState.getString(STATUS_PATH) ?: IndexBrowser.ROOT_PATH

        launch {
            var job = Job()

            path.collect { path ->
                job.cancel()
                job = Job()

                binding.listView.scrollToPosition(0)

                listing.value = null

                async(job) {
                    libraryHandler.libraryManager.streamDirectoryListing(folder, path).consumeEach {
                        listing.emit(it)
                    }
                }
            }
        }

        launch {
            listing.collect { listing ->
                if (listing == null) {
                    binding.isLoading = true
                } else {
                    supportActionBar?.title = if (PathUtils.isRoot(listing.path)) folder else PathUtils.getFileName(listing.path)
                    binding.isLoading = false
                    adapter.data = if (listing is DirectoryContentListing)
                        listing.entries.sortedWith(IndexBrowser.sortAlphabeticallyDirectoriesFirst)
                    else emptyList()
                }
            }
        }

        if (savedInstanceState == null) {
            launch {
                val devicesToAskFor = libraryHandler.libraryManager.withLibrary {
                    val folderInfo = it.configuration.folders.find { it.folderId == folder }
                    val notIgnoredBlacklistEntries = folderInfo?.notIgnoredBlacklistEntries ?: emptySet()
                    notIgnoredBlacklistEntries.mapNotNull { deviceId ->
                        it.configuration.peers.find { peer -> peer.deviceId == deviceId }
                    }
                }

                if (devicesToAskFor.isNotEmpty()) {
                    EnableFolderSyncForNewDeviceDialog.newInstance(
                        folderId = folder,
                        devices = devicesToAskFor,
                        folderName = libraryHandler.libraryManager.withLibrary {
                            it.configuration.folders.find { it.folderId == folder }?.label ?: folder
                        }
                    ).show(supportFragmentManager)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATUS_PATH, path.value)
    }

    private fun goUp(): Boolean {
        val currentListing = listing.value
        val parentPath = when (currentListing) {
            is DirectoryContentListing -> currentListing.parentEntry?.path
            is DirectoryNotFoundListing -> currentListing.theoreticalParentPath
            else -> null
        }

        return if (parentPath == null) {
            false
        } else {
            path.value = parentPath
            true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        // This method is no longer used - replaced by ActivityResultLauncher
        super.onActivityResult(requestCode, resultCode, intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.folder_browser, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.go_home -> {
            finish()
            true
        }
        android.R.id.home -> {
            if (!goUp()) finish()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        currentUploadDialog?.cleanup()
        super.onDestroy()
    }
}
