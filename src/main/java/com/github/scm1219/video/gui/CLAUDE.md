# GUI 模块 - 图形用户界面层

[根目录](../../../../../CLAUDE.md) > [src](../../../../) > [main](../../../) > [java](../../) > [com](../) > [github](../../) > [scm1219](../../../) > [video](../../../../) > **gui**

---

## 变更记录 (Changelog)

### 2026-01-29 18:35:16
- 初始化 GUI 模块文档
- 识别核心组件：FileExplorerWindow、ThemeManager、FileTree、FileTable

---

## 模块职责

**GUI 层（Graphical User Interface）** 负责提供用户交互界面，包括文件浏览、搜索、索引管理和主题切换功能。

### 核心职责
- 文件树视图（左侧）：显示磁盘和目录结构
- 文件表格视图（右侧）：显示文件列表和详情
- 搜索功能：跨磁盘搜索视频和目录
- 主题管理：支持浅色/深色/跟随系统主题
- 右键菜单：提供索引更新、SMART 检测、目录扫描等快捷操作

---

## 入口与启动

### 主窗口
- **FileExplorerWindow** (`FileExplorerWindow.java`)
  - 主界面：1024x768 像素，居中显示
  - 初始化流程：`initComponent()` → `initData()` → `createComponent()` → `AddComponentListener()`

### 启动代码
```java
// 在 VideoManagerApp.main() 中
final JFrame frame = new FileExplorerWindow();
frame.setIconImage(ImageIO.read(...));  // 设置图标
frame.setVisible(true);
```

---

## 对外接口

### FileExplorerWindow（主窗口）
```java
public class FileExplorerWindow extends JFrame {
    // 组件
    private FileTree trFileTree;          // 文件树
    private FileTable tbFile;             // 文件表
    private JTextField tfSearch;          // 搜索框
    private JTextField tfDir;             // 当前路径
    private JCheckBox chkVideoOnly;       // 仅显示视频
    private JCheckBox chkShowAllDisks;    // 显示所有磁盘

    // 功能方法
    private void updateTable(File file, Boolean isBack);  // 更新文件表
    private void updateSearchResult(List<File> files);    // 显示搜索结果
    private void refreshTree(boolean showPrompt);         // 刷新磁盘树
}
```

### ThemeManager（主题管理器）
```java
public class ThemeManager {
    // 获取单例
    public static synchronized ThemeManager getInstance();

    // 应用主题
    public boolean applyTheme(String themeName);  // light/dark/auto

    // 获取/保存配置
    public String getCurrentTheme();
    public boolean saveThemeConfig(String themeName);

    // 工具方法
    public static void updateUI();  // 刷新所有窗口
    public static String getThemeDisplayName(String themeName);
}
```

### ClickDebouncer（点击防抖）
```java
public class ClickDebouncer {
    // 防止重复打开文件（记录最近打开的文件路径）
    public static boolean shouldOpen(String filePath);
    public static void recordError(String filePath);
}
```

---

## 关键依赖与配置

### GUI 框架
- **Java Swing**：基础 GUI 框架
- **FlatLaf** (3.7)：现代化 Look and Feel
  - `FlatLightLaf`：浅色主题
  - `FlatDarkLaf`：深色主题
  - `FlatLaf.updateUI()`：运行时刷新 UI

### 主题配置
- **配置文件**：`~/.video-manager/theme.properties`
- **配置项**：`theme=light|dark|auto`
- **默认主题**：`light`

### 组件布局
- **布局管理器**：`GridBagLayout`、`BorderLayout`
- **分割面板**：`JSplitPane`（左右分割、上下分割）
- **滚动面板**：`JScrollPane`（树和表）

---

## 组件结构

### 主窗口布局
```
┌─────────────────────────────────────────────┐
│ 菜单栏：主题                                  │
├─────────────────────────────────────────────┤
│ 顶部面板：搜索、路径导航                       │
│ [搜索文件][搜索文件夹][______搜索框____][仅视频]│
│ [后退][清空搜索][____当前路径____][刷新磁盘]   │
├──────────────────┬──────────────────────────┤
│ 左侧面板          │ 右侧面板                 │
│ ┌────────────┐   │ ┌──────────────────────┐ │
│ │文件树      │   │ │文件表格              │ │
│ │            │   │ │                      │ │
│ ├────────────┤   │ │- 文件名              │ │
│ │索引信息    │   │ │- 大小                │ │
│ │            │   │ │- 类型                │ │
│ └────────────┘   │ └──────────────────────┘ │
└──────────────────┴──────────────────────────┘
```

### 子模块
- **tree**：文件树组件（`FileTree`、`FileTreeNode`、`FileTreeCellRenderer`）
- **table**：文件表格组件（`FileTable`、`FileTableModel`、`FileTableCellRenderer`）

---

## 数据流

### 浏览文件流程
1. 用户在左侧树选择目录 → 触发 `TreeSelectionListener`
2. 调用 `updateTable(file, false)` → 更新右侧表格
3. 使用 `SwingWorker` 异步加载文件列表（避免阻塞 UI）
4. 设置 `FileTableModel` 并刷新表格

### 搜索流程
1. 用户输入搜索词并点击"搜索文件" → 触发 `btnSearch` 点击事件
2. 调用 `DiskManager.getInstance().searchAllFiles(keyword)` → 跨磁盘搜索
3. 调用 `updateSearchResult(results)` → 显示搜索结果
4. 进入搜索模式：`isSearchMode = true`，禁用"转到"菜单项

### 索引创建流程
1. 用户右键点击磁盘树节点 → 弹出菜单
2. 选择"更新索引" → 启动 `FileUpdateProcesser` 窗口
3. 后台线程执行 `disk.getIndex().create(disk)` → 扫描并插入数据库
4. 进度条实时显示进度（0-100%）

---

## 事件处理

### 鼠标事件
- **单击**：选中文件/目录
- **双击**：
  - 视频文件 → 调用系统默认播放器打开（`FileUtils.openVideoFile()`）
  - 目录 → 进入目录（`updateTable()`）
- **右键**：弹出上下文菜单
  - "转到"（仅在搜索模式下可用）
  - "打开所在文件夹"
  - "扫描此目录"

### 键盘事件
- **Enter**（搜索框）：触发搜索
- **Esc**：未实现（可添加清空搜索功能）

### 菜单事件
- **主题切换**：浅色/深色/跟随系统 → 调用 `ThemeManager.applyTheme()`
- **索引更新**：弹出进度窗口 → 后台线程创建索引
- **SMART 检测**：调用 `DiskUtils.getSmartInfo()` → 弹窗显示

---

## 测试与质量

### 当前状态
- **无 GUI 测试**：缺少 Swing 单元测试框架
- **手动测试**：通过运行应用程序验证功能

### 测试建议
1. **UI 测试**：使用 AssertJ Swing 或 FEST
2. **事件测试**：模拟鼠标点击、键盘输入
3. **主题测试**：验证所有主题的样式正确性

---

## 常见问题 (FAQ)

### Q1: 为什么使用 SwingWorker 加载文件列表？
**A**: 文件系统操作可能很慢（尤其是网络驱动器），直接在事件分发线程（EDT）执行会导致界面冻结。`SwingWorker` 在后台线程执行，完成后自动更新 UI。

### Q2: 搜索模式下如何"转到"文件所在目录？
**A**: 搜索结果显示的是跨磁盘的结果，双击文件会调用 `updateTable(parentDir, false)` 进入该文件所在目录，并同步更新树的选择状态。

### Q3: 主题切换后如何刷新所有组件？
**A**: 调用 `FlatLaf.updateUI()`，它会递归刷新所有窗口和组件的 UI。这是 FlatLaf 提供的便捷方法。

### Q4: 为什么需要 ClickDebouncer？
**A**: 防止用户快速双击导致重复打开文件。如果最近已打开过该文件，则忽略本次点击。

### Q5: 如何添加新的主题？
**A**: 在 `ThemeManager` 中添加新的常量（如 `THEME_CUSTOM`），并在 `applyTheme()` 方法中调用对应的 FlatLaf 主题类。

---

## 相关文件清单

### 核心文件
- `FileExplorerWindow.java` - 主窗口（800+ 行，包含所有布局和事件）
- `ThemeManager.java` - 主题管理器（单例）
- `FileExplorerUI.java` - 旧版 UI 组件（未使用）
- `ClickDebouncer.java` - 点击防抖工具
- `IconCache.java` - 图标缓存（未实现）

### 子模块
- `tree/FileTree.java` - 文件树组件
- `tree/FileTreeNode.java` - 树节点
- `tree/FileTreeCellRenderer.java` - 树单元格渲染器
- `tree/FileUpdateProcesser.java` - 索引进度窗口
- `table/FileTable.java` - 文件表格
- `table/FileTableModel.java` - 表格模型
- `table/FileTableCellRenderer.java` - 表格单元格渲染器

### 领域层依赖
- `../domain/DiskManager.java` - 磁盘管理器
- `../domain/Disk.java` - 磁盘实体
- `../domain/Index.java` - 索引对象

### 工具类依赖
- `../../utils/FileUtils.java` - 文件操作（打开视频、打开文件夹）
- `../../utils/DiskUtils.java` - SMART 信息获取

---

## 下一步改进建议

1. **UI 现代化**：考虑迁移到 JavaFX 或使用 FlatLaf 的更多特性
2. **响应式布局**：支持窗口大小调整时自适应组件布局
3. **快捷键支持**：添加键盘快捷键（Ctrl+F 搜索、Ctrl+N 后退等）
4. **进度提示**：索引创建时显示更详细的进度信息
5. **主题预览**：添加主题预览功能，切换前先预览效果
