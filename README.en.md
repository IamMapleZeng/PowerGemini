# Power Gemini 2.4.1

[简体中文](README.md)

Power Gemini is an LSPosed module for the Chinese version of Xiaomi HyperOS. Hold the power button for the configured duration to open the Gemini overlay. Holding it for 5 seconds attempts to open the system power menu. Releasing the button cancels any pending action.

## Installation

1. Install and set up the Google app and Gemini. Select Gemini in Google's digital assistant settings.
2. In your phone's system settings, open **Default apps** → **Assist & Voice input** and set the default assistant to **Google**.
3. Install the Power Gemini 2.4.1 APK. The release package name is `io.github.iammaplezeng.powergemini`.
4. Enable Power Gemini in LSPosed. Select System Framework, XiaoAI (`com.miui.voiceassist`), the Google app (`com.google.android.googlequicksearchbox`), and Gemini (`com.google.android.apps.bard`) as scopes.
5. Restart and unlock your phone, then hold the power button to test. To troubleshoot, run `adb logcat -d` and search for `PowerGemini`.

## Set the hold duration

Open Power Gemini from the launcher. Use the slider to choose a duration from 0.3 to 3.0 seconds in 0.1-second steps, then tap **保存 (Save)**. The default is 0.5 seconds. Restart once after upgrading so LSPosed can load the new module. Later changes to the hold duration take effect without restarting.

## Launch from the lock screen

In Power Gemini, tap **打开 Google 权限设置 (Open Google permission settings)** in the lock-screen section. Allow **Show on Lock screen** for the Google app in HyperOS, and turn on **Use Gemini without unlocking** in Gemini settings. Power Gemini checks the HyperOS permission again when you return.

## Screen context

Enabled by default. When disabled, a power-button Gemini session does not request Android to capture screen text or a screenshot. Other Gemini entry points keep their own settings.

## Build from source

With JDK 17 and Android SDK Platform 34, run `./gradlew :app:testDebugUnitTest :app:assembleDebug`. To install an APK over an existing copy, both APKs must use the same signing certificate.

## Credits

Based on [SherlockChiang/Gemini-mi 1.1.2](https://github.com/SherlockChiang/Gemini-mi). Power Gemini uses its Google Assistant configuration, voice interaction service calls, XiaoAI preference hooks, and in-process region handling for the Google and Gemini apps. Power Gemini starts timing as soon as the power button is pressed, keeps the 5-second power menu, and passes the saved hold duration from the settings screen to the system framework.
