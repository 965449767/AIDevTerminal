---
description: 增强版 Android 日志查看（标签过滤/崩溃监控）
---

你可以使用 `aidev-logcat` 命令查看 Android 系统日志。

选项:
- `aidev-logcat` — 查看最近日志（默认 200 行）
- `aidev-logcat --tags Error,Exception,Crash` — 过滤包含关键词的行
- `aidev-logcat --watch-crash` — 持续监控，检测到崩溃（FATAL EXCEPTION/ANR/Native crash）自动停止
- `aidev-logcat --lines 500` — 自定义行数
- `aidev-logcat --follow` — 持续追踪新日志（Ctrl+C 停止）
- `aidev-logcat --level W` — 按级别过滤（V/D/I/W/E/F）
- `aidev-logcat --clear` — 清空日志缓冲区

当用户需要查看日志或调试崩溃时使用此命令。
