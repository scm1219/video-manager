package com.github.scm1219.video;

import java.awt.Dimension;
import java.awt.Toolkit;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.github.scm1219.video.domain.DiskManager;
import com.github.scm1219.video.gui.FileExplorerWindow;
import com.github.scm1219.video.gui.ThemeManager;

public class VideoManagerApp {
	public static void main(String args[]){

        setLookAndFeel();
		// 单实例检查：防止应用多实例启动
		if (!AppLock.getInstance().acquire()) {
			showAlreadyRunningDialog();
			System.exit(1);
			return;
		}

		DiskManager m = DiskManager.getInstance();
		m.loadDisks();
		
        

        final JFrame frame=new FileExplorerWindow();
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int left = (screen.width - frame.getWidth()) / 2;
		int top = (screen.height - frame.getHeight()) / 2;
		frame.setLocation(left, top);
		try {
			frame.setIconImage(ImageIO.read(VideoManagerApp.class.getClassLoader().getResource("0.gif")));
		}catch (Exception e) {
			e.printStackTrace();
		}
		
		frame.setVisible(true);

		// 在窗口完全显示后，激活搜索框焦点
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				if (frame instanceof FileExplorerWindow) {
					((FileExplorerWindow) frame).focusSearchField();
				}
			}
		});

    }
    /**
     * 设置应用程序的外观主题
     * 使用 FlatLaf 提供现代化的 UI 外观
     * 主题配置从用户目录加载：~/.video-manager/theme.properties
     */
    public static void setLookAndFeel() {
        try {
            // 使用 ThemeManager 加载并应用主题
            ThemeManager.getInstance().applyTheme(ThemeManager.getInstance().getCurrentTheme());
        } catch (Exception e) {
            // 如果 FlatLaf 加载失败，回退到系统默认外观
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                System.err.println("FlatLaf 加载失败，已回退到系统外观: " + e.getMessage());
            } catch (Exception ex) {
                System.err.println("系统外观加载失败: " + ex.getMessage());
            }
        }
    }

    /**
     * 显示应用已在运行的错误对话框
     *
     * <p>当检测到已有应用实例运行时，调用此方法提示用户。</p>
     */
    private static void showAlreadyRunningDialog() {
        try {
            
            String message = "Video Manager 已经在运行中。\n\n" +
                            "请不要同时启动多个实例。\n" +
                            "如果确定没有其他实例运行，请检查并删除以下文件：\n" +
                            AppLock.getInstance().getLockFile().getAbsolutePath();

            JOptionPane.showMessageDialog(
                    null,
                    message,
                    "应用程序已在运行",
                    JOptionPane.ERROR_MESSAGE
            );
        } catch (Exception e) {
            // 如果对话框显示失败（如无图形界面），输出到控制台
            System.err.println("应用程序已在运行，无法启动新实例。");
            System.err.println("锁文件位置: " + AppLock.getInstance().getLockFile().getAbsolutePath());
        }
    }
}
