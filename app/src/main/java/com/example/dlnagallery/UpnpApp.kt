package com.example.dlnagallery

import android.app.Application
import android.content.Context
import android.net.wifi.WifiManager
import org.jupnp.UpnpService
import org.jupnp.UpnpServiceImpl
import org.jupnp.model.message.header.STAllHeader
import org.jupnp.model.meta.Device
import org.jupnp.model.meta.LocalDevice
import org.jupnp.model.meta.RemoteDevice
import org.jupnp.model.types.UDAServiceId
import org.jupnp.registry.DefaultRegistryListener
import org.jupnp.registry.Registry

/**
 * 全局持有 UpnpService 单例。
 * 通过 [addServerListener] 订阅服务器上线/下线事件。
 */
class UpnpApp : Application() {

    companion object {
        lateinit var instance: UpnpApp
            private set
    }

    val upnp: UpnpService by lazy { UpnpServiceImpl() }

    private var multicastLock: WifiManager.MulticastLock? = null
    private val serverListeners = mutableListOf<(List<DlnaServer>) -> Unit>()
    private val servers = LinkedHashMap<String, DlnaServer>()

    private val registryListener = object : DefaultRegistryListener() {
        override fun remoteDeviceAdded(registry: Registry, device: RemoteDevice) {
            addDevice(device)
        }

        override fun remoteDeviceRemoved(registry: Registry, device: RemoteDevice) {
            servers.remove(device.identity.udn.toString())
            notifyListeners()
        }

        override fun localDeviceAdded(registry: Registry, device: LocalDevice) {
            addDevice(device)
        }

        override fun localDeviceRemoved(registry: Registry, device: LocalDevice) {
            servers.remove(device.identity.udn.toString())
            notifyListeners()
        }

        private fun addDevice(device: Device<*, *, *>) {
            if (device.findService(UDAServiceId("ContentDirectory")) == null) return
            val id = device.identity.udn.toString()
            val name = device.details.friendlyName ?: "未知设备"
            val detail = (device.details.manufacturerDetails?.manufacturer ?: "") +
                    " " + (device.details.modelDetails?.modelName ?: "")
            servers[id] = DlnaServer(id, name, detail.trim())
            notifyListeners()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Android 上必须持有组播锁，否则收不到 SSDP 广播
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("dlna_browser").apply {
            setReferenceCounted(true)
            acquire()
        }
        upnp.registry.addListener(registryListener)
        upnp.controlPoint.search(STAllHeader())
    }

    /** 重新搜索局域网内的 DLNA 服务器 */
    fun search() {
        upnp.controlPoint.search(STAllHeader())
    }

    fun addServerListener(listener: (List<DlnaServer>) -> Unit) {
        synchronized(serverListeners) { serverListeners.add(listener) }
        listener(currentServers())
    }

    fun removeServerListener(listener: (List<DlnaServer>) -> Unit) {
        synchronized(serverListeners) { serverListeners.remove(listener) }
    }

    fun currentServers(): List<DlnaServer> = synchronized(servers) { servers.values.toList() }

    private fun notifyListeners() {
        val list = currentServers()
        synchronized(serverListeners) { serverListeners.toList() }.forEach { it(list) }
    }

    override fun onTerminate() {
        multicastLock?.release()
        upnp.shutdown()
        super.onTerminate()
    }
}
