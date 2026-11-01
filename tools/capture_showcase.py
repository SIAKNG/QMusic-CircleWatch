"""Capture unedited native UI PNGs from an isolated offline showcase app.

Usage: python tools/capture_showcase.py --adb PATH --serial WATCH_SERIAL
Requires :app:assembleShowcase, installed showcase APK, and an unlocked watch.
Never operates the production application or a connected phone.
"""
import argparse
from contextlib import contextmanager
import json
import subprocess
import time
from pathlib import Path

SCENES = [
    ("home", "发现首页"), ("home-mini", "收起的专辑圆标与进度环"), ("my", "我的音乐"),
    ("search", "搜索与历史"), ("search-results", "搜索结果"), ("login", "扫码登录布局"),
    ("liked", "喜欢的歌曲"), ("created", "自建歌单"), ("collected", "收藏歌单"),
    ("recent", "最近播放"), ("detail", "歌单详情"), ("downloads", "缓存与任务"),
    ("player", "播放控制"), ("lyrics", "三行歌词与逐字高亮"), ("queue", "播放队列"),
    ("settings", "设置中心"), ("settings/display", "显示与歌词设置"),
    ("settings/playback", "播放设置"), ("settings/network", "网络与缓存设置"),
    ("settings/about", "关于与版本"), ("font-selector", "选择弹层"), ("confirm", "确认弹窗"),
]
PACKAGE = "com.qmusicwatch.round.showcase"

@contextmanager
def full_redraw(adb, enabled):
    """Temporarily disable Android HWUI partial surface updates for capture."""
    prop = "debug.hwui.use_partial_updates"
    previous = adb("shell", "getprop", prop).decode().strip() if enabled else None
    try:
        if enabled:
            adb("shell", "setprop", prop, "false")
            if adb("shell", "getprop", prop).decode().strip() != "false":
                raise RuntimeError("Full redraw property was not applied")
        yield
    finally:
        if enabled:
            adb("shell", "setprop", prop, previous or "''")

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--scene", help="Capture one scene only")
    parser.add_argument("--delay", type=float, default=8.0)
    parser.add_argument("--full-redraw", action="store_true", help="Temporarily use full surface redraws; restore the original property on exit")
    args = parser.parse_args()
    def adb(*command):
        return subprocess.check_output([args.adb, "-s", args.serial, *command], timeout=35)
    model = adb("shell", "getprop", "ro.product.model").decode().strip()
    if model != "OWW251":
        raise SystemExit(f"Refusing capture: expected OWW251, got {model}")
    output = Path(__file__).resolve().parents[1] / "docs/screenshots/showcase"
    output.mkdir(parents=True, exist_ok=True)
    with full_redraw(adb, args.full_redraw):
        for scene, title in SCENES:
            if args.scene and args.scene != scene:
                continue
            adb("shell", "am", "force-stop", PACKAGE)
            adb("shell", "am", "start", "-n", f"{PACKAGE}/com.ronan.qmusicwatch.ShowcaseActivity", "--es", "scene", scene)
            time.sleep(args.delay)
            focus = adb("shell", "dumpsys", "window").decode(errors="replace")
            focused = next((line for line in focus.splitlines() if "mCurrentFocus=" in line), "")
            if PACKAGE not in focused:
                raise SystemExit(f"Capture stopped: showcase not foreground: {focused}")
            png = adb("exec-out", "screencap", "-p")
            if not png.startswith(b"\x89PNG\r\n\x1a\n"):
                raise SystemExit("Invalid screenshot data")
            if len(png) < 5000:
                raise SystemExit("Capture stopped: blank or not-yet-rendered screen; retry with --delay 10")
            path = output / (scene.replace("/", "-") + ".png")
            path.write_bytes(png)
            print(json.dumps({"scene": scene, "title": title, "file": path.name, "bytes": len(png)}, ensure_ascii=False), flush=True)

if __name__ == "__main__":
    main()
