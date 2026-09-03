# Berna Focus — Final Engineering Scope

This ZIP is a production-oriented Android source project for Focused Mind. The application logic is integrated in code, not represented only by documentation.

## Runtime contract
- Canonical package/application ID: `com.focusedmind.app`.
- Launcher activity: `MainActivity`.
- User-visible reminders: exactly T−10 and exact time.
- Internal T+5 expiration is silent.
- Completion/Not Completed is accepted only during T..T+5.
- New commitments require at least 10 minutes of lead time.
- One-time commitments never recur.
- Recurring commitments create their next occurrence only after the current occurrence is resolved.
- Future alarms are cancelled when an occurrence is completed, marked not completed, expires, or is removed.
- Future alarms are rebuilt after boot/package update/time/date/timezone/exact-alarm-permission changes.

## Premium
Huawei IAP products are non-consumables. The app fetches product configuration before checkout, uses Huawei's checkout, verifies the returned signature, verifies package/product/purchase state/type, and cross-checks the receipt price/currency against the product information available at validation time. Previously validated entitlements remain available offline.

## Localization
System language is the default. Manual language selection persists. UI, notifications, academic content, progress content and paywall/system messages have coverage for the 16 requested variants, with localized asset packs for reminder/academic/progress content.

## Verification limitation
The packaging environment did not contain the Android SDK and a Gradle distribution, so a full APK compilation and real-device verification could not be executed inside this environment. Static business-rule, production-contract, JSON, package and secret-exclusion audits pass.
