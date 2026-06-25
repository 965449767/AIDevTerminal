# Current Task: 文件浏览器交互重构（阶段二）

## 已完成（2026-06-25）

### Phase 1: 点击交互改造
- 点击文件 = 直接打开（文本→编辑器，图片/APK→信息，二进制→信息）
- 点击目录 = 导航 + 通知终端 cd
- 临时文件 → 快速搜索打开时自动选择

### Phase 2: 编辑器开关
- `editToggleBtn` 可空字段存储，打开文件后自动显示

### Phase 3: 手势引擎（GestureDetector + OnTouchListener）
- `onSingleTapUp` → 点选 + 打开
- `onLongPress` → 进入多选模式 / 启动拖拽
- `onScroll` → 水平滑动多选（dx > dy × 2 区分滑动）
- `requestDisallowInterceptTouchEvent` 阻止 ScrollView 劫持拖拽

### Phase 4: 底部操作栏 `fileActionBar`（42dp HorizontalScrollView）
- ✕取消 · 全选 · 反选 · 删除

### Phase 5: 滑动多选 + `rangeSelect`
- `anchorFile` 记录起始项，滑动自动区间选择

### Phase 6: 拖拽跨栏复制
- `startDragAndDrop` + `ClipData` 多文件支持
- `ACTION_DROP` → 目标栏写入后 `loadPane`
- `ACTION_DRAG_ENDED` → `exitMultiMode`
- `dragLog()` 输出到 `/storage/emulated/0/drag.log`

### 文件操作统一
- `deleteSelected()` 统一单/多选（`multiMode` 内部判断）
- `copyToOther(move)` 统一单/多选
- `pasteClipboard()` 双阶段：应用剪贴板（文件路径优先）→ 系统文本剪切板
- `newFolder()` 含 `.` 建文件，否则建目录

### 工具栏重构
- 右对齐 `HorizontalScrollView`：同步 · 复制 · 移动 · 新建 · 粘贴 · 搜索 · 更多
- E/P 模式切换图标（紫色/绿色）

### 焦点切换修复
- 点击分页空白区域 ↔ 文件行 ACTION_DOWN 行为统一：立即切换焦点 + 更新路径栏 + 更新高亮
- `updatePaneHighlight()` 紫色 2dp 边框标示活动 pane
- `refreshHighlight()` 单刷背景避免 `ACTION_CANCEL`

### 更好菜单 → 底部卡片
- `showFileMoreMenu()` / `searchFileMoreMenu()` 改用 `MenuBottomSheet`（同终端风格）
- 保留最近使用项 + 搜索功能

### 系统返回键导航 (dispatchKeyEvent → OnBackInvokedDispatcher)
- `ShellPage` 接口新增 `onBackPressed(): Boolean = false`
- ShellActivity: API 33+ 使用 `OnBackInvokedDispatcher.registerOnBackInvokedCallback`；API < 33 回退 `onBackPressed()`
- 全局 `handleBack()` 双击退出逻辑（2秒内再按 → finish）
- EmbeddedFilesPage: 深层目录 → 向上导航；到达 SD 根目录 → `false` 交给全局双击退出

### 路径栏样式
- 格式：`/root/projects\n文件夹:38  文件:19  储存:293.91G/482.05G`
- `PathBridge.androidToUbuntu()` 缩短路径显示
- 底部增加 `ui.dp(8)` 间距

### 触觉反馈补全
- `ui.pulse()` 新增 4 处：pane 焦点切换 ×2、拖拽开始、跨栏放下 + 双击退出 Toast

### 分页背景半透明
- `paneBg` lazy 属性，80% 不透明度的 `palette.surface`，让渐变/壁纸透出
- 替换 `pane()`、`updatePaneHighlight()`、拖拽还原中所有 `ui.surfaceBackground()` 引用

## 改动文件

| 文件 | 改动内容 |
|------|---------|
| `ShellActivity.kt` | `ShellPage` 接口 + `onBackPressed()`；`dispatchKeyEvent` → `OnBackInvokedDispatcher`；`handleBack()` 双击退出 |
| `EmbeddedFilesPage.kt` | 全部手势/多选/拖拽/工具栏/文件操作/返回键/路径栏/分页背景/触觉反馈 |
| `ProjectTreeView.kt` | 点击回调更新为 `openFile()` |

## 验证

- `compileDebugKotlin` ✅
- `assembleDebug` ✅

## 后续待办

### 待改进
- `deleteRecursively()` 返回值未处理（静默失败）
- `copyDir()` 无错误报告
- `runCatching` 在 `pasteClipboard()` / `copyToOther()` 中掩盖错误
- `searchActiveDir()` 主线程文件树遍历 ANR 风险
- `loadEditor()` 竞态条件
- 多处主线程文件 IO（`isLikelyText` 等）
- 树视图搜索/过滤、拖拽排序/移动

### 潜在功能
- 拖拽到目录上 → 进入该目录
- 语法高亮、行号、大文件截断提示
