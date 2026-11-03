# 截图来源与清单

采集日期：2026-11-01—2026-11-02。设备：OPPO OWW251，Android 11，原生截图 466 × 466。

这些原生截图采集于深色圆环图标更新前，保留当时的旧版演示占位图标，没有重绘以冒充新版实机画面。当前图标见 [应用图标](../branding/qmusic-circlewatch-icon.png)。

PNG 来自手表原生 ADB 截图，或从原生 `screenrecord` 录屏导出的界面帧；均保持 466 × 466 原尺寸，没有抠图、重绘或拼接。README 仅设置显示宽度。

部分页面采用录屏帧，是因为本设备直接 `screencap` 偶尔遗漏静态图层。导出由 FFmpeg 完成，不重新绘制界面，不改变布局、文字、账号或配色；采集过程不用于性能测量。排查时使用的临时调试属性已恢复。

为避免暴露真实账号、歌单、播放地址与授权二维码，截图使用独立 `com.qmusicwatch.round.showcase` 应用；它复用正式工程的页面、布局和控件，使用固定演示数据，移除联网权限，拥有独立存储，不读取正式应用账号。

示例歌名、艺人、歌单和账号为展示用途，歌词为本次原创示例；封面统一使用项目应用图标。二维码指向 example.invalid，不是授权凭证。播放位置和时长由本地静音音频提供；缓存完成/等待 Wi-Fi 是示例状态，不代表已从音乐服务下载。

源码中 showcase 宿主只存在于 `app/src/showcase`，正式 Release 不包含该入口。逐字时序为演示数据；线上歌曲只有实际提供有效 QRC 时才使用逐字着色，否则整行高亮。

## 页面清单

| 页面 | 原图 |
| --- | --- |
| 发现首页 | [home](screenshots/showcase/home.png) |
| 收起的专辑圆标与进度环 | [home-mini](screenshots/showcase/home-mini.png) |
| 我的音乐 | [my](screenshots/showcase/my.png) |
| 搜索与历史 | [search](screenshots/showcase/search.png) |
| 搜索结果 | [search-results](screenshots/showcase/search-results.png) |
| 扫码登录布局 | [login](screenshots/showcase/login.png) |
| 喜欢的歌曲 | [liked](screenshots/showcase/liked.png) |
| 自建歌单 | [created](screenshots/showcase/created.png) |
| 收藏歌单 | [collected](screenshots/showcase/collected.png) |
| 最近播放 | [recent](screenshots/showcase/recent.png) |
| 歌单详情 | [detail](screenshots/showcase/detail.png) |
| 缓存与任务 | [downloads](screenshots/showcase/downloads.png) |
| 播放控制 | [player](screenshots/showcase/player.png) |
| 逐字歌词与双语翻译 | [lyrics](screenshots/showcase/lyrics.png) |
| 播放队列 | [queue](screenshots/showcase/queue.png) |
| 设置中心 | [settings](screenshots/showcase/settings.png) |
| 显示与歌词设置 | [settings/display](screenshots/showcase/settings-display.png) |
| 播放设置 | [settings/playback](screenshots/showcase/settings-playback.png) |
| 网络与缓存设置 | [settings/network](screenshots/showcase/settings-network.png) |
| 关于与版本 | [settings/about](screenshots/showcase/settings-about.png) |
| 选择弹层 | [font-selector](screenshots/showcase/font-selector.png) |
| 确认弹窗 | [confirm](screenshots/showcase/confirm.png) |

## 宣传图与介绍页

当前 README 和本地预览页只展示第一张 `docs/posters/01-round-music.png` 宣传概览，其下直接展开上述 22 张原生实机截图。第二至第四张生成海报不再用于介绍页展示；已有素材与下载归档保留。

宣传概览为 image_gen 辅助制作的介绍性合成画面，不是实机截图，也不作为逐像素布局依据。其余界面图片均使用上述原始截图，不重绘或替换图标。

## 复现

参照 [构建说明](BUILDING.md) 构建并安装 showcase 变体，退出手表系统充电界面，运行 `tools/capture_showcase.py`。工具核对设备型号和前台应用，遇到覆盖界面会中止。展示完成后卸载截图包，正式应用数据保留。

这些图覆盖主要界面和代表性弹层，不展示每一种网络错误或所有设置滚动位置。图片证明原生排版效果，不能作为完整账号授权、缓存、蓝牙、音质权益或动画帧率验收证据。
