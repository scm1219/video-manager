package com.github.scm1219.video.gui.tree;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import javax.swing.WindowConstants;

import com.github.scm1219.video.domain.Disk;
import com.github.scm1219.video.domain.Index.IndexStatistics;
import com.github.scm1219.video.domain.IndexCancelledException;

/**
 * 索引验证和清理进度窗口
 * <p>用于验证索引记录的有效性，并删除文件已不存在的无效记录</p>
 */
public class IndexValidationProcesser extends JFrame {

	private static final long serialVersionUID = 1L;
	private JProgressBar progressBar = new JProgressBar();
	private JButton button = new JButton("开始验证");
	private JLabel label = new JLabel();
	private JTextArea textArea = new JTextArea();

	private static final int WINDOW_WIDTH = 400;
	private static final int WINDOW_HEIGHT = 300;

	private Disk disk;

	/**
	 * 构造函数
	 * @param disk 要验证的磁盘对象
	 */
	public IndexValidationProcesser(Disk disk) {
		this.disk = disk;
		initUI();
		updateLabels();
	}

	/**
	 * 初始化UI组件
	 */
	private void initUI() {
		textArea.setEditable(false);
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		int left = (screen.width - WINDOW_WIDTH) / 2;
		int top = (screen.height - WINDOW_HEIGHT) / 2;
		setBounds(left, top, WINDOW_WIDTH, WINDOW_HEIGHT);
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		IndexValidationProcesser frame = this;

		// 设置进度条样式
		progressBar.setIndeterminate(false);
		progressBar.setStringPainted(true);

		// 按钮事件监听
		button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if(button.getText().equals("开始验证")) {
					// 启动验证线程
					new ValidationThread(progressBar, button, disk).start();
				} else if(button.getText().equals("关闭")) {
					frame.dispose();
				} else if(button.getText().equals("取消")) {
					// 二次确认
					int confirm = javax.swing.JOptionPane.showConfirmDialog(frame,
							"确定要取消验证吗？",
							"确认取消",
							javax.swing.JOptionPane.YES_NO_OPTION,
							javax.swing.JOptionPane.WARNING_MESSAGE);

					if (confirm == javax.swing.JOptionPane.YES_OPTION) {
						// 通知索引线程取消
						disk.getIndex().cancel(null);

						// 更新UI状态
						button.setEnabled(false);
						progressBar.setString("正在取消验证...");
					}
				}
			}
		});

		// 垂直布局
		Box box = Box.createVerticalBox();
		JComponent[] all = {label, progressBar, textArea, button};
		for (int i = 0; i < all.length; i++) {
			Box tmp = Box.createHorizontalBox();
			tmp.add(all[i]);
			box.add(tmp);
		}
		add(box);
		setVisible(true);
	}

	/**
	 * 更新窗口标题和标签文本
	 */
	private void updateLabels() {
		setTitle("验证索引");
		label.setText("验证磁盘: " + disk.getPath());
		progressBar.setString("准备验证索引...");
		textArea.setText("点击【开始验证】开始检查索引记录的有效性\n");
		textArea.append("该操作将删除文件已不存在的索引记录\n");
	}

	/**
	 * 验证线程
	 */
	class ValidationThread extends Thread {
		private JProgressBar bar;
		private JButton button;
		private Disk disk;

		public ValidationThread(JProgressBar bar, JButton button, Disk disk) {
			this.bar = bar;
			this.button = button;
			this.disk = disk;
		}

		@Override
		public void run() {
			textArea.setText("");
			// 开始验证后设置按钮为"取消"
			button.setEnabled(true);
			button.setText("取消");
			bar.setStringPainted(true);
			bar.setIndeterminate(false);

			long startTime = System.currentTimeMillis();

			try {
				// 执行验证和清理
				IndexStatistics stats = disk.getIndex().validateAndCleanup(bar);
				long endTime = System.currentTimeMillis();

				// 显示结果
				bar.setString("验证完成");
				StringBuilder result = new StringBuilder();
				result.append("验证完成！\n\n");
				result.append("索引记录总数: ").append(stats.getTotalCount()).append("\n");
				result.append("删除无效记录: ").append(stats.getDeletedCount()).append("\n");
				result.append("总耗时: ").append(endTime - startTime).append(" ms\n");

				if(stats.getDeletedCount() > 0) {
					result.append("\n已成功清理 ").append(stats.getDeletedCount()).append(" 条无效索引记录");
				} else {
					result.append("\n索引完整，无需清理");
				}

				textArea.setText(result.toString());
				button.setText("关闭");
				button.setEnabled(true);

			} catch (IndexCancelledException e) {
				// 用户取消验证
				bar.setString("验证已取消");
				textArea.setText("验证已取消");
				button.setText("关闭");
				button.setEnabled(true);

			} catch (Exception e) {
				// 其他异常
				bar.setString("验证失败");
				textArea.setText("错误: " + e.getMessage());
				button.setText("关闭");
				button.setEnabled(true);
				e.printStackTrace();
			}
		}
	}
}
