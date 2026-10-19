package com.wandervox.features.log.di

import com.wandervox.features.log.photo.InternalLogPhotoStorage
import com.wandervox.features.log.photo.LogPhotoStorage
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class LogModule {

    @Binds
    abstract fun bindLogPhotoStorage(impl: InternalLogPhotoStorage): LogPhotoStorage
}
