# Quick Start Guide

Get Sonnet Player running in 5 minutes!

## Prerequisites

- Android Studio (latest version)
- JDK 17
- Android device or emulator (Android 7.0+)

## Build & Run

```bash
# 1. Open project in Android Studio
# File > Open > Select project folder

# 2. Sync Gradle
# Click "Sync Now" when prompted

# 3. Run on device/emulator
# Click green "Run" button or Shift+F10
```

## Or via Command Line

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# APK location: app/build/outputs/apk/debug/app-debug.apk
```

## First Use

1. Grant storage permission when prompted
2. App will scan local videos automatically
3. Tap a video to play
4. Or tap "+" button to add streaming URL

## Basic Controls

- **Tap**: Show/hide controls
- **Double-tap left**: Rewind 10s
- **Double-tap right**: Forward 10s
- **Swipe horizontal**: Seek
- **Swipe vertical (left)**: Brightness
- **Swipe vertical (right)**: Volume
- **Speed button**: Change playback speed
- **Display button**: Change aspect ratio

## Settings

Tap ⋮ (menu) > Settings to configure:
- Default playback speed
- Display mode
- Cache size
- Gestures on/off
- Hardware acceleration

## Supported Formats

### Local Files
MP4, MKV, AVI, MOV, WEBM, FLV, 3GP, MPEG

### Streaming
HTTP, HTTPS, RTSP

Example URLs to test:
```
http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4
https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8
```

## Troubleshooting

**Video won't play**
- Check file format is supported
- Enable hardware acceleration in Settings
- Try different display mode

**Gestures not working**
- Enable in Settings > Gestures
- Swipe on video area (not controls)

**Poor performance**
- Close other apps
- Lower cache size in Settings
- Disable hardware acceleration (last resort)

**Permission denied**
- Go to Android Settings > Apps > Sonnet Player > Permissions
- Grant "Files and media" permission

## Advanced Features

### Picture-in-Picture
- Supported on Android 8.0+
- Press home button while playing
- Video continues in small window

### Streaming URLs
1. Tap "+" button
2. Enter video URL
3. Tap "Play"

### Resume Playback
- Enabled by default
- Videos resume from last position
- Disable in Settings if unwanted

## Building for Release

```bash
# Release APK
./gradlew assembleRelease

# Output: app/build/outputs/apk/release/app-release.apk
```

## Need Help?

- Check README.md for full documentation
- Open issue: https://github.com/YOUR_USERNAME/subrouter-sonnet-player/issues
- Read IMPLEMENTATION.md for architecture details

Enjoy! 🎬
