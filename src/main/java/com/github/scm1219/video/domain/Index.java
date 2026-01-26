package com.github.scm1219.video.domain;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
	 * 带进度条的处理
	 * @param disk
	 * @param bar
	 */
	public void create(Disk disk,JProgressBar bar) {
		if(bar!=null) {
			bar.setString("开始收集要处理的文件夹");
		}
		log.info("对"+indexFile.getParent()+"创建缓存");
		try(Connection connection=getConnection()) {
			connection.setAutoCommit(true);
			try(Statement stmt = connection.createStatement()){
				if(!indexFile.exists() ||  indexFile.length() <1 ) {
					stmt.executeUpdate("create table files(fileName varchar(255), dirName varchar(255), filePath varchar(255), dirPath varchar(255))");
					stmt.executeUpdate("create index idx_filename on files (fileName)");
					stmt.executeUpdate("create index idx_dirname on files (dirName)");
				}else {
					stmt.execute("delete from files");
				}
			}
			List<File> dirs = disk.listVideoDir(bar);
			Collections.sort(dirs);
			int totalCount = dirs.size();
			int processedCount =0;
			try(PreparedStatement pstmt = connection.prepareStatement("insert into files (fileName,dirName,filePath,dirPath) values (?,?,?,?)")){
				int count=0;
				for (File file : dirs) {
					log.info(file.toString());
					processedCount++;
					int num = processedCount*100/totalCount;
					bar.setValue(num);
					bar.setString("正在处理"+file.toString());
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
						}
					}
				}
				if(count>0) {
					pstmt.executeBatch();
				}
			}
			bar.setString("处理结束");
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	public synchronized boolean isIndexing() {
		return isIndexing;
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
		if(isIndexing) {
			
		}
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

		try(Connection connection=getConnection()) {
			connection.setAutoCommit(true);

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
			try(Statement stmt = connection.createStatement()) {
				String countSql = "SELECT COUNT(*) FROM files WHERE dirPath LIKE '" + dirPath + "%'";
				log.debug("执行统计SQL: {}", countSql);
				ResultSet rs = stmt.executeQuery(countSql);
				if(rs.next()) {
					deletedCount = rs.getInt(1);
				}
			}
			stats.setDeletedCount(deletedCount);

			// 删除该目录下的所有旧记录
			try(Statement stmt = connection.createStatement()) {
				String deleteSql = "DELETE FROM files WHERE dirPath LIKE '" + dirPath + "%'";
				log.debug("执行删除SQL: {}", deleteSql);
				stmt.executeUpdate(deleteSql);
			}

			if(bar!=null) {
				bar.setString("扫描视频文件...");
			}

			// 收集所有视频文件
			List<File> videoFiles = new ArrayList<>();
			collectVideoFiles(directory, videoFiles, bar);

			if(bar!=null) {
				bar.setValue(50);
				bar.setString("插入索引记录...");
			}

			// 批量插入视频文件记录
			try(PreparedStatement pstmt = connection.prepareStatement(
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
					}
				}

				// 插入剩余记录
				if(count > 0) {
					pstmt.executeBatch();
				}
			}

			if(bar!=null) {
				bar.setValue(100);
				bar.setString("扫描完成！共处理 " + videoFiles.size() + " 个视频文件");
			}

			// 设置统计信息
			stats.setTotalCount(videoFiles.size());
			stats.setAddedCount(videoFiles.size());
			stats.setScanTime(System.currentTimeMillis() - startTime);

			log.info("目录{}索引创建完成，共处理{}个视频文件", directory.getAbsolutePath(), videoFiles.size());

		} catch (Exception e) {
			log.error("创建目录索引失败", e);
			if(bar!=null) {
				bar.setString("扫描失败: " + e.getMessage());
			}
			// 即使失败也返回已收集的统计信息
			stats.setScanTime(System.currentTimeMillis() - startTime);
			throw new RuntimeException("创建目录索引失败: " + e.getMessage(), e);
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
			if(file.isDirectory()) {
				// 递归处理子目录
				collectVideoFiles(file, result, bar);
			} else if(FileUtils.isVideoFile(file)) {
				result.add(file);
			}
		}
	}


}
