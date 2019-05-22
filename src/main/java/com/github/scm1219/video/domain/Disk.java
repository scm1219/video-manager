package com.github.scm1219.video.domain;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JProgressBar;

import com.github.scm1219.utils.FileUtils;

import lombok.ToString;

@ToString
public class Disk {

	public static final String FLAF_FILE=".disk.needindex";
	public static final String INDEX_FILE=".disk.sqlite";
	
	private File disk;
	private Index index;
	private boolean needIndex;
	
	public Disk(File f) {
		disk = f;
		File indexFile = new File(disk.getPath()+INDEX_FILE);
		index = new Index(indexFile);
	}
	
	
	
	public File getRoot() {
		return disk;
	}
	
	public String getPath() {
		return disk.getPath();
	}
	
	public void createIndex() {
		index.create(this);
	}
	
	public Index getIndex() {
		return index;
	}
	
	
	public boolean needIndex() {
		File flagFile = new File(disk.getPath()+FLAF_FILE);
		return flagFile.exists();
	}
	
	
	public List<File> listVideoDir() {
		return listVideoDir(null);
	}
	
	private boolean findVideoDir(File parent,List<File> result,JProgressBar bar) {
		File[] subDirs = parent.listFiles();
		boolean currentVideo= hasVideoFiles(parent);
		if(bar!=null) {
			bar.setString("检查"+parent.getPath()+"是否需要扫描");
		}
		if(subDirs!=null) {
			for (File  subDir: subDirs) {
				if(subDir.isDirectory()) {
					//先遍历子目录是否包含视频
					boolean subHasVideo = findVideoDir(subDir, result,bar);
					if (subHasVideo) {
						currentVideo = true;
					}
				}
			}
		}
		if(currentVideo) {
			result.add(parent);
		}
		return currentVideo;
	}
	
	/**
	 * 判断当前文件夹下有无视频文件
	 * @param dir
	 * @return
	 */
	private boolean hasVideoFiles(File dir) {
		File[] listFiles = dir.listFiles();
		if(listFiles!=null) {
			for (File file : listFiles) {
				if (FileUtils.isVideoFile(file)) {
					return true;
				}
			}
		}
		return false;
	}



	public List<File> listVideoDir(JProgressBar bar) {
		File base = disk;
		List<File> result = new ArrayList<>();
		findVideoDir(base, result,bar);
		return result;
	}
}
