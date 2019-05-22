package com.github.scm1219.video;

import java.awt.Dimension;
import java.awt.Toolkit;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.UIManager;

import com.github.scm1219.video.domain.DiskManager;
import com.github.scm1219.video.gui.FileExplorerWindow;

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
			frame.setIconImage(ImageIO.read(VideoManagerApp.class.getClassLoader().getResource("./0.gif")));
		}catch (Exception e) {
		}
		
		frame.setVisible(true);

    }
    public static void setLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
