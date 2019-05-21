package com.github.scm1219.utils;

import java.io.File;
import java.text.DecimalFormat;

import lombok.extern.slf4j.Slf4j;
@Slf4j
public class FileUtils {

	private static String[] videoExtends = { ".mp4", ".mkv", ".rm", ".rmvb", "wmv", ".flv" };

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
		Runtime runtime = Runtime.getRuntime();
		try {
			log.info("尝试调用系统命令打开文件："+f.getAbsolutePath());
			runtime.exec("rundll32 url.dll FileProtocolHandler " + f.getAbsolutePath());
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	public static void openDir(File dir) {
		Runtime runtime = Runtime.getRuntime();
		try {
			log.info("尝试调用系统命令打开文件夹："+dir.getAbsolutePath());
			runtime.exec("rundll32 url.dll FileProtocolHandler " + dir.getAbsolutePath());
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
}
