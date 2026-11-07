<p align="center"><img src="branding/qmusic-circlewatch-icon.png" width="112" alt="QMusic-CircleWatch 应用图标"></p>
<h1 align="center">QMusic-CircleWatch</h1>
<p align="center">QQ 音乐安卓手表版第三方应用，适用于圆形表盘。</p>
<p align="center"><b>圆屏适配 · 逐字歌词 · 双语翻译 · 表冠与触觉反馈</b></p>
<p align="center">
  <a href="https://github.com/SIAKNG/QMusic-CircleWatch/releases/tag/untagged-753d7a6a32293452e7a0">1.1 私有审阅</a> ·
  <a href="#应用介绍图">应用介绍图</a> · <a href="#界面预览">界面预览</a> · <a href="#二创主要更新">二创更新</a> ·
  <a href="#第一次使用">使用方法</a> · <a href="#工程组成与实现">工程组成</a> ·
  <a href="CHANGELOG.md">版本记录</a>
</p>

QMusic-CircleWatch 是一款 **QQ 音乐安卓手表版第三方应用**，面向采用 **圆形表盘** 的 Android 手表设计。你可以在手表上搜索歌曲、访问音乐库、控制播放，并查看逐字歌词与双语翻译。

本项目基于 [huanghao897/QMusicWatch](https://github.com/huanghao897/QMusicWatch) **0.9.14** 二次开发，由 **SIAKNG** 维护。结合 OPPO 圆表资料完善圆屏布局、实体表冠、原生短震和滚动动画，保留原有音乐业务与账号权益判断。

当前工程版本 **1.1**，提供 **支持媒体控制版**和**普通版（可共存）**。最低 Android 7.0；主要实机验证设备为 **OPPO OWW251，Android 11，466 × 466**。方屏保留既有布局分支，其他设备尚未逐台实测。

| 安装版本 | OPPO 原厂媒体控制 | 与官方 QQ 音乐手表版共存 | 安装包 |
| --- | --- | --- | --- |
| 支持媒体控制版 | OWW251 已实测显示和播放操作 | 不支持 | `QMusic-CircleWatch-1.1-支持媒体控制版.apk` |
| 普通版（可共存） | 当前 OWW251 名单不识别；保留标准媒体会话 | 支持 | `QMusic-CircleWatch-1.1.apk` |

**当前仓库已设为私有，正在审阅 1.1。** 私有 main、源码 ZIP、两个 APK 和更新说明均同步为本次版本，确认后再公开。安装包见 [1.1 私有草稿](https://github.com/SIAKNG/QMusic-CircleWatch/releases/tag/untagged-753d7a6a32293452e7a0)，版本选择及升级方式见 [1.1 说明](docs/RELEASE_1_1.md)。

> **安装提示：** 支持媒体控制版使用与手表官方 QQ 音乐相同的应用包名，占用相同的安装位置，因此无法与官方 QQ 音乐共存。用户必须先卸载手表上的官方 QQ 音乐，才能安装此版本；卸载会删除官方应用的本地登录与下载数据。普通版可以与官方 QQ 音乐共存。

本项目不隶属于腾讯、QQ 音乐或 OPPO；音乐与授权沿用原工程服务，不内置曲库或会员授权。两版均使用本项目名称、图标与签名，不接收上游原客户端 APK 更新。

> 首张为应用宣传设计图，下方直接展示原生实机截图。截图使用离线演示数据，采集于图标更新前；歌名、歌单、账号、歌词与二维码均为示例，二维码不能登录。来源见 [截图说明](docs/SCREENSHOTS.md)。

2026-11-03 图标修订：采用深色圆环应用图标，适配圆形启动器并保留右侧表冠；同步精简介绍图排版。应用版本仍为 **1.0**。图标构建与裁切验证见 [图标说明](docs/ICON.md)。

## 版本更新

### 1.1 · 2026-11-07（私有审阅）

- 重点优化整体 APP 的使用流畅度与跟手性，改善页面打开、点击反馈、列表滑动等日常操作的响应，减少不必要的等待和界面刷新。
- 优化表冠滚动表现，调整快慢旋转的速度反馈、短惯性与首次起转响应，让滚动阻尼和松手后的停顿过渡更自然流畅。
- 优化页面打开、切换与逐层返回的动效，以及胶囊播放悬浮窗的展开和收起效果，让专辑图与容器的过渡更加连贯自然。
- 优化网络请求与数据复用，缩短登录后用户资料、歌曲列表及歌单的加载等待，让内容更快显示，重复进入页面时更快响应。
- 新增“支持媒体控制版”，在当前已验证的 OPPO 手表原厂媒体控制中显示歌曲、歌手、封面与应用图标，并支持播放、暂停及上下首操作。
- 支持专辑封面取色，播放控件、进度环及歌词高亮随当前歌曲的封面自动变换配色，让播放界面保持统一。

- 命名列表保留顶部标题与返回入口，播放队列搜索栏随滚动收起；右滑按页面层级返回。
- 完善逐字歌词和双语翻译的自动加载与显示，长歌词完整换行，播放控件与歌词随专辑封面配色。
- 修复播放进度、播放/暂停图标及歌词跟随的状态同步；操作菜单采用带叉号和“取消”文字的圆形悬浮按钮。
- 完善完整歌单缓存、读取重试、账号隔离和旧请求结果处理，保留现有音乐功能与账号权益校验。

<p align="center"><img src="docs/updates/qmusic-circlewatch-1.1.png" width="640" alt="1.1 更新展示：跟手体验、表冠滚动、动效、数据加载、媒体控制与专辑封面取色"></p>

[1.1 安装包与工程草稿](https://github.com/SIAKNG/QMusic-CircleWatch/releases/tag/untagged-753d7a6a32293452e7a0) · [安装与更新说明](docs/RELEASE_1_1.md) · [完整版本记录](CHANGELOG.md)

## 功能一览

| 功能 | 可以做什么 |
| --- | --- |
| 发现与推荐 | 查看每日推荐，进入曲目与搜索；搜索歌单后可查看详情 |
| 搜索 | 输入关键词，查看历史和结果列表，选择歌曲播放 |
| 音乐库 | 查看喜欢、自建及收藏歌单、最近播放，保留原有账号操作 |
| 扫码登录 | 显示授权二维码与有效期，根据页面提示在手机端授权 |
| 播放控制 | 播放/暂停、上一首/下一首、进度、喜欢、模式与队列 |
| 迷你播放器 | 滚动收起为专辑圆标与真实进度环，点击展开 |
| 歌词与翻译 | 逐字歌词、双语翻译与当前行强调，无逐字时序时整行高亮 |
| 队列与模式 | 查看并管理播放队列；顺序、循环和随机播放 |
| 音质 | 按曲目和账号权益显示标准、HQ、SQ 的可用性 |
| 缓存管理 | 显示任务与本地曲目，保留仅 Wi-Fi 缓存等设置 |
| 表冠与触觉 | 列表滚动、可选音量控制、OPPO 短震与系统回退 |
| 个性设置 | 界面字号、主题、歌词、睡眠定时、耳机提醒与播放行为 |

详细操作见 [功能说明](docs/FEATURES.md)。Hi-Res 未宣称可播放；版权、会员权益、网络和上游接口仍决定实际可用范围。

## 二创主要更新

原版 **已有部分圆屏布局、表冠和逐字歌词**。本版在其基础上完善 OPPO 圆表体验，不将原有音乐业务列作二创新增。

| 重点 | 本版最终效果 |
| --- | --- |
| 圆表布局 | 比例留边、稳定的边缘缩放、胶囊选项、圆形确认按钮与统一触控区 |
| 播放入口 | 胶囊与专辑圆标切换，实际进度环与点击展开 |
| 歌词 | 逐字歌词与双语翻译、稳定强调、跨行顺序上色、柔和进度前沿、同灰度非当前行 |
| 时间区 | 轻薄全宽透明渐变与阴影，软化重叠；不依赖实时背景模糊 |
| 表冠 | 识别 OWW251 鼠标式滚轮，合并限速并过滤反向噪声 |
| 原生短震 | OPPO 线性马达效果 302，按距离和时间节流并遵守系统开关 |
| 流畅度 | 稳定列表测量、歌词绘制阶段推进、按实际窗口帧采样 |
| 安装选择 | 提供支持媒体控制版与普通版（可共存），统一本项目签名，隔离原客户端更新 |
| 应用标识 | 深色圆环音乐图标，按圆形启动器安全区保留外环与右侧表冠 |

逐项对照见 [与原版相比改了什么](docs/UPSTREAM_DIFF.md)。

## 应用介绍图

<p align="center"><img src="docs/posters/01-round-music.png" width="520" alt="QMusic-CircleWatch 圆屏音乐介绍图"></p>

首图为应用宣传概览，实际界面效果见下方原生截图。

## 界面预览

以下 22 张为本工程于 2026-11-01—2026-11-02 在 OWW251 上采集的原生实机界面，使用离线演示数据，保留当时旧版占位图标。长页面展示首屏，完整选项可在应用内滚动查看；点击图片可查看原始 PNG。

### 首页与播放入口

| 发现首页 | 收起的专辑圆标与进度环 | 我的音乐 |
| :---: | :---: | :---: |
| <img src="docs/screenshots/showcase/home.png" width="240" alt="发现首页"> | <img src="docs/screenshots/showcase/home-mini.png" width="240" alt="收起的专辑圆标与进度环"> | <img src="docs/screenshots/showcase/my.png" width="240" alt="我的音乐"> |

### 搜索与登录

| 搜索与历史 | 搜索结果 | 扫码登录布局 |
| :---: | :---: | :---: |
| <img src="docs/screenshots/showcase/search.png" width="240" alt="搜索与历史"> | <img src="docs/screenshots/showcase/search-results.png" width="240" alt="搜索结果"> | <img src="docs/screenshots/showcase/login.png" width="240" alt="扫码登录布局"> |

### 音乐库与歌单

| 喜欢的歌曲 | 自建歌单 | 收藏歌单 |
| :---: | :---: | :---: |
| <img src="docs/screenshots/showcase/liked.png" width="240" alt="喜欢的歌曲"> | <img src="docs/screenshots/showcase/created.png" width="240" alt="自建歌单"> | <img src="docs/screenshots/showcase/collected.png" width="240" alt="收藏歌单"> |

### 最近、详情与缓存

| 最近播放 | 歌单详情 | 缓存与任务 |
| :---: | :---: | :---: |
| <img src="docs/screenshots/showcase/recent.png" width="240" alt="最近播放"> | <img src="docs/screenshots/showcase/detail.png" width="240" alt="歌单详情"> | <img src="docs/screenshots/showcase/downloads.png" width="240" alt="缓存与任务"> |

### 播放器与歌词

| 播放控制 | 逐字歌词与双语翻译 | 播放队列 |
| :---: | :---: | :---: |
| <img src="docs/screenshots/showcase/player.png" width="240" alt="播放控制"> | <img src="docs/screenshots/showcase/lyrics.png" width="240" alt="逐字歌词与双语翻译"> | <img src="docs/screenshots/showcase/queue.png" width="240" alt="播放队列"> |

### 设置与显示

| 设置中心 | 显示与歌词设置 | 播放设置 |
| :---: | :---: | :---: |
| <img src="docs/screenshots/showcase/settings.png" width="240" alt="设置中心"> | <img src="docs/screenshots/showcase/settings-display.png" width="240" alt="显示与歌词设置"> | <img src="docs/screenshots/showcase/settings-playback.png" width="240" alt="播放设置"> |

### 网络、关于与选择

| 网络与缓存设置 | 关于与版本 | 选择弹层 |
| :---: | :---: | :---: |
| <img src="docs/screenshots/showcase/settings-network.png" width="240" alt="网络与缓存设置"> | <img src="docs/screenshots/showcase/settings-about.png" width="240" alt="关于与版本"> | <img src="docs/screenshots/showcase/font-selector.png" width="240" alt="选择弹层"> |

### 确认弹窗

| 确认弹窗 |
| :---: |
| <img src="docs/screenshots/showcase/confirm.png" width="240" alt="确认弹窗"> |

完整清单与复现方式见 [截图说明](docs/SCREENSHOTS.md)，也可打开本地 [界面浏览页](docs/preview/index.html)。静态图片不能体现动画帧率与振动强度。

## 第一次使用

1. 按 [1.1 说明](docs/RELEASE_1_1.md) 选择支持媒体控制版或普通版（可共存）；仓库与安装包目前均在私有状态供你审阅，确认后再公开。
2. 已安装本二创版本时，仅使用相同包名和签名的版本覆盖更新；仅普通版（可共存）可与官方 QQ 音乐同时安装；支持媒体控制版需先处理官方版的同包名冲突。
3. 连接可正常上网的 Wi-Fi 或热点，在登录页按提示扫码；图库中的示例二维码不能使用。
4. 从发现、搜索或音乐库进入歌曲，点击底部播放胶囊打开播放器，切换到歌词页。
5. 列表可用触屏或表冠滚动。系统触觉关闭时不强制振动；播放页表冠音量可在设置关闭。

搜索或播放失败时，先检查网络、域名解析和登录状态。播放地址、音质和账号操作仍受上游平台限制。本版不安装原版自动更新 APK，后续更新以本仓库 Release 为准。

## 工程组成与实现

使用 **Kotlin + Jetpack Compose + Coroutines/Flow + Room/DataStore + Media3**；结合用户提供的 OPPO 控件和圆表资料适配设备能力。

界面层负责圆屏几何、胶囊控件、迷你播放器与歌词绘制；ViewModel 协调状态，网络和存储沿用原工程；表冠事件按可见区域分发，过滤和限速后触发节流短震。

- [工程结构与数据流](docs/ARCHITECTURE.md)
- [构建、签名和离线展示](docs/BUILDING.md)
- [详细功能说明](docs/FEATURES.md)
- [二创与原版对照](docs/UPSTREAM_DIFF.md)
- [应用图标与圆形裁切验证](docs/ICON.md)

当前私有 main 与草稿附件的源码 ZIP 同步为本次 1.1 工程；仓库继续保持私有，待用户确认后再开放。历史 v1.0 保留。

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

需要 JDK 21、Android SDK 36。正式包还需维护者本地签名；仓库不包含私钥、口令、个人账号与原始设备日志。

## 验证与服务边界

2026-11-07：默认包 **268 项回归通过**，兼容包名下的 **9 项媒体服务与状态检查通过**，Lint 零错误、62 警告。OWW251 兼容版已重新扫码、读取真实资料及歌单并播放；原厂媒体控制已实测歌曲、封面、新图标、暂停/恢复和上下首。X3 尚未实测。网络优化和标准控制记录见 [1.1 说明](docs/RELEASE_1_1.md) 与 [验证范围](docs/VERIFICATION.md)。

以下保留此前各次验证记录，当前兼容范围以本段及对应日期文档为准。


2026-11-03 图标修订通过 **224 项自动测试**，Lint 0 错误、56 警告；Android 原生圆形启动器在多种尺寸下均完整保留外环与表冠。2026-11-04 已在 OWW251 覆盖安装核对新图标、圆形裁切、点击启动及主要页面；本轮沿用此前音乐业务验收，未重复全量联网测试，详见 [验证说明](docs/VERIFICATION.md)。

2026-11-04 本地交互与歌词修订通过 **248 项自动测试**、Lint 零错误，并已保留数据装表；补充修复媒体播放状态同步，覆盖进度、按钮和歌词联动。X3 原厂音乐选择页仍待设备验证，已知 OWW251 的固定支持名单不能由标准媒体声明直接加入，见 [媒体兼容说明](docs/MEDIA_COMPATIBILITY.md)。此次本地修订尚未替换 GitHub Release 附件。

本轮补充检查见 [最新图标实机记录](docs/DEVICE_CHECK_20261104.md)。

实机已验证搜索、在线播放、暂停、歌词、迷你播放器切换、时间区与列表。用户已确认实体表冠正常滚动、有短震，快速或反向操作不再抽动。演示数据不能代替线上业务验收。

收藏与歌单写入、真实离线缓存、蓝牙及全部会员音质组合尚未逐项完成实机验收，不能据此声称全部线上功能已经验证。既有业务与权限判断保留。

## 来源与许可证

- 原工程：[huanghao897/QMusicWatch](https://github.com/huanghao897/QMusicWatch)，导入基线 0.9.14。
- 圆表参考：[OPPO 官方适配文档](https://open.oppomobile.com/documentation/page/info?id=12357)，及用户提供的设计资料和控件。
- 保留 [Apache-2.0](LICENSE)、[NOTICE 与署名](NOTICE.md)、[第三方说明](THIRD_PARTY.md) 和 [原版 README](README.upstream.md)。
- OPPO 本地依赖用于复现本次适配；其权利与使用条件归原发行方，不随本工程重新许可。

本 README 的 1.x 为二次开发版本号，与原工程 0.9.x 的序列分别维护。
