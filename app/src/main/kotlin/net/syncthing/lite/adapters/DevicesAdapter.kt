package net.syncthing.lite.adapters

import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import android.view.LayoutInflater
import android.view.ViewGroup
import net.syncthing.java.bep.connectionactor.ConnectionInfo
import net.syncthing.java.bep.connectionactor.ConnectionStatus
import net.syncthing.java.core.beans.DeviceInfo
import net.syncthing.lite.R
import net.syncthing.lite.databinding.ListviewDeviceBinding
import kotlin.properties.Delegates

class DevicesAdapter: RecyclerView.Adapter<DeviceViewHolder>() {
    var data: List<Pair<DeviceInfo, ConnectionInfo>> by Delegates.observable(listOf()) { _, old, new ->
        val diffResult = DiffUtil.calculateDiff(DevicesDiffCallback(old, new))
        diffResult.dispatchUpdatesTo(this)
    }

    var listener: DeviceAdapterListener? = null

    init {
        setHasStableIds(true)
    }

    override fun getItemCount() = data.size
    override fun getItemId(position: Int) = data[position].first.deviceId.deviceId.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = DeviceViewHolder(
            ListviewDeviceBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
            )
    )

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val binding = holder.binding
        val context = binding.root.context
        val (deviceInfo, connectionInfo) = data[position]

        // Ensure device name is never empty - fallback to device ID if name is null/empty
        binding.name = deviceInfo.name?.takeIf { it.isNotBlank() } 
            ?: deviceInfo.deviceId.deviceId.take(7)
        binding.isConnected = connectionInfo.status == ConnectionStatus.Connected

        binding.status = when (connectionInfo.status) {
            ConnectionStatus.Connected -> context.getString(R.string.device_status_connected, connectionInfo.currentAddress?.address)
            ConnectionStatus.Connecting -> context.getString(R.string.device_status_connecting, connectionInfo.currentAddress?.address)
            ConnectionStatus.Disconnected -> if (connectionInfo.addresses.isEmpty())
                context.getString(R.string.device_status_no_address)
            else
                context.resources.getQuantityString(R.plurals.device_status_known_addresses, connectionInfo.addresses.size, connectionInfo.addresses.size)
        }

        binding.root.setOnLongClickListener { listener?.onDeviceLongClicked(deviceInfo) ?: false }

        binding.executePendingBindings()
    }
}

interface DeviceAdapterListener {
    fun onDeviceLongClicked(deviceInfo: DeviceInfo): Boolean
}

class DeviceViewHolder(val binding: ListviewDeviceBinding): RecyclerView.ViewHolder(binding.root)

class DevicesDiffCallback(
    private val oldList: List<Pair<DeviceInfo, ConnectionInfo>>,
    private val newList: List<Pair<DeviceInfo, ConnectionInfo>>
) : DiffUtil.Callback() {
    
    override fun getOldListSize(): Int = oldList.size
    
    override fun getNewListSize(): Int = newList.size
    
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldList[oldItemPosition].first.deviceId == newList[newItemPosition].first.deviceId
    }
    
    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val old = oldList[oldItemPosition]
        val new = newList[newItemPosition]
        return old.first.name == new.first.name &&
               old.second.status == new.second.status &&
               old.second.currentAddress == new.second.currentAddress &&
               old.second.addresses == new.second.addresses
    }
}
