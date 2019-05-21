package com.github.scm1219.video.gui.table;

import java.awt.Component;
import java.io.File;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.TableCellRenderer;

import com.github.scm1219.utils.DateUtils;
import com.github.scm1219.utils.FileUtils;

public class FileTableCellRenderer extends JLabel implements TableCellRenderer {
	private static final long serialVersionUID = 1L;
	FileSystemView fileSystemView = FileSystemView.getFileSystemView();
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        this.setFont(table.getFont());
        setOpaque(true);
        setEnabled(table.isEnabled());
        if (isSelected) {
            this.setBackground(table.getSelectionBackground());
            this.setForeground(table.getSelectionForeground());
//            fileTable.setShowHorizontalLines(true);
//            fileTable.setShowVerticalLines(true);
        }
        else {
            this.setBackground(table.getBackground());
            this.setForeground(table.getForeground());
//            fileTable.setShowHorizontalLines(false);
//            fileTable.setShowVerticalLines(false);
        }

        if (column == 0)  {
            File file = (File) value;
            this.setText(fileSystemView.getSystemDisplayName(file));
            this.setIcon(fileSystemView.getSystemIcon(file));
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
        	File file = (File)table.getValueAt(row,0);
            this.setText(file.getAbsolutePath());
            this.setIcon(null);
        }
        return this;
    }

    
}
