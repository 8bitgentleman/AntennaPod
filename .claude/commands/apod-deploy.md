Build, sign, and install the AntennaPod free release APK to the connected Android device.

Steps:
1. Activate JDK 21 via SDKMAN (`sdk env` in the repo root)
2. Run `./gradlew assembleFreeRelease` — signing credentials are picked up automatically from `~/.gradle/gradle.properties`
3. Run `adb install -r app/build/outputs/apk/free/release/*.apk`
4. Confirm success with the device name and installed package version

If the build fails, read the full error output and fix it before installing. If no device is connected (`adb devices` shows nothing), tell the user and stop.
