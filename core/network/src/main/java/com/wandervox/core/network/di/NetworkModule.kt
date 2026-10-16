package com.wandervox.core.network.di

import com.wandervox.core.network.repository.TranslatorRepositoryImpl
import com.wandervox.core.domain.repository.TranslatorRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    @Binds
    @Singleton
    abstract fun bindTranslatorRepository(impl: TranslatorRepositoryImpl): TranslatorRepository
}
