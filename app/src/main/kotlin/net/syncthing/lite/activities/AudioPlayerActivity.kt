package net.syncthing.lite.activities

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import net.syncthing.lite.databinding.ActivityAudioPlayerBinding
import net.syncthing.lite.dialogs.downloadfile.DownloadFileSpec
import java.util.Locale
import net.syncthing.lite.services.AudioPlayerService
import java.io.File
import java.util.concurrent.TimeUnit

class AudioPlayerActivity : AppCompatActivity() {
    
    companion object {
        private const val EXTRA_FILE_SPEC = "file_spec"
        private const val EXTRA_FILE_PATH = "file_path"
        
        fun newIntent(context: Context, fileSpec: DownloadFileSpec): Intent {
            return Intent(context, AudioPlayerActivity::class.java).apply {
                putExtra(EXTRA_FILE_SPEC, fileSpec)
            }
        }
        
        fun newIntent(context: Context, fileSpec: DownloadFileSpec, file: File): Intent {
            return Intent(context, AudioPlayerActivity::class.java).apply {
                putExtra(EXTRA_FILE_SPEC, fileSpec)
                putExtra(EXTRA_FILE_PATH, file.absolutePath)
            }
        }
    }
    
    private lateinit var binding: ActivityAudioPlayerBinding
    private var audioService: AudioPlayerService? = null
    private var isServiceBound = false
    private var progressHandler: Handler? = null
    private var isUserSeeking = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioPlayerService.AudioPlayerBinder
            audioService = binder.getService()
            isServiceBound = true
            updateUI()
            setupProgressTracking()
            // Set up completion listener to update UI when playback finishes
            audioService?.setOnPlaybackCompletedListener {
                runOnUiThread {
                    updateUI()
                }
            }
            // Automatically start playback when service is connected
            startPlaybackWhenReady()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            audioService = null
            isServiceBound = false
            stopProgressTracking()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAudioPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        val fileSpec = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_FILE_SPEC, DownloadFileSpec::class.java)!!
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(EXTRA_FILE_SPEC) as DownloadFileSpec
        }
        
        val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
        
        binding.fileNameText.text = fileSpec.fileName
        
        binding.playButton.setOnClickListener {
            audioService?.play()
            updateUI()
        }
        
        binding.pauseButton.setOnClickListener {
            audioService?.pause()
            updateUI()
        }
        
        binding.stopButton.setOnClickListener {
            audioService?.stop()
            updateUI()
        }
        
        // Setup seek bar listener
        binding.progressSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    audioService?.let { service ->
                        val duration = service.getDuration()
                        val position = (progress * duration / 100)
                        binding.currentTimeText.text = formatTime(position)
                    }
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
            }
            
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                audioService?.let { service ->
                    val duration = service.getDuration()
                    val position = (seekBar?.progress ?: 0) * duration / 100
                    service.seekTo(position)
                }
                isUserSeeking = false
            }
        })
        
        // Start and bind to the audio service
        val serviceIntent = if (filePath != null) {
            AudioPlayerService.newIntent(this, fileSpec, File(filePath))
        } else {
            AudioPlayerService.newIntent(this, fileSpec)
        }
        startService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopProgressTracking()
        if (isServiceBound) {
            unbindService(serviceConnection)
        }
    }
    
    private fun updateUI() {
        audioService?.let { service ->
            binding.playButton.visibility = if (service.isPlaying()) View.GONE else View.VISIBLE
            binding.pauseButton.visibility = if (service.isPlaying()) View.VISIBLE else View.GONE
        }
    }
    
    private fun startPlaybackWhenReady() {
        audioService?.let { service ->
            if (service.isPlayerReady()) {
                service.play()
                updateUI()
            } else {
                // If player is not ready yet, set up a callback to start when ready
                service.setOnPlayerReadyListener {
                    service.play()
                    updateUI()
                }
            }
        }
    }
    
    private fun setupProgressTracking() {
        progressHandler = Handler(Looper.getMainLooper())
        startProgressTracking()
    }
    
    private fun startProgressTracking() {
        progressHandler?.post(object : Runnable {
            override fun run() {
                audioService?.let { service ->
                    if (service.isPlayerReady() && !isUserSeeking) {
                        val currentPosition = service.getCurrentPosition()
                        val duration = service.getDuration()
                        
                        if (duration > 0) {
                            val progress = (currentPosition * 100 / duration)
                            binding.progressSeekBar.progress = progress
                            binding.currentTimeText.text = formatTime(currentPosition)
                            binding.totalTimeText.text = formatTime(duration)
                        }
                    }
                }
                
                // Continue updating every 1000ms if service is still bound
                if (isServiceBound) {
                    progressHandler?.postDelayed(this, 1000)
                }
            }
        })
    }
    
    private fun stopProgressTracking() {
        progressHandler?.removeCallbacksAndMessages(null)
        progressHandler = null
    }
    
    private fun formatTime(milliseconds: Int): String {
        val hours = TimeUnit.MILLISECONDS.toHours(milliseconds.toLong())
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds.toLong()) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds.toLong()) % 60
        
        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        }
    }
}