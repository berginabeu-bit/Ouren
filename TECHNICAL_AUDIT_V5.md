# Technical audit — Focused Mind v5

## Fixed from the supplied v4 archive
- Kotlin sources were under `com/example` while the intended Android package was `com.focusedmind.app`. The source tree, package declarations, Android namespace, applicationId and manifest component names are now aligned to `com.focusedmind.app`.
- `AndroidManifest.xml` no longer declares a deprecated `package` attribute.
- Huawei IAP's `allowBackup` manifest conflict is resolved with `xmlns:tools` and `tools:replace="android:allowBackup"`.
- Release signing no longer breaks debug builds during Gradle configuration when secrets are absent.
- GitHub Actions now has a separate debug verification job and a manually triggered signed-release job using GitHub Secrets.
- The workflow no longer creates a throwaway certificate and presents it as a production release path.
- A 5-minute expiry alarm is scheduled internally to clean one-off commitments that were not answered, without sending a third user-facing reminder.
- Recurring commitments advance to the next selected weekday after the exact-time occurrence; one-off commitments never repeat automatically.
- Boot/package-replaced rescheduling remains enabled.

## Content actually present in this project
- General reminder JSON: 10,005 entries.
- Academic JSON: 50,008 entries.
- Gamification JSON: 500 entries.

These counts refer to the raw local databases bundled in the archive. Localized asset packs are also present, but UI localization should be tested on every target locale before store submission.

## Still account-specific before AppGallery production
- Production keystore and passwords.
- Huawei AppGallery Connect project configuration.
- Final package registration and SHA-256 certificate registration.
- `agconnect-services.json` if required by the enabled Huawei services configuration.
- Actual Huawei IAP products and final product IDs/prices/territories.
- Sandbox and real-device purchase testing.

No ZIP can safely contain those private account credentials.
