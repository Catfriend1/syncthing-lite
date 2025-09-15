package net.syncthing.lite.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import net.syncthing.java.core.beans.FileInfo
import net.syncthing.lite.databinding.DialogMp3OptionsBinding
import net.syncthing.lite.dialogs.downloadfile.DownloadCompletionAction
import net.syncthing.lite.dialogs.downloadfile.DownloadFileDialogFragment
import net.syncthing.lite.dialogs.downloadfile.DownloadFileSpec

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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DialogMp3OptionsBinding.inflate(inflater, container, false)

        binding.filenameText.text = fileSpec.fileName

        binding.playInAppButton.setOnClickListener {
            // Use the standard download dialog with audio player completion action
            DownloadFileDialogFragment.newInstance(
                fileSpec = fileSpec,
                completionAction = DownloadCompletionAction.PLAY_IN_AUDIO_PLAYER
            ).show(requireActivity().supportFragmentManager)
            dismiss()
        }

        binding.openWithAppButton.setOnClickListener {
            // Use the standard download dialog with default completion action
            DownloadFileDialogFragment.newInstance(
                fileSpec = fileSpec,
                completionAction = DownloadCompletionAction.OPEN_WITH_APP
            ).show(requireActivity().supportFragmentManager)
            dismiss()
        }

        return binding.root
    }

    fun show(fragmentManager: FragmentManager) {
        super.show(fragmentManager, TAG)
    }
}