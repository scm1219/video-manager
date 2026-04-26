package com.github.scm1219.video.gui.tree;

import java.awt.TrayIcon.MessageType;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;

import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;

import com.github.scm1219.utils.DiskUtils;
import com.github.scm1219.video.domain.Disk;
import com.github.scm1219.video.domain.DiskManager;

import lombok.extern.slf4j.Slf4j;

/**
 * 文件树右键上下文菜单
 *
 * 提供磁盘节点的右键操作：查看索引信息、更新索引、磁盘健康状况、创建 needindex 文件。
 */
@Slf4j
public class TreeContextMenu {

	private final JPopupMenu menu = new JPopupMenu();
	private final FileTree fileTree;

	public TreeContextMenu(FileTree fileTree) {
		this.fileTree = fileTree;
		initMenu();
	}

	/**
	 * 获取菜单组件
	 */
	public JPopupMenu getMenu() {
		return menu;
	}

	/**
	 * 根据节点状态更新菜单项的启用/禁用状态
	 */
	public void updateMenuState(FileTreeNode fileTreeNode) {
		File file = fileTreeNode.getFile();
		Disk disk = DiskManager.getInstance().findDisk(file);
		boolean isIndexed = fileTreeNode.isIndexed();

		mEchoIndexInfo.setEnabled(isIndexed && disk != null && disk.getIndex().exists());
		mCreateIndex.setEnabled(isIndexed && disk != null);
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

		// 查看索引信息
		mEchoIndexInfo.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (fileTree.mouseInPath != null) {
					FileTreeNode fileTreeNode = (FileTreeNode) fileTree.mouseInPath.getLastPathComponent();
					File file = fileTreeNode.getFile();
					Disk disk = DiskManager.getInstance().findDisk(file);
					if (disk == null || !disk.getIndex().exists()) {
						JOptionPane.showMessageDialog(null, "未发现索引文件");
					} else {
						String data = disk.getIndex().getInfoString();
						JOptionPane.showMessageDialog(null, "索引信息\n" + data);
					}
				}
			}
		});

		// 磁盘健康状况
		mShowSmart.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (fileTree.mouseInPath != null) {
					FileTreeNode fileTreeNode = (FileTreeNode) fileTree.mouseInPath.getLastPathComponent();
					File file = fileTreeNode.getFile();
					Disk disk = DiskManager.getInstance().findDisk(file);
					if (disk == null) {
						JOptionPane.showMessageDialog(null, "无法找到磁盘");
					} else {
						new Thread(new Runnable() {
							@Override
							public void run() {
								Object data = DiskUtils.getSmartInfo(disk);
								JOptionPane.showMessageDialog(null, data, "S.M.A.R.T检测", MessageType.INFO.ordinal());
							}
						}).start();
					}
				}
			}
		});

		// 更新索引
		mCreateIndex.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (fileTree.mouseInPath != null) {
					FileTreeNode fileTreeNode = (FileTreeNode) fileTree.mouseInPath.getLastPathComponent();
					File file = fileTreeNode.getFile();
					Disk disk = DiskManager.getInstance().findDisk(file);
					if (!disk.getIndex().isIndexing()) {
						new Thread(new Runnable() {
							@Override
							public void run() {
								FileUpdateProcesser pro = new FileUpdateProcesser(disk);
								pro.setVisible(true);
							}
						}).start();
					} else {
						JOptionPane.showMessageDialog(null, "索引正在创建中，不能重复创建");
					}
				}
			}
		});

		// 创建 needindex 文件
		mCreateNeedIndexFile.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (fileTree.mouseInPath != null) {
					FileTreeNode fileTreeNode = (FileTreeNode) fileTree.mouseInPath.getLastPathComponent();
					File file = fileTreeNode.getFile();
					try {
						File flagFile = new File(file.getPath() + Disk.FLAG_FILE);
						if (flagFile.exists()) {
							JOptionPane.showMessageDialog(null, "needindex文件已存在");
						} else {
							flagFile.createNewFile();
							JOptionPane.showMessageDialog(null, "needindex文件创建成功");
							DiskManager.getInstance().loadDisks();
							Disk disk = DiskManager.getInstance().findDisk(file);
							if (disk != null) {
								disk.initEmptyDatabase();
							}
							fileTreeNode.setIndexed(true);
							fileTree.repaint();
						}
					} catch (IOException ex) {
						JOptionPane.showMessageDialog(null, "创建needindex文件失败: " + ex.getMessage());
					}
				}
			}
		});
	}
}
