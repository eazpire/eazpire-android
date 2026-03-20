# APK-Installation

## Richtige APK (aktuellste Version)

Die APK wird wegen `buildDir` in Temp gebaut:

```
%TEMP%\eazpire-android-build\app\outputs\apk\debug\app-debug.apk
```

Windows-Beispiel:
```
C:\Users\<USER>\AppData\Local\Temp\eazpire-android-build\app\outputs\apk\debug\app-debug.apk
```

## Installieren auf verbundenem Gerät

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$apk = "$env:TEMP\eazpire-android-build\app\outputs\apk\debug\app-debug.apk"

# Verbinden (IP:Port von Wireless Debugging)
& $adb connect 192.168.3.12:45091

# Optional: Alte Version entfernen
& $adb -s 192.168.3.12:45091 uninstall com.eazpire.creator

# Zuerst bauen: .\gradlew assembleDebug
# Dann installieren
& $adb -s 192.168.3.12:45091 install -r $apk
```

## Hinweis

- **RICHTIG:** APK aus `%TEMP%\eazpire-android-build\app\outputs\apk\debug\app-debug.apk` (buildDir in build.gradle.kts)
- **FALSCH:** `android/app/build/outputs/apk/debug/app-debug.apk` – veraltete/leere Build-Ausgabe, nicht verwenden
