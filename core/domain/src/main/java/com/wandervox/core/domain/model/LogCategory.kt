package com.wandervox.core.domain.model

/**
 * Travel log categories. The enum name is the stable, language-independent value persisted in
 * Room and Firestore; user-facing labels live in each feature's string resources.
 */
enum class LogCategory {
    FOOD,
    TRANSPORT,
    LODGING,
    SHOPPING,
    EMERGENCY,
    GENERAL
}
