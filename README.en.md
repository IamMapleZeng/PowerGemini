# Power Gemini 2.1

[简体中文](README.md)

Power Gemini is an LSPosed module for the Chinese version of Xiaomi HyperOS. Hold the power button for the configured duration to open the Gemini overlay. Holding it for 5 seconds attempts to open the system power menu. Releasing the button cancels any pending action.

## Installation

1. Install and set up the Google app and Gemini. Select Gemini in Google's digital assistant settings.
2. Install the Power Gemini 2.1 APK. Its package name is `io.github.iammaplezeng.powergemini`. If upgrading from an earlier package name, disable the old module in LSPosed and set your preferred hold duration again in the new app.
3. Disable the original GeminiMi module and enable Power Gemini in LSPosed. Select System Framework, XiaoAI (`com.miui.voiceassist`), the Google app (`com.google.android.googlequicksearchbox`), and Gemini (`com.google.android.apps.bard`) as scopes.
4. Restart and unlock your phone, then hold the power button to test. To troubleshoot, run `adb logcat -d` and search for `PowerGemini`.

## Set the hold duration

Open Power Gemini from the launcher. Use the slider to choose a duration from 0.3 to 3.0 seconds in 0.1-second steps, then tap **保存 (Save)**. The default is 0.5 seconds. Restart once after upgrading so LSPosed can load the new module. Later changes to the hold duration take effect without restarting.

## Build from source

With JDK 17 and Android SDK Platform 34, run `./gradlew :app:testDebugUnitTest :app:assembleDebug`. To install an APK over an existing copy, both APKs must use the same signing certificate.

## Credits

Based on [SherlockChiang/Gemini-mi 1.1.2](https://github.com/SherlockChiang/Gemini-mi). Power Gemini uses its Google Assistant configuration, voice interaction service calls, XiaoAI preference hooks, and in-process region handling for the Google and Gemini apps. Power Gemini starts timing as soon as the power button is pressed, keeps the 5-second power menu, and passes the saved hold duration from the settings screen to the system framework.
