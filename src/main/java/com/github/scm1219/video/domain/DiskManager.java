package com.github.scm1219.video.domain;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.filechooser.FileSystemView;

import com.github.houbb.opencc4j.util.ZhConverterUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DiskManager {

	private static DiskManager instance = new DiskManager();
	private static FileSystemView fileSystemView = FileSystemView.getFileSystemView();

	// 预热 OpenCC4j（首次调用有初始化开销，避免用户搜索时等待）
	static {
		Thread warmupThread = new Thread(() -> ZhConverterUtil.toSimple("test"));
		warmupThread.setDaemon(true);
		warmupThread.start();
	}

	public static DiskManager getInstance() {
		return instance;
	}

	private DiskManager() {
		try {
			Class.forName("org.sqlite.JDBC");
		} catch (ClassNotFoundException e) {
			log.error("SQLite JDBC 驱动加载失败", e);
		}
		log.info("sqlite jdbc 驱动加载完成");
	}

	private final List<Disk> disks = new ArrayList<>();
	private final Map<String, Disk> diskMap = new HashMap<>();

	public void loadDisks() {
		diskMap.clear();
		File[] f = File.listRoots();
		for (File file : f) {
			Disk disk = new Disk(file);
			if (disk.needIndex()) {
				if (!disks.contains(disk)) {
					disks.add(disk);
				}
				diskMap.put(disk.getPath().substring(0, 2).toUpperCase(), disk);
			} else {
				log.info("因未发现" + Disk.FLAG_FILE + "文件，忽略磁盘" + disk.getPath());
			}
		}
		Collections.sort(disks, (o1, o2) -> {
			String d1 = fileSystemView.getSystemDisplayName(o1.getRoot());
			String d2 = fileSystemView.getSystemDisplayName(o2.getRoot());
			return d1.compareTo(d2);
		});
	}

	public Disk findDisk(File file) {
		String path = file.getAbsolutePath();
		if (path.length() >= 2) {
			return diskMap.get(path.substring(0, 2).toUpperCase());
		}
		return null;
	}

	public List<File> searchAllFiles(String fileName) {
		List<File> allFiles = Collections.synchronizedList(new ArrayList<>());
		disks.parallelStream().forEach(disk -> {
			List<File> findFiles = disk.getIndex().findFiles(fileName);
			allFiles.addAll(findFiles);
		});
		return allFiles;
	}

	public List<File> searchAllDirs(String dirName) {
		List<File> allFiles = Collections.synchronizedList(new ArrayList<>());
		disks.parallelStream().forEach(disk -> {
			List<File> findFiles = disk.getIndex().findDirs(dirName);
			allFiles.addAll(findFiles);
		});
		return allFiles;
	}

	public List<Disk> listDisk() {
		return Collections.unmodifiableList(disks);
	}

	public void reloadDisks() {
		disks.clear();
		loadDisks();
	}
}
