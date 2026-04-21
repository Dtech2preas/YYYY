# D-TECH Rewards

This project now includes both a web frontend and a native Android app.

## Android App
The Android app is located in the `android_app` directory. It uses Kotlin and XML layouts to provide a native equivalent to the web frontend UI and logic, communicating with the exact same Cloudflare Worker backend.

To build the APK:
1. Ensure you have the Android SDK setup.
2. In the root directory, run: `gradle assembleDebug`
3. The resulting APK will be at `android_app/build/outputs/apk/debug/android_app-debug.apk`
