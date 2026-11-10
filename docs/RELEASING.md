# Releasing a new version

F-Droid builds the app from source and ships the **developer-signed** APK of the GitHub
release after checking that its own build is bit-identical (reproducible build, `Binaries:`
and `AllowedAPKSigningKeys:` in `docs/fdroid-metadata.yml`). So every release needs a
signed APK on GitHub.

1. Raise `versionCode` and `versionName` in `app/build.gradle.kts` and add the changelog
   `fastlane/metadata/android/{en-US,de-DE}/changelogs/<versionCode>.txt`.
2. Commit, then tag and push: `git tag -a vX.Y -m "Akku Health Alarm X.Y" && git push origin main vX.Y`
3. Clean release build (JDK 21), without a `keystore.properties` so the APK stays unsigned:
   `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew clean assembleRelease`
4. Sign the unsigned APK **directly**, with `--alignment-preserved` and **without** `zipalign`
   (otherwise F-Droid cannot copy the signature onto its own build):

   ```bash
   PATH=/usr/lib/jvm/java-21-openjdk/bin:$PATH ~/Android/Sdk/build-tools/36.0.0/apksigner sign --alignment-preserved --ks ~/connectfour-release.jks --ks-key-alias cfs --out AkkuHealthAlarm-X.Y.apk app/build/outputs/apk/release/app-release-unsigned.apk
   ```

   Signing certificate SHA-256:
   `0290aabc0607b5ba98d1fbf67e8f03b166d04f2d6545634f99030278c60546a1`
5. GitHub release `vX.Y` with `AkkuHealthAlarm-X.Y.apk` and `SHA256SUMS`
   (the file name must match the `Binaries:` URL pattern):
   `sha256sum AkkuHealthAlarm-X.Y.apk > SHA256SUMS && gh release create vX.Y AkkuHealthAlarm-X.Y.apk SHA256SUMS`
6. F-Droid picks up new tags automatically (`AutoUpdateMode: Version`, `UpdateCheckMode: Tags`).
   While the inclusion merge request is still open, update `versionName`, `versionCode`,
   `commit` (full hash), `CurrentVersion` and `CurrentVersionCode` there instead.

Reproducibility check: clone the tag into another folder, build again and compare
`sha256sum app/build/outputs/apk/release/app-release-unsigned.apk` – both must be equal.
