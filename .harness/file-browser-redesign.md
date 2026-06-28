# 文件浏览器体验重构 — 阶段方案

## 说明

当前 EmbeddedFilesPage ~1945 行，包含文件列表、预览、多选、拖拽、手势 FSM 等全部逻辑。
目标：分 5 个阶段，逐层提升视觉、交互、功能、架构质量。

每个阶段完成 → 编译验证 → 用户安装 APK 确认 → 进下一阶段。

---

## Phase 1 — 视觉刷新（基础体验）

### 目标
解决列表难扫读、选中反馈差、空态缺失、路径栏不友好等问题。

### 改动清单

| # | 改动 | 文件 | 说明 |
|---|---|---|---|
| 1.1 | 行间加 1px 分割线 | `EmbeddedFilesPage.kt` — `row()` | 在 `content` TextView 下方加 1px 分割 View，颜色 `0x15FFFFFF`，靠右留 marginStart=72dp |
| 1.2 | 选中高亮只改侧边指示器 + 微变色 | `EmbeddedFilesPage.kt` — `refreshHighlight()` | 选中行：左侧加 3dp 紫色竖条（indicator View），行背景 `0x087C3AED`（半透明），不铺满 |
| 1.3 | 图标从 Emoji 改为 MaterialIcon Drawable | `EmbeddedFilesPage.kt` — `row()` | 📁 → `VectorDrawableCompat` 文件夹图标，📄 → 文件图标。复用 `DesignSystem.kt` 或内联 vector |
| 1.4 | 空目录提示 | `EmbeddedFilesPage.kt` — `loadPane()` | 当 listFiles 返回空且不包含 `..` 时，显示居中文案 "此文件夹为空" + 图标 |
| 1.5 | 路径栏改为简化面包屑 | `EmbeddedFilesPage.kt` — `updatePathBar()` | 路径分段成可点击 chip：`/storage/emulated/0/.../Documents` 点击某段跳转到该目录 |
| 1.6 | 加载旋转指示器 | `EmbeddedFilesPage.kt` — `loadPane()` | 加载前在路径栏右侧显示小型 `ProgressBar`，加载完成后隐藏 |

### 验证
- 空目录显示 "此文件夹为空"
- 选中文件：左侧紫色竖条 + 微变色行背景（文字清晰可读）
- 滚动长列表：行间分割线清晰可见
- 路径栏点击某段可直接跳转

---

## Phase 2 — 搜索 + 筛选（功能补齐）

### 目标
补齐文件搜索、类型筛选、排序、分页加载。

### 改动清单

| # | 改动 | 文件 | 说明 |
|---|---|---|---|
| 2.1 | 文件搜索栏 | `EmbeddedFilesPage.kt` — toolbar 区域 | 搜索框 + 实时过滤（debounce 300ms），搜索结果显示在列表中，匹配的高亮 |
| 2.2 | 文件类型筛选 | `EmbeddedFilesPage.kt` — toolbar 区域 | 过滤器条：全部 / 文件夹 / 图片(.png.jpg.webp.gif) / 文档(.txt.pdf.doc) / 代码(.kt.java.py.js) / APK |
| 2.3 | 排序选择 | `EmbeddedFilesPage.kt` — toolbar 区域 | 排序按钮：名称 / 修改时间 / 大小，点击循环切换，默认名称 |
| 2.4 | 分块加载 | `EmbeddedFilesPage.kt` — `loadPane()` | `listFiles` 放到协程，首次加载前 200 项，滚动到底加载更多 |

### 验证
- 搜索框输入关键字 → 列表实时过滤，文件名匹配部分高亮
- 切换类型筛选 → 只显示对应类型的文件
- 切换排序 → 列表按名称/时间/大小排列
- 打开大目录（300+ 文件）→ 只显示前 200 项，滚动到底自动加载更多

---

## Phase 3 — 交互提升（手势 + 反馈）

### 目标
解决拖拽 bar 不跟手、滑动误触、无下拉刷新、删除不可逆、长按多选与拖拽混淆。

### 改动清单

| # | 改动 | 文件 | 说明 |
|---|---|---|---|
| 3.1 | 浮动拖拽 pill | `EmbeddedFilesPage.kt` — drag 系统 | 拖拽时 pill 栏不固定在底栏，改为 `View` 跟随手指 `translationX/Y`，手松开时落在最近的目标区域 |
| 3.2 | 滑动触发优化 | `EmbeddedFilesPage.kt` — `onScroll` | `absDx > absDy * 2f && absDx > 30px` 降低误触 |
| 3.3 | 下拉刷新 | `EmbeddedFilesPage.kt` — 列表容器 | 用 `SwipeRefreshLayout`（或自定义 `OverScrollListener`）包裹 ScrollView，下拉触发 reload |
| 3.4 | 回收站 + 撤销删除 | `EmbeddedFilesPage.kt` + `FileOperationsHelper` | 删除文件移到 `目标目录/.trash/`，弹出 Toast "已删除" + "撤销" 按钮。3 秒后自动清除。加 "清空回收站" 菜单项 |
| 3.5 | 长按区分多选/拖拽 | `EmbeddedFilesPage.kt` — 手势 FSM | 轻长按（<200ms + <10px）→ 多选；重长按（>300ms + >60px）→ 拖拽 |

### 验证
- 拖拽文件：pill 跟随手指移动，松开落在目标区域执行操作
- 快速滑动列表：不会误触发侧滑菜单
- 下拉列表：刷新当前目录
- 删除文件：弹出 "已删除" Toast + "撤销" 按钮，点击恢复文件
- 轻长按进入多选，重长按+移动开始拖拽

---

## Phase 4 — 预览升级

### 目标
预览不遮挡模式切换、支持前后文件导航、支持图片预览、侧边栏信息 tab。

### 改动清单

| # | 改动 | 文件 | 说明 |
|---|---|---|---|
| 4.1 | 预览改为滑动抽屉 | `EmbeddedFilesPage.kt` — `buildFilePreviewPanel()` | 从右向左滑入，`translationX` 动画，不阻挡顶部模式切换和底部操作栏 |
| 4.2 | 前后文件导航 | `EmbeddedFilesPage.kt` — 预览头部 | 添加 "◀ 上一项 / 下一项 ▶" 按钮，在当前目录文件列表中前后切换 |
| 4.3 | 图片预览 | `EmbeddedFilesPage.kt` — previewContent | 图片文件显示 `ImageView` + 双击缩放 / 捏合手势；视频文件显示 "外部打开" 按钮 |
| 4.4 | 信息 tab | `EmbeddedFilesPage.kt` — 预览底部 | 底部 tab 切换：内容 / 文件信息（大小/修改时间/权限/MIME）/ 权限（rwx 显示） |

### 验证
- 预览从右侧滑入，顶部工具栏和模式切换可用
- 点击 "上一项/下一项" 切换文件预览内容
- 打开图片文件：显示图片 + 缩放手势
- 打开视频文件：显示 "用外部播放器打开"
- 切换 tab 到 "信息"：显示文件元数据
- 关闭预览后恢复到原始布局

---

## Phase 5 — 架构清理

### 目标
把 `row()` 拆出去、预览逻辑独立、加载异步化、统一 UI 构建。

### 改动清单

| # | 改动 | 说明 |
|---|---|---|
| 5.1 | 提取 `FileRowHandler` | 将 `row()` 中的 `GestureDetector` + `OnTouchListener` + `OnDragListener` + 状态变量（swipeConsumed/longPressTriggered/dragStarted/swipeOffset/downX）抽到独立类，通过 `FilePageHost` 回调 |
| 5.2 | 提取 `FilePreviewPresenter` | 将预览的 3 种视图模式（text/edit/web）管理、dirty 追踪、保存逻辑、tabs 切换抽到独立类 |
| 5.3 | `loadPane` 异步化 | `dir.listFiles()` + 排序放到 `withContext(Dispatchers.IO)`，结果通过 Flow 发射到 UI |
| 5.4 | 统一 AIDevUi 构建 | 将内联的 `GradientDrawable`、`TextView(activity).apply` 等替换为 `ui.listItem()`、`ui.section()` 等已有 builder |

### 验证
- 编译通过，单元测试全绿
- `row()` 行数从 365 降到 <100
- 大目录（/sdcard）首次加载不卡 UI 线程
- 所有功能和手势行为与 Phase 4 保持一致

---

## 验证方式

每个 Phase：
```bash
./gradlew :app:compileDebugKotlin --no-daemon      # 编译验证
./gradlew :app:testDebugUnitTest --no-daemon        # 单元测试
bash /root/.android-env/scripts/build-android.sh    # 构建 APK
```

用户安装 APK → 按该 Phase 验证列表逐项测试 → 确认 → 进下一阶段。

---

## 已完成

- Phase 0: GestureFeedbackManager ✓
- Phase 0: Swipe-to-reveal buttons ✓
- Phase 0: Drag-drop pill bar + arc progress + bounce-back ✓
- Phase 1: 视觉刷新（分割线 / 选中高亮 / 图标 / 空态 / 面包屑 / 加载指示器）✓
- Phase 2: 搜索 + 筛选（搜索栏 / 全部/文件夹/文件 筛选标签）✓
- Phase 3: 交互提升（浮动 pill / 滑动阈值 / 下拉刷新 / 回收站+撤销 / 长按区分）✓
- Phase 4: 预览升级（滑动抽屉 / 导航 / 图片预览 / 信息 tab）✓
- Phase 5: 架构清理（PreviewManager 提取 / RowHandler inner class / AIDevUi 统一 / 死代码移除）✓
- Bugfix: elevation/translationZ 移除（HyperOS 硬件层 bug）
- Bugfix: 选择指示器改用纯色背景 + 文字/图标变色（绕开 HyperOS GPU 渲染 bug）

## 后续可选

| 事项 | 说明 | 优先级 |
|---|---|---|
| RowHandler 提取独立文件 | 目前是 inner class，可通过 FilePageHost 接口提取到单独文件 | 低 |
| 更多预览格式 | 视频/音频/PDF 预览支持 | 低 |
| 文件操作批处理进度 | 复制/移动文件时显示进度条 | 低 |
