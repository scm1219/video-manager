package com.github.scm1219.video.gui;

import java.awt.Dimension;
import java.awt.Toolkit;

import javax.swing.JFrame;

import com.github.scm1219.video.gui.table.FileTable;


public class FileExplorerUI {

	public static void main(String[] args) {
		JFrame frame = new JFrame();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.getContentPane().add(new FileTable());
		frame.pack();
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();

		int left = (screen.width - frame.getWidth()) / 2;
		int top = (screen.height - frame.getHeight()) / 2;
		frame.setLocation(left, top);
		frame.setVisible(true);
	}
}
