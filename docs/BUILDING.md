# 构建与开发

## 环境

- JDK 21；工程编译目标 JVM 17。
- Android SDK 36、Gradle Wrapper 8.13；最低 Android 7.0 / API 24。
- 正式适配验证设备：OPPO OWW251，Android 11 / API 30，466 × 466。

克隆本仓库后，为 Android SDK 配置 `ANDROID_HOME` 或本地 `local.properties`。不要提交本机绝对路径、凭据或签名配置。

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

调试 APK 用于源码开发，不可用调试签名覆盖已安装的正式签名版本。安装到个人设备时先确认目标包名与数据保留需求。

## 正式签名

`release-signing.properties.example` 是无口令模板。维护者本地配置 `release-signing.properties`，或使用构建脚本中 `QMUSICWATCH_RELEASE_*` 环境变量。发布任务校验预期证书，缺少密钥或证书不一致会失败。

```powershell
.\gradlew.bat :app:assembleRelease
```

签名私钥、口令及设备账号数据不在仓库或源码压缩包中。重新生成密钥不能覆盖安装已有正式包。维护者的 F 盘工作区可用 `项目管理/build.ps1 -Mode verify` 运行本地中文路径兼容验证和正式包导出；这属于外层工作区工具，并非其他机器的必需目录结构。

## 1.1 双版本构建

同一工程可生成两个包名，界面与音乐业务一致：

```powershell
# 独立共存版：com.qmusicwatch.round
.\gradlew.bat :app:assembleRelease
# OPPO 媒体控制兼容版：com.tencent.qqmusicwatch
.\gradlew.bat :app:assembleRelease -PoppoMediaCompat=true
```

每次输出位于 `app/build/outputs/apk/release/app-release.apk`，构建下一种之前需分别保存。分发名称为 `QMusic-CircleWatch-1.1-Standalone.apk` 和 `QMusic-CircleWatch-1.1-MediaControl.apk`。两版均校验本项目证书，禁用上游原版 APK 更新。

兼容版不能与官方 QQ 音乐共存；独立版不进入当前 OWW251 原厂媒体名单。切换包名不迁移账号数据，覆盖更新必须匹配已安装包名和签名。详见 [1.1 安装与更新说明](RELEASE_1_1.md)。

## 离线展示与截图

```powershell
.\gradlew.bat :app:assembleShowcase
adb -s WATCH_SERIAL install -r app/build/outputs/apk/showcase/app-showcase.apk
python tools/capture_showcase.py --adb PATH_TO_ADB --serial WATCH_SERIAL --video-frames
adb -s WATCH_SERIAL uninstall com.qmusicwatch.round.showcase
```

本工具限定 OWW251 并核对前台为截图包；设备需退出充电覆盖界面。录屏帧导出需要 FFmpeg，可用 `--ffmpeg` 指定路径。截图复用真实生产页面，以明确的演示资料覆盖私人内容，不会清除或替换正式应用数据。源码入口仅位于 showcase source set。更多见 [截图说明](SCREENSHOTS.md)。

## 依赖与来源

保留 [LICENSE](../LICENSE)、[NOTICE](../NOTICE.md)、[第三方说明](../THIRD_PARTY.md) 和 [原版 README](../README.upstream.md)。OPPO 本地 AAR 由用户提供，用于复现构建；其权利与使用条件归原发行方，不随本工程重新许可。

音乐与登录仍依赖原工程网络服务，不内置音乐资源、个人登录 Cookie 或会员授权。变更发布名称不等于替换服务端。
