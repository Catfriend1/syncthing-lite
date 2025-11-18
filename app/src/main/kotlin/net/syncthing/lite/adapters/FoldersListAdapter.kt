package net.syncthing.lite.adapters

import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import android.text.format.DateUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import net.syncthing.java.bep.folder.FolderStatus
import net.syncthing.java.core.beans.FolderInfo
import net.syncthing.java.core.beans.FolderStats
import net.syncthing.lite.R
import net.syncthing.lite.databinding.ListviewFolderBinding
import kotlin.properties.Delegates

class FoldersListAdapter: RecyclerView.Adapter<FolderListViewHolder>() {
    var data: List<FolderStatus> by Delegates.observable(listOf()) { _, old, new ->
        val diffResult = DiffUtil.calculateDiff(FoldersDiffCallback(old, new))
        diffResult.dispatchUpdatesTo(this)
    }

    var listener: FolderListAdapterListener? = null

    init {
        setHasStableIds(true)
    }

    override fun getItemCount() = data.size
    override fun getItemId(position: Int) = data[position].info.folderId.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = FolderListViewHolder (
            ListviewFolderBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
            )
    )

    override fun onBindViewHolder(holder: FolderListViewHolder, position: Int) {
        val binding = holder.binding
        val item = data[position]
        val (folderInfo, folderStats) = item
        val context = holder.itemView.context

        Log.d("FolderListAdapter", "$item")

        binding.folderNameView.text = context.getString(R.string.folder_label_format, folderInfo.label, folderInfo.folderId)

        binding.folderLastmodInfo.text = context.getString(R.string.last_modified_time,
                DateUtils.getRelativeDateTimeString(context, folderStats.lastUpdate.time, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0))

        binding.folderContentInfo.text = context.resources.getQuantityString(R.plurals.folder_content_info_files, folderStats.fileCount.toInt(), folderStats.sizeDescription, folderStats.fileCount, folderStats.dirCount)

        val info2Text = if (item.missingIndexUpdates == 0L)
            null
        else
            context.resources.getQuantityString(R.plurals.pending_index_updates, item.missingIndexUpdates.toInt(), item.missingIndexUpdates)
        
        binding.folderInfo2.text = info2Text
        binding.folderInfo2.visibility = if (info2Text.isNullOrEmpty()) android.view.View.GONE else android.view.View.VISIBLE

        binding.root.setOnClickListener {
            listener?.onFolderClicked(folderInfo, folderStats)
        }

        binding.root.setOnLongClickListener {
            listener?.onFolderLongClicked(folderInfo) ?: false
        }
    }
}

class FolderListViewHolder(val binding: ListviewFolderBinding): RecyclerView.ViewHolder(binding.root)

interface FolderListAdapterListener {
    fun onFolderClicked(folderInfo: FolderInfo, folderStats: FolderStats)
    fun onFolderLongClicked(folderInfo: FolderInfo): Boolean
}

class FoldersDiffCallback(
    private val oldList: List<FolderStatus>,
    private val newList: List<FolderStatus>
) : DiffUtil.Callback() {
    
    override fun getOldListSize(): Int = oldList.size
    
    override fun getNewListSize(): Int = newList.size
    
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].info.folderId == newList[newItemPosition].info.folderId
    }
    
    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val old = oldList[oldItemPosition]
        val new = newList[newItemPosition]
        return old.info.label == new.info.label &&
               old.stats.lastUpdate == new.stats.lastUpdate &&
               old.stats.fileCount == new.stats.fileCount &&
               old.stats.dirCount == new.stats.dirCount &&
               old.stats.sizeDescription == new.stats.sizeDescription &&
               old.missingIndexUpdates == new.missingIndexUpdates
    }
}
