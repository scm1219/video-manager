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

        // 注意：此处不能用 disk.getIndex().exists() 之类磁盘 I/O 做菜单态，
        // 掉线的移动硬盘会卡住右键菜单；索引文件是否存在的检查放到点击后的后台线程里做
        mEchoIndexInfo.setEnabled(isIndexed);
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
                if (disk == null) {
                    JOptionPane.showMessageDialog(fileTree, "未发现索引文件");
                    return;
                }
                // 索引文件存在性检查与 SQLite 查询涉及磁盘 I/O，放到后台线程执行
                new Thread(() -> {
                    boolean exists = disk.getIndex().exists();
                    String data = exists ? disk.getIndex().getInfoString() : null;
                    SwingUtilities.invokeLater(() -> {
                        if (data == null) {
                            JOptionPane.showMessageDialog(fileTree, "未发现索引文件");
                        } else {
                            JOptionPane.showMessageDialog(fileTree, "索引信息\n" + data);
                        }
                    });
                }).start();
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
                    // 监听器本身在 EDT 上执行，耗时索引在窗口内部的工作线程进行
                    FileUpdateProcesser pro = new FileUpdateProcesser(disk);
                    pro.setVisible(true);
                } else {
                    JOptionPane.showMessageDialog(fileTree, "索引正在创建中，不能重复创建");
                }
            }
        });

        mCreateNeedIndexFile.addActionListener(e -> {
            if (fileTree.mouseInPath != null) {
                FileTreeNode fileTreeNode = (FileTreeNode) fileTree.mouseInPath.getLastPathComponent();
                File file = fileTreeNode.getFile();
                // 创建标记文件、重扫磁盘、初始化数据库均为磁盘 I/O，放到后台线程执行
                new Thread(() -> {
                    try {
                        File flagFile = new File(file.getPath() + Disk.FLAG_FILE);
                        if (flagFile.exists()) {
                            SwingUtilities.invokeLater(() ->
                                JOptionPane.showMessageDialog(fileTree, "needindex文件已存在"));
                            return;
                        }
                        flagFile.createNewFile();
                        DiskManager.getInstance().loadDisks();
                        Disk disk = DiskManager.getInstance().findDisk(file);
                        if (disk != null) {
                            disk.initEmptyDatabase();
                        }
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(fileTree, "needindex文件创建成功");
                            fileTreeNode.setIndexed(true);
                            fileTree.repaint();
                        });
                    } catch (IOException ex) {
                        SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(fileTree, "创建needindex文件失败: " + ex.getMessage()));
                    }
                }).start();
            }
        });
    }
}
