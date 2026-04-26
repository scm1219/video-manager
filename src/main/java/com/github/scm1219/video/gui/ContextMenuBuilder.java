package com.github.scm1219.video.gui;

import java.awt.TrayIcon.MessageType;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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

/**
 * 右键菜单构建器 - 负责创建文件表格的右键弹出菜单
 *
 * 从 FileExplorerWindow 中提取，将右键菜单构建逻辑与主窗口类解耦。
 */
public class ContextMenuBuilder {

	private ContextMenuBuilder() {
	}

	/**
	 * 导航回调接口 - 主窗口实现此接口提供导航相关功能
	 */
	public interface NavigationCallback {
		/** 导航到指定目录 */
		void updateTable(File file, Boolean isBack);
		/** 获取当前目录 */
		File getCurrentDir();
		/** 验证文件是否可以跳转 */
		boolean canNavigateToFile(File file);
		/** 获取导航栈 */
		Stack<File> getNavigationStack();
	}

	/**
	 * 构建右键弹出菜单
	 *
	 * @param parentFrame 父窗口（用于对话框定位）
	 * @param callback 导航回调
	 * @return 包含所有菜单项的 JPopupMenu 数组：[popupMenu, navigateToItem, scanDirectoryItem, renameToSimpleItem]
	 */
	public static MenuItems buildContextMenu(java.awt.Frame parentFrame, NavigationCallback callback) {
		JPopupMenu menu = new JPopupMenu();

		// 打开所在文件夹
		JMenuItem mEchoIndexInfo = new JMenuItem("打开所在文件夹");
		menu.add(mEchoIndexInfo);
		mEchoIndexInfo.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
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
			}
		});

		// 扫描此目录
		JMenuItem mScanDirectory = new JMenuItem("扫描此目录");
		menu.add(mScanDirectory);
		mScanDirectory.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				FileTable fileTable = (FileTable) menu.getInvoker();
				int row = fileTable.getSelectedRow();
				File file = (File) fileTable.getValueAt(row, 0);

				// 确定要扫描的目录
				File targetDir;
				if (file.isDirectory()) {
					targetDir = file;
				} else {
					targetDir = file.getParentFile();
				}

				// 查找磁盘
				Disk disk = DiskManager.getInstance().findDisk(targetDir);
				if (disk == null) {
					JOptionPane.showMessageDialog(null,
							"该磁盘未启用索引功能\n请在磁盘根目录创建 " + Disk.FLAG_FILE + " 文件",
							"提示",
							MessageType.INFO.ordinal());
					return;
				}

				// 检查是否正在索引
				if (disk.getIndex().isIndexing()) {
					JOptionPane.showMessageDialog(null,
							"索引正在创建中，请稍后",
							"提示",
							MessageType.INFO.ordinal());
					return;
				}

				// 启动目录扫描
				new Thread(new Runnable() {
					@Override
					public void run() {
						FileUpdateProcesser pro = new FileUpdateProcesser(disk, targetDir);
						pro.setVisible(true);
					}
				}).start();
			}
		});

		// 转到
		JMenuItem mNavigateTo = new JMenuItem("转到");
		menu.add(mNavigateTo);
		mNavigateTo.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				FileTable fileTable = (FileTable) menu.getInvoker();
				int row = fileTable.getSelectedRow();
				File file = (File) fileTable.getValueAt(row, 0);

				// 检测文件存在性
				if (callback.canNavigateToFile(file)) {
					File parentDir = file.getParentFile();
					Stack<File> navigationStack = callback.getNavigationStack();
					navigationStack.clear();  // 清空旧的历史记录

					// 重建完整的目录层级路径（从根目录到父目录）
					File current = parentDir;
					Stack<File> tempStack = new Stack<>();

					// 从父目录向上遍历到根目录
					while (current != null && current.exists()) {
						tempStack.push(current);
						current = current.getParentFile();
					}

					// 反向压入栈（从根到父目录）
					while (!tempStack.isEmpty()) {
						navigationStack.push(tempStack.pop());
					}

					// 使用 true 参数避免重复压入 parentDir
					callback.updateTable(parentDir, true);
				} else {
					JOptionPane.showMessageDialog(parentFrame,
						file == null || !file.exists() ? "文件不存在" : "父目录不存在或无法访问",
						"错误",
						JOptionPane.ERROR_MESSAGE);
				}
			}
		});

		// 文件夹名转简体
		JMenuItem mRenameToSimple = new JMenuItem("文件夹名转简体");
		menu.add(mRenameToSimple);
		mRenameToSimple.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
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
			}
		});

		return new MenuItems(menu, mNavigateTo, mScanDirectory, mRenameToSimple);
	}

	/**
	 * 右键菜单项容器 - 封装弹出菜单及其子菜单项的引用
	 */
	public static class MenuItems {
		/** 弹出菜单 */
		public final JPopupMenu popupMenu;
		/** "转到"菜单项 */
		public final JMenuItem navigateToItem;
		/** "扫描此目录"菜单项 */
		public final JMenuItem scanDirectoryItem;
		/** "文件夹名转简体"菜单项 */
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
