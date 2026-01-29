package com.github.scm1219.video.gui.table;

import java.awt.Dimension;

import javax.swing.JTable;
import javax.swing.table.TableModel;

public class FileTable extends JTable {
	private static final long serialVersionUID = 1L;
	private FileTableModel fileTableModel;

	public FileTable(){
        this.setDefaultRenderer(Object.class, new FileTableCellRenderer());
        this.setAutoCreateRowSorter(true);
        this.getTableHeader().setReorderingAllowed(false);
        this.setShowHorizontalLines(false);
        this.setShowVerticalLines(false);
        setIntercellSpacing(new Dimension(0,0)); //修改单元格间隔，因此也将影响网格线的粗细。
        setRowMargin(0);//设置相邻两行单元格的距离
	}

	@Override
    public void setModel(TableModel dataModel) {
        super.setModel(dataModel);
        if (dataModel instanceof FileTableModel) {
            fileTableModel = (FileTableModel) dataModel;
            // 为虚拟行设置固定高度（仅当表格有数据时）
            if (getRowCount() > 0) {
                setRowHeight(0, 30);
            }
        }
    }

	/**
	 * 获取 FileTableModel 实例
	 * @return FileTableModel 或 null
	 */
	public FileTableModel getFileTableModel() {
		return fileTableModel;
	}
}
