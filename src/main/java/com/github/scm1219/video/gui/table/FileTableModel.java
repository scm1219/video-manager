package com.github.scm1219.video.gui.table;

import java.io.File;

import javax.swing.filechooser.FileSystemView;
import javax.swing.table.DefaultTableModel;

public class FileTableModel extends DefaultTableModel{
	private static final long serialVersionUID = 1L;
	private static String[] tableHeader = {"名称", "修改日期", "类型", "大小","路径"};
    private FileSystemView fileSystemView = FileSystemView.getFileSystemView();
    private boolean showParentRow = false;
//    private boolean checkFileExists = false;

    public FileTableModel(Object[][] objects, boolean showParentRow){
        super(objects, tableHeader);
        this.showParentRow = showParentRow;
    }

    @Override
    public int getRowCount() {
        int count = super.getRowCount();
        return showParentRow ? count + 1 : count;
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }

    /**
     * 判断指定行是否为虚拟的"返回上一级"行
     * @param row 行索引
     * @return 如果是虚拟行返回 true
     */
    public boolean isParentRow(int row) {
        return showParentRow && row == 0;
    }

    /**
     * 设置是否显示"返回上一级"虚拟行
     * @param show true 显示，false 不显示
     */
    public void setShowParentRow(boolean show) {
        this.showParentRow = show;
        fireTableDataChanged();
    }


    @Override
    public Object getValueAt(int row, int column) {
        // 处理虚拟的"返回上一级"行
        if (isParentRow(row)) {
            return getParentRowValue(column);
        }

        // 调整真实文件的行索引（因为有虚拟行时真实行从 1 开始）
        int actualRow = showParentRow ? row - 1 : row;
        File file = (File) super.getValueAt(actualRow, column);

        if (column == 0){
            return file;
        } else if (column == 1){
            return file.lastModified();
        } else if (column == 2) {
            return fileSystemView.getSystemTypeDescription(file);
        } else if (column == 3){
            return file.length();
        }
        return super.getValueAt(actualRow, column);
    }

    /**
     * 获取虚拟行各列的显示值
     * @param column 列索引
     * @return 显示值
     */
    private Object getParentRowValue(int column) {
        if (column == 0) {
            return new File(".. 返回上一级");
        } else if (column == 1) {
            return System.currentTimeMillis();
        } else if (column == 2) {
            return "父目录";
        } else if (column == 3) {
            return 0L;
        } else if (column == 4) {
            return new File("");
        }
        return "";
    }

//    /**
//     * 设置是否检测文件存在性
//     * @param check true 检测，false 不检测
//     */
//    public void setCheckFileExists(boolean check) {
//        this.checkFileExists = check;
//    }

    /**
     * 检测指定行的文件是否存在
     * @param row 行索引
     * @return 文件存在返回 true，否则返回 false
     */
    public boolean fileExists(int row) {
        // 虚拟行视为存在
        if (isParentRow(row)) {
            return true;
        }

        // 计算实际行索引
        int actualRow = showParentRow ? row - 1 : row;
        if (actualRow < 0 || actualRow >= super.getRowCount()) {
            return false;
        }

        // 获取文件对象并检测存在性
        File file = (File) super.getValueAt(actualRow, 0);
        return file != null && file.exists();
    }
}
