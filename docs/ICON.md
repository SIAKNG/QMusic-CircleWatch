# 深色圆环应用图标

2026-11-03：应用、工程介绍和四页介绍图统一采用用户选定的深色圆环方案。绿色与青色音符、圆环和右侧表冠构成本项目标识，用于区分原版；本项目为第三方应用。

## 资源与复现

- `branding/source/approved-dark-ring.png`：获选图标原稿。
- `branding/source/adaptive-mark.png`：image_gen 提取的透明前景。
- `branding/qmusic-circlewatch-icon.png`：1080 × 1080 展示图；旧名称文件保留兼容。
- `.svg` 展示文件为内嵌 PNG 的自包含容器，并非可编辑矢量稿。
- `drawable-nodpi/ic_launcher.png`：192 × 192 应用内占位资源，避免列表反复解码大图。
- `drawable-nodpi/qmusic_circlewatch_icon.png`：512 × 512 传统启动器资源。
- `drawable-nodpi/qmusic_circlewatch_mark.png`：512 × 512 自适应透明前景。

使用 JDK 21 在工程目录运行：

```powershell
java tools/GenerateLauncherAssets.java .
```

导出工具仅生成资源分辨率与展示容器，不裁剪或重绘原稿。

## 圆形裁切

API 26 及以上采用独立暗色背景和前景；前景四边使用 **20.37037% 比例 inset**。比例随启动器尺寸缩放，可避免固定 dp 留边在 AdaptiveIconDrawable 扩大图层后裁掉右侧表冠。较低 API 使用保留完整构图的传统图标。

`LauncherIconSafetyTest` 在 Android API 30 原生图形模式验证前景安全区、传统图标圆形裁切，并按启动器实际扩展后的前景边界，在 48、96、432 像素尺寸比较裁切前后图形。三个测试通过，圆形裁切丢失的主要前景像素为 0。

2026-11-04 已在 OWW251 实机覆盖安装正式 APK，核对安装包哈希与本地一致。系统启动器实际显示了深色圆环图标，外环与右侧表冠完整，点击图标正常进入本应用；应用内占位图标也已更新。其他厂商启动器仍以各设备显示为准；既有 22 张原生展示截图保留采集时的旧图标。
