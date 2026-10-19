package com.wandervox.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.wandervox.core.database.converter.Converters
import com.wandervox.core.database.dao.TravelLogDao
import com.wandervox.core.database.entity.TravelLogEntity

@Database(
    entities = [TravelLogEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class WanderVoxDatabase : RoomDatabase() {

    abstract fun travelLogDao(): TravelLogDao

    companion object {
        const val DATABASE_NAME = "wandervox.db"
    }
}
