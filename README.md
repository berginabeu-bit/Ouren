# Berna Focus — Focused Mind

Production-oriented Android project for the **Focused Mind** personal commitment system. The app is designed around: intention → commitment → preparation → reminder → action → completion → reward → progress. It is not a normal task list.

## What is wired in the project
- Two and only two user-visible commitment notifications: **10 minutes before** and **at the exact scheduled time**.
- A silent internal T+5 expiry alarm removes unresolved occurrences without sending a third notification.
- A 5-minute response window for Completed / Not completed.
- A 10-minute minimum lead time when creating commitments so a new commitment can receive both planned reminders.
- One-time, Daily, Weekdays, Weekends, and Specific days recurrence.
- Recurrence advances only after the current occurrence is resolved or silently expires.
- Alarm cancellation on removal/completion/failure and rescheduling on reboot, package replacement, time/date/timezone changes, and exact-alarm permission changes.
- Offline-first local storage for commitments, progress, streaks, phrase history, language and validated premium access.
- System locale default plus manual language selection for 16 requested variants.
- Localized reminder/academic/progress content packs plus large offline phrase banks.
- Huawei IAP non-consumables for Important Events and Academic Focus, including product lookup and signed receipt validation.
- Release signing stays outside the repository and is injected by environment variables or GitHub Actions secrets.

## Toolchain
AGP 9.1.1, Gradle 9.3.1, JDK 17, compile/target SDK 36. The Compose BOM and AndroidX libraries are pinned in `gradle/libs.versions.toml`.

## Open/build
Open the project root in Android Studio, let Gradle sync, then run the `app` module. The GitHub workflows use Gradle 9.3.1 directly. This distribution does not ship a private signing key or secret.

## Local Huawei configuration
Create a local `local.properties` entry:

`focusedMindHuaweiIapPublicKey=YOUR_HUAWEI_PUBLIC_KEY`

Add `app/agconnect-services.json` when required by your AppGallery Connect application. Configure both products in AppGallery Connect with the exact IDs used by `PremiumProducts`.

## Release signing
Set `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` locally, or use the GitHub secrets `KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`. Release builds intentionally fail when signing is not configured.

## Verification
Run:

```bash
python3 tools/business_rules_audit.py
python3 tools/production_contract_audit.py
python3 tools/static_audit.py
```

The supplied build environment used for this package did not contain the Android SDK/Gradle artifacts required to execute a full Android APK build or device test, so those final environment-dependent checks must be performed on the Android Studio/CI machine.


## Content volume
The runtime includes 16 localized reminder banks × 10,000 phrases = **160,000 reminder phrases**. Academic content is localized across the same 16 languages.
