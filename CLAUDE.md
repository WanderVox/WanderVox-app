# WanderVox - Context & Architecture Guidelines

## Project Overview
Offline-first travel assistant Android app developed by Lautaro (Dev A) and Lucas (Dev B).
Clean Architecture (Multi-module), 100% Jetpack Compose, UDF/MVI, Hilt, Coroutines & Flow.

## Tech Stack & Toolchain Constraints
- Build System: Gradle 8.14.3, AGP 8.7.3, KSP 2.0.21-1.0.28
- Language: Kotlin 2.0.21, Java 17 toolchain (jvmTarget = "17")
- Android SDK: minSdk = 27, compileSdk = 35, targetSdk = 35
- Core Libraries: Hilt 2.51.1, Jetpack Compose (BOM), Vosk Android 0.3.47, ML Kit Translate 17.0.3, OSMDroid 6.1.18

## Architectural Invariants
1. `:core:domain` is pure Kotlin JVM (`java-library`). Zero Android SDK imports allowed.
2. Presentation layer uses Jetpack Compose exclusively (No XML layouts, no legacy AppCompat/Material views).
3. State Management: Immutable `UiState` exposed via `StateFlow` and consumed in Compose using `collectAsStateWithLifecycle()`.
4. Dependency Injection: All classes are wired via Dagger Hilt. Prefer `@Binds` over `@Provides` when binding interfaces to implementations.
5. Offline-first: Network operations must degrade gracefully to local caches (Vosk models, ML Kit local packs, Firestore offline persistence).

## Coding Rules for Dev A Scope (ML & Hardware)
- Vosk acoustic models live in `core/network/src/main/assets/models/` and are extracted asynchronously to `context.filesDir`.
- Always wrap asynchronous results in Kotlin `Result<T>`.
- Any external library logic must be justified in `DEV_LOG.md` for university evaluation tracking.