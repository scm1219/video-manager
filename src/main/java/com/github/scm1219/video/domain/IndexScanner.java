package com.github.scm1219.video.domain;

import java.io.File;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.github.houbb.opencc4j.util.ZhConverterUtil;
import com.github.scm1219.utils.FileUtils;
import com.github.scm1219.video.AppConfig;

/**
 * 索引扫描工具类
 * <p>
 * 从 Index 类中提取的文件扫描与文件名规范化逻辑，
 * 消除领域层对 JProgressBar（Swing）的直接依赖
 * </p>
 */
public class IndexScanner {

	private IndexScanner() {
		// 工具类禁止实例化
	}

	/**
	 * 规范化文件名：转换为简体中文并转小写
	 *
	 * @param data 原始文件名
	 * @return 规范化后的文件名
	 */
	public static String normalizeFileName(String data) {
		if (StringUtils.isNotEmpty(data)) {
			String result = data.toLowerCase();
			result = ZhConverterUtil.toSimple(result);
			return result;
		}
		return data;
	}

	/**
	 * 递归收集目录下的所有视频文件
	 *
	 * @param directory        要扫描的目录
	 * @param result           结果列表
	 * @param callback         进度回调（可为null）
	 * @param scannedFileCount 已扫描文件计数器（用于取消检查频率计算）
	 * @param cancellationCheck 取消检查回调（抛出 IndexCancelledException 来中断扫描）
	 */
	public static void collectVideoFiles(File directory, List<File> result, ProgressCallback callback,
			java.util.concurrent.atomic.AtomicInteger scannedFileCount, Runnable cancellationCheck) {
		if (!directory.exists() || !directory.isDirectory()) {
			return;
		}

		File[] files = directory.listFiles();
		if (files == null) {
			return;
		}

		for (File file : files) {
			// 每处理一个文件都增加扫描计数
			scannedFileCount.incrementAndGet();

			// 每100个文件检查一次取消
			if (scannedFileCount.get() % AppConfig.CANCEL_CHECK_INTERVAL == 0 && cancellationCheck != null) {
				cancellationCheck.run();
			}

			if (file.isDirectory()) {
				// 递归处理子目录
				collectVideoFiles(file, result, callback, scannedFileCount, cancellationCheck);
			} else if (FileUtils.isVideoFile(file)) {
				result.add(file);
			}
		}
	}
}
