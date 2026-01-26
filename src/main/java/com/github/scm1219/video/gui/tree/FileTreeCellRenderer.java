package com.github.scm1219.video.gui.tree;

import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.plaf.ColorUIResource;
import javax.swing.tree.DefaultTreeCellRenderer;

public class FileTreeCellRenderer extends DefaultTreeCellRenderer {
	private static final long serialVersionUID = 1L;

	// 颜色常量：避免重复创建Color对象，提高性能
	private static final Color COLOR_SELECTION = new Color(51, 153, 255);
	private static final Color COLOR_HOVER = Color.GRAY;
	private static final Color COLOR_INDEXED = new Color(0, 170, 0);
	private static final Color COLOR_UNINDEXED = new Color(153, 153, 153);

	public FileTreeCellRenderer() {
    }

	/**
	 * 根据状态设置标签背景
	 * @param label 目标标签
	 * @param isSelected 是否选中
	 * @param isHovered 是否悬停
	 */
	private void setBackgroundForState(JLabel label, boolean isSelected, boolean isHovered) {
		label.setOpaque(false);
		if (isSelected) {
			label.setOpaque(true);
			label.setBackground(COLOR_SELECTION);
		} else if (isHovered) {
			label.setOpaque(true);
			label.setBackground(COLOR_HOVER);
		}
	}

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        FileTree fileTree = (FileTree) tree;
        FileTreeNode fileNode = (FileTreeNode) value;

        String fileName = fileNode.getFileName();
        boolean isDiskRoot = fileNode.getFile().getParent() == null;
        Icon icon = fileNode.getFileIcon();

        // 分别判断悬停和选中状态
        boolean isHovered = (fileTree.mouseInPath != null &&
                fileTree.mouseInPath.getLastPathComponent().equals(value));
        boolean isSelected = selected;

        if (isDiskRoot) {
            // 磁盘根节点：使用 JPanel + 两个独立 JLabel 实现背景分离
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            panel.setOpaque(false);

            // 磁盘名称标签（可有背景）
            JLabel nameLabel = new JLabel(fileName, icon, JLabel.LEFT);
            setBackgroundForState(nameLabel, isSelected, isHovered);

            // 状态标签（始终透明背景）
            JLabel statusLabel = new JLabel();
            statusLabel.setOpaque(false);
            if (fileNode.isIndexed()) {
                statusLabel.setText(" [已索引]");
                statusLabel.setForeground(COLOR_INDEXED);
            } else {
                statusLabel.setText(" [未索引]");
                statusLabel.setForeground(COLOR_UNINDEXED);
            }

            panel.add(nameLabel);
            panel.add(statusLabel);

            return panel;
        } else {
            // 非磁盘根节点：保持原有行为
            JLabel label = (JLabel) super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            label.setText(fileName);
            label.setIcon(icon);
            setBackgroundForState(label, isSelected, isHovered);
            return label;
        }
    }

    @Override
    public void setBackground(Color bg) {
        if (bg instanceof ColorUIResource) {
            bg = null;
        }
        super.setBackground(bg);
    }
}
