# D1 alternative route backup preflight — 2026-10-31

No pairing, owner, SIM mapping, or reception state was changed in this preflight.

## Same-AVD full-app backups

Both files were exported through the app's password-protected **Full backup** UI, then selected through that app's restore flow. The app decrypted and staged each archive, validated its database, and displayed the confirmation screen. I cancelled at that screen, so no restore was committed.

| Device | AVD | Manifest result | Private archive SHA-256 |
| --- | --- | --- | --- |
| Homura | API 34, `emulator-5554` | 0.4.2-dev; 1 pairing | `8a877d4f412098412f1e63d1d408bbaf58b6191760760d905468ff968cf88705` |
| Madoka H2 | API 35 Google Play, `127.0.0.1:15559` | 0.4.2; 1 pairing | `1c48702f0ecee549448955d9bf622d28907ee40ed3dbe31468c750e7e8dab4e7` |

The encrypted archives and password file remain in `/home/ubuntu/gsm-webrtc-validation/reception-v2-r1/private/d1-isolated-20261031/`, mode 0600 under the mode-0700 parent. They are not in the repository.

## APK rollback and target checks

The currently installed Homura APK is SHA-256 `8cb9898986a80658852fed7ee622800640fedf99a9fb8a918338b66b44a959cf`, matching the retained R1 target APK; its signer is the shared debug certificate `2c613d…845f28`. No Homura package update is needed.

The currently installed H2 Madoka APK was saved privately (SHA-256 `05d6cb74d8bd5e2f30edf6927679182218fbc17e8dcf8cf40fcb584ce0d91530`). The target FCM-enabled PR APK has SHA-256 `dbc2ce502b0a0024fc423f3b5480195b89a37e0ca1d8001c0ac47d6b1c9f4205`; both verify with the same shared debug signer and version code `1000000000`. It can therefore be installed with `adb install -r`, preserving H2 app data. Its reverse path is `adb install -r` of the saved H2 APK followed by restoring the validated H2 full-app backup on the same AVD.

The confirmed stop condition remains: do not consume a pairing code or change local mesh/reception settings unless both staged backups and this same-signer APK rollback path remain available.
