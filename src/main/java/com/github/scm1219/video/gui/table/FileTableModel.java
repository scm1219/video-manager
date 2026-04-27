package com.github.scm1219.video.gui.table;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.filechooser.FileSystemView;
import javax.swing.table.AbstractTableModel;

public class FileTableModel extends AbstractTableModel {

	private static final long serialVersionUID = 1L;
	private static final String[] TABLE_HEADER = { "名称", "修改日期", "类型", "大小", "路径" };

	private final FileSystemView fileSystemView = FileSystemView.getFileSystemView();
	private final List<File> files;
	private boolean showParentRow = false;

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
			return file.lastModified();
		case 2:
			return fileSystemView.getSystemTypeDescription(file);
		case 3:
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
		File file = files.get(actualRow);
		return file != null && file.exists();
	}
}
