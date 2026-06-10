package com.github.scm1219.video.gui.table;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.filechooser.FileSystemView;
import javax.swing.table.AbstractTableModel;

public class FileTableModel extends AbstractTableModel {

	private static final long serialVersionUID = 1L;
	private static final String[] TABLE_HEADER = { "名称", "修改日期", "类型", "大小", "路径" };

	private final FileSystemView fileSystemView = FileSystemView.getFileSystemView();
	private final List<File> files;
	private boolean showParentRow = false;

	/** 搜索模式下每行对应的磁盘名（与 files 平行，null 表示非搜索模式） */
	private List<String> diskNames;

	/** 离线文件的行索引集合（实际行索引，已排除 parentRow 偏移） */
	private Set<Integer> offlineFileIndexes;

	public FileTableModel(List<File> files, boolean showParentRow) {
		this.files = new ArrayList<>(files);
		this.showParentRow = showParentRow;
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

	@Override
	public Object getValueAt(int row, int column) {
		if (isParentRow(row)) {
			return getParentRowValue(column);
		}
		int actualRow = showParentRow ? row - 1 : row;
		File file = files.get(actualRow);
		switch (column) {
		case 0:
			return file;
		case 1:
			if (isOffline(row)) {
				return 0L;
			}
			return file.lastModified();
		case 2:
			if (isOffline(row)) {
				return "离线文件";
			}
			return fileSystemView.getSystemTypeDescription(file);
		case 3:
			if (isOffline(row)) {
				return 0L;
			}
			return file.length();
		case 4:
			return file;
		default:
			return "";
		}
	}

	private Object getParentRowValue(int column) {
		switch (column) {
		case 0:
			return new File(".. 返回上一级");
		case 1:
			return System.currentTimeMillis();
		case 2:
			return "父目录";
		case 3:
			return 0L;
		case 4:
			return new File("");
		default:
			return "";
		}
	}

	/**
	 * 检测指定行的文件是否存在
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
		File file = files.get(actualRow);
		return file != null && file.exists();
	}
}
