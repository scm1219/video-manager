# Table 组件模块 - 文件表格

[根目录](../../../../../../CLAUDE.md) > [src](../../../../../) > [main](../../../../) > [java](../../../) > [com](../../) > [github](../../../) > [scm1219](../../../../) > [video](../../../../../) > [gui](../../../../) > **table**

---

## 变更记录 (Changelog)

### 2026-01-29 18:35:16
- 初始化文件表格模块文档
- 识别核心组件：FileTable、FileTableModel、FileTableCellRenderer

---

## 模块职责

**Table 组件模块** 负责右侧文件表格的显示和交互，包括文件列表、排序、右键菜单等。

### 核心职责
- 显示文件列表（文件名、大小、类型、修改时间）
- 支持列排序（文件名、大小）
- 自定义单元格渲染（图标、颜色）
- 虚拟"返回上一级"行（非根目录时显示）
- 右键菜单：打开所在文件夹、扫描目录、转到

---

## 入口与启动

### FileTable（文件表格）
```java
public class FileTable extends JTable {
    public FileTable() {
        setDefaultRenderer(Object.class, new FileTableCellRenderer());
        setAutoCreateRowSorter(true);
        getTableHeader().setReorderingAllowed(false);
        setShowHorizontalLines(false);
        setShowVerticalLines(false);
        setIntercellSpacing(new Dimension(0, 0));
        setRowMargin(0);
    }
}
```

---

## 对外接口

### FileTable（主组件）
```java
public class FileTable extends JTable {
    private FileTableModel fileTableModel;

    @Override
    public void setModel(TableModel dataModel);
    public FileTableModel getFileTableModel();
}
```

### FileTableModel（表格模型）
```java
public class FileTableModel extends AbstractTableModel {
    private Object[][] fileData;  // 文件数据
    private boolean showParentRow; // 是否显示虚拟行

    // 列定义
    private static final String[] COLUMN_NAMES = {
        "文件名", "大小", "类型", "修改时间"
    };

    // AbstractTableModel 实现
    public int getRowCount();
    public int getColumnCount();
    public Object getValueAt(int rowIndex, int columnIndex);
    public String getColumnName(int column);

    // 自定义方法
    public boolean isParentRow(int row);  // 判断是否为虚拟行
}
```

### FileTableCellRenderer（单元格渲染器）
```java
public class FileTableCellRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(
        JTable table, Object value,
        boolean isSelected, boolean hasFocus,
        int row, int column
    );

    // 自定义渲染逻辑：
    // - 文件图标（使用 FileSystemView）
    // - 虚拟行特殊样式（灰度、箭头图标）
}
```

---

## 关键依赖与配置

### GUI 框架
- **JTable**：Swing 表格组件
- **AbstractTableModel**：表格模型基类
- **DefaultTableCellRenderer**：单元格渲染器基类
- **TableRowSorter**：行排序器

### 文件系统
- **FileSystemView**：获取文件图标和显示名称
```java
Icon icon = fileSystemView.getSystemIcon(file);
String displayName = fileSystemView.getSystemDisplayName(file);
```

### 数据模型
```java
// 每行包含 5 列，但所有列都指向同一个 File 对象
Object[][] fileData = new Object[fileList.size()][5];
for (int i = 0; i < fileData.length; i++) {
    for (int j = 0; j < 5; j++) {
        fileData[i][j] = fileList.get(i);  // 所有列都是同一个 File
    }
}
```

---

## 实现细节

### 虚拟"返回上一级"行
```java
// 在非根目录时，在表格顶部插入虚拟行
private void setFileTable(File[] files, boolean showParentRow) {
    // ...
    if (showParentRow) {
        // 添加虚拟行（实际在 FileTableModel 中处理）
        model.setShowParentRow(true);
    }
}

// FileTableModel 中判断
public boolean isParentRow(int row) {
    return showParentRow && row == 0;
}
```

### 文件排序
```java
// 自定义比较器
public static final Comparator<File> FILE_COMPARATOR = (f1, f2) -> {
    if (f1.isDirectory()) {
        if (f2.isDirectory()) {
            return f1.getName().compareToIgnoreCase(f2.getName());
        } else {
            return -1;  // 目录优先
        }
    } else {
        if (!f2.isDirectory()) {
            return f1.getName().compareToIgnoreCase(f2.getName());
        } else {
            return 1;
        }
    }
};

// 应用排序器
TableRowSorter<FileTableModel> sort = new TableRowSorter<>(model);
sort.setComparator(0, FILE_COMPARATOR);      // 文件名列
sort.setComparator(3, FILE_SIZE_COMPARATOR); // 大小列
tbFile.setRowSorter(sort);
```

### 单元格渲染
```java
@Override
public Component getTableCellRendererComponent(...) {
    Component c = super.getTableCellRendererComponent(...);

    if (value instanceof File) {
        File file = (File) value;

        // 第 0 列：文件名 + 图标
        if (column == 0) {
            setIcon(fileSystemView.getSystemIcon(file));
            setText(file.getName());
        }
        // 第 1 列：文件大小
        else if (column == 1) {
            if (file.isDirectory()) {
                setText("");
            } else {
                setText(FileUtils.formetFileSize(file.length()));
            }
        }
        // 第 2 列：文件类型
        else if (column == 2) {
            setText(file.isDirectory() ? "文件夹" : FileUtils.getFileType(file));
        }
        // 第 3 列：修改时间
        else if (column == 3) {
            setText(DateUtils.getDateString(file.lastModified()));
        }
    }

    return c;
}
```

### 右键菜单
```java
// 在 FileExplorerWindow 中定义
JPopupMenu menu = new JPopupMenu();

// 打开所在文件夹
JMenuItem mEchoIndexInfo = new JMenuItem("打开所在文件夹");
mEchoIndexInfo.addActionListener(e -> {
    File file = (File) fileTable.getValueAt(row, 0);
    FileUtils.openDirAndSelectFile(file);
});

// 扫描此目录
JMenuItem mScanDirectory = new JMenuItem("扫描此目录");
mScanDirectory.addActionListener(e -> {
    File file = (File) fileTable.getValueAt(row, 0);
    File targetDir = file.isDirectory() ? file : file.getParentFile();
    // 启动目录扫描...
});

// 转到（仅在搜索模式下可用）
JMenuItem mNavigateTo = new JMenuItem("转到");
mNavigateTo.setEnabled(isSearchMode && canNavigateToFile(file));
mNavigateTo.addActionListener(e -> {
    File file = (File) fileTable.getValueAt(row, 0);
    updateTable(file.getParentFile(), false);
});
```

---

## 数据流

### 表格更新流程
1. 用户在左侧树选择目录 → 触发 `updateTable(file, false)`
2. 使用 `SwingWorker` 在后台获取文件列表
3. 调用 `setFileTable(files, !isRootDirectory)` 更新表格
4. 设置 `FileTableModel` 并应用排序器
5. 刷新表格显示

### 搜索结果流程
1. 用户输入搜索词并点击"搜索" → 触发 `searchAllFiles()`
2. 调用 `updateSearchResult(results)` 显示结果
3. 设置 `isSearchMode = true`，启用"转到"菜单项
4. 双击文件 → 进入文件所在目录（`updateTable(parentDir, false)`）

---

## 测试与质量

### 当前状态
- **无单元测试**：缺少 Swing 测试
- **手动测试**：通过运行应用程序验证功能

### 测试建议
1. **FileTableModelTest**：测试数据模型、虚拟行逻辑
2. **FileTableCellRendererTest**：测试渲染逻辑
3. **排序测试**：测试文件排序（目录优先、大小写）

---

## 常见问题 (FAQ)

### Q1: 为什么所有列都存储同一个 File 对象？
**A**: 简化数据模型，避免数据冗余。渲染器根据列索引从 File 对象提取不同属性（名称、大小、时间）。

### Q2: 虚拟行如何实现"返回上一级"？
**A**: 在表格顶部插入特殊行，`FileTableModel.isParentRow(0)` 返回 `true`。双击该行时弹出栈顶元素并更新表格。

### Q3: 为什么禁用列拖拽重排？
**A**: 列顺序固定（文件名、大小、类型、时间），重排会导致渲染逻辑复杂化。

### Q4: 搜索模式下如何"转到"文件所在目录？
**A**: 搜索结果显示跨磁盘文件，双击或右键"转到"会调用 `updateTable(parentDir, false)` 进入该文件所在目录，并同步更新树的选择状态。

### Q5: 如何自定义列宽？
**A**: 在 `FileExplorerWindow.setFileTable()` 中设置：
```java
tbFile.getColumnModel().getColumn(0).setPreferredWidth(180);  // 文件名
tbFile.getColumnModel().getColumn(1).setPreferredWidth(120);  // 大小
tbFile.getColumnModel().getColumn(2).setPreferredWidth(80);   // 类型
tbFile.getColumnModel().getColumn(3).setPreferredWidth(50);   // 修改时间
```

---

## 相关文件清单

### 核心文件
- `FileTable.java` - 文件表格组件（40 行）
- `FileTableModel.java` - 表格模型（120+ 行）
- `FileTableCellRenderer.java` - 单元格渲染器（80+ 行）

### 调用方
- `../FileExplorerWindow.java` - 使用 FileTable 作为右侧面板
- `../tree/FileTree.java` - 与表格联动（点击树更新表）

### 工具类依赖
- `../../../../utils/FileUtils.java` - 文件大小格式化
- `../../../../utils/DateUtils.java` - 日期格式化

---

## 下一步改进建议

1. **列宽自适应**：根据内容自动调整列宽
2. **分组显示**：按文件类型分组（视频、音频、文档）
3. **缩略图预览**：为视频文件生成缩略图
4. **多选支持**：支持多选文件进行批量操作
5. **性能优化**：大目录使用分页加载（当前一次性加载所有文件）
