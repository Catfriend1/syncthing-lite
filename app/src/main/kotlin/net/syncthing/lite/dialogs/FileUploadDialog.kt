package net.syncthing.lite.dialogs

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.syncthing.java.bep.BlockPusher
import net.syncthing.java.client.SyncthingClient
import net.syncthing.lite.R
import net.syncthing.lite.library.UploadFileTask
import net.syncthing.lite.utils.Util

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

    private val uiHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun show() {
        showDialog()

        scope.launch {
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
            .setOnCancelListener { cancel() }
            .create()

        dialog?.show()
    }

    private fun onProgress(observer: BlockPusher.FileUploadObserver) {
        uiHandler.post {
            progressBar.isIndeterminate = false
            progressBar.max = 100
            progressBar.progress = observer.progressPercentage()
        }
    }

    private fun onComplete() {
        uiHandler.post {
            dialog?.dismiss()
            Toast.makeText(context, R.string.toast_upload_complete, Toast.LENGTH_SHORT).show()
            onUploadCompleteListener()
            cancel()
        }
    }

    private fun onError() {
        uiHandler.post {
            dialog?.dismiss()
            Toast.makeText(context, R.string.toast_file_upload_failed, Toast.LENGTH_SHORT).show()
            cancel()
        }
    }

    private fun cancel() {
        uploadFileTask?.cancel()
        scope.cancel()  // cleanly shuts down coroutines
    }
}
