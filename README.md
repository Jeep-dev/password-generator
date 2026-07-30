# Password Generator v2.0 🔒

[![Download APK v2.0.0](https://img.shields.io/badge/Download-APK_v2.0.0-6750A4?style=for-the-badge&logo=android)](https://raw.githubusercontent.com/Jeep-dev/password-generator/main/app/build/apk/PasswordGen_v2.apk)

A clean, modern, **100% Kotlin** password generator for Android — now upgraded to **targetSdk 37 (Android 17)** with Material You aesthetics.

![App Icon](app/src/main/res/mipmap-xxxhdpi/ic_launcher.png)

---

## ✨ Features

- **🔐 Strong Random Passwords** — Uses `SecureRandom` with configurable character pools
- **⚙️ Customizable Options**
  - Uppercase / Lowercase letters
  - Numbers & Symbols
  - Exclude ambiguous characters (`0O1lI|`)
- **📏 Adjustable Length** — Slide from 6 to 32 characters
- **📊 Real-time Strength Meter** — Entropy calculation with visual color bar (Weak → Very Strong)
- **📜 Recent Passwords History** — Saves last 5 generated passwords; tap to copy instantly
- **📳 Haptic Feedback** — Micro-vibration on Generate & Copy (v2.0)
- **🎨 Immersive Status Bar** — Edge-to-edge design with light status icons
- **🔄 Instant Copy** — One-tap copy to clipboard

## 📸 Screenshots

| Main Screen | History |
|:---:|:---:|
| ![Screenshot](screenshots/main.png) | ![History](screenshots/history.png) |

> *Screenshots will be added after first release.*

---

## 🏗️ Project Structure

```
PasswordGenerator/
├── app/
│   ├── build/
│   │   └── apk/
│   │       └── PasswordGen_v2.apk    # Pre-built APK (targetSdk 37)
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── java/
│           │   └── com/passwordgen/
│           │       └── MainActivity.kt    # 100% Kotlin — single-file app
│           └── res/
│               ├── drawable/              # Vector icons & UI drawables
│               ├── mipmap-{hdpi,mdpi,xhdpi,xxhdpi,xxxhdpi}/
│               │   └── ic_launcher.png    # App launcher icons
│               └── values/
│                   ├── colors.xml         # Material You color palette
│                   └── strings.xml
├── build.sh                               # Manual build script
├── LICENSE                                # MIT License
├── .gitignore
└── README.md                              # This file
```

---

## 🛠️ Build from Source

### Prerequisites

- **Android SDK** (platform `android-37` recommended, `android-34` minimum)
- **Kotlin compiler** (`kotlinc` 1.3+)
- **Build tools**: `d8`, `aapt2`, `apksigner`, `zip`
- **Java 17+** (for `apksigner`)

### Quick Build

```bash
# 1. Compile Kotlin → class
kotlinc -cp $ANDROID_HOME/platforms/android-37/android.jar \
  -d build/kotlin_classes \
  app/src/main/java/com/passwordgen/MainActivity.kt

# 2. Convert class → dex (with Kotlin stdlib)
d8 --lib $ANDROID_HOME/platforms/android-37/android.jar \
  --output build/tmpdex/ \
  build/kotlin_classes/com/passwordgen/*.class \
  /path/to/kotlin-stdlib.jar

# 3. Compile resources
aapt2 compile --dir app/src/main/res/ -o build/compiled_res/all.zip

# 4. Link APK
aapt2 link -o build/unsigned.apk \
  -I $ANDROID_HOME/platforms/android-34/android.jar \
  --manifest app/src/main/AndroidManifest.xml \
  build/compiled_res/*.flat

# 5. Add dex & sign
cd build && zip unsigned.apk tmpdex/classes.dex
apksigner sign --ks release.keystore \
  --ks-pass pass:android \
  --ks-key-alias passwordgen \
  unsigned.apk
```

> 💡 See [`build.sh`](build.sh) for the full automated build script.

---

## 📦 Pre-built APK

> **👉 [⬇️ Download PasswordGen_v2.apk (77 KB)](https://raw.githubusercontent.com/Jeep-dev/password-generator/main/app/build/apk/PasswordGen_v2.apk)** — Click to download directly!

A ready-to-install APK is also available at [`app/build/apk/PasswordGen_v2.apk`](app/build/apk/PasswordGen_v2.apk).

```
package: com.passwordgen
versionCode: 2
versionName: 2.0.0
targetSdkVersion: 37
minSdkVersion: 23
```

### Install via ADB

```bash
adb install app/build/apk/PasswordGen_v2.apk
```

Or download the APK from the link above and install it manually on your device.

---

## 📋 Changelog

### v2.0.0 (Current)
- ✅ **100% Kotlin** — Full rewrite from Java
- ✅ **targetSdk 37 (Android 17)**
- ✅ **Recent Passwords History** — Last 5 passwords, tap to copy
- ✅ **Haptic Feedback** — On Generate & Copy
- ✅ **Immersive Status Bar** — Color-matched background + light icons
- ✅ **Compact UI** — Single-screen layout with smooth scrolling
- ✅ **Enhanced Strength Meter** — Entropy-based with color-coded bar

### v1.0 (Original)
- Java-based implementation
- Basic password generation with options

---

## 🔒 Security

- All passwords are **generated locally** on-device using `SecureRandom`
- **No data leaves your device** — no network permissions required
- History is stored **in memory only** (cleared on app restart)

---

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.

---

## 👨‍💻 Author

**Jeep-dev** — [GitHub](https://github.com/Jeep-dev)

---

*Made with ❤️ and Kotlin*
