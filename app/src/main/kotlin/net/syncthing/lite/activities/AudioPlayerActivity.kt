package net.syncthing.lite.activities

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import net.syncthing.lite.databinding.ActivityAudioPlayerBinding
import net.syncthing.lite.dialogs.downloadfile.DownloadFileSpec
import net.syncthing.lite.services.AudioPlayerService
import java.io.File

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
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioPlayerService.AudioPlayerBinder
            audioService = binder.getService()
            isServiceBound = true
            updateUI()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            audioService = null
            isServiceBound = false
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
}