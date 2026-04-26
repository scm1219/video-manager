package com.github.scm1219.video.gui.tree;

import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

import javax.swing.JTree;
import javax.swing.SwingWorker;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.filechooser.FileSystemView;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileTree extends JTree {

	private static final long serialVersionUID = 1L;
	public TreePath mouseInPath;

	private final TreeContextMenu contextMenu;

	static protected FileSystemView fileSystemView = FileSystemView.getFileSystemView();

	public FileTree() {
		setRootVisible(false);

		contextMenu = new TreeContextMenu(this);

		addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				if (e.getButton() == MouseEvent.BUTTON3) {
					if (mouseInPath != null) {
						FileTreeNode fileTreeNode = (FileTreeNode) mouseInPath.getLastPathComponent();
						contextMenu.updateMenuState(fileTreeNode);
						contextMenu.getMenu().show(FileTree.this, e.getX(), e.getY());
					}
				}
			}
		});

		addTreeWillExpandListener(new TreeWillExpandListener() {
			@Override
			public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
				FileTreeNode fileNode = (FileTreeNode) event.getPath().getLastPathComponent();
				if (!fileNode.isInit()) {
					SwingWorker<File[], Void> worker = new SwingWorker<File[], Void>() {
						@Override
						protected File[] doInBackground() {
							if (fileNode.isDummyRoot()) {
								return fileSystemView.getRoots();
							} else {
								return fileSystemView.getFiles(fileNode.getFile(), false);
							}
						}

						@Override
						protected void done() {
							try {
								File[] files = get();
								for (File file : files) {
									if (file.isDirectory()) {
										FileTreeNode childFileNode = new FileTreeNode(file);
										fileNode.add(childFileNode);
									}
								}
								fileNode.setInit(true);
								((DefaultTreeModel) getModel()).nodeStructureChanged(fileNode);
							} catch (Exception ex) {
								log.error("加载文件节点失败", ex);
							}
						}
					};
					worker.execute();
				}
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
						Rectangle newRect = getPathBounds(path);
						mouseInPath = path;
						if (oldRect != null && newRect != null) {
							repaint(newRect.union(oldRect));
						} else if (oldRect != null) {
							repaint(oldRect);
						} else if (newRect != null) {
							repaint(newRect);
						}
					} else {
						mouseInPath = path;
					}
				} else if (mouseInPath != null) {
					Rectangle oldRect = getPathBounds(mouseInPath);
					mouseInPath = null;
					if (oldRect != null) {
						repaint(oldRect);
					}
				}
			}
		});
	}
}
