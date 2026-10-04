# Embedded PJSUA2 SDK provenance

The Android host now includes a debug-capable PJSUA2 engine built from the
official upstream source. The native AAR is pinned in the app tree; rebuild it
with `tools/build-pjsua2-android.sh`, which verifies the source pin and its
security-fix ancestors before running the upstream AAR builder. The source
checkout, NDK, JDK, CMake and build output stay outside the application tree.

## Pinned artifact

| Item | Value |
|---|---|
| Upstream repository | <https://github.com/pjsip/pjproject> |
| Source commit | `a67b8e81b0024b993f47e463c01c67c25cda116f` |
| AAR | `app/libs/pjsua2-2.17-dev.aar` |
| AAR SHA-256 | `b638fff712ed6efcd366ca7d6b33de31b60bef205291947ee1407d4f27c38150` |
| Native library | `jni/arm64-v8a/libpjsua2.so` |
| Native library SHA-256 | `2042533ec5cfa1bc1f504ba73d5b69095fb70c6c47a2823bccbd262142d5af3e` |
| ABI / native minimum API | `arm64-v8a` / Android API 26 |
| App version | versionCode 4 / versionName 0.3.0 |

The pinned source commit contains all four upstream fixes checked by
`tools/check-pjsip-pin.sh`: DNS response validation, TLS SubjectAltName NUL
handling, TLS CN/SAN matching, and IP SubjectAltName matching. The check
verifies each fix commit is an ancestor of the pinned commit, fetches only
from the official repository, and leaves TLS verification enabled.

## Build record

Build host: Linux x86_64. Toolchain versions used for the checked-in artifact:

- Android NDK `28.2.13676358` (r28c), Clang `19.0.1`.
- Android SDK CMake `3.30.5`.
- Temurin OpenJDK `21.0.12.1` and SWIG `4.3.1`.
- Upstream `build/android/build-aar.sh`, `ANDROID_API=26`, `ABIS=arm64-v8a`,
  `CMAKE_BUILD_TYPE=Release`, and static C++ runtime.
- The upstream script fetched OpenSSL `3.5.8`, Opus `1.6.1`, and Oboe `1.9.0`
  only after matching their pinned SHA-256 checksums. The dependency pins are
  in that exact pinned upstream `build/android/build-aar.sh` source.
- PJSUA2 SWIG Java bindings were generated and compiled into `classes.jar`.
  The packaged `org/pjsip/pjsua2/Endpoint.class` was verified.

The upstream configure enables OpenSSL TLS, SRTP, Opus, Speex resampling,
Oboe audio and Android MediaCodec, and disables codecs/components excluded by
the upstream distribution's licensing checks. OpenSSL, Opus, Oboe, libsrtp,
the C++ runtime and other linked components are static. `DT_NEEDED` contains
only Android platform libraries. Every `PT_LOAD` in the checked-in ELF has
`0x4000` alignment (16 KB); the upstream build's artifact verifier checks
this before it creates the AAR.

The final debug APK also passes `zipalign -c -P 16 4`, verifying native-library
ZIP alignment for 16 KB pages. This complements the ELF check; it does not
replace a runtime test on a 16 KB device.

The build does not set a permissive TLS mode. Runtime configuration in
`Pjsua2SipEngine` uses one TLS signaling transport with `verifyServer=true`,
builds a PEM trust bundle from Android's `AndroidCAStore` **system** roots,
and appends the optional authenticated custom CA. It requires `serverName` to
match the configured outbound TLS proxy host. Thus the SIPS call-intent URI
can retain its logical `gsm2sip` host while PJSIP validates the actual proxy
certificate name. The engine offers no UDP/TCP signaling transport. It
requires SRTP and permits SDES keying only; signaling must be SIPS. Incoming
INVITEs expose raw SIP `Call-ID`, request URI and the exact trimmed
`X-GSM2SIP-Call-ID` header as separate fields.

The engine selects Opus, PCMU, PCMA and G.722 from the built codec set. Opus
bitrate and packet-loss/FEC settings are sampled from PJSUA2 RTCP stats and
adjusted only after an Opus stream is actually negotiated. This path is
implemented against PJSUA2's `getStreamStat`, `getStreamInfo`, and
`audStreamModifyCodecParam` APIs; interoperability and tuning still need
device/server validation. Backend rollout can continue to limit negotiation
to G.711/G.722 until the server has separately approved Opus.

## License closure

PJSIP is distributed under GPL-2.0-or-later and is statically included in the
native library. The AAR also contains OpenSSL, Opus, Oboe, libsrtp, WebRTC AEC
components, Speex, GSM and iLBC, with additional component notices. The full
texts copied from the AAR are packaged under
`app/src/main/assets/licenses/pjsua2/` so they remain available in the APK.
The APK containing this SDK must not be presented as an entirely MIT-licensed
artifact. Distribution must meet the GPL and third-party notice obligations,
or use a suitable commercial PJSIP license; see
<https://www.pjsip.org/licensing.htm> and the bundled texts.

## Release verification still required

This build proves that pinned JNI and Java wrapper artifacts build and that
the ELF has 16 KB load alignment. It does **not** complete release acceptance.
No real handset interoperability run is recorded yet. Before release, verify
certificate chain and hostname failures, expired/untrusted certificates,
mandatory SDES-SRTP downgrade rejection, registration and re-registration,
inbound correlation and late INVITE cancellation, two-way audio, Telecom audio
routes, lock/unlock, process recovery, permission/foreground-service timing,
and cellular-call interruptions on supported devices. Until those results
are recorded, keep this as a debug probe and leave the release gate incomplete.
