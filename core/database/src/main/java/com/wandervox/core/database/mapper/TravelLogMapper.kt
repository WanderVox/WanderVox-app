package com.wandervox.core.database.mapper

import com.wandervox.core.database.entity.TravelLogEntity
import com.wandervox.core.domain.model.Coordinates
import com.wandervox.core.domain.model.TravelLogEntry

fun TravelLogEntity.asDomain(): TravelLogEntry = TravelLogEntry(
    id = id,
    userId = userId,
    originalText = originalText,
    translatedText = translatedText,
    sourceLanguage = sourceLanguage,
    targetLanguage = targetLanguage,
    timestamp = timestamp,
    coordinates = if (latitude != null && longitude != null) Coordinates(latitude, longitude) else null,
    localPhotoPath = localPhotoPath,
    remotePhotoUrl = remotePhotoUrl,
    category = category,
    notes = notes,
    isPublic = isPublic,
    likes = likes,
    authorName = authorName,
    updatedAt = updatedAt,
    syncStatus = syncStatus
)

fun TravelLogEntry.asEntity(): TravelLogEntity = TravelLogEntity(
    id = id,
    userId = userId,
    originalText = originalText,
    translatedText = translatedText,
    sourceLanguage = sourceLanguage,
    targetLanguage = targetLanguage,
    timestamp = timestamp,
    latitude = coordinates?.latitude,
    longitude = coordinates?.longitude,
    localPhotoPath = localPhotoPath,
    remotePhotoUrl = remotePhotoUrl,
    category = category,
    notes = notes,
    isPublic = isPublic,
    likes = likes,
    authorName = authorName,
    updatedAt = updatedAt,
    syncStatus = syncStatus
)
