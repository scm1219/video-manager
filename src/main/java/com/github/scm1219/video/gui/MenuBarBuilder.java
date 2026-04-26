package com.github.scm1219.video.gui;

import java.util.List;

import javax.swing.ButtonGroup;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.filechooser.FileSystemView;

import com.github.scm1219.video.domain.Disk;
import com.github.scm1219.video.domain.DiskManager;
import com.github.scm1219.video.gui.tree.IndexValidationProcesser;

/**
 * 菜单栏构建器 - 负责创建主窗口的菜单栏
 *
 * 从 FileExplorerWindow 中提取，将菜单栏创建逻辑与主窗口类解耦。
 */
public class MenuBarBuilder {

	private MenuBarBuilder() {
	}

	/**
	 * 主题切换回调接口
	 */
	public interface ThemeMenuCallback {
		/** 切换主题 */
		void switchTheme(String themeName);
		/** 获取当前主题名称 */
		String getCurrentTheme();
	}

	/**
	 * 索引验证回调接口
	 */
	public interface IndexValidationCallback {
		/** 执行索引验证和清理 */
		void validateAndCleanupIndex();
	}

	/**
	 * 构建完整的菜单栏
	 *
	 * @param parentFrame 父窗口（用于对话框定位）
	 * @param themeCallback 主题切换回调
	 * @param indexCallback 索引验证回调
	 * @param fileSystemView 文件系统视图（用于获取磁盘显示名称）
	 * @return 构建好的 JMenuBar
	 */
	public static JMenuBar buildMenuBar(
			JFrame parentFrame,
			ThemeMenuCallback themeCallback,
			IndexValidationCallback indexCallback,
			FileSystemView fileSystemView) {

		JMenuBar menuBar = new JMenuBar();

		// 创建主题菜单
		menuBar.add(buildThemeMenu(themeCallback));

		// 创建索引菜单
		menuBar.add(buildIndexMenu(parentFrame, indexCallback, fileSystemView));

		// 创建帮助菜单
		menuBar.add(buildHelpMenu(parentFrame));

		return menuBar;
	}

	/**
	 * 创建主题菜单
	 */
	private static JMenu buildThemeMenu(ThemeMenuCallback callback) {
		JMenu themeMenu = new JMenu("主题");
		ButtonGroup themeButtonGroup = new ButtonGroup();

		// 浅色主题
		JRadioButtonMenuItem lightItem = new JRadioButtonMenuItem("浅色主题");
		lightItem.addActionListener(e -> callback.switchTheme(ThemeManager.THEME_LIGHT));
		themeButtonGroup.add(lightItem);
		themeMenu.add(lightItem);

		// 深色主题
		JRadioButtonMenuItem darkItem = new JRadioButtonMenuItem("深色主题");
		darkItem.addActionListener(e -> callback.switchTheme(ThemeManager.THEME_DARK));
		themeButtonGroup.add(darkItem);
		themeMenu.add(darkItem);

		// 跟随系统
		JRadioButtonMenuItem autoItem = new JRadioButtonMenuItem("跟随系统");
		autoItem.addActionListener(e -> callback.switchTheme(ThemeManager.THEME_AUTO));
		themeButtonGroup.add(autoItem);
		themeMenu.add(autoItem);

		// 根据当前主题设置选中状态
		String currentTheme = callback.getCurrentTheme();
		updateThemeMenuSelection(currentTheme, lightItem, darkItem, autoItem);

		// 将菜单项保存到客户端对象中，以便后续更新选中状态
		themeMenu.putClientProperty("lightItem", lightItem);
		themeMenu.putClientProperty("darkItem", darkItem);
		themeMenu.putClientProperty("autoItem", autoItem);

		return themeMenu;
	}

	/**
	 * 更新主题菜单的选中状态
	 *
	 * @param themeMenu 包含客户端属性的主题菜单
	 * @param currentTheme 当前主题名称
	 */
	public static void updateThemeMenuSelection(JMenu themeMenu, String currentTheme) {
		JRadioButtonMenuItem lightItem = (JRadioButtonMenuItem) themeMenu.getClientProperty("lightItem");
		JRadioButtonMenuItem darkItem = (JRadioButtonMenuItem) themeMenu.getClientProperty("darkItem");
		JRadioButtonMenuItem autoItem = (JRadioButtonMenuItem) themeMenu.getClientProperty("autoItem");
		updateThemeMenuSelection(currentTheme, lightItem, darkItem, autoItem);
	}

	/**
	 * 更新主题菜单单选按钮的选中状态
	 */
	private static void updateThemeMenuSelection(String currentTheme,
			JRadioButtonMenuItem lightItem, JRadioButtonMenuItem darkItem, JRadioButtonMenuItem autoItem) {
		switch (currentTheme.toLowerCase()) {
			case ThemeManager.THEME_DARK:
				darkItem.setSelected(true);
				break;
			case ThemeManager.THEME_AUTO:
				autoItem.setSelected(true);
				break;
			case ThemeManager.THEME_LIGHT:
			default:
				lightItem.setSelected(true);
				break;
		}
	}

	/**
	 * 创建索引菜单
	 */
	private static JMenu buildIndexMenu(JFrame parentFrame, IndexValidationCallback callback,
			FileSystemView fileSystemView) {
		JMenu indexMenu = new JMenu("索引");

		// 验证并清理索引菜单项
		JMenuItem validateAndCleanupItem = new JMenuItem("验证并清理索引");
		validateAndCleanupItem.addActionListener(e -> callback.validateAndCleanupIndex());
		indexMenu.add(validateAndCleanupItem);

		return indexMenu;
	}

	/**
	 * 创建帮助菜单
	 */
	private static JMenu buildHelpMenu(JFrame parentFrame) {
		JMenu helpMenu = new JMenu("帮助");

		// 使用说明菜单项
		JMenuItem userManualItem = new JMenuItem("使用说明");
		userManualItem.addActionListener(e -> HelpDialog.showUserManual(parentFrame));
		helpMenu.add(userManualItem);

		// 关于菜单项
		JMenuItem aboutItem = new JMenuItem("关于");
		aboutItem.addActionListener(e -> HelpDialog.showAbout(parentFrame));
		helpMenu.add(aboutItem);

		return helpMenu;
	}

	/**
	 * 显示索引验证的磁盘选择对话框并执行验证
	 *
	 * @param parentFrame 父窗口
	 * @param fileSystemView 文件系统视图
	 */
	public static void showIndexValidationDialog(JFrame parentFrame, FileSystemView fileSystemView) {
		List<Disk> disks = DiskManager.getInstance().listDisk();
		if (disks.isEmpty()) {
			JOptionPane.showMessageDialog(parentFrame,
				"未发现需要索引的磁盘",
				"提示",
				JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		if (disks.size() == 1) {
			// 只有一个磁盘，显示警告并确认
			Disk disk = disks.get(0);
			String displayName = getDiskDisplayName(disk, fileSystemView);
			if (confirmIndexCheck(parentFrame, displayName) == JOptionPane.YES_OPTION) {
				performValidateAndCleanup(disk, parentFrame);
			}
		} else {
			// 多个磁盘，让用户选择
			Object[] options = new Object[disks.size()];
			for (int i = 0; i < disks.size(); i++) {
				Disk disk = disks.get(i);
				String displayName = getDiskDisplayName(disk, fileSystemView);
				options[i] = displayName + " (" + disk.getPath() + ")";
			}

			int selected = JOptionPane.showOptionDialog(parentFrame,
				"请选择要验证的磁盘:",
				"选择磁盘",
				JOptionPane.YES_NO_CANCEL_OPTION,
				JOptionPane.QUESTION_MESSAGE,
				null,
				options,
				options[0]);

			if (selected >= 0 && selected < disks.size()) {
				Disk selectedDisk = disks.get(selected);
				String selectedDisplayName = getDiskDisplayName(selectedDisk, fileSystemView);
				if (confirmIndexCheck(parentFrame, selectedDisplayName) == JOptionPane.YES_OPTION) {
					performValidateAndCleanup(selectedDisk, parentFrame);
				}
			}
		}
	}

	/**
	 * 执行索引验证和清理（含 UI 前置检查）
	 *
	 * @param disk 要验证的磁盘
	 * @param parentFrame 父窗口
	 */
	private static void performValidateAndCleanup(Disk disk, JFrame parentFrame) {
		// 检查索引是否存在
		if (!disk.hasIndex()) {
			JOptionPane.showMessageDialog(parentFrame,
					"该磁盘尚未创建索引\n请先执行整盘索引创建",
					"提示",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		// 检查是否正在索引
		if (disk.isIndexInProgress()) {
			JOptionPane.showMessageDialog(parentFrame,
					"索引正在创建中，请稍后再试",
					"提示",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		// 启动验证和清理进程
		new Thread(new Runnable() {
			@Override
			public void run() {
				new IndexValidationProcesser(disk).setVisible(true);
			}
		}).start();
	}

	/**
	 * 获取磁盘的显示名称
	 */
	private static String getDiskDisplayName(Disk disk, FileSystemView fileSystemView) {
		String displayName = fileSystemView.getSystemDisplayName(disk.getRoot());
		if (displayName == null || displayName.isEmpty()) {
			displayName = disk.getPath();
		}
		return displayName;
	}

	/**
	 * 确认索引清理操作
	 */
	private static int confirmIndexCheck(JFrame parentFrame, String displayName) {
		return JOptionPane.showConfirmDialog(parentFrame,
			"确定要验证并清理磁盘 \"" + displayName + "\" 的索引吗？\n\n" +
			"⚠️ 警告：此操作将执行以下操作：\n" +
			"   • 删除索引中指向已不存在文件的记录\n" +
			"   • 清理无效的索引数据\n" +
			"   • 无法恢复已删除的索引记录\n\n" +
			"建议：执行前请确保磁盘已正确连接。",
			"确认索引清理",
			JOptionPane.YES_NO_OPTION,
			JOptionPane.WARNING_MESSAGE);
	}
}
