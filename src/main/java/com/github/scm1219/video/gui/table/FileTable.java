package com.github.scm1219.video.gui.table;

import java.awt.Dimension;

import javax.swing.JTable;
import javax.swing.RowSorter;
import javax.swing.event.RowSorterEvent;
import javax.swing.table.TableModel;

public class FileTable extends JTable {
    private static final long serialVersionUID = 1L;
    /** 虚拟行（返回上一级）的行高 */
    private static final int PARENT_ROW_HEIGHT = 30;

    private FileTableModel fileTableModel;
    private final int defaultRowHeight;

    public FileTable() {
        this.setDefaultRenderer(Object.class, new FileTableCellRenderer());
        this.setAutoCreateRowSorter(true);
        this.getTableHeader().setReorderingAllowed(false);
        this.setShowHorizontalLines(false);
        this.setShowVerticalLines(false);
        setIntercellSpacing(new Dimension(0, 0)); // 修改单元格间隔，因此也将影响网格线的粗细。
        setRowMargin(0);// 设置相邻两行单元格的距离
        defaultRowHeight = getRowHeight();
    }

    @Override
    public void setModel(TableModel dataModel) {
        super.setModel(dataModel);
        if (dataModel instanceof FileTableModel) {
            fileTableModel = (FileTableModel) dataModel;
            syncParentRowHeight();
        }
    }

    @Override
    public void setRowSorter(RowSorter<? extends TableModel> sorter) {
        super.setRowSorter(sorter);
        if (sorter != null) {
            // 排序变化后虚拟行的视图位置会移动，需重新同步行高
            sorter.addRowSorterListener((RowSorterEvent e) -> syncParentRowHeight());
        }
        syncParentRowHeight();
    }

    /**
     * 将虚拟行的加高样式同步到它当前所在的视图行（排序后模型行 0 不一定显示在视图第 0 行）
     */
    private void syncParentRowHeight() {
        if (fileTableModel == null) {
            return;
        }
        setRowHeight(defaultRowHeight);
        if (fileTableModel.isParentRow(0)) {
            int viewRow = convertRowIndexToView(0);
            if (viewRow >= 0) {
                setRowHeight(viewRow, PARENT_ROW_HEIGHT);
            }
        }
    }

    /**
     * 获取 FileTableModel 实例
     *
     * @return FileTableModel 或 null
     */
    public FileTableModel getFileTableModel() {
        return fileTableModel;
    }
}
