package com.github.scm1219.video.gui.tree;

import java.awt.TrayIcon.MessageType;
import java.io.File;
import java.io.IOException;

import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

import com.github.scm1219.utils.DiskUtils;
import com.github.scm1219.video.domain.Disk;
import com.github.scm1219.video.domain.DiskManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TreeContextMenu {

    private final JPopupMenu menu = new JPopupMenu();
    private final FileTree fileTree;

    public TreeContextMenu(FileTree fileTree) {
        this.fileTree = fileTree;
        initMenu();
    }

    public JPopupMenu getMenu() {
        return menu;
    }

    public void updateMenuState(FileTreeNode fileTreeNode) {
        File file = fileTreeNode.getFile();
        Disk disk = DiskManager.getInstance().findDisk(file);
        boolean isIndexed = disk != null;

        mEchoIndexInfo.setEnabled(isIndexed && disk.getIndex().exists());
        mCreateIndex.setEnabled(isIndexed);
        mCreateNeedIndexFile.setEnabled(!isIndexed);
        mShowSmart.setEnabled(true);
    }

    private JMenuItem mEchoIndexInfo;
    private JMenuItem mCreateIndex;
    private JMenuItem mShowSmart;
    private JMenuItem mCreateNeedIndexFile;

    private void initMenu() {
        mEchoIndexInfo = new JMenuItem("查看索引信息");
        menu.add(mEchoIndexInfo);
        mCreateIndex = new JMenuItem("更新索引");
        menu.add(mCreateIndex);
        mShowSmart = new JMenuItem("磁盘健康状况");
        menu.add(mShowSmart);
        mCreateNeedIndexFile = new JMenuItem("创建needindex文件");
        menu.add(mCreateNeedIndexFile);

        mEchoIndexInfo.addActionListener(e -> {
            if (fileTree.mouseInPath != null) {
                FileTreeNode fileTreeNode = (FileTreeNode) fileTree.mouseInPath.getLastPathComponent();
                File file = fileTreeNode.getFile();
                Disk disk = DiskManager.getInstance().findDisk(file);
                if (disk == null || !disk.getIndex().exists()) {
                    JOptionPane.showMessageDialog(fileTree, "未发现索引文件");
                } else {
                    String data = disk.getIndex().getInfoString();
                    JOptionPane.showMessageDialog(fileTree, "索引信息\n" + data);
                }
            }
        });

        mShowSmart.addActionListener(e -> {
            if (fileTree.mouseInPath != null) {
                FileTreeNode fileTreeNode = (FileTreeNode) fileTree.mouseInPath.getLastPathComponent();
                File file = fileTreeNode.getFile();
                Disk disk = DiskManager.getInstance().findDisk(file);
                if (disk == null) {
                    JOptionPane.showMessageDialog(fileTree, "无法找到磁盘");
                } else {
                    new Thread(() -> {
                        Object data = DiskUtils.getSmartInfo(disk);
                        SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(fileTree, data, "S.M.A.R.T检测", MessageType.INFO.ordinal())
                        );
                    }).start();
                }
            }
        });

        mCreateIndex.addActionListener(e -> {
            if (fileTree.mouseInPath != null) {
                FileTreeNode fileTreeNode = (FileTreeNode) fileTree.mouseInPath.getLastPathComponent();
                File file = fileTreeNode.getFile();
                Disk disk = DiskManager.getInstance().findDisk(file);
                if (disk == null) {
                    JOptionPane.showMessageDialog(fileTree, "该磁盘未启用索引功能");
                    return;
                }
                if (!disk.getIndex().isIndexing()) {
                    // 窗口构造与显示必须在 EDT 上执行，耗时索引在窗口内部的工作线程进行
                    SwingUtilities.invokeLater(() -> {
                        FileUpdateProcesser pro = new FileUpdateProcesser(disk);
                        pro.setVisible(true);
                    });
                } else {
                    JOptionPane.showMessageDialog(fileTree, "索引正在创建中，不能重复创建");
                }
            }
        });

        mCreateNeedIndexFile.addActionListener(e -> {
            if (fileTree.mouseInPath != null) {
                FileTreeNode fileTreeNode = (FileTreeNode) fileTree.mouseInPath.getLastPathComponent();
                File file = fileTreeNode.getFile();
                try {
                    File flagFile = new File(file.getPath() + Disk.FLAG_FILE);
                    if (flagFile.exists()) {
                        JOptionPane.showMessageDialog(fileTree, "needindex文件已存在");
                    } else {
                        flagFile.createNewFile();
                        JOptionPane.showMessageDialog(fileTree, "needindex文件创建成功");
                        DiskManager.getInstance().loadDisks();
                        Disk disk = DiskManager.getInstance().findDisk(file);
                        if (disk != null) {
                            disk.initEmptyDatabase();
                        }
                        fileTreeNode.setIndexed(true);
                        fileTree.repaint();
                    }
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(fileTree, "创建needindex文件失败: " + ex.getMessage());
                }
            }
        });
    }
}
