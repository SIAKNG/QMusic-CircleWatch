# QMusic-CircleWatch 1.3

发布日期：2026-11-25

**从 1.2 起，后续安装包默认支持 OPPO 原厂媒体控制。此版无法与手表官方 QQ 音乐共存，安装前需卸载官方版。** 本项目旧版本可直接覆盖更新，包名和签名保持一致。卸载官方版会清除其本地登录和下载数据。

### 优化

- 优化了会员信息显示的效果，方便用户直接确认自己的会员信息及到期时间。
- 优化了歌曲播放和网络缓冲时的等待时间，调整了加载状态下的动画效果，让用户明确看出歌曲正在加载。网络较差时临时使用较低音质播放；手动切歌或自动续播后，重新按默认音质加载。
- 优化了部分情况下安装包无法正常安装的问题及安装包的整体兼容性。

### 修复

- 修复了部分情况下登录二维码首次加载失败的问题，短暂网络异常时自动重试，并兼容较大的二维码图片响应。
- 修复了豪华绿钻与超级会员识别混淆、部分有效期缺失及旧会员缓存长期不刷新的问题。

## 安装与验证说明

- APK：`QMusic-CircleWatch-1.3-支持媒体控制版.apk`，版本代码 5，包名 `com.tencent.qqmusicwatch`。
- 如果系统只提示「未安装应用」，请参考 [安装排查](INSTALL_TROUBLESHOOTING.md) 获取具体错误码；签名兼容调整不能保证解决所有安装失败原因。
- 本版调整通过自动回归和发布构建校验。本次已在 OWW251 覆盖安装并核对音质菜单、禁用发送入口及播放加载状态；不将受控网络测试等同所有真实弱网情况。
- 不包含签名私钥、真实账号资料、设备原始日志、腾讯官方 APK 或邮件服务部署文件。

## 下载与工程

[下载支持媒体控制版 APK](https://github.com/SIAKNG/QMusic-CircleWatch/releases/download/v1.3/QMusic-CircleWatch-1.3.apk) · [完整离线包（内含中文名称安装包）](https://github.com/SIAKNG/QMusic-CircleWatch/releases/download/v1.3/QMusic-CircleWatch-1.3-Package.zip) · [完整工程源码](https://github.com/SIAKNG/QMusic-CircleWatch/releases/download/v1.3/QMusic-CircleWatch-1.3-Source.zip)

已通过 309 项自动测试、Lint（0 错误、69 项警告）、Release 构建及 v1/v2/v3 签名校验。
