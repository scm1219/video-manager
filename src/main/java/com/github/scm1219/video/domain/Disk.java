package com.github.scm1219.video.domain;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.JProgressBar;

import com.github.scm1219.utils.FileUtils;
import com.github.scm1219.video.gui.FileExplorerWindow;
import com.github.scm1219.video.gui.tree.IndexValidationProcesser;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ToString
@EqualsAndHashCode
public class Disk {

	public static final String FLAF_FILE=".disk.needindex";
	public static final String INDEX_FILE=".disk.sqlite";
	
	private File disk;
	private Index index;
	private boolean needIndex;
	
	public Disk(File f) {
		disk = f;
		File indexFile = new File(disk.getPath()+INDEX_FILE);
		index = new Index(indexFile);
	}
	
	
	public String getVolumeName() {
		return getPath().substring(0,2);
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
		File flagFile = new File(disk.getPath()+FLAF_FILE);
		return flagFile.exists();
	}
	
	
	public List<File> listVideoDir() {
		return listVideoDir(null);
	}
	
	private boolean findVideoDir(File parent,List<File> result,JProgressBar bar,boolean isTop) {
		File[] subDirs = parent.listFiles();
		boolean currentVideo= hasVideoFiles(parent);

		if(subDirs!=null) {
			for (File subDir: subDirs) {
				// 检查是否取消（每个目录都检查）
				if(index.isCancelled()) {
					log.info("已经取消检查");
					throw new IndexCancelledException();
				}

				if(bar!=null && isTop) {
					bar.setString("检查"+subDir.getAbsolutePath()+"是否需要扫描");
				}
				if(subDir.isDirectory()) {
					//先遍历子目录是否包含视频
					boolean subHasVideo = findVideoDir(subDir, result,bar,false);
					if (subHasVideo) {
						currentVideo = true;
					}
				}
			}
		}
		if(currentVideo) {
			result.add(parent);
		}
		return currentVideo;
	}
	
	/**
	 * 判断当前文件夹下有无视频文件
	 * @param dir
	 * @return
	 */
	private boolean hasVideoFiles(File dir) {
		File[] listFiles = dir.listFiles();
		if(listFiles!=null) {
			for (File file : listFiles) {
				if (FileUtils.isVideoFile(file)) {
					return true;
				}
			}
		}
		return false;
	}



	public List<File> listVideoDir(JProgressBar bar) {
		File base = disk;
		List<File> result = new ArrayList<>();
		findVideoDir(base, result,bar,true);
		return result;
	}

	public void initEmptyDatabase() {
		index.initEmptyTables();
	}
	
	/**
     * 执行索引验证和清理
     * @param disk 要验证的磁盘
     */
    public void performValidateAndCleanup(FileExplorerWindow window) {
    	// 检查索引是否存在
    	if(!getIndex().exists()) {
    		JOptionPane.showMessageDialog(window,
    			"该磁盘尚未创建索引\n请先执行整盘索引创建",
    			"提示",
    			JOptionPane.INFORMATION_MESSAGE);
    		return;
    	}

    	// 检查是否正在索引
    	if(getIndex().isIndexing()) {
    		JOptionPane.showMessageDialog(window,
    			"索引正在创建中，请稍后再试",
    			"提示",
    			JOptionPane.INFORMATION_MESSAGE);
    		return;
    	}
    	Disk target = this;
    	// 启动验证和清理进程
    	new Thread(new Runnable() {
    		@Override
    		public void run() {
    			new IndexValidationProcesser(target).setVisible(true);
    		}
    	}).start();
    }
}
