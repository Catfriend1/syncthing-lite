package net.syncthing.lite.dialogs

import android.app.AlertDialog
import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import net.syncthing.java.bep.BlockPusher
import net.syncthing.java.client.SyncthingClient
import net.syncthing.lite.R
import net.syncthing.lite.library.UploadFileTask
import net.syncthing.lite.utils.Util
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.toast

class FileUploadDialog(
    private val context: Context,
    private val syncthingClient: SyncthingClient,
    private val localFile: Uri,
    private val syncthingFolder: String,
    private val syncthingSubFolder: String,
    private val onUploadCompleteListener: () -> Unit
) {
    private var uploadFileTask: UploadFileTask? = null
    private var dialog: AlertDialog? = null
    private lateinit var progressBar: ProgressBar
    private lateinit var messageText: TextView

    fun show() {
        showDialog()
        doAsync {
            uploadFileTask = UploadFileTask(
                context,
                syncthingClient,
                localFile,
                syncthingFolder,
                syncthingSubFolder,
                this@FileUploadDialog::onProgress,
                this@FileUploadDialog::onComplete,
                this@FileUploadDialog::onError
            )
        }
    }

    private fun showDialog() {
        val view: View = LayoutInflater.from(context).inflate(R.layout.dialog_upload_progress, null)

        progressBar = view.findViewById(R.id.progressBar)
        messageText = view.findViewById(R.id.progressMessage)

        messageText.text = context.getString(
            R.string.dialog_uploading_file,
            Util.getContentFileName(context, localFile)
        )

        dialog = AlertDialog.Builder(context)
            .setView(view)
            .setCancelable(true)
            .setOnCancelListener { uploadFileTask?.cancel() }
            .create()

        dialog?.show()
    }

    private fun onProgress(observer: BlockPusher.FileUploadObserver) {
        context.runOnUiThread {
            progressBar.isIndeterminate = false
            progressBar.max = 100
            progressBar.progress = observer.progressPercentage()
        }
    }

    private fun onComplete() {
        context.runOnUiThread {
            dialog?.dismiss()
            context.toast(R.string.toast_upload_complete)
            onUploadCompleteListener()
        }
    }

    private fun onError() {
        context.runOnUiThread {
            dialog?.dismiss()
            context.toast(R.string.toast_file_upload_failed)
        }
    }

    private fun Context.runOnUiThread(action: () -> Unit) {
        if (this is android.app.Activity) {
            runOnUiThread(action)
        } else {
            action()
        }
    }
}
