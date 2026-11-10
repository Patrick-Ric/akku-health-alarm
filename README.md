# Akku Health Alarm

Android app that helps your battery live longer: it tells you when to unplug the charger and when
to plug it in – at limits you set yourself, or with one tap on a gentle preset (25–70 %, 30–60 %).
It keeps a history of every charge and measures the real battery capacity. Fully offline – the app
has no internet permission.

<p>
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="200" alt="Start page">
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="200" alt="History list">
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width="200" alt="Chart of a week">
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" width="200" alt="Battery health">
</p>

## Features

**Alarm**
- Free limits from 1 to 100 %, with − / + or the keypad (a recommendation is shown, no fixed bounds)
- Quick choices 20–80 %, 25–70 % (gentle) and 30–60 % (very gentle); the two gentle ones can be changed (hold the button)
- Repeat until you react (10 s … 12 h), with a countdown in the app and in the notification
- App sound or phone sound, short spoken announcement (app voice or the phone's offline voice), vibration
- Night rest, "also when muted", the alarm notification can be switched off
- Optional temperature alarm: too hot, or too cold (by default only while charging)

**History**
- Log with one line per real change: level, temperature, current, charger type (USB / AC / wireless), alarms
- Chart for day, week, month or everything, zoomable, with temperature, voltage or charge/discharge current

**Battery health**
- Capacity estimated from your own charges (at least 15 % each), weighted over the last 10, with ± uncertainty
- Phones whose "charge counter" is only percentage × rated capacity are detected; the app then measures the current itself
- Charging speed per charger, equivalent charge cycles, recent charges

**Reliability and privacy**
- The watcher runs in its own small process (about 15 MB) as a foreground service
- Help for the battery savers of Huawei, Xiaomi, Samsung, Oppo, Vivo, OnePlus
- No internet permission, no ads, no tracking, no analytics

20 languages (en, de, fr, es, it, nl, pt-BR, pl, cs, sk, hu, ro, sv, da, nb, fi, el, tr, ru, uk);
the language can be chosen in the app. Android 7.0 (API 24) and newer.

## Building

Requirements: JDK 21 and the Android SDK.

```bash
./gradlew assembleDebug testDebugUnitTest
```

The APK is in `app/build/outputs/apk/debug/`.

### Translations

The texts live in `app/src/main/res/values*/strings.xml`. After changing them directly, run
`python3 tools/i18n/export.py`; it writes `tools/i18n/source.json` and `tools/i18n/<lang>.txt`,
from which `python3 tools/i18n/build.py <langs>` rebuilds the XML files (with checks for placeholders).

## License

GPL-3.0-or-later, see [LICENSE](LICENSE).
