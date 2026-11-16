# Signing W-Design.apk safely

No keystore, password or secret is ever committed (`.gitignore` blocks `*.jks`, `*.keystore`, `*.p12`, `keystore.properties`).

## GitHub Actions (planned release job)
Add these repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `WD_KEYSTORE_BASE64` | `base64 -w0 release.jks` |
| `WD_STORE_PASSWORD` | keystore password |
| `WD_KEY_ALIAS` | key alias |
| `WD_KEY_PASSWORD` | key password |

The release job decodes the keystore into `$RUNNER_TEMP` only. Without the secrets it must sign with an
ephemeral key generated in `$RUNNER_TEMP`, and later builds then cannot update earlier installs.

## Local signing
```bash
mkdir -p ~/.wdesign
keytool -genkeypair -v -keystore ~/.wdesign/local.jks -alias wdesign -keyalg RSA -keysize 4096 -validity 10000
export WD_KEYSTORE_PATH=~/.wdesign/local.jks WD_STORE_PASSWORD=... WD_KEY_ALIAS=wdesign WD_KEY_PASSWORD=...
```
Keep the keystore outside the repository.
