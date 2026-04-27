package com.github.scm1219.video.gui;

import java.awt.TrayIcon.MessageType;
import java.io.File;
import java.util.Stack;

import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;

import com.github.scm1219.video.domain.Disk;
import com.github.scm1219.video.domain.DiskManager;
import com.github.scm1219.video.gui.table.FileTable;
import com.github.scm1219.video.gui.tree.FileUpdateProcesser;
import com.github.scm1219.utils.FileUtils;

public class ContextMenuBuilder {

	private ContextMenuBuilder() {
	}

	public interface NavigationCallback {
		void updateTable(File file, Boolean isBack);
		File getCurrentDir();
		boolean canNavigateToFile(File file);
		Stack<File> getNavigationStack();
	}

	public static MenuItems buildContextMenu(java.awt.Frame parentFrame, NavigationCallback callback) {
		JPopupMenu menu = new JPopupMenu();

		JMenuItem mEchoIndexInfo = new JMenuItem("打开所在文件夹");
		menu.add(mEchoIndexInfo);
		mEchoIndexInfo.addActionListener(e -> {
			FileTable fileTable = (FileTable) menu.getInvoker();
			int row = fileTable.getSelectedRow();
			File file = (File) fileTable.getValueAt(row, 0);
			String filePath = file.getAbsolutePath();
			if (ClickDebouncer.shouldOpen(filePath)) {
				try {
					FileUtils.openDirAndSelectFile(file);
				} catch (Exception ex) {
					ClickDebouncer.recordError(filePath);
				}
			}
		});

		JMenuItem mScanDirectory = new JMenuItem("扫描此目录");
		menu.add(mScanDirectory);
		mScanDirectory.addActionListener(e -> {
			FileTable fileTable = (FileTable) menu.getInvoker();
			int row = fileTable.getSelectedRow();
			File file = (File) fileTable.getValueAt(row, 0);

			File targetDir;
			if (file.isDirectory()) {
				targetDir = file;
			} else {
				targetDir = file.getParentFile();
			}

			Disk disk = DiskManager.getInstance().findDisk(targetDir);
			if (disk == null) {
				JOptionPane.showMessageDialog(null,
						"该磁盘未启用索引功能\n请在磁盘根目录创建 " + Disk.FLAG_FILE + " 文件",
						"提示",
						MessageType.INFO.ordinal());
				return;
			}

			if (disk.getIndex().isIndexing()) {
				JOptionPane.showMessageDialog(null,
						"索引正在创建中，请稍后",
						"提示",
						MessageType.INFO.ordinal());
				return;
			}

			new Thread(() -> {
				FileUpdateProcesser pro = new FileUpdateProcesser(disk, targetDir);
				pro.setVisible(true);
			}).start();
		});

		JMenuItem mNavigateTo = new JMenuItem("转到");
		menu.add(mNavigateTo);
		mNavigateTo.addActionListener(e -> {
			FileTable fileTable = (FileTable) menu.getInvoker();
			int row = fileTable.getSelectedRow();
			File file = (File) fileTable.getValueAt(row, 0);

			if (callback.canNavigateToFile(file)) {
				File parentDir = file.getParentFile();
				Stack<File> navigationStack = callback.getNavigationStack();
				navigationStack.clear();

				File current = parentDir;
				Stack<File> tempStack = new Stack<>();

				while (current != null && current.exists()) {
					tempStack.push(current);
					current = current.getParentFile();
				}

				while (!tempStack.isEmpty()) {
					navigationStack.push(tempStack.pop());
				}

				callback.updateTable(parentDir, true);
			} else {
				JOptionPane.showMessageDialog(parentFrame,
					file == null || !file.exists() ? "文件不存在" : "父目录不存在或无法访问",
					"错误",
					JOptionPane.ERROR_MESSAGE);
			}
		});

		JMenuItem mRenameToSimple = new JMenuItem("文件夹名转简体");
		menu.add(mRenameToSimple);
		mRenameToSimple.addActionListener(e -> {
			FileTable fileTable = (FileTable) menu.getInvoker();
			int row = fileTable.getSelectedRow();
			File file = (File) fileTable.getValueAt(row, 0);

			if (!file.isDirectory()) return;

			String oldName = file.getName();
			String newName = com.github.houbb.opencc4j.util.ZhConverterUtil.toSimple(oldName);

			if (newName.equals(oldName)) {
				JOptionPane.showMessageDialog(parentFrame,
					"文件夹名已经是简体中文，无需转换",
					"提示", JOptionPane.INFORMATION_MESSAGE);
				return;
			}

			int confirm = JOptionPane.showConfirmDialog(parentFrame,
				"将文件夹重命名：\n\"" + oldName + "\"\n→ \"" + newName + "\"",
				"确认重命名", JOptionPane.YES_NO_OPTION);

			if (confirm != JOptionPane.YES_OPTION) return;

			File newFile = new File(file.getParentFile(), newName);
			if (file.renameTo(newFile)) {
				IconCache.clear();
				if (callback.getCurrentDir() != null) {
					callback.updateTable(callback.getCurrentDir(), true);
				}
			} else {
				JOptionPane.showMessageDialog(parentFrame,
					"重命名失败，可能文件正在被使用或权限不足",
					"错误", JOptionPane.ERROR_MESSAGE);
			}
		});

		return new MenuItems(menu, mNavigateTo, mScanDirectory, mRenameToSimple);
	}

	public static class MenuItems {
		public final JPopupMenu popupMenu;
		public final JMenuItem navigateToItem;
		public final JMenuItem scanDirectoryItem;
		public final JMenuItem renameToSimpleItem;

		public MenuItems(JPopupMenu popupMenu, JMenuItem navigateToItem,
				JMenuItem scanDirectoryItem, JMenuItem renameToSimpleItem) {
			this.popupMenu = popupMenu;
			this.navigateToItem = navigateToItem;
			this.scanDirectoryItem = scanDirectoryItem;
			this.renameToSimpleItem = renameToSimpleItem;
		}
	}
}
