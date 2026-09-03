# GitHub release setup

Create these repository Actions secrets before manually running the `release` job:

- `KEYSTORE_BASE64`: base64 of your permanent production `.jks` file.
- `STORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Example local command to produce the secret value:

`base64 -w 0 focusedmind-release.jks`

Do not commit the keystore, passwords, `release.properties`, or Huawei account credentials.
