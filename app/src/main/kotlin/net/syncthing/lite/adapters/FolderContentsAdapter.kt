package net.syncthing.lite.adapters

import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import net.syncthing.java.core.beans.FileInfo
import net.syncthing.lite.R
import net.syncthing.lite.databinding.ListviewFileBinding
import org.apache.commons.io.FileUtils
import kotlin.properties.Delegates

// TODO: enable setHasStableIds and add a good way to get an id
class FolderContentsAdapter: RecyclerView.Adapter<FolderContentsViewHolder>() {
    var data: List<FileInfo> by Delegates.observable(listOf()) { _, old, new ->
        val diffResult = DiffUtil.calculateDiff(FolderContentsDiffCallback(old, new))
        diffResult.dispatchUpdatesTo(this)
    }

    var listener: FolderContentsListener? = null

    init {
        // setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = FolderContentsViewHolder(
            ListviewFileBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
            )
    )

    override fun onBindViewHolder(holder: FolderContentsViewHolder, position: Int) {
        val binding = holder.binding
        val fileInfo = data[position]

        binding.fileLabel.text = fileInfo.fileName

        if (fileInfo.isDirectory()) {
            binding.fileIcon.setImageResource(R.drawable.ic_folder_black_24dp)
            binding.fileSize.visibility = View.GONE
        } else {
            binding.fileIcon.setImageResource(R.drawable.ic_image_black_24dp)
            binding.fileSize.text = binding.root.context.getString(R.string.file_info,
                    FileUtils.byteCountToDisplaySize(fileInfo.size!!),
                    DateUtils.getRelativeDateTimeString(binding.root.context, fileInfo.lastModified.time, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0))
            binding.fileSize.visibility = View.VISIBLE
        }

        binding.root.setOnClickListener {
            listener?.onItemClicked(fileInfo)
        }

        binding.root.setOnLongClickListener {
            listener?.onItemLongClicked(fileInfo) ?: false
        }

        // Note: executePendingBindings() not needed for ViewBinding
    }

    override fun getItemCount() = data.size
    // override fun getItemId(position: Int) = data[position].fileName.hashCode().toLong()
}

interface FolderContentsListener {
    fun onItemClicked(fileInfo: FileInfo)
    fun onItemLongClicked(fileInfo: FileInfo): Boolean
}

class FolderContentsViewHolder(val binding: ListviewFileBinding): RecyclerView.ViewHolder(binding.root)

class FolderContentsDiffCallback(
    private val oldList: List<FileInfo>,
    private val newList: List<FileInfo>
) : DiffUtil.Callback() {
    
    override fun getOldListSize(): Int = oldList.size
    
    override fun getNewListSize(): Int = newList.size
    
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].fileName == newList[newItemPosition].fileName
    }
    
    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val old = oldList[oldItemPosition]
        val new = newList[newItemPosition]
        return old.fileName == new.fileName &&
               old.isDirectory() == new.isDirectory() &&
               old.size == new.size &&
               old.lastModified == new.lastModified
    }
}
