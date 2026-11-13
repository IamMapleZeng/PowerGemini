# Power Gemini

HyperOS 4 / Android 15 及以上的 LSPosed 模块。要求管理器的框架 API 为 **102 或更高**。

## 安装和使用

1. 安装 Google App 和 Gemini，打开 Gemini 完成初始化并将 Google 的数字助理切换到 Gemini。
2. 在系统设置搜索「数字助理应用」或「默认助理」，选择 **Google**。
3. 保持 HyperOS 的「长按电源键唤醒小爱」手势开启，让系统分发原有的电源键长按事件。
4. 安装 Power Gemini APK，在 LSPosed 中启用 Power Gemini，勾选 **系统框架**（`system`），然后重启手机。
5. 解锁手机并长按电源键，检查是否弹出 Gemini 浮层。

模块复用系统长按判定，仅接管已知的小爱电源键入口。它使用 Android 的语音交互服务打开 Google 助理浮层。服务接受请求后才跳过小爱。Google 端的助理选择决定浮层是 Gemini 还是 Google 助理。模块只读取系统的默认语音助理设置。禁用模块后重启即可撤销按键重定向。

如果仍弹小爱，先确认重启后 Google 仍是系统默认助理。查看 LSPosed 中 `PowerGemini` 的日志。需要排查时请提供手机型号、完整 ROM 版本号、Android 版本，以及触发后的相关日志。不同 ROM 的内部接口可能有差异。

## 从源码构建

用 Android Studio 打开此目录，或使用 JDK 17、Gradle 9.5.1 和 Android SDK Platform 37 运行 `gradle :app:assembleDebug`。Gradle 生成的 APK 位于 `app/build/outputs/apk/debug/`。自行构建的 APK 使用自己的签名，覆盖安装要求签名一致。

## 参考

- [libxposed API](https://github.com/libxposed/api)
- [HyperOS 4 电源键入口](https://github.com/fulu2778/Gemini-For-HyperOS4)
- [HyperOS 4 的电源键功能名](https://github.com/Leekin2/XGoogle)
- [Android 语音交互服务接口](https://android.googlesource.com/platform/frameworks/base.git/+/master/core/java/com/android/internal/app/IVoiceInteractionManagerService.aidl)
