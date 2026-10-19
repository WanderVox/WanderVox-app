package com.wandervox.core.network.di

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.wandervox.core.network.remote.image.CloudinaryConfig
import com.wandervox.core.network.remote.image.CloudinaryImageUploader
import com.wandervox.core.network.remote.image.ImageUploader
import com.wandervox.core.network.remote.log.FirestoreLogRemoteDataSource
import com.wandervox.core.network.remote.log.LogRemoteDataSource
import com.wandervox.network.BuildConfig
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/** Remote side of the Travel Log: Firestore sync and Cloudinary photo uploads (Track B). */
@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkRemoteModule {

    @Binds
    @Singleton
    abstract fun bindLogRemoteDataSource(impl: FirestoreLogRemoteDataSource): LogRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindImageUploader(impl: CloudinaryImageUploader): ImageUploader

    // SDK and configuration objects have no injectable constructor, so @Provides is required here.
    companion object {

        @Provides
        @Singleton
        fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance().apply {
            // Settings must be applied before the first read or write; this provider is the only entry point.
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                .build()
        }

        @Provides
        @Singleton
        fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // Photos go up on slow mobile links; leave room for the request body.
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        @Provides
        fun provideCloudinaryConfig(): CloudinaryConfig = CloudinaryConfig(
            cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME,
            uploadPreset = BuildConfig.CLOUDINARY_UPLOAD_PRESET,
        )
    }
}
