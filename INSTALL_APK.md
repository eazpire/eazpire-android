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
& $adb connect 192.168.3.12:35301

# Installieren
& $adb -s 192.168.3.12:35301 install -r $apk
```

## Hinweis

Die Datei `app-debug.apk` im Projektordner ist **nicht** die aktuelle Build-Ausgabe.
