package net.syncthing.lite.dialogs

import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import androidx.fragment.app.FragmentManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import net.syncthing.java.core.beans.FileInfo
import net.syncthing.lite.R
import net.syncthing.lite.databinding.DialogFileBinding
import net.syncthing.lite.dialogs.downloadfile.DownloadFileDialogFragment
import net.syncthing.lite.dialogs.downloadfile.DownloadFileSpec
import net.syncthing.lite.library.LibraryHandler
import net.syncthing.lite.library.RenameFileTask
import net.syncthing.lite.utils.MimeType
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.*
import net.syncthing.java.core.utils.PathUtils

class FileMenuDialogFragment: BottomSheetDialogFragment() {
    companion object {
        private const val ARG_FILE_SPEC = "file spec"
        private const val TAG = "DownloadFileDialog"

        fun newInstance(fileInfo: FileInfo) = newInstance(DownloadFileSpec(
                folder = fileInfo.folder,
                path = fileInfo.path,
                fileName = fileInfo.fileName
        ))

        fun newInstance(fileSpec: DownloadFileSpec) = FileMenuDialogFragment().apply {
            arguments = Bundle().apply {
                putSerializable(ARG_FILE_SPEC, fileSpec)
            }
        }
    }

    private lateinit var saveAsLauncher: ActivityResultLauncher<Intent>

    val fileSpec: DownloadFileSpec by lazy {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireArguments().getSerializable(ARG_FILE_SPEC, DownloadFileSpec::class.java)!!
        } else {
            @Suppress("DEPRECATION")
            requireArguments().getSerializable(ARG_FILE_SPEC) as DownloadFileSpec
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        saveAsLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                DownloadFileDialogFragment.newInstance(fileSpec, result.data!!.data!!).show(requireActivity().supportFragmentManager)
                dismiss()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = DialogFileBinding.inflate(inflater, container, false)

        binding.filenameText.text = fileSpec.fileName

        binding.saveAsButton.setOnClickListener {
            saveAsLauncher.launch(
                    Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)

                        type = MimeType.getFromFilename(fileSpec.fileName)

                        putExtra(Intent.EXTRA_TITLE, fileSpec.fileName)
                    }
            )
        }

        binding.renameButton.setOnClickListener {
            showRenameDialog()
        }

        binding.deleteButton.setOnClickListener {
            showDeleteConfirmationDialog()
        }

        return binding.root
    }
    
    private fun showRenameDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.dialog_rename_file_hint)
            setText(fileSpec.fileName)
            selectAll()
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_rename_file_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newFileName = input.text.toString().trim()
                if (newFileName.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.error_empty_filename, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                if (!isValidFileName(newFileName)) {
                    Toast.makeText(requireContext(), R.string.error_invalid_filename, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                if (newFileName == fileSpec.fileName) {
                    // No change needed
                    dismiss()
                    return@setPositiveButton
                }
                
                renameFile(newFileName)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun isValidFileName(name: String): Boolean {
        // Check for invalid characters that might cause issues
        val invalidChars = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        return name.isNotEmpty() && !name.any { it in invalidChars } && name != "." && name != ".."
    }
    
    private fun renameFile(newFileName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                LibraryHandler(requireContext()).syncthingClient { syncthingClient ->
                    try {
                        // Check if we have active connections for this folder
                        val activeConnections = syncthingClient.getActiveConnectionsForFolder(fileSpec.folder)
                        if (activeConnections.isEmpty()) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(requireContext(), R.string.toast_file_rename_failed_not_connected, Toast.LENGTH_SHORT).show()
                            }
                            return@syncthingClient
                        }
                        
                        RenameFileTask(
                            requireContext(),
                            syncthingClient,
                            fileSpec.folder,
                            fileSpec.path,
                            newFileName
                        ).execute()
                        
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), R.string.toast_file_rename_success, Toast.LENGTH_SHORT).show()
                            dismiss()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), R.string.toast_file_rename_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), R.string.toast_file_rename_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_delete_file_title)
            .setMessage(getString(R.string.dialog_delete_file_message, fileSpec.fileName))
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                LibraryHandler(requireContext()).syncthingClient { syncthingClient ->
                    DeleteFileDialog(
                        requireContext(),
                        syncthingClient,
                        fileSpec.folder,
                        fileSpec.path
                    ) { dismiss() }.show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun show(fragmentManager: FragmentManager) {
        super.show(fragmentManager, TAG)
    }
}
