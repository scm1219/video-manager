# Tree 组件模块 - 文件树

[根目录](../../../../../../CLAUDE.md) > [src](../../../../../) > [main](../../../../) > [java](../../../) > [com](../../) > [github](../../../) > [scm1219](../../../../) > [video](../../../../../) > [gui](../../../../) > **tree**

---

## 变更记录 (Changelog)

### 2026-01-29 18:35:16
- 初始化文件树模块文档
- 识别核心组件：FileTree、FileTreeNode、FileTreeCellRenderer、FileUpdateProcesser

---

## 模块职责

**Tree 组件模块** 负责左侧文件树的显示和交互，包括磁盘目录浏览、右键菜单和索引管理。

### 核心职责
- 显示磁盘根目录和文件系统层级
- 懒加载子目录（展开时动态加载）
- 右键菜单：索引更新、SMART 检测、创建标记文件
- 鼠标悬停高亮效果
- 索引进度窗口

---

## 入口与启动

### FileTree（文件树组件）
```java
public class FileTree extends JTree {
    public FileTree() {
        setRootVisible(false);  // 隐藏虚拟根节点
        initPopMenu();          // 初始化右键菜单
        addTreeWillExpandListener();  // 懒加载
        addMouseMotionListener();     // 鼠标悬停高亮
    }
}
```

### FileTreeNode（树节点）
```java
public class FileTreeNode extends DefaultMutableTreeNode {
    private File file;      // 关联的文件对象
    private boolean init;   // 是否已加载子节点
    private boolean indexed;// 是否已索引
    private boolean dummyRoot; // 是否为虚拟根节点
}
```

---

## 对外接口

### FileTree（主组件）
```java
public class FileTree extends JTree {
    public TreePath mouseInPath;  // 当前鼠标悬停的路径

    // 右键菜单项
    private JMenuItem mEchoIndexInfo;     // 查看索引信息
    private JMenuItem mCreateIndex;       // 更新索引
    private JMenuItem mShowSmart;         // 磁盘健康状况
    private JMenuItem mCreateNeedIndexFile; // 创建 needindex 文件
}
```

### FileTreeNode（节点）
```java
public class FileTreeNode extends DefaultMutableTreeNode {
    public FileTreeNode(File file, boolean dummyRoot);
    public File getFile();
    public boolean isInit();
    public void setInit(boolean init);
    public boolean isIndexed();
    public void setIndexed(boolean indexed);
    public boolean isDummyRoot();
}
```

### FileTreeCellRenderer（渲染器）
```java
public class FileTreeCellRenderer extends DefaultTreeCellRenderer {
    // 自定义节点渲染：
    // - 索引磁盘使用特殊图标/颜色
    // - 鼠标悬停高亮
}
```

### FileUpdateProcesser（索引窗口）
```java
public class FileUpdateProcesser extends JFrame {
    // 构造函数
    public FileUpdateProcesser(Disk disk);           // 整盘索引
    public FileUpdateProcesser(Disk disk, File directory); // 目录级索引

    // 组件
    private JProgressBar progressBar;
    private JTextArea textArea;
    private JButton button;
}
```

---

## 关键依赖与配置

### GUI 框架
- **JTree**：Swing 树组件
- **DefaultTreeModel**：树数据模型
- **DefaultMutableTreeNode**：可变树节点
- **TreeWillExpandListener**：节点展开事件监听器

### 文件系统
- **FileSystemView**：获取文件系统图标和显示名称
```java
static protected FileSystemView fileSystemView = FileSystemView.getFileSystemView();
```

### 领域层依赖
- `../../../domain/Disk.java` - 磁盘实体
- `../../../domain/DiskManager.java` - 磁盘管理器
- `../../../domain/Index.java` - 索引对象

---

## 实现细节

### 懒加载机制
```java
addTreeWillExpandListener(new TreeWillExpandListener() {
    @Override
    public void treeWillExpand(TreeExpansionEvent event) {
        FileTreeNode fileNode = (FileTreeNode) event.getPath().getLastPathComponent();
        if (!fileNode.isInit()) {
            // 使用 SwingWorker 在后台加载子节点
            SwingWorker<File[], Void> worker = new SwingWorker<File[], Void>() {
                @Override
                protected File[] doInBackground() {
                    return fileSystemView.getFiles(fileNode.getFile(), false);
                }

                @Override
                protected void done() {
                    // 添加子节点并刷新树
                    File[] files = get();
                    for (File file : files) {
                        if (file.isDirectory()) {
                            FileTreeNode child = new FileTreeNode(file);
                            fileNode.add(child);
                        }
                    }
                    fileNode.setInit(true);
                    ((DefaultTreeModel)getModel()).nodeStructureChanged(fileNode);
                }
            };
            worker.execute();
        }
    }
});
```

### 右键菜单逻辑
```java
// 动态启用/禁用菜单项
mEchoIndexInfo.setEnabled(isIndexed && disk != null && disk.getIndex().exists());
mCreateIndex.setEnabled(isIndexed && disk != null);
mCreateNeedIndexFile.setEnabled(!isIndexed);
mShowSmart.setEnabled(true);
```

### 鼠标悬停高亮
```java
addMouseMotionListener(new MouseAdapter() {
    @Override
    public void mouseMoved(MouseEvent e) {
        TreePath path = getPathForLocation(e.getX(), e.getY());
        if (path != null) {
            // 重绘旧路径和新路径的并集
            Rectangle oldRect = getPathBounds(mouseInPath);
            Rectangle newRect = getPathBounds(path);
            mouseInPath = path;
            repaint(newRect.union(oldRect));
        }
    }
});
```

### 索引创建流程
```java
// 在 Progress 线程中执行
public void run() {
    button.setEnabled(false);
    button.setText("取消");

    if(targetDirectory == null) {
        // 整盘索引
        disk.getIndex().create(disk, bar);
    } else {
        // 目录级索引
        IndexStatistics stats = disk.getIndex().createForDirectory(targetDirectory, bar);
        textArea.setText("扫描完成\n" + stats.toFormattedString());
    }

    button.setText("关闭");
    button.setEnabled(true);
}
```

---

## 数据流

### 节点展开流程
1. 用户点击节点展开图标 → 触发 `treeWillExpand` 事件
2. 检查节点是否已初始化（`isInit()`）
3. 如果未初始化，启动 `SwingWorker` 后台线程
4. 在 `doInBackground()` 中调用 `FileSystemView.getFiles()` 获取子目录
5. 在 `done()` 中添加子节点并刷新树模型

### 右键菜单流程
1. 用户右键点击节点 → 显示菜单
2. 根据节点状态启用/禁用菜单项
3. 用户选择菜单项 → 执行相应操作
4. 更新 UI（如刷新树、弹窗显示信息）

---

## 测试与质量

### 当前状态
- **无单元测试**：缺少 Swing 测试
- **手动测试**：通过运行应用程序验证功能

### 测试建议
1. **FileTreeTest**：测试懒加载、菜单项状态
2. **FileTreeNodeTest**：测试节点属性
3. **FileUpdateProcesserTest**：测试进度窗口逻辑

---

## 常见问题 (FAQ)

### Q1: 为什么需要虚拟根节点？
**A**: JTree 需要单一根节点，但文件系统可能有多个磁盘根目录（C:、D:、E:）。使用虚拟根节点可以统一管理多个磁盘。

### Q2: 懒加载如何避免性能问题？
**A**: 仅在用户展开节点时加载子节点，避免一次性加载整个文件系统。使用 `SwingWorker` 在后台线程执行，避免阻塞 UI。

### Q3: 如何判断节点是否已索引？
**A**: 通过 `FileTreeNode.indexed` 属性标记，在 `FileExplorerWindow.initData()` 中设置：
```java
Disk disk = DiskManager.getInstance().findDisk(files[i]);
childNode.setIndexed(disk != null && disk.needIndex());
```

### Q4: 右键菜单的"创建 needindex 文件"如何工作？
**A**: 在磁盘根目录创建 `.disk.needindex` 空文件，然后调用 `DiskManager.loadDisks()` 重新加载磁盘列表，最后初始化空数据库。

### Q5: 索引窗口如何区分整盘索引和目录级索引？
**A**: 通过 `targetDirectory` 字段：
- `null`：整盘索引
- 非 `null`：目录级索引

---

## 相关文件清单

### 核心文件
- `FileTree.java` - 文件树组件（230+ 行）
- `FileTreeNode.java` - 树节点
- `FileTreeCellRenderer.java` - 树单元格渲染器
- `FileUpdateProcesser.java` - 索引进度窗口（180+ 行）

### 调用方
- `../FileExplorerWindow.java` - 使用 FileTree 作为左侧面板
- `../table/FileTable.java` - 与树联动（点击树更新表）

---

## 下一步改进建议

1. **节点图标**：为不同类型的节点（磁盘、目录、已索引磁盘）显示不同图标
2. **搜索高亮**：在搜索结果中高亮匹配的节点
3. **拖放支持**：支持拖放文件到树节点进行索引
4. **缓存优化**：缓存已加载的子节点，避免重复加载
5. **取消索引**：实现索引创建的取消功能（当前按钮显示"取消"但未实现）
