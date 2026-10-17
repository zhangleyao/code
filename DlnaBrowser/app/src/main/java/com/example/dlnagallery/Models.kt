package com.example.dlnagallery

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** 一台 DLNA 媒体服务器 */
data class DlnaServer(
    val id: String,
    val name: String,
    val details: String
)

/** 媒体条目（图片或视频） */
@Parcelize
data class MediaItem(
    val id: String,
    val title: String,
    val url: String,
    val isVideo: Boolean
) : Parcelable

/** 浏览结果：子文件夹 + 媒体文件 */
data class BrowseResult(
    val folders: List<FolderItem>,
    val media: List<MediaItem>
)

data class FolderItem(
    val id: String,
    val title: String
)
