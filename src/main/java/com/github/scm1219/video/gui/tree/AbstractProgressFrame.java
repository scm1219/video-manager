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

/**
 * 进度对话框基类，封装通用的布局、按钮状态机和取消确认逻辑
 */
public abstract class AbstractProgressFrame extends JFrame {

	private static final long serialVersionUID = 1L;
	private static final int WINDOW_WIDTH = 400;
	private static final int WINDOW_HEIGHT = 300;

	protected JProgressBar progressBar = new JProgressBar();
	protected JButton button;
	protected JLabel label = new JLabel();
	protected JTextArea textArea = new JTextArea();

	private final String startButtonText;
	private final String cancelConfirmMessage;
	private final String cancelProgressText;

	protected AbstractProgressFrame(String startButtonText, String cancelConfirmMessage, String cancelProgressText) {
		this.startButtonText = startButtonText;
		this.cancelConfirmMessage = cancelConfirmMessage;
		this.cancelProgressText = cancelProgressText;
		this.button = new JButton(startButtonText);
	}

	protected void initUI() {
		textArea.setEditable(false);
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		int left = (screen.width - WINDOW_WIDTH) / 2;
		int top = (screen.height - WINDOW_HEIGHT) / 2;
		setBounds(left, top, WINDOW_WIDTH, WINDOW_HEIGHT);
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		progressBar.setIndeterminate(false);
		progressBar.setStringPainted(true);

		AbstractProgressFrame frame = this;
		button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				String text = button.getText();
				if (text.equals(startButtonText)) {
					onStart();
				} else if (text.equals("关闭")) {
					frame.dispose();
				} else if (text.equals("取消")) {
					int confirm = javax.swing.JOptionPane.showConfirmDialog(frame,
							cancelConfirmMessage,
							"确认取消",
							javax.swing.JOptionPane.YES_NO_OPTION,
							javax.swing.JOptionPane.WARNING_MESSAGE);
					if (confirm == javax.swing.JOptionPane.YES_OPTION) {
						onCancelRequested();
						button.setEnabled(false);
						progressBar.setString(cancelProgressText);
					}
				}
			}
		});

		Box box = Box.createVerticalBox();
		JComponent[] all = { label, progressBar, textArea, button };
		for (JComponent comp : all) {
			Box tmp = Box.createHorizontalBox();
			tmp.add(comp);
			box.add(tmp);
		}
		add(box);
		setVisible(true);
	}

	/**
	 * 子类实现：点击"开始"按钮时启动工作线程
	 */
	protected abstract void onStart();

	/**
	 * 子类实现：用户确认取消后执行取消操作
	 */
	protected abstract void onCancelRequested();

	/**
	 * 将按钮切换到"取消"状态
	 */
	protected void setCancelButtonState() {
		button.setEnabled(true);
		button.setText("取消");
		progressBar.setStringPainted(true);
		progressBar.setIndeterminate(false);
	}

	/**
	 * 任务完成后将按钮切换到"关闭"状态
	 */
	protected void setCloseButtonState() {
		button.setText("关闭");
		button.setEnabled(true);
	}
}
