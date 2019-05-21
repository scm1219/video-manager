package com.github.scm1219.video.gui.tree;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

import com.github.scm1219.video.domain.Disk;


public class FileUpdateProcesser extends JFrame {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private JProgressBar progressBar = new JProgressBar();
	private JButton button = new JButton("开始");
	private JLabel label = new JLabel();
	private JTextArea textArea = new JTextArea();
	
	private Box box = Box.createVerticalBox();
	private Box box1 = Box.createHorizontalBox();
	private Box box2 = Box.createHorizontalBox();
	private Box box3 = Box.createHorizontalBox();
	private Box box4 = Box.createHorizontalBox();
	
	public FileUpdateProcesser(Disk disk) {
		setTitle("更新索引");
		textArea.setEditable(false);
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int left = (screen.width - 400) / 2;
		int top = (screen.height - 300) / 2;
		setBounds(left, top, 400, 300);
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		
		FileUpdateProcesser frame = this;
		label.setText("更新索引");
		label.setLocation(SwingConstants.CENTER, getDefaultCloseOperation());
		
		progressBar.setIndeterminate(false);//设置进度条的样式为不确定的进度条样式（进度条来回滚动），false为确定的进度条样式（即进度条从头到尾显示）
		progressBar.setStringPainted(true);//设置进度条显示提示信息
		progressBar.setString("准备更新"+disk.getPath());//设置提示信息
		
		//给按钮添加事件监听器，点击按钮开始升级
		button.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent e) {
				if(button.getText().equals("开始")) {
					new Progress(progressBar, button,disk).start();// 利用线程模拟一个在线升级任务
				}else if(button.getText().equals("关闭")) {
					frame.dispose();
				}
			}
		});
		
		box1.add(label);
		box2.add(progressBar);
		box3.add(textArea);
		box4.add(button);
		box.add(box1);
		box.add(box2);
		box.add(box3);
		box.add(box4);
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
			
			button.setEnabled(false);
			bar.setStringPainted(true);
			bar.setIndeterminate(false);//采用确定的进度条样式
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
