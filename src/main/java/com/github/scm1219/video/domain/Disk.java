package com.github.scm1219.video.domain;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.github.scm1219.utils.FileUtils;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ToString
@EqualsAndHashCode
public class Disk {

	public static final String FLAG_FILE = ".disk.needindex";
	public static final String INDEX_FILE = ".disk.sqlite";

	private File disk;
	private Index index;
	private boolean needIndex;

	public Disk(File f) {
		disk = f;
		File indexFile = new File(disk.getPath() + INDEX_FILE);
		index = new Index(indexFile);
	}

	public String getVolumeName() {
		return getPath().substring(0, 2);
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
		File flagFile = new File(disk.getPath() + FLAG_FILE);
		return flagFile.exists();
	}

	public List<File> listVideoDir() {
		return listVideoDir(null);
	}

	private boolean findVideoDir(File parent, List<File> result, ProgressCallback callback, boolean isTop) {
		File[] files = parent.listFiles();
		boolean currentVideo = false;

		if (files != null) {
			for (File file : files) {
				// 检查当前目录是否包含视频文件
				if (!file.isDirectory() && FileUtils.isVideoFile(file)) {
					currentVideo = true;
				}

				if (file.isDirectory()) {
					// 检查是否取消（每个目录都检查）
					if (index.isCancelled()) {
						log.info("已经取消检查");
						throw new IndexCancelledException();
					}

					if (callback != null && isTop) {
						callback.update(0, "检查" + file.getAbsolutePath() + "是否需要扫描");
					}
					// 递归检查子目录
					if (findVideoDir(file, result, callback, false)) {
						currentVideo = true;
					}
				}
			}
		}
		if (currentVideo) {
			result.add(parent);
		}
		return currentVideo;
	}

	public List<File> listVideoDir(ProgressCallback callback) {
		File base = disk;
		List<File> result = new ArrayList<>();
		findVideoDir(base, result, callback, true);
		return result;
	}

	public void initEmptyDatabase() {
		index.initEmptyTables();
	}

	/**
	 * 检查索引是否存在
	 *
	 * @return true 如果索引文件存在
	 */
	public boolean hasIndex() {
		return getIndex().exists();
	}

	/**
	 * 检查索引是否正在创建中
	 *
	 * @return true 如果正在创建索引
	 */
	public boolean isIndexInProgress() {
		return getIndex().isIndexing();
	}
}
