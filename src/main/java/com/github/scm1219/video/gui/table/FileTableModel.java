package com.github.scm1219.video.gui.table;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.filechooser.FileSystemView;
import javax.swing.table.AbstractTableModel;

public class FileTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;
    private static final String[] TABLE_HEADER = { "名称", "修改日期", "类型", "大小", "路径" };

    /** 虚拟行展示用的固定对象，避免每次渲染重新创建 */
    private static final File PARENT_ROW_NAME_FILE = new File(".. 返回上一级");
    private static final File PARENT_ROW_PATH_FILE = new File("");

    private static final FileSystemView fileSystemView = FileSystemView.getFileSystemView();
    private final List<File> files;
    private boolean showParentRow = false;

    /** 搜索模式下每行对应的磁盘名（与 files 平行，null 表示非搜索模式） */
    private List<String> diskNames;

    /** 离线文件的行索引集合（实际行索引，已排除 parentRow 偏移） */
    private Set<Integer> offlineFileIndexes;

    /**
     * 行元数据缓存：目录加载/搜索时在后台线程预取填充，
     * 渲染与排序路径只读缓存，避免每次重绘都做磁盘 I/O；未命中时懒加载一次
     */
    private final Map<String, FileMetaData> rowMetaCache = new HashMap<>();

    public FileTableModel(List<File> files, boolean showParentRow) {
        this.files = new ArrayList<>(files);
        this.showParentRow = showParentRow;
    }

    /**
     * 文件行元数据（存在性、目录标记、修改时间、大小、类型描述）
     */
    public static class FileMetaData {
        public final boolean exists;
        public final boolean directory;
        public final long lastModified;
        public final long length;
        public final String typeDescription;

        private FileMetaData(boolean exists, boolean directory, long lastModified, long length,
                String typeDescription) {
            this.exists = exists;
            this.directory = directory;
            this.lastModified = lastModified;
            this.length = length;
            this.typeDescription = typeDescription;
        }

        /** 读取文件的各项元数据（含磁盘 I/O，仅供后台线程预取或懒加载兜底调用） */
        public static FileMetaData load(File file) {
            if (file == null) {
                return new FileMetaData(false, false, 0L, 0L, "");
            }
            return new FileMetaData(file.exists(), file.isDirectory(), file.lastModified(), file.length(),
                    fileSystemView.getSystemTypeDescription(file));
        }
    }

    /**
     * 在模型挂到表格前批量填充元数据缓存（数据由后台线程预取）
     *
     * @param cache 以文件绝对路径为键的元数据映射
     */
    public void setRowMetaCache(Map<String, FileMetaData> cache) {
        if (cache != null) {
            rowMetaCache.putAll(cache);
        }
    }

    private FileMetaData metaFor(File file) {
        if (file == null) {
            return FileMetaData.load(null);
        }
        return rowMetaCache.computeIfAbsent(file.getAbsolutePath(), key -> FileMetaData.load(file));
    }

    @Override
    public int getRowCount() {
        return files.size() + (showParentRow ? 1 : 0);
    }

    @Override
    public int getColumnCount() {
        return TABLE_HEADER.length;
    }

    @Override
    public String getColumnName(int column) {
        return TABLE_HEADER[column];
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }

    /**
     * 判断指定行是否为虚拟的"返回上一级"行
     */
    public boolean isParentRow(int row) {
        return showParentRow && row == 0;
    }

    /**
     * 设置是否显示"返回上一级"虚拟行
     */
    public void setShowParentRow(boolean show) {
        this.showParentRow = show;
        fireTableDataChanged();
    }

    /**
     * 设置搜索模式下的磁盘名和离线标记
     *
     * @param diskNames          与 files 平行的磁盘名列表
     * @param offlineFileIndexes 离线文件的索引集合（相对于 files 列表）
     */
    public void setSearchMetadata(List<String> diskNames, Set<Integer> offlineFileIndexes) {
        this.diskNames = diskNames;
        this.offlineFileIndexes = offlineFileIndexes != null ? offlineFileIndexes : new HashSet<>();
        fireTableDataChanged();
    }

    /**
     * 判断是否处于搜索模式（有磁盘名数据）
     */
    public boolean isSearchMode() {
        return diskNames != null && !diskNames.isEmpty();
    }

    /**
     * 获取指定行对应的磁盘名
     *
     * @param row 表格行号
     * @return 磁盘名，非搜索模式返回 null
     */
    public String getDiskName(int row) {
        if (diskNames == null || isParentRow(row)) {
            return null;
        }
        int actualRow = showParentRow ? row - 1 : row;
        if (actualRow >= 0 && actualRow < diskNames.size()) {
            return diskNames.get(actualRow);
        }
        return null;
    }

    /**
     * 判断指定行是否为离线文件
     *
     * @param row 表格行号
     * @return true 如果该文件来自离线缓存
     */
    public boolean isOffline(int row) {
        if (offlineFileIndexes == null || isParentRow(row)) {
            return false;
        }
        int actualRow = showParentRow ? row - 1 : row;
        return offlineFileIndexes.contains(actualRow);
    }

    /**
     * 判断指定行是否为目录（读取预取的元数据，避免渲染时做磁盘 I/O）
     */
    public boolean isDirectory(int row) {
        if (isParentRow(row)) {
            return false;
        }
        int actualRow = showParentRow ? row - 1 : row;
        if (actualRow < 0 || actualRow >= files.size()) {
            return false;
        }
        return metaFor(files.get(actualRow)).directory;
    }

    @Override
    public Object getValueAt(int row, int column) {
        if (isParentRow(row)) {
            return getParentRowValue(column);
        }
        int actualRow = showParentRow ? row - 1 : row;
        if (actualRow < 0 || actualRow >= files.size()) {
            return ""; // 行号越界（如点击空白区域）时兜底，避免抛出异常
        }
        File file = files.get(actualRow);
        FileMetaData meta = metaFor(file);
        switch (column) {
        case 0:
            return file;
        case 1:
            if (isOffline(row)) {
                return 0L;
            }
            return meta.lastModified;
        case 2:
            if (isOffline(row)) {
                return "离线文件";
            }
            return meta.typeDescription;
        case 3:
            if (isOffline(row)) {
                return 0L;
            }
            return meta.length;
        case 4:
            return file;
        default:
            return "";
        }
    }

    private Object getParentRowValue(int column) {
        switch (column) {
        case 0:
            return PARENT_ROW_NAME_FILE;
        case 1:
            return 0L; // 修改日期无实际值，渲染为 "-"
        case 2:
            return "父目录";
        case 3:
            return 0L;
        case 4:
            return PARENT_ROW_PATH_FILE;
        default:
            return "";
        }
    }

    /**
     * 检测指定行的文件是否存在（读取预取的元数据）
     */
    public boolean fileExists(int row) {
        if (isParentRow(row))
            return true;
        int actualRow = showParentRow ? row - 1 : row;
        if (actualRow < 0 || actualRow >= files.size())
            return false;
        // 离线文件不存在于本地
        if (isOffline(row))
            return false;
        return metaFor(files.get(actualRow)).exists;
    }
}
