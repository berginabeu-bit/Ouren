# Berna Focus 4.0.0

- 16 supported locales; localized UI parity audit.
- 160,000 runtime reminder phrases (10,000 per locale).
- Huawei IAP product IDs and receipt validation remain separated from private credentials.
- GitHub Actions builds debug APK plus signed release APK/AAB and runs tests/lint/content audit.
- Self-bootstrapping Gradle launcher for local/Termux-friendly builds when Gradle is not installed.
- Offline conflict awareness warns about commitments scheduled within 20 minutes of each other.
- No T-5 visible notification; only T-10 and exact-time reminders.
- Silent T+5 expiry remains internal.
