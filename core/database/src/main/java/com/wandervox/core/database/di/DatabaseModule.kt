package com.wandervox.core.database.di

import android.content.Context
import androidx.room.Room
import com.wandervox.core.database.WanderVoxDatabase
import com.wandervox.core.database.dao.TravelLogDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** `@Provides` (not `@Binds`): Room generates the implementations, there is no class to bind. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WanderVoxDatabase =
        Room.databaseBuilder(context, WanderVoxDatabase::class.java, WanderVoxDatabase.DATABASE_NAME)
            .build()

    @Provides
    fun provideTravelLogDao(database: WanderVoxDatabase): TravelLogDao = database.travelLogDao()
}
