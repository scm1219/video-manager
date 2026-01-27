package com.github.scm1219.utils;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FileUtils extends org.apache.commons.io.FileUtils{

	private static String[] videoExtends = { ".mp4", ".mkv", ".rm", ".rmvb", "wmv", ".flv",".ogm" };

	/**
	 * @param dirName "G:\\anime\\ddd\\S2"
	 * @param prefix "RANMA_2BUNNO1.S02E"
	 */
	public static void renameFiles(String dirName,String prefix) {
		File dir = new File(dirName);
//		File[] files = dir.listFiles();
		Collection<File> listFiles = FileUtils.listFiles(dir, null, false);
		List<File> ss = new ArrayList<>(listFiles);
		Collections.sort(ss);
		String filePrefix=prefix;
		int count =1;
		for (int i = 0; i < listFiles.size(); i++) {
			String str = String.format("%02d", count);  
			File d = ss.get(i);
			
			String fileName = d.getName();
			String suffix = fileName.substring(fileName.lastIndexOf("."));
			String finalName = filePrefix+str+suffix;
			File newFile = new File(d.getParentFile().getAbsolutePath()+File.separator+finalName);
			System.out.println(d.getName()+"-->"+newFile.getAbsolutePath());
			d.renameTo(newFile);
			if(i%2!=0) {
				count++;
			}
		}
	}
	public static boolean isVideoFile(File f) {
		String fileName = f.getName().toLowerCase();
		boolean flag = false;
		for (String ext : videoExtends) {
			flag = flag | fileName.endsWith(ext);
			if (flag) {
				break;
			}
		}
		return flag;
	}

	public static String formetFileSize(long fileS) {//转换文件大小
        DecimalFormat df = new DecimalFormat("#.00");
        String fileSizeString = "";
        if (fileS == 0){
            return fileSizeString;
        }
        if (fileS < 1024) {
            fileSizeString = df.format((double) fileS) + "B";
        } else if (fileS < 1048576) {
            fileSizeString = df.format((double) fileS / 1024) + "K";
        } else if (fileS < 1073741824) {
            fileSizeString = df.format((double) fileS / 1048576) + "M";
        } else {
            fileSizeString = df.format((double) fileS / 1073741824) +"G";
        }
        return fileSizeString;
    }

	public static void openVideoFile(File f) {
		if (f == null) {
			log.warn("文件为空，无法打开");
			return;
		}
		String filePath = f.getAbsolutePath();
		try {
			log.info("尝试调用系统命令打开文件："+filePath);
			ProcessBuilder processBuilder = new ProcessBuilder("rundll32", "url.dll", "FileProtocolHandler", filePath);
			// 重定向输出流，避免创建 nul 文件
			processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);
			processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
			processBuilder.start();
		} catch (Exception ex) {
			log.error("打开文件失败："+filePath, ex);
		}
	}

	public static void openDir(File dir) {
		if (dir == null) {
			log.warn("文件夹为空，无法打开");
			return;
		}
		String dirPath = dir.getAbsolutePath();
		try {
			log.info("尝试调用系统命令打开文件夹："+dirPath);
			ProcessBuilder processBuilder = new ProcessBuilder("rundll32", "url.dll", "FileProtocolHandler", dirPath);
			// 重定向输出流，避免创建 nul 文件
			processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);
			processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
			processBuilder.start();
		} catch (Exception ex) {
			log.error("打开文件夹失败："+dirPath, ex);
		}
	}

	public static void openDirAndSelectFile(File file) {
		if (file == null) {
			log.warn("文件为空，无法打开");
			return;
		}
		if(!file.exists()) {
			log.warn("文件不存在，无法打开");
			return;
		}
		String filePath = file.getAbsolutePath();
		try {
			log.info("尝试调用系统命令打开文件夹并选中文件（cmd 单参数方式）："+filePath);
			// 使用 cmd /c 将整个命令作为单个参数传递，避免特殊字符解析问题
			String command = "explorer /select,\"" + filePath + "\"";
			ProcessBuilder processBuilder = new ProcessBuilder("cmd", "/c", command);
			// 重定向输出流，避免创建 nul 文件
			processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);
			processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
			processBuilder.start();
		} catch (Exception ex) {
			log.error("打开文件夹并选中文件失败："+filePath, ex);
		}
	}
}
