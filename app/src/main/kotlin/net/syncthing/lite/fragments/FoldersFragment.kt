package net.syncthing.lite.fragments

import androidx.lifecycle.Observer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import net.syncthing.java.core.beans.FolderInfo
import net.syncthing.java.core.beans.FolderStats
import net.syncthing.lite.activities.FolderBrowserActivity
import net.syncthing.lite.adapters.FolderListAdapterListener
import net.syncthing.lite.adapters.FoldersListAdapter
import net.syncthing.lite.databinding.FragmentFoldersBinding
import net.syncthing.lite.dialogs.FolderInfoDialog
import android.content.Intent

class FoldersFragment : SyncthingFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val adapter = FoldersListAdapter()

        adapter.listener = object : FolderListAdapterListener {
            override fun onFolderClicked(folderInfo: FolderInfo, folderStats: FolderStats) {
                val intent = Intent(activity, FolderBrowserActivity::class.java).apply {
                    putExtra(FolderBrowserActivity.EXTRA_FOLDER_NAME, folderInfo.folderId)
                }
                startActivity(intent)
            }

            override fun onFolderLongClicked(folderInfo: FolderInfo): Boolean {
                FolderInfoDialog
                        .newInstance(folderId = folderInfo.folderId)
                        .show(parentFragmentManager)

                return true
            }
        }

        val binding = FragmentFoldersBinding.inflate(layoutInflater, container, false)
        binding.list.adapter = adapter
        libraryHandler.isListeningPortTaken.observe(viewLifecycleOwner, Observer { binding.listeningPortTaken = it })

        launch {
            libraryHandler.subscribeToFolderStatusList().collect {
                adapter.data = it
                binding.isEmpty = it.isEmpty()
            }
        }

        return binding.root
    }
}
