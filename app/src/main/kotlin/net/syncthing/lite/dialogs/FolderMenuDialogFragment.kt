package net.syncthing.lite.dialogs

import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.os.Bundle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import androidx.fragment.app.FragmentManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import net.syncthing.java.core.beans.FileInfo
import net.syncthing.lite.databinding.DialogFolderBinding
import net.syncthing.lite.dialogs.downloadfolder.FolderDownloadDialogFragment
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import android.widget.Toast
import kotlinx.coroutines.launch
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.syncthing.java.bep.index.browser.DirectoryContentListing
import net.syncthing.lite.activities.SyncthingActivity
import net.syncthing.lite.R

class FolderMenuDialogFragment: BottomSheetDialogFragment() {
    companion object {
        private const val ARG_FOLDER_NAME = "folder_name"
        private const val ARG_FOLDER_PATH = "folder_path"
        private const val ARG_FOLDER_ID = "folder_id"
        private const val TAG = "FolderMenuDialog"

        fun newInstance(folderInfo: FileInfo) = FolderMenuDialogFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_FOLDER_NAME, folderInfo.fileName)
                putString(ARG_FOLDER_PATH, folderInfo.path)
                putString(ARG_FOLDER_ID, folderInfo.folder)
            }
        }
    }

    private lateinit var selectFolderLauncher: ActivityResultLauncher<Intent>

    private val folderName: String by lazy { requireArguments().getString(ARG_FOLDER_NAME)!! }
    private val folderPath: String by lazy { requireArguments().getString(ARG_FOLDER_PATH)!! }
    private val folderId: String by lazy { requireArguments().getString(ARG_FOLDER_ID)!! }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        selectFolderLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    // TODO: Start folder download process
                    // This will initiate recursive download of the folder to the selected URI
                    startFolderDownload(uri)
                }
                dismiss()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = DialogFolderBinding.inflate(inflater, container, false)

        binding.foldernameText.text = folderName

        binding.downloadFolderButton.setOnClickListener {
            selectFolderLauncher.launch(
                Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    // Request permission to create files in the selected directory
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or 
                           Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                           Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                }
            )
        }

        binding.deleteFolderButton.setOnClickListener {
            showDeleteConfirmationDialog()
        }

        return binding.root
    }

    private fun startFolderDownload(targetUri: android.net.Uri) {
        // Create FileInfo object for the folder
        val fileInfo = net.syncthing.java.core.beans.FileInfo(
            folder = folderId,
            type = net.syncthing.java.core.beans.FileInfo.FileType.DIRECTORY,
            path = folderPath
        )
        
        // Show the folder download progress dialog
        FolderDownloadDialogFragment.newInstance(fileInfo, targetUri)
            .show(parentFragmentManager)
    }

    fun show(fragmentManager: FragmentManager) {
        super.show(fragmentManager, TAG)
    }

    private fun showDeleteConfirmationDialog() {
        MainScope().launch(Dispatchers.IO) {
            try {
                val syncthingActivity = requireActivity() as SyncthingActivity
                val counts = getFilesAndFoldersCount(syncthingActivity)
                val folderCount = counts.first
                val fileCount = counts.second

                withContext(Dispatchers.Main) {
                    val message = getString(R.string.dialog_delete_folder_warning) + "\n\n" +
                        getString(R.string.dialog_delete_folder_count_format, folderCount.toString(), fileCount.toString())

                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.dialog_delete_folder_title)
                        .setMessage(message)
                        .setIcon(android.R.drawable.ic_dialog_alert) // Red warning icon
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            performFolderDelete()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), R.string.toast_folder_delete_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun getFilesAndFoldersCount(syncthingActivity: SyncthingActivity): Pair<Int, Int> {
        return withContext(Dispatchers.IO) {
            try {
                var result: Pair<Int, Int> = Pair(0, 0)
                val deferred = kotlinx.coroutines.CompletableDeferred<Pair<Int, Int>>()
                
                syncthingActivity.libraryHandler.syncthingClient { syncthingClient ->
                    try {
                        val counts = countFilesAndFoldersRecursively(
                            syncthingClient.indexHandler.indexBrowser,
                            folderId,
                            folderPath
                        )
                        deferred.complete(counts)
                    } catch (e: Exception) {
                        // If we can't get counts, default to 0
                        deferred.complete(Pair(0, 0))
                    }
                }
                
                deferred.await()
            } catch (e: Exception) {
                Pair(0, 0)
            }
        }
    }

    private fun countFilesAndFoldersRecursively(
        indexBrowser: net.syncthing.java.bep.index.browser.IndexBrowser,
        folder: String,
        path: String
    ): Pair<Int, Int> {
        var folderCount = 0
        var fileCount = 0
        
        val listing = indexBrowser.getDirectoryListing(folder, path)
        
        if (listing is DirectoryContentListing) {
            for (entry in listing.entries) {
                when (entry.type) {
                    net.syncthing.java.core.beans.FileInfo.FileType.FILE -> {
                        fileCount++
                    }
                    net.syncthing.java.core.beans.FileInfo.FileType.DIRECTORY -> {
                        folderCount++
                        // Recursively count files and folders in subdirectories
                        val (subFolders, subFiles) = countFilesAndFoldersRecursively(indexBrowser, folder, entry.path)
                        folderCount += subFolders
                        fileCount += subFiles
                    }
                }
            }
        }
        
        return Pair(folderCount, fileCount)
    }

    private fun performFolderDelete() {
        MainScope().launch(Dispatchers.IO) {
            try {
                val syncthingActivity = requireActivity() as SyncthingActivity
                syncthingActivity.libraryHandler.syncthingClient { syncthingClient ->
                    val blockPusher = syncthingClient.getBlockPusher(folderId)
                    val indexBrowser = syncthingClient.indexHandler.indexBrowser
                    
                    // First recursively delete all contents of the folder
                    deleteRecursively(blockPusher, indexBrowser, folderId, folderPath)
                    
                    // Finally delete the folder itself
                    blockPusher.pushDelete(folderId, folderPath)
                }
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), R.string.toast_folder_delete_success, Toast.LENGTH_SHORT).show()
                    dismiss()
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), R.string.toast_folder_delete_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private suspend fun deleteRecursively(
        blockPusher: net.syncthing.java.bep.BlockPusher,
        indexBrowser: net.syncthing.java.bep.index.browser.IndexBrowser,
        folder: String,
        path: String
    ) {
        val listing = indexBrowser.getDirectoryListing(folder, path)
        
        if (listing is DirectoryContentListing) {
            for (entry in listing.entries) {
                when (entry.type) {
                    net.syncthing.java.core.beans.FileInfo.FileType.FILE -> {
                        // Delete the file
                        blockPusher.pushDelete(folder, entry.path)
                    }
                    net.syncthing.java.core.beans.FileInfo.FileType.DIRECTORY -> {
                        // Recursively delete subdirectory contents first
                        deleteRecursively(blockPusher, indexBrowser, folder, entry.path)
                        // Then delete the subdirectory itself
                        blockPusher.pushDelete(folder, entry.path)
                    }
                }
            }
        }
    }
}