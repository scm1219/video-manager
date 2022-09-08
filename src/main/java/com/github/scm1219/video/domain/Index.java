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
	
	public Index(File indexFile) {
		this.indexFile = indexFile;
		exists = indexFile.exists() && indexFile.length()>0;
		
		//数据库链接预初始化
		if(exists) {
			new Thread(new Runnable() {
				@Override
				public void run() {
					try(Connection conn =getConnection()){
						log.debug("sqlite数据库链接正常");
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

	
}
