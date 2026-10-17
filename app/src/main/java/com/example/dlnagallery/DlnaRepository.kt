package com.example.dlnagallery

import org.jupnp.controlpoint.ControlPoint
import org.jupnp.model.action.ActionInvocation
import org.jupnp.model.message.UpnpResponse
import org.jupnp.model.meta.Device
import org.jupnp.model.meta.Service
import org.jupnp.model.types.UDAServiceId
import org.jupnp.support.contentdirectory.callback.Browse
import org.jupnp.support.model.BrowseFlag
import org.jupnp.support.model.DIDLContent
import org.jupnp.support.model.container.Container
import org.jupnp.support.model.item.Item
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * DLNA ContentDirectory 浏览封装。
 */
object DlnaRepository {

    /**
     * 浏览指定服务器的某个容器，返回子文件夹和媒体列表。
     * 在协程中调用。
     */
    suspend fun browse(udn: String, containerId: String): BrowseResult =
        suspendCoroutine { cont ->
            val app = UpnpApp.instance
            val device: Device<*, *, *>? = app.upnp.registry.getDevice(
                org.jupnp.model.types.UDN(udn), false
            )
            val service: Service<*, *>? = device?.findService(UDAServiceId("ContentDirectory"))
            if (service == null) {
                cont.resumeWithException(IllegalStateException("找不到 ContentDirectory 服务，设备可能已离线"))
                return@suspendCoroutine
            }

            val latch = CountDownLatch(1)
            var result: BrowseResult? = null
            var error: String? = null

            app.upnp.controlPoint.execute(object : Browse(
                service, containerId, BrowseFlag.DIRECT_CHILDREN,
                "*", 0, 0L, null
            ) {
                override fun received(
                    invocation: ActionInvocation<out Service<*, *>>?,
                    didl: DIDLContent?
                ) {
                    val folders = mutableListOf<FolderItem>()
                    val media = mutableListOf<MediaItem>()
                    didl?.containers?.forEach { c: Container ->
                        folders.add(FolderItem(c.id, c.title ?: "文件夹"))
                    }
                    didl?.items?.forEach { it: Item ->
                        val res = it.firstResource
                        val url = res?.value
                        if (!url.isNullOrEmpty()) {
                            val mime = res.protocolInfo?.contentFormatMimeType?.toString() ?: ""
                            val clazz = it.clazz?.value ?: ""
                            val isImage = mime.startsWith("image/") || clazz.contains("imageItem")
                            val isVideo = mime.startsWith("video/") || clazz.contains("videoItem")
                            if (isImage || isVideo) {
                                media.add(
                                    MediaItem(
                                        id = it.id,
                                        title = it.title ?: "未命名",
                                        url = url,
                                        isVideo = isVideo
                                    )
                                )
                            }
                        }
                    }
                    result = BrowseResult(folders, media)
                    latch.countDown()
                }

                override fun updateStatus(status: Status?) {}

                override fun failure(
                    invocation: ActionInvocation<out Service<*, *>>?,
                    operation: UpnpResponse?,
                    defaultMsg: String?
                ) {
                    error = defaultMsg ?: "浏览失败"
                    latch.countDown()
                }
            })

            // 阻塞等待回调（最多 20 秒），避免依赖 kotlinx-coroutines
            val ok = try {
                latch.await(20, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                false
            }
            when {
                result != null -> cont.resume(result!!)
                error != null -> cont.resumeWithException(RuntimeException(error))
                !ok -> cont.resumeWithException(RuntimeException("浏览超时"))
                else -> cont.resumeWithException(RuntimeException("浏览失败"))
            }
        }
}
