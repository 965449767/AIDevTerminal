# AnExplorer 可复用模式参考

> 源项目：[1hakr/AnExplorer](https://github.com/1hakr/AnExplorer) (Apache-2.0)
> 语言: Java 96.4%, 包名: `dev.dworks.apps.anexplorer`

---

## 目录

1. [MultiChoiceHelper 多选模式](#1-multichoicehelper-多选模式)
2. [图标系统](#2-图标系统)
3. [MimeTypes 扩展名→MIME 映射](#3-mimetypes-扩展名mime-映射)
4. [NoteActivity 文本编辑器模式](#4-noteactivity-文本编辑器模式)
5. [UI 布局模式](#5-ui-布局模式)
6. [文件操作工具](#6-文件操作工具)
7. [AdvTerminal 中可用的参考实现](#7-advterminal-中可用的参考实现)

---

## 1. MultiChoiceHelper 多选模式

### 概述

一个自包含的 RecyclerView 多选辅助类，模拟 `ListView.CHOICE_MODE_MULTIPLE_MODAL` 行为。

### 核心机制

```
长按 → 进入 Action Mode → 单击切换选中 → ActionBar 显示批量操作
```

### 关键类

| 类 | 作用 |
|---|---|
| `MultiChoiceHelper` | 多选状态管理（SparseBooleanArray）、ActionMode 协调 |
| `MultiChoiceHelper.ViewHolder` | 基础 ViewHolder，绑定 onClick/longClick 多选逻辑 |
| `MultiChoiceModeWrapper` | 代理 MultiChoiceModeListener，委托给外部回调 |

### 核心接口

```java
public interface MultiChoiceModeListener extends ActionMode.Callback {
    void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked);
}
```

### 状态管理

- `SparseBooleanArray checkStates` — 按 position 追踪选中状态
- `LongSparseArray<Integer> checkedIdStates` — 按 stable ID 追踪（当 adapter 有 stableIds 时）
- `checkedItemCount` — 选中计数
- `confirmCheckedPositions()` — 数据变更后按 ID 重新定位

### 状态保存/恢复

通过 `SavedState` (Parcelable) 保存 `checkStates`、`checkedIdStates`、`checkedItemCount`。

### ViewHolder 行为

```java
onClick:
  if 多选模式活跃 → toggleItemChecked(position)
  else → 转发到普通 click listener

onLongClick:
  if 多选模式未活跃 → setItemChecked(position, true) + startSupportActionMode()
  else → return false
```

### 与 AdvTerminal 的适配方案

当前 split pane 使用 `LinearLayout` + `TextView`，非 RecyclerView。建议：

- **长按进入选中模式**：与现有 `setOnLongClickListener` 共存（当前已用于弹出操作菜单）
- **改用长按→ checkbox 出现**：在 row() 中添加 checkbox 指示器，再出现批量操作栏
- **批量操作栏**：替换当前的单文件 toolbar，改为上下文相关（选中 >0 时显示批量操作按钮）

---

## 2. 图标系统

### 向量图标资源

22 个文件类型图标 + 5 个实用图标，全部为白色填充（`#FFFFFFFF`），使用 `ImageView.setColorFilter()` 着色。

### 文件→图标映射表

| 文件/类型 | 图标资源 | 背景色 |
|---|---|---|
| APK | `ic_doc_apk.xml` | `#8BC34A` (浅绿) |
| 音频 | `ic_doc_audio.xml` | `#FF9800` (橙) |
| 视频 | `ic_doc_video.xml` | `#C74141` (红) |
| 图片 | `ic_doc_image.xml` | `#00838F` (青) |
| PDF | `ic_doc_pdf.xml` | `#FFDB4437` (红) |
| Word/文档(蓝色) | `ic_doc_word.xml` | `#4883F3` (蓝) |
| Excel/电子表格(绿色) | `ic_doc_excel.xml` | `#16A765` (绿) |
| PowerPoint(橙) | `ic_doc_powerpoint.xml` | `#FF7537` (橙) |
| 演示文稿 | `ic_doc_presentation.xml` | `#F4B400` (黄) |
| 压缩包 | `ic_doc_archive.xml` | `#795548` (棕) |
| 代码 | `ic_doc_codes.xml` | `#607D8B` (蓝灰) |
| 文本 | `ic_doc_text.xml` | `#808080` (灰) |
| 证书 | `ic_doc_certificate.xml` | `#757575` (灰) |
| 联系人 | `ic_doc_contact.xml` | `#03A9F4` (亮蓝) |
| 事件/日历 | `ic_doc_event.xml` | `#1E88E5` (蓝) |
| 字体 | `ic_doc_font.xml` | `#455A64` (深灰) |
| 相册 | `ic_doc_album.xml` | `#00838F` (青) |
| 通用文件 | `ic_doc_generic.xml` | `#DDD` (浅灰) |
| 文件夹 | `ic_doc_folder.xml` | — |
| 文档(默认) | `ic_doc_document.xml` | `#DDD` (浅灰) |
| 电子表格(十字) | `ic_doc_spreadsheet.xml` | `#16A765` (绿) |

### IconUtils 加载策略

```java
loadMimeIcon(Context, mimeType):
  1. 精确匹配 MIME → 返回对应 drawable
  2. 部分匹配 (audio/*, image/*, video/*, text/*) → 返回对应 drawable
  3. `application/zip` → `ic_doc_archive`
  4. 其他 → `ic_doc_generic`

loadMimeIcon(Context, mimeType, authority, docId, mode):
  // 处理文件夹图标（相册 vs 普通文件夹）
  if 是目录:
    if 是媒体相册 → ic_doc_album or ic_grid_folder
    else → ic_doc_folder or ic_grid_folder
  else:
    → 委托到上面的 loadMimeIcon(Context, mimeType)
```

### 图标着色

```java
// 给 drawable 应用 ColorStateList 着色
DrawableCompat.setTint(drawable.mutate(), color);

// IconUtils 的工具方法
applyTintList(Context, drawableId, tintColorId)  // ColorStateList
applyTint(Context, drawableId, tintColorId)       // 纯色
applyTintAttr(Context, drawableId, tintAttrId)    // 从 Theme attribute 解析
```

---

## 3. MimeTypes 扩展名→MIME 映射

### 补充系统 MimeTypeMap

`android.webkit.MimeTypeMap` 不覆盖的扩展名在此补充：

```kotlin
object MimeTypes {
    private val EXTRA = mapOf(
        // 文本变体
        "asm" to "text/x-asm", "def" to "text/plain", "in" to "text/plain",
        "rc" to "text/plain", "list" to "text/plain", "log" to "text/plain",
        "pl" to "text/plain", "prop" to "text/plain", "properties" to "text/plain",
        "ksh" to "text/plain",
        // 电子书
        "epub" to "application/epub+zip",
        // 邮件
        "eml" to "message/rfc822", "msg" to "application/vnd.ms-outlook",
        // 压缩包
        "ace" to "application/x-ace-compressed", "bz" to "application/x-bzip",
        "bz2" to "application/x-bzip2", "cab" to "application/vnd.ms-cab-compressed",
        "gz" to "application/x-gzip", "jar" to "application/java-archive",
        "xz" to "application/x-xz",
        // 脚本/可执行
        "bat" to "application/x-msdownload", "sh" to "application/x-sh",
        // 字体
        "otf" to "application/x-font-otf", "ttf" to "application/x-font-ttf",
        // 图片
        "dwg" to "image/vnd.dwg", "dxf" to "image/vnd.dxf",
        "pct" to "image/x-pict",
        // 音频
        "au" to "audio/basic", "snd" to "audio/basic",
        "aac" to "audio/x-aac", "mka" to "audio/x-matroska",
        // 视频
        "flv" to "video/x-flv", "mkv" to "video/x-matroska",
    )

    fun fromExtension(ext: String): String? {
        val lower = ext.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(lower)
            ?: EXTRA[lower]
    }
}
```

### MimePredicate 分组

```kotlin
// AnExplorer 的分组方式
TEXT_MIMES     = 匹配 "text/*", "application/javascript", ...
VISUAL_MIMES   = 匹配 "image/*", "video/*"
MEDIA_MIMES    = 匹配 "audio/*"
COMPRESSED_MIMES = 匹配 "application/zip", "application/x-gzip", ...
SHARE_SKIP_MIMES  = 匹配 "image/*" (分享时跳过)
```

---

## 4. NoteActivity 文本编辑器模式

### 架构

```
onCreate: 设置 EditText + TextWatcher → onStart: 通过 URI 异步加载
          → TextWatcher 检测修改 → Toolbar save/revert 按钮
          → 返回时 checkUnsavedChanges 弹对话框
```

### 关键模式

#### 异步加载

```kotlin
// 用 Coroutine 替代 AsyncTask
lifecycleScope.launch(Dispatchers.IO) {
    val text = withContext(Dispatchers.IO) { file.readText() }
    withContext(Dispatchers.Main) {
        original = text
        editorContent.setText(text)
        editorContent.setSelection(0)
    }
}
```

#### 未保存检测 (TextWatcher + Timer)

```kotlin
class EditorWatcher : TextWatcher {
    private var timer: Timer? = null
    var isDirty = false

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        timer?.cancel()
        timer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    isDirty = editorContent.text.toString() != original
                    // 更新 UI（通知 activity 刷新保存按钮状态）
                }
            }, 250) // 250ms 防抖
        }
    }
}
```

#### 返回/切文件前确认

```kotlin
private fun checkUnsavedChanges(block: () -> Unit) {
    if (isDirty) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("未保存的修改")
            .setMessage("文件已修改，是否保存？")
            .setPositiveButton("保存") { _, _ -> saveEditor { block() } }
            .setNegativeButton("不保存") { _, _ -> block() }
            .setNeutralButton("取消", null)
            .show()
    } else {
        block()
    }
}
```

#### 加载/保存状态

```kotlin
private fun setEditorLoading(loading: Boolean) {
    editorContent.isEnabled = !loading
    editorProgress.visibility = if (loading) View.VISIBLE else View.GONE
}
```

#### 错误提示 (Snackbar 替代 Toast)

```kotlin
Snackbar.make(rootView, "无法读取文件", Snackbar.LENGTH_SHORT)
    .setAction("确定") { /* dismiss */ }
    .show()
```

### 完整流程

```
用户点击文件
  → loadEditor(file)
    → 设置 loading
    → launch(IO) { file.readText() }
    → 完成后: original = text, editorContent.setText(text), 取消 loading

用户编辑
  → TextWatcher.onTextChanged()
    → 250ms 防抖后: isDirty = (current != original)
    → 更新保存按钮可见性

用户点击"保存"
  → if isDirty:
    → 设置 saving
    → launch(IO) { file.writeText(editorContent.text) }
    → 完成后: original = current, isDirty = false, 取消 saving

用户点击另一个文件/返回/切换模式
  → checkUnsavedChanges()
    → if isDirty: 弹对话框（保存/不保存/取消）
    → else: 直接切换
```

---

## 5. UI 布局模式

### item_doc_list.xml 结构

```
FrameLayout (selectableItemBackground foreground)
  └── LinearLayout (horizontal)
      ├── FrameLayout (icon container, 40dp)
      │   ├── CircleImage (MIME 背景色圆形)
      │   ├── ImageView (icon_mime, 居中)
      │   └── ImageView (icon_thumb, 缩略图)
      └── LinearLayout (vertical, weight=1)
          ├── LinearLayout (horizontal)
          │   ├── TextView (id=title, weight=1, filename)
          │   └── ImageView (额外标识)
          └── LinearLayout (horizontal)
              ├── TextView (date, 90dp)
              ├── TextView (size, 90dp)
              └── TextView (summary, weight=1)
```

### 关键 UI 类

| 类 | 作用 |
|---|---|
| `DocumentsAdapter` | RecyclerView Adapter，支持 list/grid/loading/error/header 多 view type |
| `ListDocumentHolder` | 列表模式 ViewHolder (72dp 高) |
| `GridDocumentHolder` | 网格模式 ViewHolder (176dp 高) |
| `BaseHolder` | 基础 ViewHolder，集成 MultiChoiceHelper |
| `RecyclerViewPlus` | 自定义 RecyclerView，支持 type (list/grid/custom) 和 columnWidth |
| `CircleImage` | 圆形背景 ImageView |
| `DirectoryView` | 自定义 ViewGroup，目录容器 |

### 布局文件速查

| 文件 | 作用 |
|---|---|
| `activity.xml` | 主 Activity：Toolbar + DrawerLayout + 主内容 |
| `activity_note.xml` | 编辑器：CoordinatorLayout + ScrollView + EditText + ProgressBar |
| `fragment_directory.xml` | 目录列表：DirectoryView (progress + RecyclerViewPlus + 空状态) |
| `fragment_roots.xml` | 存储根列表（导航抽屉） |
| `fragment_detail.xml` | 文件信息侧边栏 (300dp 右抽屉) |
| `fragment_move.xml` | 移动/复制底部栏 |

---

## 6. 文件操作工具

### FileUtils 有用方法

```kotlin
object FileUtils {
    // 生成唯一文件名（避免覆盖）
    fun buildUniqueFile(targetDir: File, mimeType: String, displayName: String): File

    // 文件名净化（移除非 FAT32 字符）
    fun buildValidFatFilename(name: String): String
    fun buildValidExtFilename(name: String): String

    // 文件大小格式化
    fun formatFileSize(size: Long): String  // "12.5 KB", "3.2 MB"

    // 获取文件类型名称
    fun getTypeForFile(file: File): String

    // ZIP 压缩
    fun compressFile(file: File, targetDir: File): Boolean
    fun uncompressFile(zipFile: File, targetDir: File): Boolean
}
```

### 操作模式

```java
// copy/move: 选择 → 进入 MoveFragment（选择目标路径）→ 确认执行
// delete: 选择 → 确认对话框 → OperationTask (AsyncTask)
// rename: 选择 → RenameFragment (Dialog) → 确认执行
// create: CreateDirectoryFragment / CreateFileFragment (Dialog)
```

---

## 7. AdvTerminal 中可用的参考实现

### 可直接复制的代码

| 代码 | 位置 | 适配难度 |
|---|---|---|
| `ic_doc_*.xml` 向量图标 (22个) | `app/src/main/res/drawable/` | 零（直接复制） |
| `MimeTypes` EXTRA 映射表 | 新文件 | 低（翻译为 Kotlin） |
| `MimePredicate` 分组 | 同上 | 低 |
| `NoteActivity` 异步编辑模式 | `EmbeddedFilesPage.kt` | 中（改用 coroutine） |
| `FileUtils.buildValidExtFilename` | 新文件 | 低 |
| `colors.xml` 文档类型配色 | `colors.xml` | 低 |
| `IconUtils` 图标选择逻辑 | 新工具类 | 中 |
| `CircleImage` 圆形图标组件 | 新 View | 中 |

### 需要适配的内容

| 功能 | 适配方式 |
|---|---|
| **MultiChoiceHelper** | 当前 split pane 使用 LinearLayout，非 RecyclerView。建议轻量实现：Set<String> 追踪选中 path + 长按进入模式 + 底部批量操作栏 |
| **item_doc_list.xml** | 当前 row() 只有文件名，可改为 icon + 文件名 + 修改日期 + 大小 |
| **DrawerLayout 布局** | 当前无导航抽屉，可暂缓 |
| **DocumentsProvider** | 太重，不适用（我们直接操作 java.io.File） |
| **RecyclerViewPlus** | 如需 list/grid 切换，可参考 |

### 推荐的集成顺序

1. ✅ 复制图标资源 + `MimeTypes` 工具类 → 改进 `FileIconMapper`
2. ▶️ 多选功能（轻量实现：Set<String> 追踪 + 底部批量操作栏）
3. ▶️ 编辑器异步 + 未保存检测
4. ▶️ 改进 row() 布局（图标 + 文件名 + 日期 + 大小）
