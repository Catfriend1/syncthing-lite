package net.syncthing.lite.dialogs

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import net.syncthing.java.client.SyncthingClient
import net.syncthing.lite.R
import net.syncthing.lite.library.DeleteFileTask

class DeleteFileDialog(
    private val context: Context,
    private val syncthingClient: SyncthingClient,
    private val syncthingFolder: String,
    private val syncthingPath: String,
    private val onDeleteCompleteListener: () -> Unit
) {
    private var deleteFileTask: DeleteFileTask? = null
    private var dialog: AlertDialog? = null
    private lateinit var progressBar: ProgressBar
    private lateinit var messageText: TextView

    private val uiHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun show() {
        uiHandler.post {
            showDialog()
        }
        
        // Start the delete operation in the dialog's scope
        scope.launch {
            try {
                Log.d("DeleteFileDialog", "Starting delete operation")
                deleteFileTask = DeleteFileTask(
                    context,
                    syncthingClient,
                    syncthingFolder,
                    syncthingPath
                )
                
                Log.d("DeleteFileDialog", "Calling deleteFileTask.execute()")
                deleteFileTask!!.execute()
                
                Log.d("DeleteFileDialog", "Delete operation completed successfully")
                uiHandler.post { 
                    Log.d("DeleteFileDialog", "Calling onComplete from scope")
                    onComplete() 
                }
            } catch (ex: Exception) {
                Log.e("DeleteFileDialog", "Error in delete operation", ex)
                uiHandler.post { 
                    Log.d("DeleteFileDialog", "Calling onError from scope")
                    onError() 
                }
            }
        }
    }

    private fun showDialog() {
        val view: View = LayoutInflater.from(context).inflate(R.layout.dialog_delete_progress, null)

        progressBar = view.findViewById(R.id.progressBar)
        messageText = view.findViewById(R.id.progressMessage)

        messageText.text = context.getString(R.string.dialog_deleting_file)

        dialog = AlertDialog.Builder(context)
            .setView(view)
            .setCancelable(false)
            .create()

        dialog?.show()
    }

    private fun onComplete() {
        Log.d("DeleteFileDialog", "onComplete() called")
        uiHandler.post {
            Log.d("DeleteFileDialog", "onComplete() - dismissing dialog")
            dialog?.dismiss()
            Toast.makeText(context, R.string.toast_delete_complete, Toast.LENGTH_SHORT).show()
            onDeleteCompleteListener()
            cancel()
        }
    }

    private fun onError() {
        Log.d("DeleteFileDialog", "onError() called")
        uiHandler.post {
            Log.d("DeleteFileDialog", "onError() - dismissing dialog")
            dialog?.dismiss()
            Toast.makeText(context, R.string.toast_file_delete_failed, Toast.LENGTH_SHORT).show()
            cancel()
        }
    }

    private fun cancel() {
        scope.cancel()
    }
}