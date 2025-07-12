package net.syncthing.lite.dialogs

import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.os.Bundle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import androidx.fragment.app.FragmentManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import net.syncthing.java.core.beans.FileInfo
import net.syncthing.lite.databinding.DialogFileBinding
import net.syncthing.lite.dialogs.downloadfile.DownloadFileDialogFragment
import net.syncthing.lite.dialogs.downloadfile.DownloadFileSpec
import net.syncthing.lite.utils.MimeType
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

class FileMenuDialogFragment: BottomSheetDialogFragment() {
    companion object {
        private const val ARG_FILE_SPEC = "file spec"
        private const val TAG = "DownloadFileDialog"

        fun newInstance(fileInfo: FileInfo) = newInstance(DownloadFileSpec(
                folder = fileInfo.folder,
                path = fileInfo.path,
                fileName = fileInfo.fileName
        ))

        fun newInstance(fileSpec: DownloadFileSpec) = FileMenuDialogFragment().apply {
            arguments = Bundle().apply {
                putSerializable(ARG_FILE_SPEC, fileSpec)
            }
        }
    }

    private lateinit var saveAsLauncher: ActivityResultLauncher<Intent>

    val fileSpec: DownloadFileSpec by lazy {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireArguments().getSerializable(ARG_FILE_SPEC, DownloadFileSpec::class.java)!!
        } else {
            @Suppress("DEPRECATION")
            requireArguments().getSerializable(ARG_FILE_SPEC) as DownloadFileSpec
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        saveAsLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                DownloadFileDialogFragment.newInstance(fileSpec, result.data!!.data!!).show(requireActivity().supportFragmentManager)
                dismiss()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = DialogFileBinding.inflate(inflater, container, false)

        binding.filename = fileSpec.fileName

        binding.saveAsButton.setOnClickListener {
            saveAsLauncher.launch(
                    Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)

                        type = MimeType.getFromFilename(fileSpec.fileName)

                        putExtra(Intent.EXTRA_TITLE, fileSpec.fileName)
                    }
            )
        }

        return binding.root
    }

    fun show(fragmentManager: FragmentManager) {
        super.show(fragmentManager, TAG)
    }
}
