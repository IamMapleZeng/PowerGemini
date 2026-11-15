# Power Gemini 2.4.1

[English](README.en.md)

适用于国行 HyperOS 的 LSPosed 模块。按住电源键达到设定时间时向 Google 助手服务请求显示 Gemini 浮层。持续按住至 5 秒时尝试显示系统电源菜单。松手会取消尚未执行的操作。

## 安装

1. 安装并初始化 Google App 和 Gemini，在 Google 的数字助理选项中选择 Gemini。
2. 安装 Power Gemini 2.4.1 APK。发布版包名为 `io.github.iammaplezeng.powergemini`。从旧包名升级时，请先在 LSPosed 停用旧版，再启用新包名的模块。旧版的唤醒时间需要重新设置。
3. 在 LSPosed 停用原版 GeminiMi，再启用 Power Gemini。勾选系统框架、超级小爱（`com.miui.voiceassist`）、Google App（`com.google.android.googlequicksearchbox`）和 Gemini（`com.google.android.apps.bard`）。
4. 重启并解锁，然后长按电源键测试。使用 `adb logcat -d` 并搜索 `PowerGemini` 可查看模块日志。

## 设置唤醒时间

从桌面打开 Power Gemini，将滑块调至 0.3–3.0 秒，以 0.1 秒为单位，然后点「保存」。默认值是 0.5 秒。升级后先重启一次，让 LSPosed 加载新版模块，之后保存时长无需重启。

## 锁屏唤起

在 Power Gemini 的「锁屏唤起」卡片中点「打开 Google 权限设置」，允许 Google App「在锁屏上显示」。在 Gemini 的锁屏设置中开启「无需解锁即可使用 Gemini」。返回 Power Gemini 后，自检会重新检查 HyperOS 权限。

## 屏幕内容

默认开启。关闭后，长按电源键唤起 Gemini 时，系统不会为该会话采集屏幕文字和截图。Gemini 的其他入口继续使用自身设置。

## 构建

使用 JDK 17 和 Android SDK Platform 34，运行 `./gradlew :app:testDebugUnitTest :app:assembleDebug`。自行构建的 APK 使用自己的签名，覆盖安装时须使用相同的签名。

## 来源

基于 [SherlockChiang/Gemini-mi 1.1.2](https://github.com/SherlockChiang/Gemini-mi)，沿用其 Google 助手设置、语音交互服务调用、小爱偏好钩子，以及 Google App 和 Gemini 的进程内地区适配。本版在电源键按下时启动计时，保留 5 秒电源菜单。唤醒时间由设置页保存并同步到系统框架。
