# Android 开发经验日志

## 不要用 FileObserver —— 用协程轮询

**场景：** 监控目录中新出现的文件，处理跨进程请求（如 Ubuntu PRoot → Android 通知）。

**教训：** `FileObserver` 依赖 Linux `inotify`，而 Android 新设备多用 F2FS 文件系统，`inotify` 在 F2FS 上行为不一致，事件可能根本不会触发。调试极其困难 —— 没有错误，没有日志，只有静默失败。

**替代方案：**

```kotlin
CoroutineScope(Dispatchers.IO).launch {
    while (isActive) {
        dir.listFiles()?.filter { it.name.startsWith("req-") }?.forEach { file ->
            val text = runCatching { file.readText() }.getOrNull()
            file.delete()
            if (text != null) process(text)
        }
        delay(500)
    }
}
```

- 每 500ms 轮询一次，不依赖内核
- 代码量和 FileObserver 差不多
- 100% 可靠
- 没有线程安全问题（协程按序执行）

## 异步初始化的坑：Handler.post 还没执行就开始使用

**场景：** `homeDir` 在 `ensureSession()` 的 `Handler(Looper.getMainLooper()).post { ... }` 中赋值，但调用方在同一帧直接读取。

**教训：** `Handler.post` 的回调不会在当前帧执行。后续代码如果依赖其赋值结果，必须加重试机制。

**模式：**

```kotlin
fun initXxx(ctx: Context) {
    val dep = dependency ?: run {
        postDelayed(3000) { initXxx(ctx) }
        return
    }
    // 使用 dep
}
```

## 两点总结

1. **Android 上永远别用 FileObserver**，用轮询。
2. **异步赋值的字段，永远加 null→重试兜底**，不要假设调用时已经赋值。
