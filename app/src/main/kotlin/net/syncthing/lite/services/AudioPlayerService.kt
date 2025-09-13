package net.syncthing.lite.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import net.syncthing.lite.R
import net.syncthing.lite.activities.AudioPlayerActivity
import net.syncthing.lite.dialogs.downloadfile.DownloadFileSpec
import java.io.File
import java.io.IOException

class AudioPlayerService : Service() {

    companion object {
        private const val EXTRA_FILE_SPEC = "file_spec"
        private const val EXTRA_FILE_PATH = "file_path"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "audio_player_channel"
        
        // Media session actions
        const val ACTION_PLAY = "action_play"
        const val ACTION_PAUSE = "action_pause"
        const val ACTION_STOP = "action_stop"
        const val ACTION_SEEK = "action_seek"
        const val EXTRA_SEEK_POSITION = "seek_position"
        
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
    private var mediaSession: MediaSessionCompat? = null

    inner class AudioPlayerBinder : Binder() {
        fun getService(): AudioPlayerService = this@AudioPlayerService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupMediaSession()
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle media button actions first
        intent?.action?.let { action ->
            when (action) {
                ACTION_PLAY -> {
                    play()
                    updateNotification()
                }
                ACTION_PAUSE -> {
                    pause()
                    updateNotification()
                }
                ACTION_STOP -> {
                    stop()
                    // No need to update notification as stop() removes it
                }
                ACTION_SEEK -> {
                    val position = intent.getIntExtra(EXTRA_SEEK_POSITION, 0)
                    seekTo(position)
                    updateNotification()
                }
                else -> {
                    // Handle regular start command
                    handleStartCommand(intent, flags, startId)
                }
            }
            return START_NOT_STICKY
        }
        
        // Regular start command handling
        return handleStartCommand(intent, flags, startId)
    }
    
    private fun handleStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
                updatePlaybackState()
                startForeground(NOTIFICATION_ID, createNotification())
            }
        }
    }

    fun pause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                updatePlaybackState()
                stopForeground(Service.STOP_FOREGROUND_DETACH)
                updateNotification()
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
        updatePlaybackState()
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
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
    
    fun getCurrentPosition(): Int {
        return mediaPlayer?.currentPosition ?: 0
    }
    
    fun getDuration(): Int {
        return if (isPlayerReady) {
            mediaPlayer?.duration ?: 0
        } else {
            0
        }
    }
    
    fun seekTo(position: Int) {
        mediaPlayer?.let { player ->
            if (isPlayerReady) {
                player.seekTo(position)
                updatePlaybackState()
            }
        }
    }
    
    private fun updatePlaybackState() {
        mediaSession?.let { session ->
            val state = if (isPlaying()) {
                PlaybackStateCompat.STATE_PLAYING
            } else if (isPlayerReady) {
                PlaybackStateCompat.STATE_PAUSED
            } else {
                PlaybackStateCompat.STATE_NONE
            }
            
            val playbackState = PlaybackStateCompat.Builder()
                .setState(state, getCurrentPosition().toLong(), 1.0f)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_STOP or
                    PlaybackStateCompat.ACTION_SEEK_TO
                )
                .build()
                
            session.setPlaybackState(playbackState)
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
    
    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "AudioPlayerService").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    play()
                }
                
                override fun onPause() {
                    pause()
                }
                
                override fun onStop() {
                    stop()
                }
                
                override fun onSeekTo(pos: Long) {
                    seekTo(pos.toInt())
                }
            })
            
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            isActive = true
        }
    }
    
    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    private fun createNotification(): Notification {
        val activityIntent = Intent(this, AudioPlayerActivity::class.java).apply {
            fileSpec?.let { spec ->
                putExtra("file_spec", spec)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, activityIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create action intents
        val playIntent = Intent(this, AudioPlayerService::class.java).apply { action = ACTION_PLAY }
        val pauseIntent = Intent(this, AudioPlayerService::class.java).apply { action = ACTION_PAUSE }
        val stopIntent = Intent(this, AudioPlayerService::class.java).apply { action = ACTION_STOP }
        
        val playPendingIntent = PendingIntent.getService(this, 0, playIntent, PendingIntent.FLAG_IMMUTABLE)
        val pausePendingIntent = PendingIntent.getService(this, 1, pauseIntent, PendingIntent.FLAG_IMMUTABLE)
        val stopPendingIntent = PendingIntent.getService(this, 2, stopIntent, PendingIntent.FLAG_IMMUTABLE)
        
        val isCurrentlyPlaying = isPlaying()
        
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.audio_player_notification_title))
            .setContentText(fileSpec?.fileName ?: "")
            .setSmallIcon(if (isCurrentlyPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(stopPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            
        // Add media controls
        if (isCurrentlyPlaying) {
            builder.addAction(android.R.drawable.ic_media_pause, getString(R.string.audio_player_pause), pausePendingIntent)
        } else {
            builder.addAction(android.R.drawable.ic_media_play, getString(R.string.audio_player_play), playPendingIntent)
        }
        builder.addAction(android.R.drawable.ic_delete, getString(R.string.audio_player_stop), stopPendingIntent)
        
        // Set media session token for MediaStyle
        mediaSession?.let { session ->
            builder.setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(session.sessionToken)
                .setShowActionsInCompactView(0, 1))
        }
        
        return builder.build()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.release()
        mediaSession = null
        mediaPlayer?.release()
        mediaPlayer = null
    }
}