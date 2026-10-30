# F1–F3 baseline artifact audit — 2026-10-30

This audit is read-only. It does not claim that F1, F2, or F3 was run.

## R1 `main` artifacts

The exact source commits required by the R1 plan are present in the local repositories and the remote source archives:

| Component | Source commit | Remote APK | APK SHA-256 | Signing certificate SHA-256 |
| --- | --- | --- | --- | --- |
| Homura | `31f65a7d5aa7fbca7df575d73a87fcf3b018b254` | `/home/ubuntu/gsm-webrtc-validation/reception-v2-r1/build/gateway-main/app/build/outputs/apk/debug/app-debug.apk` | `c0042dd263184c6e7c83d8bacb45122d60b835f3cf221fa659983aae5e865e21` | `2c613d0386c319b54abd14796fee004ea44681bfe3b69ddd33f7062cd3845f28` |
| Madoka | `5b721c3b80763264a73dbe128364529e11801c97` | `/home/ubuntu/gsm-webrtc-validation/reception-v2-r1/build/host-main/app/build/outputs/apk/debug/app-debug.apk` | `29bda6b53f22b9da7bb482be70fe11688712724bdc2ce6812ca45a2f8a263445` | `2c613d0386c319b54abd14796fee004ea44681bfe3b69ddd33f7062cd3845f28` |

The source archives are `/home/ubuntu/gsm-webrtc-validation/reception-v2-r1/reception-v2-gateway-main-31f65a7.tar.gz` (SHA-256 `6c3bbff8ce35ca82f480a71a4e6cf60c43140812263cb1a2e9eae46d7a0ce28d`) and `reception-v2-host-main-5b721c3.tar.gz` (SHA-256 `5066bc53d5d97e988722396fecebc493321bf78ce75e36445f6ed7161b8b0efe`). The APK and signer hashes above were rechecked on the test machine. DNS lookup for the old Host APK's embedded temporary relay hostname `behaviour-nomination-chen-firewall.trycloudflare.com` returned no address. Do not use that binary for a real-push F1 run; rebuild the exact `5b721c3` source with the currently working relay URL and the same debug key/version code, then record the new APK hash separately.

## Historical 0.4 artifacts

The prior joint-test directories also contain real signed APKs; these are useful provenance and signer cross-checks, but they are not the R1 plan's specified `main` starting version:

| Historical artifact | Commit | SHA-256 | Signer |
| --- | --- | --- | --- |
| `/data0/codes/gsm-0.4-joint/artifacts/builds/baseline/gateway/gateway-debug.apk` | Gateway `dee22f9` | `036142963c9a77e45d5e7160914235ba1d66c41a4c644f06428aa5207e7e5d7d` | `2c613d…845f28` |
| `/data0/codes/gsm-0.4-joint/artifacts/builds/baseline/host/host-debug.apk` | Host `e5cf86f` | `d71b60ff33e1ea588372f9410790b2209d2bd66fe841b88b8d4d2801a0e22323` | `2c613d…845f28` |
| `/data0/codes/gsm-0.4-joint/round2/artifacts/builds/baseline/host/host-debug.apk` | Host `e5cf86f` | `278074420f1ccf27ac6d60d5c1127b5ca66014dd645fcb5e3af90442d8d512fb` | `2c613d…845f28` |
| `/data0/codes/gsm-0.4-joint/round3/retry-20261023/builds/madoka-75af4b7-fcm-x86_64-debug.apk` | Host `75af4b7` | `8008b7b11b0e0570b6a5e5e9889c51503f98da2619e816fdaec0939d166bbd9c` | `2c613d…845f28` |

The associated local image archives are `server-baseline-images.tar.gz` (SHA-256 `05f9e41ec70b16423a713f04f0970655995b9edd29d60043fc0182241a24f656`), `server-0.4-images.tar.gz` (`186e7adfc825919d4d291ee1ed27148a2bcaaa5b343c9e6f6c4d4a0f2dbb4ee7`), and `round2/server-images-r2.tar.gz` (`bc93e7fb2d4fa0a6c73376a0b2231b26987ad5707271b796a9eee963e9e6fff3`). The baseline SoulGem image was imported as local Docker image ID `sha256:c1a083bd9e274fa590a8f3b6dd8ff2cd3d9ff1426af6bde4bf4e2ee7ebb17880`, with revision label `bf41e9e`. The 0.4 Docker IDs are local/imported image IDs, not verified GHCR manifest digests. R1 F1–F3 keep the current R1 SoulGem/Kyubey deployment; these older server images must not be substituted.

## Execution decision

F's earlier “missing baseline artifact” conclusion is corrected: the required source and same-signer main APKs are present. The archived Madoka main APK needs a relay-config rebuild because its embedded temporary hostname no longer resolves. F1–F3 have not been started: the current paired H1 state is v2, so creating the specified v1 start requires a separately backed-up/reconstructed pairing state; the current headless GW has no usable Extended Controls UI, and the latest `gsm call` attempt did not retain an ADB/console result. Starting a destructive rebaseline before a valid call trigger is available would leave F1 incomplete and risk the current R1 test state. See `b6-call-retry-trigger-20261030.txt` for the trigger evidence.
