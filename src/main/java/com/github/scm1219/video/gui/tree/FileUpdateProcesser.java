package com.github.scm1219.video.gui.tree;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

import com.github.scm1219.video.domain.Disk;
import com.github.scm1219.video.domain.Index.IndexStatistics;


public class FileUpdateProcesser extends JFrame {

	private static final long serialVersionUID = 1L;
	private JProgressBar progressBar = new JProgressBar();
	private JButton button = new JButton("开始");
	private JLabel label = new JLabel();
	private JTextArea textArea = new JTextArea();

	private static final int WINDOW_WITDH=400;
	private static final int WINDOW_HEIGHT=300;

	private File targetDirectory = null; // 目标目录，用于目录级索引
	private Disk disk; // 磁盘对象

	/**
	 * 构造函数 - 用于整盘索引
	 * @param disk 磁盘对象
	 */
	public FileUpdateProcesser(Disk disk) {
		this.disk = disk;
		this.targetDirectory = null;
		initUI();
		updateLabels();
	}

	/**
	 * 构造函数 - 用于目录级索引
	 * @param disk 磁盘对象
	 * @param directory 要扫描的目录
	 */
	public FileUpdateProcesser(Disk disk, File directory) {
		this.disk = disk;
		this.targetDirectory = directory;
		initUI();
		updateLabels();
	}

	/**
	 * 初始化UI组件（公共逻辑）
	 */
	private void initUI() {
		textArea.setEditable(false);
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		int left = (screen.width - WINDOW_WITDH) / 2;
		int top = (screen.height - WINDOW_HEIGHT) / 2;
		setBounds(left, top, WINDOW_WITDH, WINDOW_HEIGHT);
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		FileUpdateProcesser frame = this;

		label.setLocation(SwingConstants.CENTER, getDefaultCloseOperation());

		//设置进度条的样式为不确定的进度条样式（进度条来回滚动），false为确定的进度条样式（即进度条从头到尾显示）
		progressBar.setIndeterminate(false);
		//设置进度条显示提示信息
		progressBar.setStringPainted(true);

		//给按钮添加事件监听器，点击按钮开始更新
		button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if(button.getText().equals("开始")) {
					// 使用线程更新
					new Progress(progressBar, button, disk, targetDirectory).start();
				}else if(button.getText().equals("关闭")) {
					frame.dispose();
				}else if(button.getText().equals("取消")) {

				}
			}
		});

		Box box = Box.createVerticalBox();
		JComponent[] all= {label,progressBar,textArea,button};
		for (int i = 0; i < all.length; i++) {
			Box tmp = Box.createHorizontalBox();
			tmp.add(all[i]);
			box.add(tmp);
		}
		add(box);
		setVisible(true);
	}

	/**
	 * 更新窗口标题和标签文本（根据索引类型）
	 */
	private void updateLabels() {
		if(targetDirectory == null) {
			// 整盘索引
			setTitle("更新索引");
			label.setText("更新"+disk.getPath()+"下的索引");
			progressBar.setString("准备更新目录"+disk.getPath());
		} else {
			// 目录级索引
			setTitle("扫描目录");
			label.setText("扫描目录: "+targetDirectory.getAbsolutePath());
			progressBar.setString("准备扫描目录: "+targetDirectory.getName());
		}
	}

	class Progress extends Thread{

		private JProgressBar bar;
		private JButton button;
		private Disk disk;
		private File targetDirectory;

		public Progress(JProgressBar progressBar, JButton button,Disk disk) {
			this.bar = progressBar;
			this.button = button;
			this.disk = disk;
			this.targetDirectory = null;
		}

		public Progress(JProgressBar progressBar, JButton button, Disk disk, File directory) {
			this.bar = progressBar;
			this.button = button;
			this.disk = disk;
			this.targetDirectory = directory;
		}

		public void run() {
			textArea.setText("");
			//开始更新后禁用按钮
			button.setEnabled(false);
			button.setText("取消");
			bar.setStringPainted(true);
			//采用确定的进度条样式
			bar.setIndeterminate(false);
			long t1 = System.currentTimeMillis();

			// 根据是否有目标目录选择不同的索引方法
			if(targetDirectory == null) {
				// 整盘索引
				disk.getIndex().create(disk,bar);
			} else {
				// 目录级索引
				IndexStatistics stats = disk.getIndex().createForDirectory(targetDirectory, bar);
				long t2 = System.currentTimeMillis();
				bar.setString("扫描完成，耗时："+(t2-t1)+"ms\n");
				textArea.setText("目录: " + targetDirectory.getAbsolutePath() + "\n" +
							   "状态: 扫描完成\n" +
							   stats.toFormattedString() +
							   "总耗时: " + (t2-t1) + "ms");
			}

			long t2 = System.currentTimeMillis();

			if(targetDirectory == null) {
				bar.setString("索引创建完成，耗时："+(t2-t1)+"ms\n");
				textArea.setText(disk.getIndex().getInfoString());
			}

			button.setText("关闭");
			button.setEnabled(true);
		}

	}
	
}
