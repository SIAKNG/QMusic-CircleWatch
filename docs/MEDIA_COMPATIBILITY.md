# 系统媒体控制兼容性

2026-11-04 本地修订，应用版本维持 1.0。

应用通过 MediaLibraryService / MediaLibrarySession 提供标准 Android 媒体会话和媒体浏览连接，兼容旧版 MediaBrowserService 发现入口。浏览根目录仅用于建立连接，不对外公开账号歌单；上一首、下一首路由到应用现有队列与权限检查。会话提供返回应用的启动入口，并声明音乐应用类别。

原始 OPPO 控件说明 V7.0.18（80 页）及配套 AAR 未找到音乐应用注册或 Watch X3 选择列表接入说明。其列表、表冠和振动接口已作为现有适配依据。

当前 OWW251 原厂“请选择音乐应用”页面使用固定包名名单，并非自动枚举所有已安装音乐应用。本工程的独立包名不在该列表中。因此标准媒体会话可以被系统发现，不等于原厂选择页必然显示本应用。未冒用原版 QQ 音乐包名，未修改系统程序。

当前未连接 Watch X3（OWW261），不能宣布 X3 的原厂媒体选择页已修复。需要 X3 实机确认其入口机制，或由 OPPO 提供适用的官方接入流程。

自动测试覆盖服务声明、媒体浏览器连接与根目录、标准队列控制和命令可用性事件。真实设备原始日志与厂商二进制分析资料只保存在本地项目中，不随源码上传。

参考：[Android 后台播放](https://developer.android.com/media/media3/session/background-playback)、[媒体库服务](https://developer.android.com/media/media3/session/serve-content)、[OPPO 原始圆表文档](https://open.oppomobile.com/documentation/page/info?id=12357)。
