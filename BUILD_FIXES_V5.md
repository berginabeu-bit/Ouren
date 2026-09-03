# Focused Mind v5 Build and Stability Fixes

This revision fixes the package mismatch that caused `ClassNotFoundException: com.focusedmind.app.MainActivity` by making the Android namespace, applicationId, Kotlin package tree and manifest class names consistent: `com.focusedmind.app`.

It also fixes Huawei IAP manifest merging with `xmlns:tools` and `tools:replace="android:allowBackup"`, removes the deprecated manifest `package` attribute, keeps debug builds possible without release credentials, and makes release builds fail clearly only when signing credentials are missing.

## GitHub release secrets
- `KEYSTORE_BASE64`
- `STORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Do not commit the production keystore or passwords.

## Important
The debug workflow builds on every push/PR. The signed release job runs only manually and requires the four secrets above.
