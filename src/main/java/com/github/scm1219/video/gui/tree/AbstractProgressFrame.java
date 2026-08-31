package com.github.scm1219.video.gui.tree;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

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
        // 关闭行为由 windowClosing 统一处理：任务运行中需先确认取消
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        progressBar.setIndeterminate(false);
        progressBar.setStringPainted(true);

        AbstractProgressFrame frame = this;
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String text = button.getText();
                if (text.equals(startButtonText)) {
                    // 在 EDT 上同步切换按钮状态，防止快速双击"开始"重复启动任务
                    setCancelButtonState();
                    onStart();
                } else if (text.equals("关闭")) {
                    frame.dispose();
                } else if (text.equals("取消")) {
                    if (confirmCancel()) {
                        onCancelRequested();
                        button.setEnabled(false);
                        progressBar.setString(cancelProgressText);
                    }
                }
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // 按钮处于"取消"态说明任务正在运行，关窗前先确认取消，
                // 避免窗口消失后工作线程无人监管地继续写数据库
                if (button.getText().equals("取消")) {
                    if (confirmCancel()) {
                        onCancelRequested();
                        frame.dispose();
                    }
                } else {
                    frame.dispose();
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
     * 弹出取消确认对话框
     *
     * @return true 表示用户确认取消
     */
    private boolean confirmCancel() {
        return javax.swing.JOptionPane.showConfirmDialog(this,
                cancelConfirmMessage,
                "确认取消",
                javax.swing.JOptionPane.YES_NO_OPTION,
                javax.swing.JOptionPane.WARNING_MESSAGE) == javax.swing.JOptionPane.YES_OPTION;
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
