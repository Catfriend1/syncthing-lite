package net.syncthing.lite.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import net.syncthing.lite.R
import net.syncthing.lite.dialogs.downloadfile.DownloadFileSpec
import java.io.File
import java.io.IOException

class AudioPlayerService : Service() {

    companion object {
        private const val EXTRA_FILE_SPEC = "file_spec"
        private const val EXTRA_FILE_PATH = "file_path"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "audio_player_channel"
        
        fun newIntent(context: Context, fileSpec: DownloadFileSpec): Intent {
            return Intent(context, AudioPlayerService::class.java).apply {
                putExtra(EXTRA_FILE_SPEC, fileSpec)
            }
        }
        
        fun newIntent(context: Context, fileSpec: DownloadFileSpec, file: File): Intent {
            return Intent(context, AudioPlayerService::class.java).apply {
                putExtra(EXTRA_FILE_SPEC, fileSpec)
                putExtra(EXTRA_FILE_PATH, file.absolutePath)
            }
        }
    }

    private val binder = AudioPlayerBinder()
    private var mediaPlayer: MediaPlayer? = null
    private var fileSpec: DownloadFileSpec? = null
    private var isPlayerReady = false
    private var onPlayerReadyListener: (() -> Unit)? = null

    inner class AudioPlayerBinder : Binder() {
        fun getService(): AudioPlayerService = this@AudioPlayerService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val spec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getSerializableExtra(EXTRA_FILE_SPEC, DownloadFileSpec::class.java)
            } else {
                @Suppress("DEPRECATION")
                it.getSerializableExtra(EXTRA_FILE_SPEC) as? DownloadFileSpec
            }
            
            val filePath = it.getStringExtra(EXTRA_FILE_PATH)
            
            spec?.let { fileSpec ->
                this.fileSpec = fileSpec
                if (filePath != null) {
                    initializeMediaPlayerWithFile(File(filePath))
                } else {
                    initializeMediaPlayer(fileSpec)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun initializeMediaPlayerWithFile(file: File) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener {
                    isPlayerReady = true
                    onPlayerReadyListener?.invoke()
                }
                setOnCompletionListener {
                    stop()
                }
                setOnErrorListener { _, _, _ ->
                    stop()
                    true
                }
                prepareAsync() // Prepare the media player asynchronously
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun initializeMediaPlayer(fileSpec: DownloadFileSpec) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                // Note: This is a basic implementation. In a real app, you would
                // need to handle file downloading and local storage properly
                setOnPreparedListener {
                    isPlayerReady = true
                }
                setOnCompletionListener {
                    stop()
                }
                setOnErrorListener { _, _, _ ->
                    stop()
                    true
                }
                // For now, this is just a placeholder - actual file loading would
                // require integration with the download system
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun play() {
        mediaPlayer?.let { player ->
            if (isPlayerReady && !player.isPlaying) {
                player.start()
                startForeground(NOTIFICATION_ID, createNotification())
            }
        }
    }

    fun pause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                stopForeground(false)
            }
        }
    }

    fun stop() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.stop()
            }
            player.reset()
            isPlayerReady = false
        }
        stopForeground(true)
    }

    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying ?: false
    }
    
    fun isPlayerReady(): Boolean {
        return isPlayerReady
    }
    
    fun setOnPlayerReadyListener(listener: () -> Unit) {
        onPlayerReadyListener = listener
        // If already ready, call immediately
        if (isPlayerReady) {
            listener.invoke()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.audio_player_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.audio_player_notification_title))
            .setContentText(fileSpec?.fileName ?: "")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}