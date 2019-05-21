package com.github.scm1219.video;

import java.io.File;
import java.util.List;

import com.github.scm1219.utils.FileUtils;
import com.github.scm1219.video.domain.Disk;
import com.github.scm1219.video.domain.DiskManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DiskTest {

	public static void main(String[] args) {
		DiskManager m = DiskManager.getInstance();
		m.loadDisks();
		List<Disk> listDisk = m.listDisk();
		for (Disk disk : listDisk) {
			log.info(disk.toString());
			
			List<File> listVideoDir = disk.listVideoDir();
			for (File dir : listVideoDir) {
				log.info(dir.toString());
			}
			disk.createIndex();
		}
		
		for (Disk disk : listDisk) {
			List<File> findFiles = disk.getIndex().findFiles("41");
			if(findFiles.size()>0) {
				log.info("找到匹配的视频文件");
				FileUtils.openDir(findFiles.get(0).getParentFile());
				try {
					Thread.sleep(1000);
				} catch (Exception e) {
				}
				FileUtils.openVideoFile(findFiles.get(0));
			}
		}
	}
}
