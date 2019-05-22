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
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

import com.github.scm1219.video.domain.Disk;


public class FileUpdateProcesser extends JFrame {
	
	private static final long serialVersionUID = 1L;
	private JProgressBar progressBar = new JProgressBar();
	private JButton button = new JButton("开始");
	private JLabel label = new JLabel();
	private JTextArea textArea = new JTextArea();
	
	private static final int WINDOW_WITDH=400;
	private static final int WINDOW_HEIGHT=300;
	
	public FileUpdateProcesser(Disk disk) {
		setTitle("更新索引");
		textArea.setEditable(false);
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int left = (screen.width - WINDOW_WITDH) / 2;
		int top = (screen.height - WINDOW_HEIGHT) / 2;
		setBounds(left, top, WINDOW_WITDH, WINDOW_HEIGHT);
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		
		FileUpdateProcesser frame = this;
		label.setText("更新"+disk.getPath()+"下的索引");
		label.setLocation(SwingConstants.CENTER, getDefaultCloseOperation());
		
		//设置进度条的样式为不确定的进度条样式（进度条来回滚动），false为确定的进度条样式（即进度条从头到尾显示）
		progressBar.setIndeterminate(false);
		//设置进度条显示提示信息
		progressBar.setStringPainted(true);
		//设置提示信息
		progressBar.setString("准备更新目录"+disk.getPath());
		
		//给按钮添加事件监听器，点击按钮开始更新
		button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if(button.getText().equals("开始")) {
					// 使用线程更新
					new Progress(progressBar, button,disk).start();
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
	
	class Progress extends Thread{
		
		private JProgressBar bar;
		private JButton button;
		private Disk disk;
		
		public Progress(JProgressBar progressBar, JButton button,Disk disk) {
			this.bar = progressBar;
			this.button = button;
			this.disk = disk;
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
			disk.getIndex().create(disk,bar);
			long t2 = System.currentTimeMillis();
			bar.setString("索引创建完成，耗时："+(t2-t1)+"ms\n");
			textArea.setText(disk.getIndex().getInfoString());
			button.setText("关闭");
			button.setEnabled(true);
		}
		
	}
	
}
