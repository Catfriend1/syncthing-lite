package net.syncthing.lite.dialogs.downloadfolder

import android.app.Dialog
import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import net.syncthing.java.core.beans.FileInfo
import net.syncthing.lite.R
import net.syncthing.lite.library.LibraryHandler
import android.widget.Toast

class FolderDownloadDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_FOLDER_SPEC = "folder_spec"
        private const val ARG_TARGET_URI = "target_uri"
        private const val TAG = "FolderDownloadDialog"

        fun newInstance(fileInfo: FileInfo, targetUri: Uri): FolderDownloadDialogFragment {
            return FolderDownloadDialogFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_FOLDER_SPEC, FolderDownloadSpec(
                        folder = fileInfo.folder,
                        path = fileInfo.path,
                        folderName = fileInfo.fileName
                    ))
                    putParcelable(ARG_TARGET_URI, targetUri)
                }
            }
        }
    }

    private lateinit var model: FolderDownloadViewModel
    private lateinit var progressBar: ProgressBar
    private lateinit var progressMessage: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model = ViewModelProvider(this, ViewModelProvider.NewInstanceFactory())
            .get(FolderDownloadViewModel::class.java)

        val folderSpec = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireArguments().getSerializable(ARG_FOLDER_SPEC, FolderDownloadSpec::class.java)!!
        } else {
            @Suppress("DEPRECATION")
            requireArguments().getSerializable(ARG_FOLDER_SPEC) as FolderDownloadSpec
        }
        val targetUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireArguments().getParcelable(ARG_TARGET_URI, Uri::class.java)!!
        } else {
            @Suppress("DEPRECATION")
            requireArguments().getParcelable<Uri>(ARG_TARGET_URI)!!
        }

        model.init(
            libraryHandler = LibraryHandler(requireContext()),
            folderSpec = folderSpec,
            externalCacheDir = requireNotNull(requireContext().externalCacheDir),
            targetUri = targetUri,
            contentResolver = requireContext().contentResolver,
            context = requireContext()
        )
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val folderSpec = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireArguments().getSerializable(ARG_FOLDER_SPEC, FolderDownloadSpec::class.java)!!
        } else {
            @Suppress("DEPRECATION")
            requireArguments().getSerializable(ARG_FOLDER_SPEC) as FolderDownloadSpec
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_download_progress, null)
        progressBar = dialogView.findViewById(R.id.progress_bar)
        progressMessage = dialogView.findViewById(R.id.progress_message)

        progressMessage.text = getString(R.string.dialog_downloading_folder, folderSpec.folderName)
        progressBar.isIndeterminate = true
        progressBar.max = FolderDownloadStatusRunning.MAX_PROGRESS

        val alertDialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        model.status.observe(this, Observer { status ->
            when (status) {
                is FolderDownloadStatusPending -> {
                    progressBar.isIndeterminate = true
                    progressMessage.text = getString(R.string.dialog_downloading_folder, folderSpec.folderName)
                }
                is FolderDownloadStatusRunning -> {
                    progressBar.isIndeterminate = false
                    
                    if (status.totalFiles > 0) {
                        val overallProgress = (status.processedFiles * 100 / status.totalFiles)
                        progressBar.progress = overallProgress
                        
                        progressMessage.text = resources.getQuantityString(
                            R.plurals.dialog_downloading_folder_progress,
                            status.totalFiles,
                            status.currentFile,
                            status.processedFiles + 1,
                            status.totalFiles
                        )
                    } else {
                        progressBar.isIndeterminate = true
                        progressMessage.text = getString(R.string.dialog_downloading_folder, folderSpec.folderName)
                    }
                }
                is FolderDownloadStatusDone -> {
                    dismissAllowingStateLoss()
                    Toast.makeText(
                        requireContext(),
                        resources.getQuantityString(R.plurals.toast_folder_download_success, status.downloadedFiles, status.downloadedFiles, status.targetFolder),
                        Toast.LENGTH_LONG
                    ).show()
                }
                is FolderDownloadStatusFailed -> {
                    dismissAllowingStateLoss()
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.toast_folder_download_failed, status.error),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        })

        return alertDialog
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        model.cancel()
    }

    fun show(fragmentManager: FragmentManager) {
        super.show(fragmentManager, TAG)
    }
}