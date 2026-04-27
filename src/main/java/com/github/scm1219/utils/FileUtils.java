package com.github.scm1219.utils;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileUtils {

	private static final Set<String> VIDEO_EXTENSIONS = Set.of(
		".mp4", ".mkv", ".rm", ".rmvb", ".wmv", ".flv", ".ogm"
	);

	/**
	 * @param dirName "G:\\anime\\ddd\\S2"
	 * @param prefix  "RANMA_2BUNNO1.S02E"
	 */
	public static void renameFiles(String dirName, String prefix) {
		File dir = new File(dirName);
		Collection<File> listFiles = org.apache.commons.io.FileUtils.listFiles(dir, null, false);
		List<File> ss = new ArrayList<>(listFiles);
		Collections.sort(ss);
		int count = 1;
		for (int i = 0; i < listFiles.size(); i++) {
			String str = String.format("%02d", count);
			File d = ss.get(i);
			String fileName = d.getName();
			String suffix = fileName.substring(fileName.lastIndexOf("."));
			String finalName = prefix + str + suffix;
			File newFile = new File(d.getParentFile().getAbsolutePath() + File.separator + finalName);
			log.info("{} -> {}", d.getName(), newFile.getAbsolutePath());
			d.renameTo(newFile);
			if (i % 2 != 0) {
				count++;
			}
		}
	}

	public static boolean isVideoFile(File f) {
		String fileName = f.getName().toLowerCase();
		int dotIndex = fileName.lastIndexOf('.');
		if (dotIndex < 0) return false;
		return VIDEO_EXTENSIONS.contains(fileName.substring(dotIndex));
	}

	public static String formatFileSize(long fileS) {
		if (fileS == 0) {
			return "";
		}
		DecimalFormat df = new DecimalFormat("#.00");
		if (fileS < 1024) {
			return df.format((double) fileS) + "B";
		} else if (fileS < 1048576) {
			return df.format((double) fileS / 1024) + "K";
		} else if (fileS < 1073741824) {
			return df.format((double) fileS / 1048576) + "M";
		} else {
			return df.format((double) fileS / 1073741824) + "G";
		}
	}

	public static void openVideoFile(File f) {
		if (f == null) {
			log.warn("文件为空，无法打开");
			return;
		}
		log.info("尝试调用系统命令打开文件：{}", f.getAbsolutePath());
		executeCommand("rundll32", "url.dll", "FileProtocolHandler", f.getAbsolutePath());
	}

	public static void openDir(File dir) {
		if (dir == null) {
			log.warn("文件夹为空，无法打开");
			return;
		}
		log.info("尝试调用系统命令打开文件夹：{}", dir.getAbsolutePath());
		executeCommand("rundll32", "url.dll", "FileProtocolHandler", dir.getAbsolutePath());
	}

	public static void openDirAndSelectFile(File file) {
		if (file == null) {
			log.warn("文件为空，无法打开");
			return;
		}
		if (!file.exists()) {
			log.warn("文件不存在，无法打开");
			return;
		}
		log.info("尝试调用系统命令打开文件夹并选中文件：{}", file.getAbsolutePath());
		String command = "explorer /select,\"" + file.getAbsolutePath() + "\"";
		executeCommand("cmd", "/c", command);
	}

	private static void executeCommand(String... command) {
		try {
			ProcessBuilder pb = new ProcessBuilder(command);
			pb.redirectError(ProcessBuilder.Redirect.DISCARD);
			pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
			Process process = pb.start();
			Thread cleanup = new Thread(() -> {
				try {
					process.waitFor();
				} catch (InterruptedException e) {
					process.destroyForcibly();
					Thread.currentThread().interrupt();
				}
			});
			cleanup.setDaemon(true);
			cleanup.start();
		} catch (Exception ex) {
			log.error("执行命令失败: {}", String.join(" ", command), ex);
		}
	}
}
