package com.mangaviewer.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

const val DEFAULT_FOLDER = "default"
const val ALL_FOLDER = "all"

const val DOWNLOAD_STATUS_QUEUED = "QUEUED"
const val DOWNLOAD_STATUS_RUNNING = "RUNNING"
const val DOWNLOAD_STATUS_PAUSED = "PAUSED"
const val DOWNLOAD_STATUS_DONE = "DONE"
const val DOWNLOAD_STATUS_FAILED = "FAILED"

@Entity(tableName = "favorites", indices = [Index("addedAt")])
data class FavoriteEntity(
    @PrimaryKey val galleryId: Long,
    val title: String,
    val prettyTitle: String?,
    val thumbPath: String,
    val numPages: Int,
    val numFavorites: Long,
    val folder: String = DEFAULT_FOLDER,
    val addedAt: Long,
)

@Entity(tableName = "downloads", indices = [Index("galleryId")])
data class DownloadEntity(
    @PrimaryKey val galleryId: Long,
    val title: String,
    val thumbPath: String,
    val totalPages: Int,
    val donePages: Int,
    val status: String,
    val pagesJson: String,
    val createdAt: Long,
    val updatedAt: Long,
    val error: String? = null,
    val bytes: Long = 0,
)

@Entity(tableName = "history", indices = [Index("updatedAt")])
data class HistoryEntity(
    @PrimaryKey val galleryId: Long,
    val title: String,
    val thumbPath: String,
    val numPages: Int,
    val lastPage: Int,
    val updatedAt: Long,
)

@Entity(tableName = "comment_drafts")
data class CommentDraftEntity(
    @PrimaryKey val galleryId: Long,
    val body: String,
    val updatedAt: Long,
)
