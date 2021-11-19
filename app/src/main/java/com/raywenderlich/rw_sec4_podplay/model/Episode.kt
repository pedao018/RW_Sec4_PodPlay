package com.raywenderlich.rw_sec4_podplay.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.*

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Podcast::class,
            parentColumns = ["id"],
            childColumns = ["podcastId"],
            /*
            onDelete: Defines the behavior when the parent entity is deleted.
            CASCADE indicates that any time you delete a podcast, all related child episodes are deleted automatically.
            * */
            onDelete = ForeignKey.CASCADE
        )
    ],
    /*
    Room recommends creating an index on the child table.
    This prevents a full scan of the database when performing cascading operations.
    In this case, the indices attribute define podcastId as the index.
    * */
    indices = [Index("podcastId")]
)
data class Episode(
    @PrimaryKey var guid: String = "",
    var podcastId: Long? = null,
    var title: String = "",
    var description: String = "",
    var mediaUrl: String = "",
    var mimeType: String = "",
    var releaseDate: Date = Date(),
    var duration: String = ""
)
