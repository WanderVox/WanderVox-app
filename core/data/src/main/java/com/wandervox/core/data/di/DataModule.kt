package com.wandervox.core.data.di

import com.wandervox.core.data.repository.InMemoryLogRepository
import com.wandervox.domain.repository.LogRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    // TODO: bind the persistent (Room/Firestore) implementation once available.
    @Binds
    @Singleton
    abstract fun bindLogRepository(impl: InMemoryLogRepository): LogRepository
}
