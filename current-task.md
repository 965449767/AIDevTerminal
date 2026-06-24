# Current Task: Phase A（稳定性 + 安全）+ UI/UX 修复 — Complete

## Summary
完成 Phase A 全部 6 个步骤 + 5 项 UI/UX 修复，构建和单元测试均通过。

## Phase A 完成情况

| Step | 内容 | 状态 |
|------|------|------|
| A1 | 修复进程流泄漏（SystemMonitorPage, NetworkDiagnosticsPage, SecurityAuditPage） | ✅ |
| A1-fix | 修复 NetworkDiagnostics 闪退（ACCESS_NETWORK_STATE）+ bootstrap 自动输入修复 | ✅ |
| A2 | 修复 CoroutineScope 泄漏（EmbeddedSettingsPage, EmbeddedFilesPage） | ✅ |
| A3 | SystemMonitorPage 主线程 IO 移到 Dispatchers.IO | ✅ |
| A4 | AndroidManifest 安全加固（allowBackup=false, usesCleartextTraffic=false） | ✅ |
| A5 | 命令输入校验（NetworkDiagnosticsPage 正则 + SecurityAuditPage 白名单） | ✅ |

## UI/UX 修复

| 内容 | 状态 |
|------|------|
| ⌨ 按钮背景色统一（0xFF0D1117，与 CompletionEngine 一致） | ✅ |
| ⌨ 按钮固定右边（FrameLayout + Gravity.END，TUI 模式不跳） | ✅ |
| 键盘状态同步（WindowInsetsCompat 替换 OnGlobalLayoutListener） | ✅ |
| onNewIntent 页面保留（不强制切到终端页） | ✅ |
| Shizuku 移到权限管理分类 | ✅ |

## 验证
- 编译：BUILD SUCCESSFUL
- 单元测试：通过
- 实机验证：待用户确认 ⌨ 按钮 TUI 模式下位置

## 下一步
- Android 集成测试（补充 androidTest/ 目录）
- Phase B: 代码质量（SharedPreferences 集中化、死代码清理）
