# Verification and Integration Notes

1. Huawei IAP is code-integrated but cannot be authenticated against the owner's AppGallery Connect account without the owner's configuration and public key.
2. The private release keystore is intentionally excluded.
3. The large 10,000+ general and 50,000+ academic source banks are included as offline JSON resources. Localized runtime packs are provided for all requested languages; their curated size is intentionally smaller than the English master banks to keep the APK practical.
4. Android vendor battery restrictions can still affect exact alarms. The app requests the Android exact-alarm permission where supported and falls back to the best permitted alarm mode when exact scheduling is unavailable.
5. Device-level validation on Huawei hardware is still required before production publication.
