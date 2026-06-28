# 当前任务：Android 开发链全量审计 + 修复

## 说明
对 Android 构建/运行/测试/部署全链路进行逐层审计，发现并修复所有问题，确保 `aidev-*` 命令套件在 Ubuntu PRoot 环境中可靠运行。

## 全部完成（2026-06-28）

### P0 — 构建断裂
| 问题 | 修复 |
|------|------|
| NDK r28 缺失 `lib/` 目录 | 降级到 r27，shell 测试验证路径有效 |
| `apkanalyzer` 路径错误 | 添加 android-sdk-lib → system/bin 软链接 |
| Rust 工具链缺失 (cargo-apk) | 安装 rustc + cargo + cargo-apk |

### P1 — 运行/调试
| 问题 | 修复 |
|------|------|
| `aidev-opencode` 未复制到 PRoot | `ensure_ubuntu_helpers()` 添加 |
| `aidev-build` TUI 修改未保存时状态不回写 | 添加状态机：IDLE→RUNNING→parse→complete |
| `aidev-error-why` colordiff 缺失 | 安装 colordiff |
| TUI 进程 daemon 残留 | scope.cancel() → children.forEach cancel |
| WakeLock 并发错误 | WakeLock 操作放 ViewLifecycleOwner.lifecycleScope |

### P2 — 构建加速/IDE 体验
| 问题 | 修复 |
|------|------|
| NDK 未配置到项目链接 | 工具检查警告 |
| Gradle 远程构建 cache | 代理优先的 cache 配置 |

### P3 — 部署
| 问题 | 修复 |
|------|------|
| `properties` 批量写缺失 `apply()` | 加 commit() |
| `PreferencesManager` 崩溃 | null → `==` 值安全、文件路径验证 |

### P4 — Shell 脚本测试框架
- 设计 `aidev-test-shell` 框架：`shunit2` + 测试分拣 + 错误诊断
- 覆盖 6 个脚本 47 个测试，全部通过

### 验证
```
./gradlew :app:testShellScripts --no-daemon       # 47/47 PASS
./gradlew :app:compileDebugKotlin --no-daemon       # BUILD SUCCESSFUL
./gradlew :app:testDebugUnitTest --no-daemon        # 78/78 PASS
./gradlew :app:assembleDebug --no-daemon             # BUILD SUCCESSFUL
```

## 用户验证待办
- 安装 APK 后测试：`aidev-build`、`aidev-error-why`、`aidev-opencode`、`aidev-logcat`、`aidev-apk-info`
- 确认 WakeLock/TUI 残留/WakeLock 并发修复在生产环境有效
