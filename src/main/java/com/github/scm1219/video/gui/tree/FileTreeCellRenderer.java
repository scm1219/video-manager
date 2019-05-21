package com.github.scm1219.video.gui.tree;

import java.awt.Color;
import java.awt.Component;

import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.plaf.ColorUIResource;
import javax.swing.tree.DefaultTreeCellRenderer;

public class FileTreeCellRenderer extends  DefaultTreeCellRenderer{
	private static final long serialVersionUID = 1L;

	public FileTreeCellRenderer(){
    }
    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        FileTree fileTree = (FileTree) tree;
        FileTreeNode fileNode = (FileTreeNode) value;
//        File file = (File) fileNode.getUserObject();

        JLabel label = (JLabel)super.getTreeCellRendererComponent(tree,value,selected,expanded,leaf,row,hasFocus);
        label.setText(fileNode.getFileName());

        label.setIcon(fileNode.getFileIcon());
        label.setOpaque(false);
        if (fileTree.mouseInPath != null &&
                fileTree.mouseInPath.getLastPathComponent().equals(value)) {
            label.setOpaque(true);
//            label.setBackground(new Color(255, 0, 0, 90));
            label.setBackground(Color.gray);
        }
        return label;
    }

    @Override
    public void setBackground(Color bg) {
        if (bg instanceof ColorUIResource){
            bg = null;
        }
        super.setBackground(bg);
    }
}
