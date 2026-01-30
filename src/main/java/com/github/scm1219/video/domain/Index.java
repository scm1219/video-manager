package com.github.scm1219.video.domain;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.JProgressBar;

import com.github.houbb.opencc4j.util.ZhConverterUtil;

import com.github.scm1219.utils.DateUtils;
import com.github.scm1219.utils.FileUtils;
import com.github.scm1219.utils.VideoFileFilter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * 磁盘对应的索引
 * @author scm12
 *
 */
@Slf4j
@ToString
public class Index {


	private File indexFile;

	private boolean exists =false;

	boolean isIndexing=false;

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
	private int scannedFileCount = 0;

	/**
	 * 索引统计信息
	 */
	public static class IndexStatistics {
		private int totalCount;      // 扫描文件总数
		private int addedCount;      // 新增文件数
		private int deletedCount;    // 删除记录数
		private long scanTime;       // 扫描耗时(ms)

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
		exists = indexFile.exists() && indexFile.length()>0;
		
		//数据库链接预初始化
		if(exists) {
			new Thread(new Runnable() {
				@Override
				public void run() {
					try(Connection conn =getConnection()){
						conn.createStatement().executeQuery("select 1");
						String rootDrive = indexFile.getAbsolutePath().split(":")[0] + ":";
						log.debug(rootDrive + "\\ sqlite数据库链接正常");
					}catch (Exception e) {
						e.printStackTrace();
					}
				}
			}).start();
		}
	}
	
	private Connection getConnection() throws Exception {
		return DriverManager.getConnection("jdbc:sqlite:"+indexFile.getAbsolutePath());
	}

	/**
	 * 创建索引文件的备份
	 * <p>在索引开始前调用，备份原索引文件以便取消时回滚</p>
	 * <p>异常在方法内部处理，如果备份失败将记录日志并设置 backupFile 为 null</p>
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
	 * <p>在索引正常结束或回滚完成后调用</p>
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
	 * <p>先尝试 SQLite 事务回滚，失败时使用备份文件回滚</p>
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
			log.error("回滚完全失败，数据库可能处于不一致状态");
			JOptionPane.showMessageDialog(null,
					"回滚失败，索引可能已损坏\n建议重新创建索引",
					"警告", JOptionPane.WARNING_MESSAGE);
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
	 * 带进度条的处理
	 * @param disk
	 * @param bar
	 */
	public void create(Disk disk,JProgressBar bar) {
		if(bar!=null) {
			bar.setString("开始收集要处理的文件夹");
		}
		log.info("对"+indexFile.getParent()+"创建缓存");

		try {
			// 步骤1：创建备份
			createBackup();

			// 步骤2：获取数据库连接（提升为字段以便事务控制）
			activeConnection = getConnection();

			// 步骤3：DDL 操作（在自动提交模式下，使用幂等 SQL）
			try(Statement stmt = activeConnection.createStatement()){
				activeConnection.setAutoCommit(true);
				if(!indexFile.exists() || indexFile.length() < 1) {
					stmt.executeUpdate("create table if not exists files(fileName varchar(255), dirName varchar(255), filePath varchar(255), dirPath varchar(255))");
					stmt.executeUpdate("create index if not exists idx_filename on files (fileName)");
					stmt.executeUpdate("create index if not exists idx_dirname on files (dirName)");
				}
			}

			// 步骤4：开始事务（针对 DML 操作）
			activeConnection.setAutoCommit(false);

			try {
				// 删除旧数据
				try(Statement stmt = activeConnection.createStatement()) {
					stmt.execute("delete from files");
				}

				// 检查取消
				checkCancelled();

				// 重置扫描计数器
				scannedFileCount = 0;

				List<File> dirs = disk.listVideoDir(bar);

				// 收集完目录后再次检查取消
				checkCancelled();

				Collections.sort(dirs);
				int totalCount = dirs.size();
				int processedCount = 0;

				try(PreparedStatement pstmt = activeConnection.prepareStatement("insert into files (fileName,dirName,filePath,dirPath) values (?,?,?,?)")){
					int count = 0;
					for (File file : dirs) {
						// 每个目录检查取消
						checkCancelled();

						log.info(file.toString());
						processedCount++;
						int num = processedCount*100/totalCount;
						if(bar!=null) {
							bar.setValue(num);
							bar.setString("正在处理"+file.toString());
						}

						File[] videoFiles = file.listFiles(new VideoFileFilter());
						String dirName = file.getName();
						for (File videoFile : videoFiles) {
							pstmt.setString(1,getString(videoFile.getName()));
							pstmt.setString(2, getString(dirName));
							pstmt.setString(3, videoFile.getAbsolutePath().substring(videoFile.getAbsolutePath().indexOf(":")+1));
							pstmt.setString(4, videoFile.getParentFile().getAbsolutePath().substring(videoFile.getAbsolutePath().indexOf(":")+1));
							pstmt.addBatch();
							count++;
							if(count>100) {
								pstmt.executeBatch();
								count=0;
								// 每100条记录检查取消
								checkCancelled();
							}
						}
					}
					if(count>0) {
						pstmt.executeBatch();
					}
				}

				// 步骤5：提交事务
				activeConnection.commit();
				activeConnection.setAutoCommit(true);

				log.info("整盘索引完成，共扫描 {} 个文件", scannedFileCount);

				if(bar!=null) {
					bar.setString("处理结束");
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
			if(bar!=null) {
				bar.setString("索引已取消");
			}
		} catch (Exception e) {
			log.error("创建索引失败", e);
			if(bar!=null) {
				bar.setString("处理失败: " + e.getMessage());
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
	 * <p>供外部类（如 Disk）在耗时操作中检查取消状态</p>
	 *
	 * @return true 如果已请求取消，false 否则
	 */
	public boolean isCancelled() {
		return isCancelled;
	}
	public void create(Disk disk) {
		if(isIndexing) {
			throw new RuntimeException("索引正在创建中，请稍后");
		}else {
			isIndexing=true;
			try {
				create(disk, null);
			} catch (Exception e) {
				JOptionPane.showMessageDialog(null, "创建索引出错："+e.getLocalizedMessage());
			}
			isIndexing=false;
		}
	}
	
	public void cancel(Disk disk) {
		isCancelled = true;
		log.info("用户请求取消索引创建");

	}
	
	private static String getString(String data) {
		if(org.apache.commons.lang3.StringUtils.isNotEmpty(data)) {
			String result  = data.toLowerCase();
			result = ZhConverterUtil.toSimple(result);
			return result;
		}else {
			return data;
		}
	}
	
	public List<File> findFiles(String name) {
		List<File> results = new ArrayList<>();
		//当前盘符
		name = getString(name);
		String currentDrive = indexFile.getAbsolutePath().split(":")[0];
		try(Connection conn =getConnection()){
			String sql = "select filePath from files where fileName like '%"+name+"%'";
			try(Statement stmt = conn.createStatement()){
				ResultSet rs = stmt.executeQuery(sql);
				while(rs.next()) {
					String newPath = rs.getString(1);
					File f = new File(currentDrive+":"+newPath);
					results.add(f);
				}
			}
		}catch (Exception e) {
			e.printStackTrace();
		}
		return results;
	}
	
	public List<File> findDirs(String dirName) {
		List<File> results = new ArrayList<>();
		//当前盘符
		dirName = getString(dirName);
		String currentDrive = indexFile.getAbsolutePath().split(":")[0];
		try(Connection conn =getConnection()){
			String sql = "select distinct(dirPath) from files where dirName like '%"+dirName+"%'";
			try(Statement stmt = conn.createStatement()){
				ResultSet rs = stmt.executeQuery(sql);
				while(rs.next()) {
					String newPath = rs.getString(1);
					File f = new File(currentDrive+":"+newPath);
					results.add(f);
				}
			}
		}catch (Exception e) {
			e.printStackTrace();
		}
		return results;
	}
	
	
	public boolean exists() {
		return exists;
	}

	public void initEmptyTables() {
		try(Connection connection=getConnection()) {
			connection.setAutoCommit(true);
			try(Statement stmt = connection.createStatement()){
				stmt.executeUpdate("create table files(fileName varchar(255), dirName varchar(255), filePath varchar(255), dirPath varchar(255))");
				stmt.executeUpdate("create index idx_filename on files (fileName)");
				stmt.executeUpdate("create index idx_dirname on files (dirName)");
				stmt.executeQuery("select count(*) from files");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public String getInfoString() {
		StringBuilder sb = new StringBuilder();
		sb.append("位置："+indexFile.getParent());
		sb.append("\n");
		sb.append("文件名："+indexFile.getName());
		sb.append("\n");
		sb.append("索引大小："+FileUtils.formetFileSize(indexFile.length()));
		sb.append("\n");
		sb.append("修改时间："+ DateUtils.getDateString(indexFile.lastModified()));
		sb.append("\n");
		sb.append("包含记录条数："+getRecordsCount());
		sb.append("\n");
		return sb.toString();
	}
	
	private long getRecordsCount() {
		long count =0L;
		try(Connection conn = getConnection()){
			String sql ="select count(*) from files";
			try(Statement stmt = conn.createStatement()){
				ResultSet rs = stmt.executeQuery(sql);
				while(rs.next()) {
					count =rs.getLong(1);
				}
			}
		}catch (Exception e) {
			e.printStackTrace();
		}
		return count;
	}

	/**
	 * 为指定目录创建索引（先删除旧记录，再扫描并插入新记录）
	 * @param directory 要扫描的目录
	 * @param bar 进度条（可为null）
	 * @return 索引统计信息
	 */
	public IndexStatistics createForDirectory(File directory, JProgressBar bar) {
		long startTime = System.currentTimeMillis();
		IndexStatistics stats = new IndexStatistics();
		if(bar!=null) {
			bar.setString("开始扫描目录: " + directory.getName());
		}
		log.info("为目录{}创建索引", directory.getAbsolutePath());

		try {
			// 步骤1：创建备份
			createBackup();

			// 步骤2：获取数据库连接
			try {
				activeConnection = getConnection();

				// 步骤3：开始事务
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
				if(dirPath.contains(":")) {
					dirPath = dirPath.substring(dirPath.indexOf(":") + 1);
				}
				// 确保路径以/开头
				if(!dirPath.startsWith("/") && !dirPath.startsWith("\\")) {
					dirPath = "/" + dirPath;
				}
				// 统一使用/作为路径分隔符
				dirPath = dirPath.replace("\\", "/");

				if(bar!=null) {
					bar.setString("删除旧索引记录...");
				}

				// 统计删除前的记录数
				int deletedCount = 0;
				try(Statement stmt = activeConnection.createStatement()) {
					String countSql = "SELECT COUNT(*) FROM files WHERE dirPath LIKE '" + dirPath + "%'";
					log.debug("执行统计SQL: {}", countSql);
					ResultSet rs = stmt.executeQuery(countSql);
					if(rs.next()) {
						deletedCount = rs.getInt(1);
					}
				}
				stats.setDeletedCount(deletedCount);

				// 删除该目录下的所有旧记录
				try(Statement stmt = activeConnection.createStatement()) {
					String deleteSql = "DELETE FROM files WHERE dirPath LIKE '" + dirPath + "%'";
					log.debug("执行删除SQL: {}", deleteSql);
					stmt.executeUpdate(deleteSql);
				}

				// 检查取消
				checkCancelled();

				if(bar!=null) {
					bar.setString("扫描视频文件...");
				}

				// 重置扫描计数器
				scannedFileCount = 0;

				// 收集所有视频文件
				List<File> videoFiles = new ArrayList<>();
				collectVideoFiles(directory, videoFiles, bar);

				// 收集完成后立即检查取消
				checkCancelled();

				log.info("扫描完成，共扫描 {} 个文件，找到 {} 个视频文件",
						scannedFileCount, videoFiles.size());

				if(bar!=null) {
					bar.setValue(50);
					bar.setString("插入索引记录...");
				}

				// 批量插入视频文件记录
				try(PreparedStatement pstmt = activeConnection.prepareStatement(
						"INSERT INTO files (fileName, dirName, filePath, dirPath) VALUES (?, ?, ?, ?)")) {

					int count = 0;
					int totalFiles = videoFiles.size();
					int processedFiles = 0;

					for(File videoFile : videoFiles) {
						log.debug("处理文件: {}", videoFile.getAbsolutePath());

						String fileName = getString(videoFile.getName());
						File parentDir = videoFile.getParentFile();
						String dirName = getString(parentDir.getName());
						String filePath = videoFile.getAbsolutePath().substring(videoFile.getAbsolutePath().indexOf(":") + 1);
						String fileDirPath = parentDir.getAbsolutePath().substring(parentDir.getAbsolutePath().indexOf(":") + 1);
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
						if(bar!=null && totalFiles > 0) {
							int progress = 50 + (processedFiles * 50 / totalFiles);
							bar.setValue(progress);
							bar.setString("正在处理 " + processedFiles + "/" + totalFiles);
						}

						// 每100条执行一次批量插入
						if(count >= 100) {
							pstmt.executeBatch();
							count = 0;
							// 每100条记录检查取消
							checkCancelled();
						}
					}

					// 插入剩余记录
					if(count > 0) {
						pstmt.executeBatch();
					}
				}

				// 提交事务
				activeConnection.commit();
				activeConnection.setAutoCommit(true);

				if(bar!=null) {
					bar.setValue(100);
					bar.setString("扫描完成！共处理 " + videoFiles.size() + " 个视频文件");
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
			if(bar!=null) {
				bar.setString("索引已取消");
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
	 * 递归收集目录下的所有视频文件
	 * @param directory 要扫描的目录
	 * @param result 结果列表
	 * @param bar 进度条（可为null）
	 */
	private void collectVideoFiles(File directory, List<File> result, JProgressBar bar) {
		if(!directory.exists() || !directory.isDirectory()) {
			return;
		}

		File[] files = directory.listFiles();
		if(files == null) {
			return;
		}

		for(File file : files) {
			// 每处理一个文件都增加扫描计数
			scannedFileCount++;

			// 每100个文件检查一次取消
			if(scannedFileCount % 100 == 0) {
				checkCancelled();
			}

			if(file.isDirectory()) {
				// 递归处理子目录
				collectVideoFiles(file, result, bar);
			} else if(FileUtils.isVideoFile(file)) {
				result.add(file);
			}
		}
	}


}
