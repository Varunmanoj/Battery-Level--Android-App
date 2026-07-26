# Google Play release guide

The project produces two independently targeted Android App Bundles from the
same source code and package name:

- Mobile: `app-mobile-release.aab`, version code 140, version name 8.
- Wear OS: `app-wear-release.aab`, version code 141, version name 8.

The Wear OS variant declares `android.hardware.type.watch` as required and is
standalone, so Google Play recognizes it as a watch-only artifact. Do not upload
the mobile bundle to the Wear OS track or the Wear bundle to the mobile track.

## Configure local signing

1. Verify that the SHA-256 certificate fingerprint in your upload keystore
   matches Play Console > Setup > App integrity > Upload key certificate.
2. Copy `key.properties.example` to `key.properties`.
3. Fill in the absolute keystore path, upload-key alias, and passwords locally.
4. Never commit `key.properties`, a keystore, or a PEPK file.

`private_key.pepk` is only used when Google Play explicitly asks for an
encrypted app-signing-key export. It is not used to sign routine updates.

## Build and verify

Run:

```shell
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew \
  test lint bundlePlayRelease --console=plain
```

The Play release task stops immediately if `key.properties` is absent or
incomplete, so an unsigned bundle cannot be mistaken for an upload artifact.

The signed bundles are written to:

```text
app/build/outputs/bundle/mobileRelease/app-mobile-release.aab
app/build/outputs/bundle/wearRelease/app-wear-release.aab
```

Verify both signatures before uploading:

```shell
jarsigner -verify -verbose -certs \
  app/build/outputs/bundle/mobileRelease/app-mobile-release.aab
jarsigner -verify -verbose -certs \
  app/build/outputs/bundle/wearRelease/app-wear-release.aab
```

## Upload to Play Console

1. Upload the mobile bundle to the mobile internal-testing track.
2. In Advanced settings > Form factors, add and opt in to Wear OS.
3. Add Wear OS screenshots and complete the Wear OS declarations.
4. Upload the Wear bundle to the dedicated Wear OS testing track.
5. Test Play-delivered installations on a phone and a watch.
6. Promote the mobile and Wear releases to their respective production tracks.
