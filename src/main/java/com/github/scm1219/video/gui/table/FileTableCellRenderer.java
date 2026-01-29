package com.github.scm1219.video.gui.table;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.io.File;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.TableCellRenderer;

import com.formdev.flatlaf.FlatLaf;
import com.github.scm1219.utils.DateUtils;
import com.github.scm1219.utils.FileUtils;
import com.github.scm1219.video.gui.IconCache;

public class FileTableCellRenderer extends JLabel implements TableCellRenderer {
	private static final long serialVersionUID = 1L;
	FileSystemView fileSystemView = FileSystemView.getFileSystemView();

	// 浅色主题配色方案
	private static final Color PARENT_ROW_BG_LIGHT = new Color(220, 235, 255);
	private static final Color PARENT_ROW_FG_LIGHT = new Color(0, 51, 153);

	// 深色主题配色方案
	private static final Color PARENT_ROW_BG_DARK = new Color(60, 70, 90);
	private static final Color PARENT_ROW_FG_DARK = new Color(150, 180, 220);

	@Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        this.setFont(table.getFont());
        setOpaque(true);
        setEnabled(table.isEnabled());

        // 检查是否为虚拟的"返回上一级"行
        FileTableModel model = null;
        if (table.getModel() instanceof FileTableModel) {
            model = (FileTableModel) table.getModel();
        }

        boolean isParentRow = (model != null && model.isParentRow(row));

        // 设置背景色和前景色
        if (isSelected) {
            this.setBackground(table.getSelectionBackground());
            this.setForeground(table.getSelectionForeground());
        }
        else {
            if (isParentRow) {
                // 虚拟行根据主题使用不同的配色
                boolean isDark = FlatLaf.isLafDark();
                if (isDark) {
                    this.setBackground(PARENT_ROW_BG_DARK);
                    this.setForeground(PARENT_ROW_FG_DARK);
                } else {
                    this.setBackground(PARENT_ROW_BG_LIGHT);
                    this.setForeground(PARENT_ROW_FG_LIGHT);
                }
            } else {
                // 检测文件是否存在，不存在则显示红色
                if (model != null && !model.fileExists(row)) {
                    this.setBackground(table.getBackground());
                    this.setForeground(Color.RED);
                } else {
                    this.setBackground(table.getBackground());
                    this.setForeground(table.getForeground());
                }
            }
        }

        // 虚拟行使用粗体字体
        if (isParentRow) {
            Font originalFont = table.getFont();
            this.setFont(originalFont.deriveFont(Font.BOLD));
        } else {
            this.setFont(table.getFont());
        }

        if (column == 0)  {
            File file = (File) value;
            if (isParentRow) {
                // 虚拟行显示特殊文本和图标
                this.setText("↑ 返回上一级");
                // 使用文件夹图标或 null
                this.setIcon(IconCache.getSystemIcon(fileSystemView.getDefaultDirectory()));
            } else {
                this.setText(IconCache.getSystemDisplayName(file));
                this.setIcon(IconCache.getSystemIcon(file));
            }
        }
        else if (column == 1) {
            long datetime = (long)value;
            this.setText(DateUtils.getDateString(datetime));
            this.setIcon(null);
        } else if (column == 2) {
            String description = (String)value;
            this.setText(description);
            this.setIcon(null);
        } else if (column == 3) {
            long size = (long)value;
            String fileSize = FileUtils.formetFileSize(size);
            File file = (File)table.getValueAt(row,0);
            if (fileSystemView.isComputerNode(file) || fileSystemView.isDrive(file) || file.isDirectory()){
                this.setText(null);
            } else {
                this.setText(fileSize);
            }
            this.setIcon(null);
        } else if (column == 4) {
        	File file = (File) value;
            this.setText(file.getAbsolutePath());
            this.setIcon(null);
        }
        return this;
    }
}
