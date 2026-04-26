package com.github.scm1219.video.gui;

import java.awt.Desktop;
import java.awt.Dimension;
import java.io.InputStream;
import java.util.Properties;

import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.UIManager;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

/**
 * 帮助对话框工具类 - 提供使用说明和关于对话框
 */
public class HelpDialog {

	private HelpDialog() {
	}

	/**
	 * 获取应用程序版本号
	 * 从 version.properties 读取，如果失败则返回默认版本
	 * @return 版本号字符串
	 */
	public static String getAppVersion() {
		try {
			Properties props = new Properties();
			try (InputStream is = HelpDialog.class.getClassLoader()
					.getResourceAsStream("version.properties")) {
				if (is != null) {
					props.load(is);
					String version = props.getProperty("version");
					if (version != null && !version.isEmpty()) {
						return version;
					}
				}
			}
		} catch (Exception e) {
			// 读取失败，使用默认版本
		}
		return "1.1.0-RELEASE"; // 默认版本
	}

	/**
	 * 显示使用说明对话框
	 * @param parent 父窗口
	 */
	public static void showUserManual(JFrame parent) {
		// 获取当前主题的颜色
		String primaryColor = ThemeManager.getPrimaryColor();
		String textColor = ThemeManager.getTextColor();

		String helpHtml = String.format("""
			<html>
			<div style='padding: 0px; font-family: Microsoft YaHei, Arial;'>
			<h2 style='color: %s; margin: 5px 0;'>使用说明</h2>

			<h3 style='color: %s; margin: 0px 0;'>基本功能</h3>
			<li style='margin-top: 0px; margin: 1px 0;'><b>搜索文件</b>：在搜索框输入关键词，按回车或点击搜索按钮</li>
			<li style='margin-top: 0px; margin: 1px 0;'><b>实时搜索</b>：勾选"实时搜索"复选框，输入时自动搜索（700ms延迟）</li>
			<li style='margin-top: 0px; margin: 1px 0;'><b>搜索目录</b>：点击"搜索目录"按钮查找包含视频的文件夹</li>

			<h3 style='color: %s; margin: 0px 0;'>导航操作</h3>
			<li style='margin-top: 0px; margin: 1px 0;'><b>后退</b>：点击后退按钮返回上一级目录</li>
			<li style='margin-top: 0px; margin: 1px 0;'><b>双击</b>：双击文件夹进入，双击视频文件直接播放</li>
			<li style='margin-top: 0px; margin: 1px 0;'><b>右键菜单</b>：打开所在文件夹、扫描目录、转到</li>

			<h3 style='color: %s; margin: 0px 0;'>索引管理</h3>
			<li style='margin-top: 0px; margin: 1px 0;'><b>创建索引</b>：在磁盘根目录创建 <code>.disk.needindex</code> 文件</li>
			<li style='margin-top: 0px; margin: 1px 0;'><b>验证索引</b>：使用"索引 → 验证并清理索引"菜单</li>
			<li style='margin-top: 0px; margin: 1px 0;'><b>索引文件</b>：存储在磁盘根目录的 <code>.disk.sqlite</code></li>

			<h3 style='color: %s; margin: 0px 0;'>快捷键</h3>
			<li style='margin-top: 0px; margin: 1px 0;'><b>Ctrl + F</b>：聚焦搜索框并选中全部文本</li>
			<li style='margin-top: 0px; margin: 1px 0;'><b>Alt + W</b>：清空搜索内容</li>
			<li style='margin-top: 0px; margin: 1px 0;'><b>Enter</b>：执行搜索（搜索框焦点时）</li>

			<h3 style='color: %s; margin: 0px 0;'>主题切换</h3>
			<li style='margin-top: 0px; margin: 1px 0;'>使用"主题"菜单切换浅色/深色/跟随系统主题</li>

			</div>
			</html>
			""", primaryColor, textColor, textColor, textColor, textColor, textColor);

		// 创建 JEditorPane 并设置 HTML 内容
		JEditorPane editorPane = new JEditorPane("text/html", helpHtml);
		editorPane.setEditable(false);
		editorPane.setBackground(UIManager.getColor("Panel.background"));

		// 包装到 JScrollPane
		JScrollPane scrollPane = new JScrollPane(editorPane);
		scrollPane.setPreferredSize(new Dimension(600, 500));

		JOptionPane.showMessageDialog(parent,
			scrollPane,
			"使用说明",
			JOptionPane.INFORMATION_MESSAGE);
	}

	/**
	 * 显示关于对话框
	 * @param parent 父窗口
	 */
	public static void showAbout(JFrame parent) {
		String version = getAppVersion();
		// 获取当前主题的颜色
		String primaryColor = ThemeManager.getPrimaryColor();
		String textColor = ThemeManager.getTextColor();
		String secondaryTextColor = ThemeManager.getSecondaryTextColor();

		String aboutHtml = String.format("""
			<html>
			<div style='text-align: left; padding: 0px; font-family: Microsoft YaHei, Arial;'>
			<h2 style='color: %s; margin: 5px 0;'>视频文件管理器</h2>
            <p style='font-size: 10px; margin: 3px 0; color: %s;'>
			针对多移动硬盘的视频文件快速查找与管理工具
			</p>
			<p style='font-size: 10px; margin: 3px 0; color: %s;'>
			<b>版本:</b> %s
			</p>
            <p style='font-size: 10px; margin: 3px 0; color: %s;'>
			<b>作者:</b> scm1219
			</p>
			<p style='font-size: 10px; margin: 3px 0;'>
            <b>地址:</b>
			<a href='https://github.com/scm1219/video-manager'
			   style='color: %s; text-decoration: none;'>
			   https://github.com/scm1219/video-manager
			</a>
			</p>

			</div>
			</html>
			""", primaryColor, secondaryTextColor, textColor, version, textColor, primaryColor);

		// 创建 JEditorPane 并设置 HTML 内容
		JEditorPane editorPane = new JEditorPane("text/html", aboutHtml);
		editorPane.setEditable(false);
		editorPane.setBackground(UIManager.getColor("Panel.background"));

		// 添加超链接监听器（支持 GitHub 链接点击）
		editorPane.addHyperlinkListener(new HyperlinkListener() {
			@Override
			public void hyperlinkUpdate(HyperlinkEvent e) {
				if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
					try {
						Desktop.getDesktop().browse(e.getURL().toURI());
					} catch (Exception ex) {
						// 用户友好的错误提示
						JOptionPane.showMessageDialog(parent,
								"无法打开浏览器，请手动访问：\n" + e.getURL(),
								"错误",
								JOptionPane.ERROR_MESSAGE);
						ex.printStackTrace();
					}
				}
			}
		});

		JScrollPane scrollPane = new JScrollPane(editorPane);
		scrollPane.setPreferredSize(new Dimension(500, 300));

		JOptionPane.showMessageDialog(parent,
			scrollPane,
			"关于",
			JOptionPane.INFORMATION_MESSAGE);
	}
}
