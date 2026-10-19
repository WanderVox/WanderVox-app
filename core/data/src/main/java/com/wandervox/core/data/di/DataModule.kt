package com.wandervox.core.data.di

import com.wandervox.core.data.location.LocationRepositoryImpl
import com.wandervox.core.data.repository.OfflineFirstLogRepository
import com.wandervox.core.domain.repository.LocationRepository
import com.wandervox.core.domain.repository.LogRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindLogRepository(impl: OfflineFirstLogRepository): LogRepository

    // Unscoped: stateless, it only holds the application Context and a system service handle.
    @Binds
    abstract fun bindLocationRepository(impl: LocationRepositoryImpl): LocationRepository
}
