package com.raywenderlich.rw_sec4_podplay.db

import android.content.Context
import androidx.room.*
import com.raywenderlich.rw_sec4_podplay.model.Episode
import com.raywenderlich.rw_sec4_podplay.model.Podcast
import kotlinx.coroutines.CoroutineScope
import java.util.*

@Database(
    entities = arrayOf(Podcast::class, Episode::class),
    version = 1
)
@TypeConverters(Converters::class)
abstract class PodPlayDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao

    companion object {
        /*
        The single instance of the PodPlayDatabase is defined and set to null.
        The @Volatile annotation marks the JVM backing field of the annotated property as volatile, meaning that writes to this field are immediately made visible to other threads.
        */
        @Volatile
        private var INSTANCE: PodPlayDatabase? = null

        fun getInstance(context: Context, coroutineScope: CoroutineScope): PodPlayDatabase {
            val tempInstance = INSTANCE
            if (tempInstance != null) {
                return tempInstance
            }

            synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PodPlayDatabase::class.java,
                    "PodPlayer"
                )
                    .build()
                INSTANCE = instance
                return instance
            }
        }
    }
}

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return if (value == null) null else Date(value)
    }

    @TypeConverter
    fun toTimestamp(date: Date?): Long? {
        return (date?.time)
    }
}

