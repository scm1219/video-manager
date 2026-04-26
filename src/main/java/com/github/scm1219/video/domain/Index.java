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
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * 磁盘对应的索引
 *
 * @author scm12
 *
 */
@Slf4j
@ToString
public class Index {

	private final File indexFile;

	private boolean exists = false;

	boolean isIndexing = false;

	/**
	 * 数据库操作仓库
	 */
	private final IndexRepository repository;

	/**
	 * 备份文件引用（用于取消索引时的回滚）
	 */
	private File backupFile;

	/**
	 * 取消标志位（volatile 保证多线程可见性）
	 */
	private volatile boolean isCancelled = false;

	/**
	 * 当前活跃的数据库连接（用于事务控制和取消操作）
	 */
	private Connection activeConnection;

	/**
	 * 实际扫描的文件计数器（用于取消检查频率计算）
	 */
	private final AtomicInteger scannedFileCount = new AtomicInteger(0);

	/**
	 * 索引统计信息
	 */
	public static class IndexStatistics {
		private int totalCount; // 扫描文件总数
		private int addedCount; // 新增文件数
		private int deletedCount; // 删除记录数
		private long scanTime; // 扫描耗时(ms)

		public IndexStatistics() {
		}

		public IndexStatistics(int totalCount, int addedCount, int deletedCount, long scanTime) {
			this.totalCount = totalCount;
			this.addedCount = addedCount;
			this.deletedCount = deletedCount;
			this.scanTime = scanTime;
		}

		public int getTotalCount() {
			return totalCount;
		}

		public void setTotalCount(int totalCount) {
			this.totalCount = totalCount;
		}

		public int getAddedCount() {
			return addedCount;
		}

		public void setAddedCount(int addedCount) {
			this.addedCount = addedCount;
		}

		public int getDeletedCount() {
			return deletedCount;
		}

		public void setDeletedCount(int deletedCount) {
			this.deletedCount = deletedCount;
		}

		public long getScanTime() {
			return scanTime;
		}

		public void setScanTime(long scanTime) {
			this.scanTime = scanTime;
		}

		/**
		 * 格式化输出用于UI显示
		 *
		 * @return 格式化的统计信息字符串
		 */
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

		// 数据库链接预初始化
		if (exists) {
			new Thread(() -> repository.warmUp()).start();
		}
	}

	/**
	 * 创建索引文件的备份
	 * <p>
	 * 在索引开始前调用，备份原索引文件以便取消时回滚
	 * </p>
	 * <p>
	 * 异常在方法内部处理，如果备份失败将记录日志并设置 backupFile 为 null
	 * </p>
	 */
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

	/**
	 * 清理备份文件
	 * <p>
	 * 在索引正常结束或回滚完成后调用
	 * </p>
	 */
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

	/**
	 * 处理索引取消操作（双重保障回滚）
	 * <p>
	 * 先尝试 SQLite 事务回滚，失败时使用备份文件回滚
	 * </p>
	 */
	private synchronized void handleCancel() {
		boolean rollbackSuccess = false;

		// 尝试1: SQLite 事务回滚（快速）
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

		// 尝试2: 文件备份回滚（兜底）
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

		// 清理资源
		cleanupBackup();

		if (!rollbackSuccess) {
			log.error("回滚完全失败，数据库可能处于不一致状态，建议重新创建索引");
		}

		isIndexing = false;
		isCancelled = false;
	}

	/**
	 * 检查是否已取消，如取消则抛出异常
	 *
	 * @throws IndexCancelledException 用户已取消索引创建
	 */
	private void checkCancelled() {
		if (isCancelled) {
			throw new IndexCancelledException();
		}
	}

	/**
	 * 带进度回调的处理
	 *
	 * @param disk     磁盘
	 * @param callback 进度回调（可为null）
	 */
	public void create(Disk disk, ProgressCallback callback) {
		if (callback != null) {
			callback.update(0, "开始收集要处理的文件夹");
		}
		log.info("对" + indexFile.getParent() + "创建缓存");

		try {
			// 步骤1：创建备份
			createBackup();

			// 步骤2：获取数据库连接（提升为字段以便事务控制）
			activeConnection = repository.getConnection();

			// 步骤3：DDL 操作（在自动提交模式下，使用幂等 SQL）
			activeConnection.setAutoCommit(true);
			if (!indexFile.exists() || indexFile.length() < 1) {
				repository.ensureSchema(activeConnection);
			}

			// 步骤4：开始事务（针对 DML 操作）
			activeConnection.setAutoCommit(false);

			try {
				// 删除旧数据
				repository.deleteAllFiles(activeConnection);

				// 检查取消
				checkCancelled();

				// 重置扫描计数器
				scannedFileCount.set(0);

				List<File> dirs = disk.listVideoDir(callback);

				// 收集完目录后再次检查取消
				checkCancelled();

				Collections.sort(dirs);
				int totalCount = dirs.size();
				int processedCount = 0;

				try (PreparedStatement pstmt = activeConnection.prepareStatement(IndexRepository.INSERT_SQL)) {
					int count = 0;
					for (File file : dirs) {
						// 每个目录检查取消
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
								// 每100条记录检查取消
								checkCancelled();
							}
						}
					}
					if (count > 0) {
						pstmt.executeBatch();
					}
				}

				// 步骤5：提交事务
				activeConnection.commit();
				activeConnection.setAutoCommit(true);

				log.info("整盘索引完成，共扫描 {} 个文件", scannedFileCount.get());

				if (callback != null) {
					callback.update(100, "处理结束");
				}

				// 步骤6：清理备份
				cleanupBackup();

			} catch (IndexCancelledException e) {
				// 用户取消
				log.info("索引创建被用户取消");
				handleCancel();
				throw e;

			} catch (Exception e) {
				// 其他异常：回滚事务
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
			// 取消异常已在上面处理，这里仅记录日志
			if (callback != null) {
				callback.update(0, "索引已取消");
			}
		} catch (Exception e) {
			log.error("创建索引失败", e);
			if (callback != null) {
				callback.update(0, "处理失败: " + e.getMessage());
			}
		} finally {
			// 确保连接关闭
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

	/**
	 * 检查是否已请求取消
	 * <p>
	 * 供外部类（如 Disk）在耗时操作中检查取消状态
	 * </p>
	 *
	 * @return true 如果已请求取消，false 否则
	 */
	public boolean isCancelled() {
		return isCancelled;
	}

	public void create(Disk disk) {
		if (isIndexing) {
			throw new RuntimeException("索引正在创建中，请稍后");
		} else {
			isIndexing = true;
			try {
				create(disk, null);
			} catch (Exception e) {
				log.error("创建索引出错: {}", e.getLocalizedMessage(), e);
			}
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
	 *
	 * @param directory 要扫描的目录
	 * @param callback  进度回调（可为null）
	 * @return 索引统计信息
	 */
	public IndexStatistics createForDirectory(File directory, ProgressCallback callback) {
		long startTime = System.currentTimeMillis();
		IndexStatistics stats = new IndexStatistics();
		if (callback != null) {
			callback.update(0, "开始扫描目录: " + directory.getName());
		}
		log.info("为目录{}创建索引", directory.getAbsolutePath());

		try {
			// 步骤1：创建备份
			createBackup();

			// 步骤2：获取数据库连接
			try {
				activeConnection = repository.getConnection();

				// 步骤3：DDL 操作（确保表和索引存在，使用幂等 SQL）
				activeConnection.setAutoCommit(true);
				repository.ensureSchema(activeConnection);

				// 步骤4：开始事务
				activeConnection.setAutoCommit(false);
			} catch (Exception e) {
				log.error("获取数据库连接或设置事务失败", e);
				cleanupBackup();
				stats.setScanTime(System.currentTimeMillis() - startTime);
				throw new RuntimeException("创建目录索引失败: " + e.getMessage(), e);
			}

			try {
				// 获取目录路径（去掉盘符，统一格式）
				String dirPath = directory.getAbsolutePath();
				if (dirPath.contains(":")) {
					dirPath = dirPath.substring(dirPath.indexOf(":") + 1);
				}
				// 确保路径以/开头
				if (!dirPath.startsWith("/") && !dirPath.startsWith("\\")) {
					dirPath = "/" + dirPath;
				}
				// 统一使用/作为路径分隔符
				dirPath = dirPath.replace("\\", "/");

				if (callback != null) {
					callback.update(0, "删除旧索引记录...");
				}

				// 统计删除前的记录数
				int deletedCount = repository.countFilesByDirPath(activeConnection, dirPath);
				log.debug("删除 \"{}\" 下的旧索引记录，共{}条", dirPath, deletedCount);
				stats.setDeletedCount(deletedCount);

				// 删除该目录下的所有旧记录
				repository.deleteFilesByDirPath(activeConnection, dirPath);

				// 检查取消
				checkCancelled();

				if (callback != null) {
					callback.update(0, "扫描视频文件...");
				}

				// 重置扫描计数器
				scannedFileCount.set(0);

				// 收集所有视频文件
				List<File> videoFiles = new ArrayList<>();
				IndexScanner.collectVideoFiles(directory, videoFiles, callback, scannedFileCount,
						() -> checkCancelled());

				// 收集完成后立即检查取消
				checkCancelled();

				log.info("扫描完成，共扫描 {} 个文件，找到 {} 个视频文件",
						scannedFileCount.get(), videoFiles.size());

				if (callback != null) {
					callback.update(50, "插入索引记录...");
				}

				// 批量插入视频文件记录
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
						// 统一路径分隔符
						filePath = filePath.replace("\\", "/");
						fileDirPath = fileDirPath.replace("\\", "/");
						pstmt.setString(1, fileName);
						pstmt.setString(2, dirName);
						pstmt.setString(3, filePath);
						pstmt.setString(4, fileDirPath);
						pstmt.addBatch();
						count++;

						// 更新进度
						processedFiles++;
						if (callback != null && totalFiles > 0) {
							int progress = 50 + (processedFiles * 50 / totalFiles);
							callback.update(progress, "正在处理 " + processedFiles + "/" + totalFiles);
						}

						// 每100条执行一次批量插入
						if (count >= AppConfig.INDEX_BATCH_SIZE) {
							pstmt.executeBatch();
							count = 0;
							// 每100条记录检查取消
							checkCancelled();
						}
					}

					// 插入剩余记录
					if (count > 0) {
						pstmt.executeBatch();
					}
				}

				// 提交事务
				activeConnection.commit();
				activeConnection.setAutoCommit(true);

				if (callback != null) {
					callback.update(100, "扫描完成！共处理 " + videoFiles.size() + " 个视频文件");
				}

				// 设置统计信息
				stats.setTotalCount(videoFiles.size());
				stats.setAddedCount(videoFiles.size());
				stats.setScanTime(System.currentTimeMillis() - startTime);

				log.info("目录{}索引创建完成，共处理{}个视频文件", directory.getAbsolutePath(), videoFiles.size());

				// 清理备份
				cleanupBackup();

			} catch (IndexCancelledException e) {
				// 用户取消
				log.info("目录索引创建被用户取消");
				handleCancel();
				stats.setScanTime(System.currentTimeMillis() - startTime);
				throw e;

			} catch (Exception e) {
				// 其他异常：回滚事务
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
			// 取消异常已在上面处理
			if (callback != null) {
				callback.update(0, "索引已取消");
			}
			return stats;

		} finally {
			// 确保连接关闭
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
	 * <p>
	 * 此方法会遍历索引中的所有记录，检查对应的文件是否真实存在，
	 * 删除那些文件已被移除或删除的索引记录。
	 * </p>
	 *
	 * @param callback 进度回调（可为null）
	 * @return 清理统计信息
	 */
	public IndexStatistics validateAndCleanup(ProgressCallback callback) {
		long startTime = System.currentTimeMillis();
		IndexStatistics stats = new IndexStatistics();

		if (callback != null) {
			callback.update(0, "开始验证索引...");
		}
		log.info("开始验证索引，检查无效记录");

		try {
			// 获取当前盘符
			String currentDrive = repository.getCurrentDrive();

			// 获取数据库连接
			try (Connection conn = repository.getConnection()) {
				conn.setAutoCommit(false);

				try {
					// 步骤1：查询所有索引记录
					List<String> invalidPaths = new ArrayList<>();
					int totalRecords = 0;
					int checkedCount = 0;

					if (callback != null) {
						callback.update(0, "正在读取索引记录...");
					}

					// 获取所有文件路径
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

					// 检查每个文件是否存在
					for (String filePath : allPaths) {
						String fullPath = currentDrive + ":" + filePath;
						File file = new File(fullPath);

						checkedCount++;

						// 更新进度
						if (callback != null && totalRecords > 0) {
							int progress = checkedCount * 100 / totalRecords;
							callback.update(progress, "验证中 " + checkedCount + "/" + totalRecords);
						}

						// 检查文件是否存在
						if (!file.exists()) {
							invalidPaths.add(filePath);
							log.debug("发现无效记录: {}", fullPath);
						}

						// 每100条记录检查一次取消
						if (checkedCount % AppConfig.CANCEL_CHECK_INTERVAL == 0) {
							checkCancelled();
						}
					}

					// 步骤2：删除无效记录
					if (!invalidPaths.isEmpty()) {
						if (callback != null) {
							callback.update(100, "删除 " + invalidPaths.size() + " 条无效记录...");
						}

						log.info("发现 {} 条无效记录，开始删除", invalidPaths.size());
						stats.setDeletedCount(invalidPaths.size());

						repository.deleteByFilePaths(conn, invalidPaths);

						// 提交事务
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
					// 用户取消
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
					// 其他异常：回滚事务
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
			// 取消异常已在上面处理
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
