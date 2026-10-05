# Android SIP SDK probe status

The Android host now packages a locally built PJSUA2 JNI library and its
generated Java wrapper. `docs/native-sdk-provenance.md` records the exact
source pin, AAR/native hashes, build inputs, security setup, license closure,
and the remaining release checks.

`SipEngineFactory.isAvailable(context)` attempts to load the JNI library
without creating a SIP endpoint or opening audio. The host does not expose a
separate probe screen. A standalone instrumentation smoke can verify endpoint
startup and codec enumeration against an installed host APK. The PJSUA2 adapter
is the app's sole SIP engine; it has no fallback to Android's phone dialer or
another SIP stack.

## Source and security pin

The official upstream `pjproject` commit is
`a67b8e81b0024b993f47e463c01c67c25cda116f`. It contains the recorded upstream
fixes for DNS response validation and TLS certificate-name validation. Run
`tools/check-pjsip-pin.sh` to verify the exact pin and that each fix is an
ancestor of that pinned commit.

The adapter requires a TLS proxy and rejects a configuration where
`serverName` does not identify that proxy. TLS certificate and hostname
validation stay enabled. It exports Android's system CA certificates as PEM
for OpenSSL and appends the optional custom CA returned by the authenticated
server configuration. SIPS request URIs may retain the logical `gsm2sip`
host because the configured public proxy is the TLS next hop. The SIP engine
creates no UDP or TCP signaling transport.

Media requires SRTP with SDES keying and secure SIPS signaling. A plain RTP or
unsupported secure-media negotiation cannot fall back. Startup and REGISTER
use PJSUA2's null sound device. Incoming calls receive only provisional 180
Ringing until the user answers; microphone capture starts only after the
call service's microphone foreground service is ready. Outgoing audio stays
gated until the host explicitly authorizes it after that service is ready.

Incoming snapshots expose the raw dialog `Call-ID`, request URI and
`X-GSM2SIP-Call-ID` independently. The call coordinator joins that server
identifier against the authenticated GET `/calls` record before it creates a
Telecom incoming call. It does not use caller number matching.

## Release gate

Native arm64-v8a and Java wrapper builds passed, and the packaged library's
ELF load segments are aligned to 16 KB. The adapter includes the real TLS,
SRTP and codec configuration. The real-device/server interoperability gate
is **still incomplete**: no handset run has yet validated native endpoint startup,
TLS rejection cases, SDES-SRTP negotiation, the full inbound/outbound flow,
Telecom audio routes, process recovery, and cellular interruptions. The
standalone instrumentation smoke passed on the API 35 Google APIs foldable
emulator in [Actions run 37248094071](https://github.com/KiriKira/gsm2sip-client-android/actions/runs/37248094071)
(source commit `bd870b8fceadf61069995e92986851664673c0e3`). It installed the
arm64 host APK and a harness signed with the same certificate, loaded PJSUA2,
created/started/destroyed an endpoint with null audio, and enumerated Opus,
PCMU, PCMA and G.722. It did not capture microphone audio or place a SIP call.
The latest run passed 19 native checks and 22 unpaired UI checks. Its signed,
synthetic offline paired fixture passed another 27 checks across nine screenshot
stages: dashboard/cached hosts, folded/unfolded hosts, SIM selection, inbound and
queued SMS previews, compose fields, background settings, and unavailable call
controls. It verified airplane/Wi-Fi/mobile-data state and no default network,
used only `.invalid` endpoints, and removed the synthetic cache/test harness and
restored network settings. No real pairing, SMS or SIP/cellular call was made.
The paired dashboard exposed a Material Chip multiline crash during earlier
runs; the fix has a seeded Activity regression covering single selection and
recreation. Startup foreground and off-screen button assertions now read fresh
state and scroll the actual control into view, retaining their original gates.

The generic foldable UI smoke also checked input and IME restoration after
rotation, observed CLOSED/OPENED device states, and checked visible buttons
against a simulated hole cutout. This is preliminary emulator evidence,
not acceptance on Z Fold8 or a cellular handset.

To reproduce, build the debug APK, install it on a compatible Android device,
and run `bash scripts/run-native-smoke.sh`. Set `PJSUA_SMOKE_KEYSTORE` to the
actual debug Store reported by `./gradlew :app:signingReport` when it differs
from the default Android directory. The harness verifies certificate equality
before installation. The `Android KVM UI smoke` workflow performs these steps
and uploads `results.json`, instrumentation output, screenshots, hierarchy XML
and logcat under its artifact. Magisk hardware validation is separate.

The PJSIP AAR is GPL-2.0-or-later with a static third-party dependency
closure. Full notices are packaged under
`app/src/main/assets/licenses/pjsua2/`; the resulting APK must not be described
as entirely MIT-licensed. See `docs/native-sdk-provenance.md` before any
distribution.
