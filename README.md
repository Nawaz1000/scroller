# ⚡ ReelFlow — Hands-Free Auto-Scroller for Shorts & Reels

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android_8.0+_(API_26--35)-00F59B?style=for-the-badge&logo=android&logoColor=black" />
  <img src="https://img.shields.io/badge/Language-Kotlin_2.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" />
  <img src="https://img.shields.io/badge/Design-Cyberpunk_OLED_Dark-00D2FF?style=for-the-badge" />
  <img src="https://img.shields.io/badge/Build-Gradle_8.11_|_JDK_21-02569B?style=for-the-badge&logo=gradle&logoColor=white" />
</p>

**ReelFlow** is an intelligent, cyber-minimalist Android companion app that provides **100% hands-free automated scrolling** on short-form video platforms including **YouTube Shorts**, **Instagram Reels**, **TikTok**, and **Facebook Reels**.

Equipped with exact video seekbar timing detection, dynamic swipe scheduling, and a draggable dual-state Floating HUD controller, ReelFlow lets you relax while your video feed automatically advances the moment each video completes.

---

## ✨ Features

### 🔄 The Pulse Engine
* **Rotating Radar Visualizer**: High-tech cyber pulse graphics showing live engine status (`ONLINE` / `PAUSED`).
* **Session Telemetry**: Real-time counter of total videos scrolled today and cumulative time saved.
* **Instant Master Switch**: Pause or activate auto-scrolling with a single tap.

### ⏱️ High-Precision Video Detection
* **Seekbar Time Description Parsing**: Extracts live playback timing directly from media accessibility nodes (e.g. `0 minutes 8 seconds of 0 minutes 13 seconds`).
* **Dynamic Swipe Scheduling**: Accurately computes remaining milliseconds and schedules the gesture to fire exactly when the video ends.
* **Intelligent Loop Detection**: Detects video loop resets ($>80\% \rightarrow <12\%$) for videos without explicit timers.
* **Safety Timeout Fallback**: Configurable timeout (10s – 90s) prevents getting stuck on indefinite live streams or static feeds.

### 🪄 Dual-State Floating Overlay Controller
* **Expanded Capsule Mode**:
  * Live status dot & audio waveform visual.
  * **Play / Pause** toggle.
  * **Skip** button to manually advance immediately.
  * **Minimize** button to collapse into bubble mode.
* **Collapsed Bubble Mode**:
  * Compact circular floating badge anchored to the screen edge.
  * Draggable to any position.
  * Tap to instantly expand back into full controls.

### 🎛️ Target Platforms & Fine-Tuning
* **Platform Toggles (2x2 Grid)**:
  * YouTube Shorts (`com.google.android.youtube`)
  * Instagram Reels (`com.instagram.android`)
  * TikTok (`com.zhiliaoapp.musically` / `com.ss.android.ugc.trill`)
  * Facebook Reels (`com.facebook.katana`)
* **Fine-Tuning Sliders**:
  * **Post-Video Delay**: Add a comfortable buffer (0s – 5.0s) before swiping.
  * **Safety Timeout**: Set maximum dwell time (10s – 90s).

### 🛡️ Permissions & Readiness Dashboard
* Interactive permission cards showing real-time readiness status:
  * **Accessibility Engine** (`BIND_ACCESSIBILITY_SERVICE`)
  * **Overlay Controller** (`SYSTEM_ALERT_WINDOW`)
  * **Battery Optimization Exemption** (ensures uninterrupted background operation)

---

## 🏛️ Architecture & Tech Stack

```text
com.autoscroller.app
├── data
│   └── PreferencesManager.kt         # Persistent user settings & telemetry store
├── service
│   ├── AutoScrollAccessibilityService.kt  # Background polling & gesture dispatching
│   ├── VideoDetector.kt              # Regex time extraction & loop evaluator
│   └── FloatingControlService.kt     # Dual-state WindowManager overlay HUD
└── ui
    └── MainActivity.kt               # OLED Dark UI, radar animation & controls
```

* **Kotlin 2.0.21** + **Coroutines & StateFlow**
* **Android Accessibility Service API** (`dispatchGesture` for smooth touch flicks)
* **Android WindowManager API** (Floating overlay HUD with touch listener drag physics)
* **Google Material 3** Cyberpunk dark design tokens

---

## 🚀 Getting Started

### Prerequisites
* **Android Studio** Ladybug (2024.2+) or newer
* **JDK 21** (Eclipse Temurin 21 recommended)
* **Android Device / Emulator** running Android 8.0+ (API 26+)

### Clone the Repository
```bash
git clone https://github.com/Nawaz1000/scroller.git
cd scroller
```

### Build & Run via Gradle
```bash
# On Linux / macOS:
./gradlew assembleDebug

# On Windows:
.\gradlew.bat assembleDebug

# Install APK to connected device:
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🐳 Docker Build

ReelFlow includes a multi-stage Docker build environment to compile the APK headlessly without installing the Android SDK locally.

### Build APK via Docker
```bash
# Build the Docker image
docker build -t reelflow .

# Extract the compiled APK to your current directory
docker create --name reelflow-extract reelflow
docker cp reelflow-extract:/out/reelflow-debug.apk ./reelflow-debug.apk
docker rm -f reelflow-extract
```

---

## ⚡ Quick ADB Permissions (For Developers)

To grant all required permissions instantly via ADB:

```bash
# 1. Enable Accessibility Service:
adb shell settings put secure enabled_accessibility_services com.autoscroller.app/.service.AutoScrollAccessibilityService
adb shell settings put secure accessibility_enabled 1

# 2. Grant Overlay Permission:
adb shell appops set com.autoscroller.app SYSTEM_ALERT_WINDOW allow

# 3. Launch ReelFlow:
adb shell am start -n com.autoscroller.app/.ui.MainActivity
```

---

## 📄 License
This project is open-source and licensed under the [MIT License](LICENSE).
