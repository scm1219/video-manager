package com.github.scm1219.video;

import java.awt.Dimension;
import java.awt.Toolkit;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.UIManager;

import com.github.scm1219.video.domain.DiskManager;
import com.github.scm1219.video.gui.FileExplorerWindow;
import com.github.scm1219.video.gui.ThemeManager;

public class VideoManagerApp {
	public static void main(String args[]){
		DiskManager m = DiskManager.getInstance();
		m.loadDisks();
		
        setLookAndFeel();

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
}
