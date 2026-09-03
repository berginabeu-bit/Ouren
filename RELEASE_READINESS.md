# Release readiness

The project is structured around a consistent namespace/package/applicationId (`com.focusedmind.app`), Android Manifest receivers, offline local state, two-notification scheduling, recurring occurrence handling, and Huawei IAP separation.

Before a public release, the owner must test on a Huawei/AppGallery device with the real AppGallery Connect application, real IAP products, signing certificate/SHA-256, Huawei IAP public key, notifications enabled, exact alarm access where available, battery restrictions, reboot, package replacement, offline mode, purchase/restore and refund/ownership scenarios.
