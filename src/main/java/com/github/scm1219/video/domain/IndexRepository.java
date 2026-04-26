package com.github.scm1219.video.domain;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.github.scm1219.video.AppConfig;
import lombok.extern.slf4j.Slf4j;

/**
 * 索引数据库仓库层 - 封装所有 SQLite 数据库操作
 * <p>
 * 负责索引文件的 CRUD 操作，包括：
 * - 数据库连接管理
 * - DDL 操作（建表、建索引）
 * - DML 操作（增删改查）
 * </p>
 *
 * @author scm12
 */
@Slf4j
public class IndexRepository {

	private final File indexFile;

	public static final String INSERT_SQL = "INSERT INTO files (fileName, dirName, filePath, dirPath) VALUES (?, ?, ?, ?)";

	public IndexRepository(File indexFile) {
		this.indexFile = indexFile;
	}

	/**
	 * 获取一个新的数据库连接
	 *
	 * @return SQLite 数据库连接
	 * @throws Exception 连接失败时抛出
	 */
	public Connection getConnection() throws Exception {
		return DriverManager.getConnection("jdbc:sqlite:" + indexFile.getAbsolutePath());
	}

	/**
	 * 确保表和索引存在（幂等 DDL，使用 IF NOT EXISTS）
	 *
	 * @param conn 数据库连接（由调用方管理）
	 * @throws Exception SQL 执行失败时抛出
	 */
	public void ensureSchema(Connection conn) throws Exception {
		try (Statement stmt = conn.createStatement()) {
			stmt.executeUpdate(
					"CREATE TABLE IF NOT EXISTS files(fileName varchar(255), dirName varchar(255), filePath varchar(255), dirPath varchar(255))");
			stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_filename ON files (fileName)");
			stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_dirname ON files (dirName)");
		}
	}

	/**
	 * 创建表和索引（不带 IF NOT EXISTS，用于 initEmptyTables）
	 *
	 * @param conn 数据库连接（由调用方管理）
	 * @throws Exception SQL 执行失败时抛出
	 */
	public void createSchema(Connection conn) throws Exception {
		try (Statement stmt = conn.createStatement()) {
			stmt.executeUpdate(
					"CREATE TABLE files(fileName varchar(255), dirName varchar(255), filePath varchar(255), dirPath varchar(255))");
			stmt.executeUpdate("CREATE INDEX idx_filename ON files (fileName)");
			stmt.executeUpdate("CREATE INDEX idx_dirname ON files (dirName)");
			stmt.executeQuery("SELECT count(*) FROM files");
		}
	}

	/**
	 * 删除 files 表中的所有记录
	 *
	 * @param conn 数据库连接（由调用方管理）
	 * @throws Exception SQL 执行失败时抛出
	 */
	public void deleteAllFiles(Connection conn) throws Exception {
		try (Statement stmt = conn.createStatement()) {
			stmt.execute("DELETE FROM files");
		}
	}

	/**
	 * 按目录路径前缀删除文件记录
	 *
	 * @param conn    数据库连接（由调用方管理）
	 * @param dirPath 目录路径前缀
	 * @return 删除的记录数
	 * @throws Exception SQL 执行失败时抛出
	 */
	public int deleteFilesByDirPath(Connection conn, String dirPath) throws Exception {
		try (PreparedStatement pstmt = conn.prepareStatement("DELETE FROM files WHERE dirPath LIKE ?")) {
			pstmt.setString(1, dirPath + "%");
			return pstmt.executeUpdate();
		}
	}

	/**
	 * 按目录路径前缀统计文件记录数
	 *
	 * @param conn    数据库连接（由调用方管理）
	 * @param dirPath 目录路径前缀
	 * @return 记录数
	 * @throws Exception SQL 执行失败时抛出
	 */
	public int countFilesByDirPath(Connection conn, String dirPath) throws Exception {
		try (PreparedStatement pstmt = conn.prepareStatement("SELECT COUNT(*) FROM files WHERE dirPath LIKE ?")) {
			pstmt.setString(1, dirPath + "%");
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				return rs.getInt(1);
			}
			return 0;
		}
	}

	/**
	 * 按文件名模式搜索文件路径（使用 PreparedStatement + LIKE）
	 *
	 * @param name 文件名关键词（已转换为简体小写）
	 * @return 匹配的文件路径列表（去盘符后的路径）
	 * @throws Exception SQL 执行失败时抛出
	 */
	public List<String> findFilePathsByName(String name) throws Exception {
		List<String> results = new ArrayList<>();
		try (Connection conn = getConnection()) {
			try (PreparedStatement pstmt = conn.prepareStatement("SELECT filePath FROM files WHERE fileName LIKE ?")) {
				pstmt.setString(1, "%" + name + "%");
				ResultSet rs = pstmt.executeQuery();
				while (rs.next()) {
					results.add(rs.getString(1));
				}
			}
		}
		return results;
	}

	/**
	 * 按目录名模式搜索去重的目录路径（使用 PreparedStatement + LIKE）
	 *
	 * @param dirName 目录名关键词（已转换为简体小写）
	 * @return 匹配的去重目录路径列表（去盘符后的路径）
	 * @throws Exception SQL 执行失败时抛出
	 */
	public List<String> findDistinctDirPathsByName(String dirName) throws Exception {
		List<String> results = new ArrayList<>();
		try (Connection conn = getConnection()) {
			try (PreparedStatement pstmt = conn
					.prepareStatement("SELECT DISTINCT(dirPath) FROM files WHERE dirName LIKE ?")) {
				pstmt.setString(1, "%" + dirName + "%");
				ResultSet rs = pstmt.executeQuery();
				while (rs.next()) {
					results.add(rs.getString(1));
				}
			}
		}
		return results;
	}

	/**
	 * 获取索引中的总记录数
	 *
	 * @return 记录总数
	 */
	public long getRecordCount() {
		try (Connection conn = getConnection()) {
			try (Statement stmt = conn.createStatement()) {
				ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM files");
				if (rs.next()) {
					return rs.getLong(1);
				}
			}
		} catch (Exception e) {
			log.error("获取记录数失败", e);
		}
		return 0L;
	}

	/**
	 * 获取索引中所有文件路径
	 *
	 * @param conn 数据库连接（由调用方管理）
	 * @return 所有文件路径列表（去盘符后的路径）
	 * @throws Exception SQL 执行失败时抛出
	 */
	public List<String> getAllFilePaths(Connection conn) throws Exception {
		List<String> paths = new ArrayList<>();
		try (Statement stmt = conn.createStatement()) {
			ResultSet rs = stmt.executeQuery("SELECT filePath FROM files");
			while (rs.next()) {
				paths.add(rs.getString(1));
			}
		}
		return paths;
	}

	/**
	 * 按文件路径批量删除记录
	 *
	 * @param conn  数据库连接（由调用方管理）
	 * @param paths 要删除的文件路径列表
	 * @throws Exception SQL 执行失败时抛出
	 */
	public void deleteByFilePaths(Connection conn, List<String> paths) throws Exception {
		try (PreparedStatement pstmt = conn.prepareStatement("DELETE FROM files WHERE filePath = ?")) {
			int count = 0;
			for (String path : paths) {
				pstmt.setString(1, path);
				pstmt.addBatch();
				count++;
				if (count >= AppConfig.INDEX_BATCH_SIZE) {
					pstmt.executeBatch();
					count = 0;
				}
			}
			if (count > 0) {
				pstmt.executeBatch();
			}
		}
	}

	/**
	 * 从索引文件路径中获取当前盘符
	 *
	 * @return 盘符字符串（如 "E"）
	 */
	public String getCurrentDrive() {
		return indexFile.getAbsolutePath().split(":")[0];
	}

	/**
	 * 预热数据库连接（测试连接是否正常）
	 */
	public void warmUp() {
		try (Connection conn = getConnection()) {
			conn.createStatement().executeQuery("SELECT 1");
			String rootDrive = indexFile.getAbsolutePath().split(":")[0] + ":";
			log.debug(rootDrive + "\\ sqlite数据库链接正常");
		} catch (Exception e) {
			log.error("数据库连接预热失败", e);
		}
	}
}
