# Fresh F1–F3 emulator APK builds — 2026-10-31

**Build preparation passed; fresh F business assertions have not yet run.** No product source changed. These APKs were built on the test machine with its retained debug signing key; they were not downloaded from CI.

Private root: `/home/ubuntu/gsm-webrtc-validation/reception-v2-r1/private/f123-fresh-20261031/`.

| Artifact under private root | SHA-256 |
| --- | --- |
| `host-main-f1.apk` | `fae4ffd5d777416a81315e1987eba8539e83cd74e7f91c1d15f9663c123b4ee5` |
| `host-main-f1-androidTest.apk` | `2b56b07f298dbadfafe34be38651851b76cc8f8484f9674505b1fb48bfae14e1` |
| `host-pr-f3.apk` | `66e15ee3f1bc40cf47a22b81e7ef502da3fba1db37c5efe32c79d68e08f03e10` |
| `host-pr-f3-androidTest.apk` | `ebd180ebb627a0ca8f96ba630109124744f9243514d4f5ba66c89e35dfe8e109` |

Both product APKs are `com.callagent.host`, versionName `0.4.2`, versionCode `1000000000`. All four packages verify with signing certificate SHA-256 `2c613d0386c319b54abd14796fee004ea44681bfe3b69ddd33f7062cd3845f28`. Both product packages contain native `x86_64` libraries, stored uncompressed; SDK 35.0.0 `zipalign -c -P 16 4` passes after signing. The test packages register `com.callagent.lab.HostJointInstrumentation` and target the production Host package.

F1 product source is exact main `5b721c3b80763264a73dbe128364529e11801c97`, independently compared against its retained source archive. F3 uses the remote `build/host-current-5b6db6b` tree: its complete `app/src/main` and `rtcmedia/src/main` contents match the current local R1 branch, including the B3/C1 changes, across 155 files. SHA-256 of the JSON-serialized sorted `(relative path, file SHA-256)` records is `574405924554bc1e0299051e87a712854d82d9334e063165cff73618fc5285ec` on both machines. `5b6db6b` adds the testing runner; main product changes are recorded separately in the validation report.

The builds use the same verified currently reachable HTTPS Kyubey origin and retained Firebase configuration. Generated product `BuildConfig.PUSH_RELAY_URL` matches that origin, and each `google-services.json` is present. Remote GET to the relay returned HTTP 200; the TLS worker and SSH reverse forward were still running. Real device registration/delivery is a separate remaining test, and is not inferred from GET/build success. Relay values and credentials remain private.

Actual build command in each verified build directory, with `ANDROID_HOME` and `ANDROID_SDK_ROOT` set to the test SDK, `GSM_RELEASE_VERSION_CODE=1000000000`, and `MAHOGSM_PUSH_RELAY_URL` supplied privately:

```bash
./gradlew --no-daemon --max-workers=1 \
  '-Dorg.gradle.jvmargs=-Xmx768m -XX:MaxMetaspaceSize=384m' \
  -Pkotlin.compiler.execution.strategy=in-process \
  -PrtcLabRunner=com.callagent.lab.HostJointInstrumentation \
  -I /home/ubuntu/gsm-webrtc-validation/reception-v2-r1/private/f123-fresh-20261031/f-host-x86.gradle \
  :app:assembleDebug :app:assembleDebugAndroidTest
```

External init script (SHA-256 `226841b92187b626370e10d57db34058e71e7f0ecf85e1ef828d78e043be3ad3`):

```groovy
allprojects {
    afterEvaluate { project ->
        def ext = project.extensions.findByName('android')
        if (ext != null && project.plugins.hasPlugin('com.android.application')) {
            ext.defaultConfig.ndk.abiFilters.clear()
            ext.defaultConfig.ndk.abiFilters.add('x86_64')
        }
    }
}
```

| Private build log | Result | Log SHA-256 |
| --- | --- | --- |
| `host-main-f1-build.private.log` | `BUILD SUCCESSFUL in 5m 5s` | `956cec8ad582f89338c32b3f33db2bcdc1f68e5b6b9597710e12782e351e488a` |
| `host-pr-f3-build.private.log` | `BUILD SUCCESSFUL in 2m 25s` | `7fd4a1572175434976e31b80d158b1869dbc3252b35ca1757d2841a2ce36522a` |

## Auxiliary H3 native packaging correction

While the Gradle build ran, a native-only packaging variant of the previously tested current Host `05d6cb74…91530` was prepared for H3. Its original arm64 native libraries matched the retained build dependencies byte-for-byte; corresponding x86_64 libraries were added without changing any DEX/resource bytes.

The first variant (`87225f02…a5d9`) incorrectly compressed the added libraries despite `extractNativeLibs=false`. H3 rejected installation with `INSTALL_FAILED_INVALID_APK`; the installed app/data remained unchanged. This is a test packaging error, not a product result. That artifact is rejected.

The corrected auxiliary artifact is `host-current-tested-05d-x86-stored.apk`, SHA-256 `30d697ebd62c8ecf7403327016cac429ae0194b037fea32085e1ab7cb1f59d45`: all four arm64/x86_64 `.so` entries use `ZIP_STORED`, `zipalign -c -P 16 4` passes, and the original signing certificate verifies. Installation of this auxiliary correction is not claimed here. The completed normal Gradle `host-pr-f3.apk` above was subsequently handed to the E2/E3 operator and is preferred for new installations.
