# MRT1 – Native Android Dashboard (Peugeot Pars 1392/1393)

Native Kotlin app (no WebView/browser) for the XY Auto head unit — Android 10, 2 GB RAM, 1080×600.
Luxury dark glass design, Persian RTL UI, hybrid Persian voice assistant **MRT / Jarvis**.
Version 1.1.0.

## Install the APK
1. Copy `MRT1-v1.1.0.apk` to a USB stick → open it on the head unit (allow *Unknown sources*).
2. Press Home and choose **MRT1** → *Always*: it becomes the launcher and starts on every boot.
   A BOOT_COMPLETED receiver is also included.
3. Allow the microphone when asked.

The APK is signed with a development key (fine for sideloading). For your own signing key, edit
`signingConfigs` in `app/build.gradle`.

## Build it yourself
- GitHub: upload this folder → **Actions** → *Build APK* → download the `MRT1-apk` artifact.
- Local: JDK 17 + Android SDK 34 → `gradle :app:assembleRelease` → `app/build/outputs/apk/release/`.

## First launch (automatic, no manual setup)
`HardwareProfiler` detects Android version/SDK, resolution, density, orientation, status/navigation bar,
audio, microphone, speaker/TTS, Persian TTS, speech recognition, Bluetooth, GPS, storage, MCU properties
and the CAN provider, then saves the profile locally. If the system grants reboot permission, MRT1 performs
**one** controlled reboot; otherwise it shows "Initialization completed. Please reboot the head unit once."
No root, nothing in the Android system is changed.

## CAN — auto-detected, read-only, honest
- `CanDiscovery` inspects vendor apps (XY Auto/xygala, syu, microntek, fyt, hzbhd…), vendor broadcasts,
  published binder services, serial ports, USB CAN adapters, SocketCAN and MCU properties. It never binds or transmits.
- `CanManager` starts candidates in order and keeps the first one that **actually delivers valid frames**;
  otherwise it falls through to the next. No provider → "CAN: DISCONNECTED", all values `--`.
- Pipeline: CAN frame → `CanDecoder` → vehicle signal → `VehicleDataRepository` → dashboard.
  `CanDecoder` contains no guessed Pars IDs; add verified signals captured on the car.
- Default operation is **READ-ONLY** — MRT1 never sends CAN commands.

## Settings (12 sections, glass UI)
Vehicle (Pars profile, units) · CAN (provider auto, status, protocol, read-only diagnostics, raw monitor/frame log)
· Dashboard layout (Standard / Left info / Right info / Custom) · Clock & Date · Display · Voice · AI/OpenAI
· Media · Steering wheel (learn assistant key) · Diagnostics · System · About MRT1.

## Clock & date
Taken from the Android system clock and timezone (HH:MM, DD/MM/YYYY, day of week, optional analog clock);
updates automatically when system time changes, works offline. No hard-coded date.

## Diagnostics
Long-press the gear icon (or Settings → Diagnostics): provider, connection, RX/TX/error counts, last frame
ID + payload, decoded signal, measured frequency per ID, hardware profile, touch coordinates, Program Log
with **Clear MRT1 Log**.

## Stability & self-recovery
`CrashHandler` saves a small record, stops crash loops, clears only MRT1's own cache/logs and restarts to the
dashboard. The dashboard is always shown: CAN fails → "CAN DISCONNECTED", microphone → "Unavailable",
TTS → "Voice output: Unavailable", OpenAI → "AI: Offline". Boot order: dashboard → CAN → vehicle data → voice → AI;
nothing waits for Internet.

## OpenAI key (never in source code)
Settings → AI → paste key → enable → Save. Stored with Android Keystore (EncryptedSharedPreferences).

## Voice
Installed speech recognition (fa-IR, offline preferred) and installed TTS. Vehicle/media/volume/screen commands
are handled locally (`CommandRouter`); only general questions go to OpenAI when online. Push-to-talk by default;
wake word "MRT" is an extension point.

## Demo mode
Settings → Vehicle → Demo mode (OFF by default, orange banner) — UI testing only.

## Safety
No root, no system partitions, no MCU/CAN firmware, camera or radio changes. Volume/media/call keys pass through
to the factory system. All texts in `res/values/strings.xml`, read via `getString(R.string.x)`.

## Testing status
Compiled successfully. Not yet run on the real head unit — CAN, voice and boot auto-start must be verified in the car.

## Structure
```
app/src/main/java/com/mrt/jarvis/
  MainActivity.kt, MrtApp.kt
  dashboard/    GaugeView, AnalogClockView, StatusIndicatorsView, DashboardViewModel
  can/          CanModels, CanDecoder, CanProvider, CanManager, CanDiscovery, CanFrameLog, providers
  vehicle/      VehicleData, VehicleDataRepository
  voice/        VoiceManager, AssistantController
  ai/           CommandRouter, OpenAIService, Net
  settings/     SettingsActivity, AppPrefs
  diagnostics/  DiagnosticsActivity
  system/       HardwareProfiler, CrashHandler, LogManager
  boot/         BootReceiver
```
