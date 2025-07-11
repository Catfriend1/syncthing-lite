package net.syncthing.lite.dialogs.downloadfile

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import net.syncthing.java.core.beans.FileInfo
import net.syncthing.lite.R
import net.syncthing.lite.library.CacheFileProviderUrl
import net.syncthing.lite.library.LibraryHandler
import net.syncthing.lite.utils.MimeType
import android.widget.Toast

class DownloadFileDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_FILE_SPEC = "file spec"
        private const val ARG_SAVE_AS_URI = "save as"
        private const val TAG = "DownloadFileDialog"

        fun newInstance(fileInfo: FileInfo): DownloadFileDialogFragment = newInstance(
            DownloadFileSpec(
                folder = fileInfo.folder,
                path = fileInfo.path,
                fileName = fileInfo.fileName
            )
        )

        fun newInstance(fileSpec: DownloadFileSpec, outputUri: Uri? = null): DownloadFileDialogFragment {
            return DownloadFileDialogFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_FILE_SPEC, fileSpec)
                    outputUri?.let { putParcelable(ARG_SAVE_AS_URI, it) }
                }
            }
        }
    }

    private lateinit var model: DownloadFileDialogViewModel
    private lateinit var progressBar: ProgressBar
    private lateinit var progressMessage: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        model = ViewModelProvider(this, ViewModelProvider.NewInstanceFactory())
            .get(DownloadFileDialogViewModel::class.java)

        val fileSpec = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arguments!!.getSerializable(ARG_FILE_SPEC, DownloadFileSpec::class.java)!!
        } else {
            @Suppress("DEPRECATION")
            arguments!!.getSerializable(ARG_FILE_SPEC) as DownloadFileSpec
        }
        val outputUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelable(ARG_SAVE_AS_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelable<Uri>(ARG_SAVE_AS_URI)
        }

        model.init(
            libraryHandler = LibraryHandler(requireContext()),
            fileSpec = fileSpec,
            externalCacheDir = requireNotNull(requireContext().externalCacheDir),
            outputUri = outputUri,
            contentResolver = requireContext().contentResolver
        )
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val fileSpec = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arguments!!.getSerializable(ARG_FILE_SPEC, DownloadFileSpec::class.java)!!
        } else {
            @Suppress("DEPRECATION")
            arguments!!.getSerializable(ARG_FILE_SPEC) as DownloadFileSpec
        }
        val outputUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelable(ARG_SAVE_AS_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelable<Uri>(ARG_SAVE_AS_URI)
        }

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_download_progress, null)
        progressBar = dialogView.findViewById(R.id.progress_bar)
        progressMessage = dialogView.findViewById(R.id.progress_message)

        progressMessage.text = getString(R.string.dialog_downloading_file, fileSpec.fileName)
        progressBar.isIndeterminate = true
        progressBar.max = DownloadFileStatusRunning.MAX_PROGRESS

        val alertDialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        model.status.observe(this, Observer { status ->
            when (status) {
                is DownloadFileStatusRunning -> {
                    progressBar.isIndeterminate = false
                    progressBar.progress = status.progress
                }
                is DownloadFileStatusDone -> {
                    dismissAllowingStateLoss()
                    if (outputUri == null) {
                        val file = status.file
                        val mimeType = MimeType.getFromFilename(fileSpec.fileName)
                        try {
                            startActivity(
                                Intent(Intent.ACTION_VIEW)
                                    .setDataAndType(
                                        CacheFileProviderUrl.fromFile(
                                            filename = fileSpec.fileName,
                                            mimeType = mimeType,
                                            file = file,
                                            context = requireContext()
                                        ).serialized,
                                        mimeType
                                    )
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            )
                        } catch (e: ActivityNotFoundException) {
                            Log.w(TAG, "No handler found for file ${file.name}", e)
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.toast_open_file_failed),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
                is DownloadFileStatusFailed -> {
                    dismissAllowingStateLoss()
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.toast_file_download_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                else -> { /* no-op or log unexpected status */ }
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
