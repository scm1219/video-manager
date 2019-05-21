package com.github.scm1219.video.gui.tree;

import java.awt.Rectangle;
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

import com.github.scm1219.video.domain.Disk;
import com.github.scm1219.video.domain.DiskManager;

public class FileTree extends JTree {
	
	private static final long serialVersionUID = 1L;
	public TreePath mouseInPath;
	private JPopupMenu menu = new JPopupMenu();

	private void initPopMenu() {
		FileTree parent = this;

		JMenuItem mEchoIndexInfo, mCreateIndex;
		mEchoIndexInfo = new JMenuItem("查看索引信息");
		menu.add(mEchoIndexInfo);
		mCreateIndex = new JMenuItem("更新索引");
		menu.add(mCreateIndex);
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
				// TODO: 检查mouseInPath为啥会null
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
		
		mCreateIndex.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				FileTreeNode fileTreeNode =(FileTreeNode)mouseInPath.getLastPathComponent();
				File file = fileTreeNode.getFile();
				Disk disk = DiskManager.getInstance().findDisk(file);
//				boolean startIndex = false;
//				if(disk.getIndex().exists()) {
//					int result = JOptionPane.showConfirmDialog(null, "是否更新索引？");
//					if (JOptionPane.YES_OPTION == result) {
//						startIndex = true;
//					}
//				}else {
//					startIndex = true;
//				}
//				if(startIndex) {
					new Thread(new Runnable() {
						@Override
						public void run() {
//							long t1 = System.currentTimeMillis();
//							disk.createIndex();
//							long t2 = System.currentTimeMillis();
//							String data = disk.getIndex().getInfoString();
//							JOptionPane.showMessageDialog(null, "索引创建成功\n"+"耗时："+(t2-t1)+"毫秒\n"+data);
							FileUpdateProcesser pro = new FileUpdateProcesser(disk);
							pro.setVisible(true);
						}
					}).start();
//				}
				
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
