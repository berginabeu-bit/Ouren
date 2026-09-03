# Berna Focus 4.0.0 — Huawei AppGallery final setup

The project contains the Android programming, offline logic, two-stage notification system, localization, gamification, premium gating and Huawei IAP validation flow. The remaining owner-only steps are store/account configuration and production credentials.

## GitHub Actions secrets

Required for signed release:
- `KEYSTORE_BASE64` — Base64 of the production keystore.
- `STORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Required for Huawei Premium validation:
- `HUAWEI_IAP_PUBLIC_KEY` — the public key supplied by your Huawei IAP configuration.

Optional:
- `AGCONNECT_SERVICES_JSON_BASE64` — Base64 of `app/agconnect-services.json` when your AppGallery/Huawei configuration requires it.

## Huawei AppGallery products

Create these two permanent non-consumable products with the exact IDs:
- `focused_mind_important_event` — Important Events — planned price €7.
- `focused_mind_academic_focus` — Academic Focus — planned price €10.

The app validates the product ID, package name, purchase state, purchase token, signature and configured price/currency before unlocking locally.

## Signing

Keep the production keystore outside the repository. The same production signing identity must be preserved for future updates.

## What the release workflow produces

- signed `app-release.apk`
- signed `app-release.aab`

The workflow also runs unit tests and lint before producing artifacts.

## Store submission material still required

You must provide the AppGallery Connect app registration, store listing, screenshots, icon/feature graphic where requested, privacy policy URL/text, content/age declarations, support details, and any country/payment/tax information requested by Huawei. Store approval is determined by Huawei review and cannot be guaranteed by source code alone.
