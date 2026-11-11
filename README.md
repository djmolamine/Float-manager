# Float Manager

Petty cash, float and customer-collections tracker for Android. Works offline; all data stays on the phone.

- `web/index.html` is the whole app (one page). Edit this file.
- `tools/build_assets.py` copies it into the APK with local fonts and libraries.
- `app/` is a small Android shell that shows the page and handles camera, file export and sharing.

## Get the APK

Every push to `main` builds a signed APK. Download `FloatManager.apk` from the latest entry under **Releases**.

## Signing key

`keystore/floatmanager.jks` is committed so each build can update the previous install. Anyone with this key can sign an
update for this app, so keep the repository private.
