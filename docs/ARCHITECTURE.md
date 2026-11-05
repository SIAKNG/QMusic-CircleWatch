# 工程组成

工程使用 Kotlin、Jetpack Compose、Material 3、Navigation、Coroutines/Flow、Room、DataStore 和 Media3。界面维持原工程的 ViewModel 与数据层关系，OPPO 控件 AAR 为设备能力和外观适配提供支持。

| 路径 | 职责 |
| --- | --- |
| `app/src/main/.../MainActivity.kt` | 主入口、页面路由、发现/搜索/库/播放器/设置，以及原始表冠事件转发 |
| `AppViewModel.kt` | 会话与页面状态、搜索、曲库、播放、歌单、缓存等业务协调 |
| `ui/RoundWatchLayout.kt` | 圆屏几何、安全区、列表缩放与时间显示 |
| `ui/WatchRotaryInput.kt` | 可见列表注册、滚轮分发、OPPO 线性马达与系统回退 |
| `ui/WatchNavigationMotion.kt` | 单页进入/返回过渡与绘制层按压反馈 |
| `playback/CrownScrollFilter.kt` | 表冠速度增益、短距离惯性、输入合并与反向噪声过滤 |
| `ui/WatchMiniPlayer.kt` | 胶囊/专辑圆标状态和播放进度环 |
| `ui/WatchLyricText.kt`、`lyrics/` | 逐字时间解析、换行文本绘制和柔和的进度边界 |
| `playback/` | Media3 媒体库会话、标准/应用队列命令、服务与播放控制 |
| `network/`、`data/` | 原工程服务访问、模型、缓存和本地存储 |
| `performance/FramePerformanceMonitor.kt` | 按实际窗口绘制帧采集性能信息 |
| `app/src/test/`、`app/src/androidTest/` | 解析、权限/状态、布局、滚轮过滤和界面回归检查 |
| `app/src/showcase/` | 独立离线截图宿主；复用正式页面，使用演示数据，正式包不包含此入口 |
| `docs/`、`tools/capture_showcase.py` | 使用/工程/差异文档、原始 PNG 和可重复的实机截图工具 |
| `app/libs/`、`third_party/` | 本次工程使用的本地依赖及说明 |

## 状态与绘制

UI 读取 ViewModel/Flow 提供的业务状态。播放位置用于进度和当前歌词计算；歌词渐进着色在绘制阶段按帧读取，避免每帧重组整列歌词。圆形列表的缩放放在图形层，测量尺寸保持不变，从而避免缩放改变布局、布局再次改变缩放的反馈循环。

表冠事件在 Activity 层识别原始轴及设备来源，再交给当前可见区域注册的处理器。输入过滤器负责合并与每帧限速，触觉通道异步且节流，不在滚动路径上阻塞等待马达服务。

## 账号与发布隔离

正式包 `com.qmusicwatch.round` 沿用本项目专用签名，改名不改变包名、数据目录和签名。原版包不能覆盖升级本版，本版也不会安装原作者的自动更新 APK。音乐账号校验、权限判断与上游业务仍保留。

截图包 `com.qmusicwatch.round.showcase` 拥有独立存储和 UID，清单移除联网权限；示例账号、歌单、二维码、文字及运行时静音音频不接入用户的真实账号。展示 PNG 来自设备原生截屏或原生录屏帧，不重绘界面。
