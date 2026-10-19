package com.wandervox.core.network.remote.placeholder

import com.wandervox.core.network.remote.image.ImageUploader
import com.wandervox.core.network.remote.log.LogRemoteDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// TEMPORARY: replaced by Track B's bindings. See UnavailableRemote.kt.
@Module
@InstallIn(SingletonComponent::class)
abstract class RemotePlaceholderModule {

    @Binds
    abstract fun bindLogRemoteDataSource(impl: UnavailableLogRemoteDataSource): LogRemoteDataSource

    @Binds
    abstract fun bindImageUploader(impl: UnavailableImageUploader): ImageUploader
}
