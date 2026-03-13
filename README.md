# Eazpire Creator – Android App

Native Android-App für den Eazpire Creator-Bereich (Shop + Creator).

## Voraussetzungen

- **Option A:** Android Studio (empfohlen)
- **Option B:** CLI (ohne Android Studio)
  - JDK 17 (z.B. `winget install EclipseAdoptium.Temurin.17.JDK`)
  - `npm run android:setup` – lädt Android SDK, installiert Komponenten, erstellt `local.properties`

## Projekt öffnen

1. Android Studio öffnen (enthält JDK 17)
2. **File → Open** → `android/` Ordner wählen
3. Gradle Sync abwarten (Wrapper ist enthalten: `gradlew`, `gradlew.bat`)

**CLI-Build:** `npm run android:build` (nach `npm run android:setup`) oder `./gradlew assembleDebug`

## API-Konfiguration

Die App nutzt die creator-engine API:
- **Base URL:** `https://creator-engine.eazpire.workers.dev`
- **Auth:** JWT via `Authorization: Bearer <token>`

**OAuth-Flow:** App öffnet Shopify-Login in WebView → Callback `shop.allyoucanpink.eazpire://callback` → Token-Exchange gegen JWT → JWT in EncryptedSharedPreferences.

Voraussetzung: Shopify Customer Account API mit Mobile Client (Callback-URL) konfiguriert.

## Sync zu eazpire-android

Änderungen an `android/` werden bei Push auf `main` automatisch zu
`eazpire/eazpire-android` synchronisiert (GitHub Action: `sync-android.yml`).

**Manuell:** `node scripts/android/sync-to-android-repo.js`

Voraussetzung: `eazpire-android` Repo existiert, `ANDROID_REPO_PUSH_TOKEN` gesetzt.
Setup-Anleitung: `docs/setup/ANDROID_REPO_SETUP.md` oder `npm run android:repo-setup`
