package com.github.scm1219.video.domain;

import com.github.scm1219.video.AppConfig;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.scm1219.utils.DateUtils;
import com.github.scm1219.utils.FileUtils;
import com.github.scm1219.utils.VideoFileFilter;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * 磁盘对应的索引
 *
 * @author scm12
 */
@Slf4j
@ToString
public class Index {

	private final File indexFile;

	private boolean exists = false;

	volatile boolean isIndexing = false;

	private final IndexRepository repository;

	private File backupFile;

	private volatile boolean isCancelled = false;

	private Connection activeConnection;

	private final AtomicInteger scannedFileCount = new AtomicInteger(0);

	@Getter
	@Setter
	public static class IndexStatistics {
		private int totalCount;
		private int addedCount;
		private int deletedCount;
		private long scanTime;

		public IndexStatistics() {
		}

		public IndexStatistics(int totalCount, int addedCount, int deletedCount, long scanTime) {
			this.totalCount = totalCount;
			this.addedCount = addedCount;
			this.deletedCount = deletedCount;
			this.scanTime = scanTime;
		}

		public String toFormattedString() {
			StringBuilder sb = new StringBuilder();
			sb.append("扫描文件总数: ").append(totalCount).append("\n");
			sb.append("新增文件数: ").append(addedCount).append("\n");
			sb.append("删除旧记录: ").append(deletedCount).append("\n");
			return sb.toString();
		}

		@Override
		public String toString() {
			return String.format("IndexStatistics{total=%d, added=%d, deleted=%d, time=%dms}",
					totalCount, addedCount, deletedCount, scanTime);
		}
	}

	public Index(File indexFile) {
		this.indexFile = indexFile;
		this.repository = new IndexRepository(indexFile);
		exists = indexFile.exists() && indexFile.length() > 0;

		if (exists) {
			Thread warmUpThread = new Thread(() -> repository.warmUp());
			warmUpThread.setDaemon(true);
			warmUpThread.start();
		}
	}

	private void createBackup() {
		try {
			if (indexFile.exists() && indexFile.length() > 0) {
				backupFile = new File(indexFile.getAbsolutePath() + ".bak");
				Files.copy(indexFile.toPath(), backupFile.toPath(),
						StandardCopyOption.REPLACE_EXISTING);
				log.info("已创建备份文件: {}", backupFile.getAbsolutePath());
			} else {
				backupFile = null;
				log.info("索引文件不存在或为空，跳过备份");
			}
		} catch (IOException e) {
			log.error("创建备份文件失败", e);
			backupFile = null;
		}
	}

	private void cleanupBackup() {
		if (backupFile != null && backupFile.exists()) {
			if (backupFile.delete()) {
				log.info("已删除备份文件: {}", backupFile.getAbsolutePath());
			} else {
				log.warn("无法删除备份文件: {}", backupFile.getAbsolutePath());
			}
		}
		backupFile = null;
	}

	private synchronized void handleCancel() {
		boolean rollbackSuccess = false;

		try {
			if (activeConnection != null && !activeConnection.isClosed() &&
					!activeConnection.getAutoCommit()) {
				activeConnection.rollback();
				activeConnection.setAutoCommit(true);
				rollbackSuccess = true;
				log.info("事务回滚成功");
			}
		} catch (SQLException e) {
			log.warn("事务回滚失败，尝试文件回滚: {}", e.getMessage());
		}

		if (!rollbackSuccess && backupFile != null && backupFile.exists()) {
			try {
				Files.copy(backupFile.toPath(), indexFile.toPath(),
						StandardCopyOption.REPLACE_EXISTING);
				rollbackSuccess = true;
				log.info("文件回滚成功");
			} catch (IOException e) {
				log.error("文件回滚失败", e);
			}
		}

		cleanupBackup();

		if (!rollbackSuccess) {
			log.error("回滚完全失败，数据库可能处于不一致状态，建议重新创建索引");
		}

		isIndexing = false;
		isCancelled = false;
	}

	private void checkCancelled() {
		if (isCancelled) {
			throw new IndexCancelledException();
		}
	}

	/**
	 * 带进度回调的整盘索引创建
	 */
	public void create(Disk disk, ProgressCallback callback) {
		if (callback != null) {
			callback.update(0, "开始收集要处理的文件夹");
		}
		log.info("对" + indexFile.getParent() + "创建缓存");

		try {
			createBackup();

			activeConnection = repository.getConnection();

			activeConnection.setAutoCommit(true);
			if (!indexFile.exists() || indexFile.length() < 1) {
				repository.ensureSchema(activeConnection);
			}

			activeConnection.setAutoCommit(false);

			try {
				repository.deleteAllFiles(activeConnection);
				checkCancelled();

				scannedFileCount.set(0);
				List<File> dirs = disk.listVideoDir(callback);
				checkCancelled();

				Collections.sort(dirs);
				int totalCount = dirs.size();
				int processedCount = 0;

				try (PreparedStatement pstmt = activeConnection.prepareStatement(IndexRepository.INSERT_SQL)) {
					int count = 0;
					for (File file : dirs) {
						checkCancelled();

						log.info(file.toString());
						processedCount++;
						int num = processedCount * 100 / totalCount;
						if (callback != null) {
							callback.update(num, "正在处理" + file.toString());
						}

						File[] videoFiles = file.listFiles(new VideoFileFilter());
						String dirName = file.getName();
						for (File videoFile : videoFiles) {
							pstmt.setString(1, IndexScanner.normalizeFileName(videoFile.getName()));
							pstmt.setString(2, IndexScanner.normalizeFileName(dirName));
							pstmt.setString(3, videoFile.getAbsolutePath()
									.substring(videoFile.getAbsolutePath().indexOf(":") + 1));
							pstmt.setString(4, videoFile.getParentFile().getAbsolutePath()
									.substring(videoFile.getAbsolutePath().indexOf(":") + 1));
							pstmt.addBatch();
							count++;
							if (count > AppConfig.INDEX_BATCH_SIZE) {
								pstmt.executeBatch();
								count = 0;
								checkCancelled();
							}
						}
					}
					if (count > 0) {
						pstmt.executeBatch();
					}
				}

				activeConnection.commit();
				activeConnection.setAutoCommit(true);

				log.info("整盘索引完成，共扫描 {} 个文件", scannedFileCount.get());

				if (callback != null) {
					callback.update(100, "处理结束");
				}

				cleanupBackup();

			} catch (IndexCancelledException e) {
				log.info("索引创建被用户取消");
				handleCancel();
				throw e;

			} catch (Exception e) {
				try {
					if (!activeConnection.getAutoCommit()) {
						activeConnection.rollback();
						activeConnection.setAutoCommit(true);
					}
				} catch (SQLException ex) {
					log.error("事务回滚失败", ex);
				}
				cleanupBackup();
				throw e;
			}

		} catch (IndexCancelledException e) {
			if (callback != null) {
				callback.update(0, "索引已取消");
			}
		} catch (Exception e) {
			log.error("创建索引失败", e);
			if (callback != null) {
				callback.update(0, "处理失败: " + e.getMessage());
			}
		} finally {
			if (activeConnection != null) {
				try {
					activeConnection.close();
				} catch (SQLException e) {
					log.error("关闭数据库连接失败", e);
				}
				activeConnection = null;
			}
		}
	}

	public synchronized boolean isIndexing() {
		return isIndexing;
	}

	public boolean isCancelled() {
		return isCancelled;
	}

	public void create(Disk disk) {
		if (isIndexing) {
			throw new RuntimeException("索引正在创建中，请稍后");
		}
		isIndexing = true;
		try {
			create(disk, null);
		} catch (Exception e) {
			log.error("创建索引出错: {}", e.getLocalizedMessage(), e);
		} finally {
			isIndexing = false;
		}
	}

	public void cancel(Disk disk) {
		isCancelled = true;
		log.info("用户请求取消索引创建");
	}

	public List<File> findFiles(String name) {
		name = IndexScanner.normalizeFileName(name);
		try {
			List<String> paths = repository.findFilePathsByName(name);
			String drive = repository.getCurrentDrive();
			List<File> results = new ArrayList<>();
			for (String path : paths) {
				results.add(new File(drive + ":" + path));
			}
			return results;
		} catch (Exception e) {
			log.error("搜索文件失败", e);
			return new ArrayList<>();
		}
	}

	public List<File> findDirs(String dirName) {
		dirName = IndexScanner.normalizeFileName(dirName);
		try {
			List<String> paths = repository.findDistinctDirPathsByName(dirName);
			String drive = repository.getCurrentDrive();
			List<File> results = new ArrayList<>();
			for (String path : paths) {
				results.add(new File(drive + ":" + path));
			}
			return results;
		} catch (Exception e) {
			log.error("搜索目录失败", e);
			return new ArrayList<>();
		}
	}

	public boolean exists() {
		return exists;
	}

	public void initEmptyTables() {
		try (Connection conn = repository.getConnection()) {
			conn.setAutoCommit(true);
			repository.createSchema(conn);
		} catch (Exception e) {
			log.error("初始化数据库表失败", e);
		}
	}

	public String getInfoString() {
		StringBuilder sb = new StringBuilder();
		sb.append("位置：" + indexFile.getParent());
		sb.append("\n");
		sb.append("文件名：" + indexFile.getName());
		sb.append("\n");
		sb.append("索引大小：" + FileUtils.formatFileSize(indexFile.length()));
		sb.append("\n");
		sb.append("修改时间：" + DateUtils.getDateString(indexFile.lastModified()));
		sb.append("\n");
		sb.append("包含记录条数：" + repository.getRecordCount());
		sb.append("\n");
		return sb.toString();
	}

	/**
	 * 为指定目录创建索引（先删除旧记录，再扫描并插入新记录）
	 */
	public IndexStatistics createForDirectory(File directory, ProgressCallback callback) {
		long startTime = System.currentTimeMillis();
		IndexStatistics stats = new IndexStatistics();
		if (callback != null) {
			callback.update(0, "开始扫描目录: " + directory.getName());
		}
		log.info("为目录{}创建索引", directory.getAbsolutePath());

		try {
			createBackup();

			try {
				activeConnection = repository.getConnection();
				activeConnection.setAutoCommit(true);
				repository.ensureSchema(activeConnection);
				activeConnection.setAutoCommit(false);
			} catch (Exception e) {
				log.error("获取数据库连接或设置事务失败", e);
				cleanupBackup();
				stats.setScanTime(System.currentTimeMillis() - startTime);
				throw new RuntimeException("创建目录索引失败: " + e.getMessage(), e);
			}

			try {
				String dirPath = directory.getAbsolutePath();
				if (dirPath.contains(":")) {
					dirPath = dirPath.substring(dirPath.indexOf(":") + 1);
				}
				if (!dirPath.startsWith("/") && !dirPath.startsWith("\\")) {
					dirPath = "/" + dirPath;
				}
				dirPath = dirPath.replace("\\", "/");

				if (callback != null) {
					callback.update(0, "删除旧索引记录...");
				}

				int deletedCount = repository.countFilesByDirPath(activeConnection, dirPath);
				log.debug("删除 \"{}\" 下的旧索引记录，共{}条", dirPath, deletedCount);
				stats.setDeletedCount(deletedCount);
				repository.deleteFilesByDirPath(activeConnection, dirPath);
				checkCancelled();

				if (callback != null) {
					callback.update(0, "扫描视频文件...");
				}

				scannedFileCount.set(0);
				List<File> videoFiles = new ArrayList<>();
				IndexScanner.collectVideoFiles(directory, videoFiles, callback, scannedFileCount,
						() -> checkCancelled());
				checkCancelled();

				log.info("扫描完成，共扫描 {} 个文件，找到 {} 个视频文件",
						scannedFileCount.get(), videoFiles.size());

				if (callback != null) {
					callback.update(50, "插入索引记录...");
				}

				try (PreparedStatement pstmt = activeConnection.prepareStatement(IndexRepository.INSERT_SQL)) {
					int count = 0;
					int totalFiles = videoFiles.size();
					int processedFiles = 0;

					for (File videoFile : videoFiles) {
						log.debug("处理文件: {}", videoFile.getAbsolutePath());

						String fileName = IndexScanner.normalizeFileName(videoFile.getName());
						File parentDir = videoFile.getParentFile();
						String dirName = IndexScanner.normalizeFileName(parentDir.getName());
						String filePath = videoFile.getAbsolutePath()
								.substring(videoFile.getAbsolutePath().indexOf(":") + 1);
						String fileDirPath = parentDir.getAbsolutePath()
								.substring(parentDir.getAbsolutePath().indexOf(":") + 1);
						filePath = filePath.replace("\\", "/");
						fileDirPath = fileDirPath.replace("\\", "/");
						pstmt.setString(1, fileName);
						pstmt.setString(2, dirName);
						pstmt.setString(3, filePath);
						pstmt.setString(4, fileDirPath);
						pstmt.addBatch();
						count++;

						processedFiles++;
						if (callback != null && totalFiles > 0) {
							int progress = 50 + (processedFiles * 50 / totalFiles);
							callback.update(progress, "正在处理 " + processedFiles + "/" + totalFiles);
						}

						if (count >= AppConfig.INDEX_BATCH_SIZE) {
							pstmt.executeBatch();
							count = 0;
							checkCancelled();
						}
					}

					if (count > 0) {
						pstmt.executeBatch();
					}
				}

				activeConnection.commit();
				activeConnection.setAutoCommit(true);

				if (callback != null) {
					callback.update(100, "扫描完成！共处理 " + videoFiles.size() + " 个视频文件");
				}

				stats.setTotalCount(videoFiles.size());
				stats.setAddedCount(videoFiles.size());
				stats.setScanTime(System.currentTimeMillis() - startTime);

				log.info("目录{}索引创建完成，共处理{}个视频文件", directory.getAbsolutePath(), videoFiles.size());
				cleanupBackup();

			} catch (IndexCancelledException e) {
				log.info("目录索引创建被用户取消");
				handleCancel();
				stats.setScanTime(System.currentTimeMillis() - startTime);
				throw e;

			} catch (Exception e) {
				try {
					if (!activeConnection.getAutoCommit()) {
						activeConnection.rollback();
						activeConnection.setAutoCommit(true);
					}
				} catch (SQLException ex) {
					log.error("事务回滚失败", ex);
				}
				cleanupBackup();
				stats.setScanTime(System.currentTimeMillis() - startTime);
				throw new RuntimeException("创建目录索引失败: " + e.getMessage(), e);
			}

		} catch (IndexCancelledException e) {
			if (callback != null) {
				callback.update(0, "索引已取消");
			}
			return stats;

		} finally {
			if (activeConnection != null) {
				try {
					activeConnection.close();
				} catch (SQLException e) {
					log.error("关闭数据库连接失败", e);
				}
				activeConnection = null;
			}
		}

		return stats;
	}

	/**
	 * 验证并清理索引中的无效记录（文件已不存在的记录）
	 */
	public IndexStatistics validateAndCleanup(ProgressCallback callback) {
		long startTime = System.currentTimeMillis();
		IndexStatistics stats = new IndexStatistics();

		if (callback != null) {
			callback.update(0, "开始验证索引...");
		}
		log.info("开始验证索引，检查无效记录");

		try {
			String currentDrive = repository.getCurrentDrive();

			try (Connection conn = repository.getConnection()) {
				conn.setAutoCommit(false);

				try {
					List<String> invalidPaths = new ArrayList<>();
					int totalRecords = 0;
					int checkedCount = 0;

					if (callback != null) {
						callback.update(0, "正在读取索引记录...");
					}

					List<String> allPaths = repository.getAllFilePaths(conn);
					totalRecords = allPaths.size();

					stats.setTotalCount(totalRecords);
					log.info("索引中共有 {} 条记录需要验证", totalRecords);

					if (totalRecords == 0) {
						if (callback != null) {
							callback.update(100, "索引为空，无需清理");
						}
						stats.setScanTime(System.currentTimeMillis() - startTime);
						return stats;
					}

					for (String filePath : allPaths) {
						String fullPath = currentDrive + ":" + filePath;
						File file = new File(fullPath);

						checkedCount++;

						if (callback != null && totalRecords > 0) {
							int progress = checkedCount * 100 / totalRecords;
							callback.update(progress, "验证中 " + checkedCount + "/" + totalRecords);
						}

						if (!file.exists()) {
							invalidPaths.add(filePath);
							log.debug("发现无效记录: {}", fullPath);
						}

						if (checkedCount % AppConfig.CANCEL_CHECK_INTERVAL == 0) {
							checkCancelled();
						}
					}

					if (!invalidPaths.isEmpty()) {
						if (callback != null) {
							callback.update(100, "删除 " + invalidPaths.size() + " 条无效记录...");
						}

						log.info("发现 {} 条无效记录，开始删除", invalidPaths.size());
						stats.setDeletedCount(invalidPaths.size());
						repository.deleteByFilePaths(conn, invalidPaths);
						conn.commit();
						log.info("成功删除 {} 条无效记录", invalidPaths.size());

						if (callback != null) {
							callback.update(100, "清理完成！删除了 " + invalidPaths.size() + " 条无效记录");
						}
					} else {
						log.info("未发现无效记录，索引完整");
						if (callback != null) {
							callback.update(100, "验证完成！索引完整，无需清理");
						}
					}

					conn.setAutoCommit(true);
					stats.setScanTime(System.currentTimeMillis() - startTime);

				} catch (IndexCancelledException e) {
					log.info("索引验证被用户取消");
					try {
						conn.rollback();
						conn.setAutoCommit(true);
					} catch (SQLException ex) {
						log.error("事务回滚失败", ex);
					}
					stats.setScanTime(System.currentTimeMillis() - startTime);
					throw e;
				} catch (Exception e) {
					try {
						if (!conn.getAutoCommit()) {
							conn.rollback();
							conn.setAutoCommit(true);
						}
					} catch (SQLException ex) {
						log.error("事务回滚失败", ex);
					}
					stats.setScanTime(System.currentTimeMillis() - startTime);
					throw new RuntimeException("验证索引失败: " + e.getMessage(), e);
				}
			}

		} catch (IndexCancelledException e) {
			if (callback != null) {
				callback.update(0, "验证已取消");
			}
			return stats;

		} catch (Exception e) {
			log.error("验证索引失败", e);
			if (callback != null) {
				callback.update(0, "验证失败: " + e.getMessage());
			}
			throw new RuntimeException("验证索引失败: " + e.getMessage(), e);
		}

		return stats;
	}

}
