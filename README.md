# Hojo

An Android companion app for managing XTEINK X4 e-paper displays via WiFi hotspot connectivity.

## Overview

Hojo is a native Android application built with Jetpack Compose that provides a seamless interface for connecting to and managing XTEINK X4 e-paper displays. The app handles WiFi network switching, file management, and wallpaper customization.

> **Note**: This app is specifically designed for the **XTEINK X4** e-paper display and has only been tested on this device. Compatibility with other e-paper displays is not guaranteed.

## Features

### 🔌 Smart Connectivity
- Automatic WiFi hotspot detection and connection to e-paper devices
- Intelligent network binding with fallback to internet connectivity
- Real-time connection status monitoring
- Network health checks with automatic retry logic

### 📁 File Manager
- Browse and navigate the e-paper device's file system
- Upload files from your Android device
- Create folders and organize content
- Rename and delete files/folders
- Download files from the e-paper device

### 🎨 Wallpaper Editor
- Create custom wallpapers optimized for e-paper displays (480x800px, 3:5 aspect ratio)
- Image cropping and editing tools
- Direct upload to e-paper device

### 🔗 Quick Link
- Convert web articles to EPUB format using dotEPUB
- Uploads to /books directory on device for quick content display

## Technical Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material 3
- **Architecture**: MVVM with ViewModels and StateFlow
- **Minimum SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)

### Key Dependencies
- AndroidX Core KTX
- Jetpack Compose BOM
- Material 3 with Extended Icons
- Navigation Compose
- OkHttp for networking
- Gson for JSON parsing
- Jsoup for HTML parsing
- Android Image Cropper for image editing

## Project Structure

```
app/src/main/java/wtf/anurag/hojo/
├── connectivity/          # E-paper device connectivity management
│   ├── EpaperConnectivityManager.kt
│   └── SmartNetworkInterceptor.kt
├── data/                  # Data models and repositories
├── ui/                    # UI components and screens
│   ├── apps/             # Feature-specific apps (wallpaper editor)
│   ├── components/       # Reusable UI components
│   ├── viewmodels/       # ViewModels for state management
│   └── theme/            # App theming
├── utils/                # Utility functions
└── MainActivity.kt       # App entry point
```

## Building the App

### Prerequisites
- Android Studio Hedgehog or newer
- JDK 8 or higher
- Android SDK 34

### Build Instructions

1. Clone the repository:
```bash
git clone <repository-url>
cd hojo
```

2. Open the project in Android Studio

3. Sync Gradle dependencies

4. Build the project:
```bash
./gradlew build
```

5. Run on device/emulator:
```bash
./gradlew installDebug
```

### Release Build
```bash
./gradlew assembleRelease
```

The release APK will be generated at `app/build/outputs/apk/release/`

## XTEINK X4 Device Configuration

The app is configured to connect to the XTEINK X4 e-paper display with the following default settings:

- **SSID**: `E-Paper`
- **Password**: `12345678`
- **IP Address**: `192.168.3.3`
- **Port**: `80`

These settings can be modified in `EpaperConnectivityManager.kt` if your device uses different credentials.

## Permissions

The app requires the following permissions:
- `INTERNET` - For network communication
- `ACCESS_WIFI_STATE` - To check WiFi status
- `CHANGE_WIFI_STATE` - To connect to e-paper hotspot
- `CHANGE_WIFI_MULTICAST_STATE` - Required for some network discovery operations
- `ACCESS_NETWORK_STATE` - To monitor network connectivity
- `CHANGE_NETWORK_STATE` - To bind to specific networks
- `NEARBY_WIFI_DEVICES` - For WiFi device discovery (Android 13+)
- `ACCESS_FINE_LOCATION` - Required for WiFi scanning

## Usage

1. **Connect to E-Paper Device**
   - Launch the app
   - Tap the "Connect" button on the home screen
   - Grant required permissions when prompted
   - The app will automatically connect to the e-paper hotspot

2. **Manage Files**
   - Tap "File Manager" from the home screen
   - Navigate through folders
   - Use the toolbar to create folders or upload files
   - Long-press files for rename/delete options

3. **Create Wallpapers**
   - Tap "Wallpaper Editor" from the home screen
   - Select an image from your device
   - Crop and adjust as needed
   - Save to upload directly to the e-paper device

4. **Quick Link**
   - Tap "Quick Link" from the home screen
   - Enter a URL
   - The app converts and uploads the content to your e-paper display

## Development

### Code Style
- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Document complex logic with comments

### Architecture
- UI components use Jetpack Compose
- State management via ViewModels and StateFlow
- Network operations on IO dispatcher
- UI updates on Main dispatcher

## License

[Add your license information here]

## Contributing

[Add contribution guidelines here]

## Support

For issues, questions, or contributions, please [add contact/issue tracker information].
