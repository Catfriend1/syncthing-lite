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
}