package com.github.scm1219.video.domain;

import java.io.File;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.swing.filechooser.FileSystemView;

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

	private static final FileSystemView fileSystemView = FileSystemView.getFileSystemView();

	private File disk;
	private Index index;
	private boolean needIndex;

	/**
	 * 磁盘 UUID（从 disk_meta 表读取，首次索引时生成）
	 */
	private String uuid;

	/**
	 * 磁盘显示名称（从系统获取，如"我的视频硬盘"）
	 */
	private String displayName;

	public Disk(File f) {
		disk = f;
		File indexFile = new File(disk.getPath() + INDEX_FILE);
		index = new Index(indexFile);
		// 尝试从已有的索引文件中读取 UUID
		loadUuidFromIndex();
	}

	/**
	 * 从索引文件的 disk_meta 表中读取 UUID
	 */
	private void loadUuidFromIndex() {
		if (index.exists()) {
			try (Connection conn = index.getRepository().getConnection()) {
				index.getRepository().ensureSchema(conn);
				uuid = index.getRepository().getDiskUuid(conn);
			} catch (Exception e) {
				log.warn("读取磁盘 UUID 失败: {}", disk.getPath(), e);
			}
		}
	}

	/**
	 * 确保磁盘有 UUID（首次索引时调用）
	 *
	 * @return 当前 UUID
	 */
	public String ensureUuid() {
		if (uuid == null || uuid.isEmpty()) {
			uuid = UUID.randomUUID().toString();
			try (Connection conn = index.getRepository().getConnection()) {
				conn.setAutoCommit(true);
				index.getRepository().ensureSchema(conn);
				index.getRepository().setMeta(conn, "uuid", uuid);
				index.getRepository().setMeta(conn, "disk_name", getDisplayName());
				log.info("为新磁盘生成 UUID: {} → {}", getDisplayName(), uuid);
			} catch (Exception e) {
				log.error("写入磁盘 UUID 失败", e);
			}
		}
		return uuid;
	}

	public String getVolumeName() {
		return getPath().substring(0, 2);
	}

	/**
	 * 获取磁盘显示名称（系统卷标名）
	 *
	 * @return 系统显示名称，如"我的视频硬盘 (E:)"
	 */
	public String getDisplayName() {
		if (displayName == null) {
			displayName = fileSystemView.getSystemDisplayName(disk);
			if (displayName == null || displayName.isEmpty()) {
				displayName = getVolumeName();
			}
		}
		return displayName;
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

	public String getUuid() {
		return uuid;
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

	/**
	 * 将当前磁盘的索引同步到本地缓存
	 */
	public void syncToCache() {
		ensureUuid();
		IndexCacheManager.getInstance().syncToLocal(this);
	}
}
