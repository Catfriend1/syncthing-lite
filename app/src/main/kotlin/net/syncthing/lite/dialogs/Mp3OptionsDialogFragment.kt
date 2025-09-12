package net.syncthing.lite.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import net.syncthing.java.core.beans.FileInfo
import net.syncthing.lite.R
import net.syncthing.lite.activities.AudioPlayerActivity
import net.syncthing.lite.databinding.DialogMp3OptionsBinding
import net.syncthing.lite.dialogs.downloadfile.DownloadFileDialogFragment
import net.syncthing.lite.dialogs.downloadfile.DownloadFileDialogViewModel
import net.syncthing.lite.dialogs.downloadfile.DownloadFileSpec
import net.syncthing.lite.dialogs.downloadfile.DownloadFileStatus
import net.syncthing.lite.dialogs.downloadfile.DownloadFileStatusDone
import net.syncthing.lite.dialogs.downloadfile.DownloadFileStatusFailed
import net.syncthing.lite.dialogs.downloadfile.DownloadFileStatusRunning
import net.syncthing.lite.library.LibraryHandler

class Mp3OptionsDialogFragment : BottomSheetDialogFragment() {
    companion object {
        private const val ARG_FILE_SPEC = "file spec"
        private const val TAG = "Mp3OptionsDialog"

        fun newInstance(fileInfo: FileInfo) = newInstance(DownloadFileSpec(
            folder = fileInfo.folder,
            path = fileInfo.path,
            fileName = fileInfo.fileName
        ))

        fun newInstance(fileSpec: DownloadFileSpec) = Mp3OptionsDialogFragment().apply {
            arguments = Bundle().apply {
                putSerializable(ARG_FILE_SPEC, fileSpec)
            }
        }
    }

    val fileSpec: DownloadFileSpec by lazy {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireArguments().getSerializable(ARG_FILE_SPEC, DownloadFileSpec::class.java)!!
        } else {
            @Suppress("DEPRECATION")
            requireArguments().getSerializable(ARG_FILE_SPEC) as DownloadFileSpec
        }
    }

    private lateinit var binding: DialogMp3OptionsBinding
    private lateinit var downloadViewModel: DownloadFileDialogViewModel
    private var isDownloading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        downloadViewModel = ViewModelProvider(this, ViewModelProvider.NewInstanceFactory())
            .get(DownloadFileDialogViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DialogMp3OptionsBinding.inflate(inflater, container, false)

        binding.filenameText.text = fileSpec.fileName

        binding.playInAppButton.setOnClickListener {
            if (!isDownloading) {
                startDownloadForAudioPlayer()
            }
        }

        binding.openWithAppButton.setOnClickListener {
            // Show the regular download dialog to open with associated application
            DownloadFileDialogFragment.newInstance(fileSpec).show(requireActivity().supportFragmentManager)
            dismiss()
        }

        // Observe download status
        downloadViewModel.status.observe(this, Observer { status ->
            when (status) {
                is DownloadFileStatusRunning -> {
                    isDownloading = true
                    binding.playInAppButton.text = getString(R.string.downloading)
                    binding.playInAppButton.isEnabled = false
                }
                is DownloadFileStatusDone -> {
                    isDownloading = false
                    dismiss()
                    // Start audio player with the downloaded file
                    val intent = AudioPlayerActivity.newIntent(requireContext(), fileSpec, status.file)
                    startActivity(intent)
                }
                is DownloadFileStatusFailed -> {
                    isDownloading = false
                    binding.playInAppButton.text = getString(R.string.dialog_mp3_play_in_app)
                    binding.playInAppButton.isEnabled = true
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.toast_file_download_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })

        return binding.root
    }

    private fun startDownloadForAudioPlayer() {
        downloadViewModel.init(
            libraryHandler = LibraryHandler(requireContext()),
            fileSpec = fileSpec,
            externalCacheDir = requireNotNull(requireContext().externalCacheDir),
            outputUri = null,
            contentResolver = requireContext().contentResolver
        )
    }

    fun show(fragmentManager: FragmentManager) {
        super.show(fragmentManager, TAG)
    }
}