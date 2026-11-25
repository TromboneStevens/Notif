# Notif — Keyword Alarm

An Android app that watches a web browser (Brave by default) for keywords you
choose, and when one appears on screen it **vibrates**, **plays an alarm**, and
**wakes the screen** so you don't miss it.

> Personal-use app. It uses an Android **Accessibility Service** to read
> on-screen text, which is the only supported way for one app to observe another
> app's screen. All keyword matching happens on-device; nothing is sent anywhere.

## Download the APK

A pre-built debug APK is committed directly in the repo:
**[`releases/keyword-alarm-v1.0-debug.apk`](releases/keyword-alarm-v1.0-debug.apk)**
(~6 MB, debug-signed — installs on any phone with "install unknown apps"
enabled). On GitHub, open that file and click **Download raw file**.

CI also rebuilds the APK on every push to `main`:

1. Go to the **[Actions](../../actions)** tab.
2. Open the latest **Build APK** run.
3. Download the **keyword-alarm-debug-apk** artifact.

Tagged releases (e.g. `v1.0`) attach the APK to the
**[Releases](../../releases)** page too.

## Build it yourself

Open the project in **Android Studio** (use the JDK it bundles), then
**Build → Build APK(s)**, or from a terminal with the Android SDK installed:

```bash
./gradlew assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

Requirements: Android SDK 34, JDK 17 (Android Gradle does not support newer
JDKs yet).

## Install & set up on your phone

1. Install the APK (you'll need "install unknown apps" enabled for your file
   manager / browser).
2. Open **Keyword Alarm**, enter comma-separated keywords, tap **Save**, and turn
   on **Enable monitoring**.
3. Tap **Open Accessibility settings** and enable **"Keyword Alarm detector"**.
4. Grant the notification permission when prompted.
5. Use **Test alarm** to confirm vibration + sound work.

## How it works

| Piece | Role |
|-------|------|
| `KeywordAccessibilityService` | Watches the configured browser, reads visible text nodes, matches your keywords (with a cooldown + scan rate-limit). |
| `AlarmService` | Foreground service that vibrates, loops the alarm sound, and holds a screen wake lock. Posts a high-priority, full-screen notification with a **Stop** action. |
| `MonitoringService` | While monitoring is on, shows a persistent, silent notification banner with a **Stop monitoring** button so you can turn it off from the shade. |
| `MainActivity` | Settings UI: keywords, enable toggle, cooldown, keep-awake, case-sensitivity, plus Test/Stop buttons. |
| `Prefs` | Shared settings store. |

## Settings

- **Keywords** — comma-separated; case-insensitive by default.
- **Cooldown** — minimum seconds between alarms for repeat matches (default 10).
- **Keep screen awake** — acquires a screen wake lock during the alarm.
- **Case-sensitive matching** — off by default.
- **Watched app** — Brave (`com.brave.browser`) by default (editable in `Prefs.kt`).

## Known limitations

- **Battery optimization** can throttle the background service. For 24/7 use,
  exclude the app from battery optimization in system settings.
- The accessibility tree only exposes **rendered, visible** text. Text inside a
  `<canvas>`, images, or content not yet scrolled into view may not be readable.
- Google Play restricts accessibility-service apps; this is intended for personal
  sideloading, not Play distribution.
