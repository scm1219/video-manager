package com.github.scm1219.video.gui.tree;

import java.awt.Rectangle;
import java.awt.TrayIcon.MessageType;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.filechooser.FileSystemView;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;

import com.github.scm1219.utils.DiskUtils;
import com.github.scm1219.video.domain.Disk;
import com.github.scm1219.video.domain.DiskManager;

public class FileTree extends JTree {
	
	private static final long serialVersionUID = 1L;
	public TreePath mouseInPath;
	private JPopupMenu menu = new JPopupMenu();

	private void initPopMenu() {
		FileTree parent = this;

		JMenuItem mEchoIndexInfo, mCreateIndex,mShowSmart;
		mEchoIndexInfo = new JMenuItem("查看索引信息");
		menu.add(mEchoIndexInfo);
		mCreateIndex = new JMenuItem("更新索引");
		menu.add(mCreateIndex);
		mShowSmart = new JMenuItem("磁盘健康状况");
		menu.add(mShowSmart);
		this.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				if (e.getButton() == MouseEvent.BUTTON3) {
					if(mouseInPath!=null) {
						menu.show(parent, e.getX(), e.getY());
					}
				}
			}
		});
		mEchoIndexInfo.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if(mouseInPath != null) {
					FileTreeNode fileTreeNode =(FileTreeNode)mouseInPath.getLastPathComponent();
					File file = fileTreeNode.getFile();
					Disk disk = DiskManager.getInstance().findDisk(file);
					if(disk==null || !disk.getIndex().exists()) {
						JOptionPane.showMessageDialog(null, "未发现索引文件");
					}else {
						String data = disk.getIndex().getInfoString();
						JOptionPane.showMessageDialog(null, "索引信息\n"+data);
					}
				}
				
			}
		});
		
		mShowSmart.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if(mouseInPath != null) {
					FileTreeNode fileTreeNode =(FileTreeNode)mouseInPath.getLastPathComponent();
					File file = fileTreeNode.getFile();
					Disk disk = DiskManager.getInstance().findDisk(file);
					if(disk==null) {
						JOptionPane.showMessageDialog(null, "无法找到磁盘");
					}else {
						new Thread(new Runnable() {
							@Override
							public void run() {
								Object data = DiskUtils.getSmartInfo(disk);
								JOptionPane.showMessageDialog(null, data,"S.M.A.R.T检测",MessageType.INFO.ordinal());
							}
						}).start();
						
					}
				}
			}
		});
		
		mCreateIndex.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if(mouseInPath!=null) {
					FileTreeNode fileTreeNode =(FileTreeNode)mouseInPath.getLastPathComponent();
					File file = fileTreeNode.getFile();
					Disk disk = DiskManager.getInstance().findDisk(file);
					if(!disk.getIndex().isIndexing()) {
						new Thread(new Runnable() {
							@Override
							public void run() {
								FileUpdateProcesser pro = new FileUpdateProcesser(disk);
								pro.setVisible(true);
							}
						}).start();
					}else {
						JOptionPane.showMessageDialog(null, "索引正在创建中，不能重复创建");
					}
				}
			}
		});

	}

	static protected FileSystemView fileSystemView = FileSystemView.getFileSystemView();

	public FileTree() {
		setRootVisible(false);
		initPopMenu();
		addTreeWillExpandListener(new TreeWillExpandListener() {
			@Override
			public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
				FileTreeNode fileNode = (FileTreeNode) event.getPath().getLastPathComponent();
				if (!fileNode.isInit()) {
					File[] files;
					if (fileNode.isDummyRoot()) {
						files = fileSystemView.getRoots();
					} else {
						files = fileSystemView.getFiles(fileNode.getFile(), false);
					}

					for (int i = 0; i < files.length; i++) {
						if (files[i].isDirectory()) {
							FileTreeNode childFileNode = new FileTreeNode(files[i]);
							fileNode.add(childFileNode);
						}
					}
				}
				fileNode.setInit(true);
			}

			@Override
			public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {

			}
		});

		addMouseMotionListener(new MouseAdapter() {
			@Override
			public void mouseMoved(MouseEvent e) {
				TreePath path = getPathForLocation(e.getX(), e.getY());
				if (path != null) {
					if (mouseInPath != null) {
						Rectangle oldRect = getPathBounds(mouseInPath);
						mouseInPath = path;
						repaint(getPathBounds(path).union(oldRect));
					} else {
						mouseInPath = path;
					}
				} else if (mouseInPath != null) {
					Rectangle oldRect = getPathBounds(mouseInPath);
					mouseInPath = null;
					repaint(oldRect);
				}
			}
		});
	}
}
