package com.github.scm1219.video.domain;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.swing.filechooser.FileSystemView;

import com.github.houbb.opencc4j.util.ZhConverterUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DiskManager {
	
	private static DiskManager instance = new DiskManager();
	
	private static FileSystemView fileSystemView=FileSystemView.getFileSystemView();
	
	public static DiskManager getInstance() {
		//code warm up
		new Thread(new Runnable() {
			@Override
			public void run() {
				ZhConverterUtil.toSimple("test");
			}
		}).start();
		
		return instance;
	}
	private DiskManager() {
		try {
			Class.forName("org.sqlite.JDBC");
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		log.info("sqlite jdbc 驱动加载完成");
	}
	private List<Disk> disks = new ArrayList<>();
	
	public void loadDisks() {
		File[] f = File.listRoots();
		for (int i = 0; i < f.length; i++) {
			Disk disk = new Disk(f[i]);
			if(disk.needIndex()) {
				if (!disks.contains(disk)) {
					disks.add(disk);
				}
			}else {
				log.info("因未发现"+Disk.FLAF_FILE+"文件，忽略磁盘"+disk.getPath());
			}
		}

		Collections.sort(disks, new Comparator<Disk>() {
        	@Override
        	public int compare(Disk o1, Disk o2) {

        		String displayName1  =fileSystemView.getSystemDisplayName(o1.getRoot());
        		String dispalyName2 = fileSystemView.getSystemDisplayName(o2.getRoot());
        		return displayName1.compareTo(dispalyName2);
        	}
		});
	}
	
	public Disk findDisk(File file) {
		// 获取文件的绝对路径
		String filePath = file.getAbsolutePath();

		for (Disk disk : disks) {
			// 检查文件路径是否以该磁盘根路径开头
			String rootPath = disk.getRoot().getAbsolutePath();
			if (filePath.startsWith(rootPath)) {
				return disk;
			}
		}
		return null;
	}
	
	public List<File> searchAllFiles(String fileName) {
		List<File> allFiles = Collections.synchronizedList(new ArrayList<>());
		disks.parallelStream().forEach(disk -> {List<File> findFiles = disk.getIndex().findFiles(fileName);allFiles.addAll(findFiles);});
		return allFiles;
	}
	
	public List<File> searchAllDirs(String dirName) {
		List<File> allFiles = Collections.synchronizedList(new ArrayList<>());
		disks.parallelStream().forEach(disk -> {List<File> findFiles = disk.getIndex().findDirs(dirName);allFiles.addAll(findFiles);});
		return allFiles;
	}
	
	public List<Disk> listDisk(){
		
		return Collections.unmodifiableList(disks);
	}

	public void reloadDisks() {
		disks.clear();
		loadDisks();
	}

}
