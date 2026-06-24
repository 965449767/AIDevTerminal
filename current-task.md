# Current Task: 无

所有已计划的任务均已完成。

## 完成情况

### Phase A（稳定性 + 安全）— 提交: 2ec8fb0

| Step | 内容 | 状态 |
|------|------|------|
| A1 | 修复进程流泄漏（SystemMonitorPage, NetworkDiagnosticsPage, SecurityAuditPage） | ✅ |
| A1-fix | 修复 NetworkDiagnostics 闪退（ACCESS_NETWORK_STATE）+ bootstrap 自动输入修复 | ✅ |
| A2 | 修复 CoroutineScope 泄漏（EmbeddedSettingsPage, EmbeddedFilesPage） | ✅ |
| A3 | SystemMonitorPage 主线程 IO 移到 Dispatchers.IO | ✅ |
| A4 | AndroidManifest 安全加固（allowBackup=false, usesCleartextTraffic=false） | ✅ |
| A5 | 命令输入校验（NetworkDiagnosticsPage 正则 + SecurityAuditPage 白名单） | ✅ |

### Phase B（代码质量）— 提交: 810e666

| Step | 内容 | 状态 |
|------|------|------|
| B1 | Constants.kt + PreferencesManager.kt（PrefKeys, sharedPreferences, StringSet 属性） | ✅ |
| B2 | 字符串常量替换（AIAgentActivity, ShellEnhancementsPage, SyncCoordinator） | ✅ |
| B3 | 小文件迁移（EmbeddedTasksPage, ContainerManagerPage, CompletionEngine） | ✅ |
| B4 | 中等文件迁移（VirtualKeyEditor, ServerCenterActivity） | ✅ |
| B5a | ShellActivity prefs 类型改为 PreferencesManager | ✅ |
| B5b | EmbeddedFilesPage SP 访问迁移 | ✅ |
| B5c | EmbeddedShellPages SP 访问迁移 | ✅ |
| B6 | 清理未使用 import（17 个，跨 5 文件） | ✅ |

### UI/UX 修复（陪跑）

| 内容 | 状态 |
|------|------|
| ⌨ 按钮背景色统一 | ✅ |
| ⌨ 按钮固定右边 | ✅ |
| 键盘状态同步 | ✅ |
| onNewIntent 页面保留 | ✅ |
| Shizuku 移到权限管理分类 | ✅ |

### 菜单去冗余 + 命令优化

| 内容 | 状态 |
|------|------|
| 终端"更多"菜单移除 3 个字号条目 | ✅ |
| 删除 AIAgentActivity | ✅ |
| 移除终端"退出"按钮 | ✅ |
| 移除文件页"旧版文件管理" | ✅ |
| 命令面板 handleCommandPalette 索引对齐（30→18 项，补洞 + 去重） | ✅ |
| 设置页：开发环境拆为 Android 诊断 + Ubuntu 环境 | ✅ |
| 设置页：权限管理 + 系统与后台 合并为 权限与后台 | ✅ |
| 设置页：容器管理 → Ubuntu 管理，监听端口改用 ss/netstat 绕过失效脚本 | ✅ |
| 设置页：顶部说明文字 → 分隔线 | ✅ |
| 保活 KeepAliveService 自动执行（删掉按钮 + 命令面板项 + if 守卫 + bootReceiver if + pref + const） | ✅ |
| ServerCenterActivity：去"常驻"按钮 + "后台常驻"状态行 + "后台说明"行 | ✅ |
| 命令面板删除 9 项重复（切Tab 4 + 后台常驻 1 + 任务模板 5 + Git状态 + 清屏） | ✅ |
| 一键修复简化：去除 Android 权限检查 + 可选工具检查 + 多选子对话框，直接执行 | ✅ |
| deploy-dev-env / install-aitool 注册到 aidev-ubuntu-core + .aidevrc 确保跨环境可用 | ✅ |
| 命令改名：install-aitool → opencode-install, deploy-dev-env → setup-dev-env（7 文件 12 处引用） | ✅ |
| Command suggestions 补全 26 个自定义命令（TerminalUtils.kt builtinCompletions） | ✅ |
| 知识库新增"内置命令"分类（26 项，含 aidev-agent-* 内部命令） | ✅ |
| 知识库修正：ping → curl, 删除 traceroute/last/systemctl, pkg → apt, chown/find-SUID 加 PRoot 说明 | ✅ |
