package com.github.scm1219.video.gui;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClickDebouncer {
	private static final long DUPLICATE_CLICK_THRESHOLD = 1000;
	private static final Map<String, Long> recentOpenHistory = new ConcurrentHashMap<>();

	private static boolean isDuplicateClick(String path) {
		Long lastOpenTime = recentOpenHistory.get(path);
		long currentTime = System.currentTimeMillis();
		if (lastOpenTime != null && (currentTime - lastOpenTime) < DUPLICATE_CLICK_THRESHOLD) {
			return true;
		}
		recentOpenHistory.put(path, currentTime);
		return false;
	}

	private static void cleanupHistory() {
		long currentTime = System.currentTimeMillis();
		recentOpenHistory.entrySet().removeIf(entry -> (currentTime - entry.getValue()) > DUPLICATE_CLICK_THRESHOLD * 10);
	}

	public static boolean shouldOpen(String path) {
		if (isDuplicateClick(path)) {
			log.info("文件正在打开中，忽略重复点击：" + path);
			return false;
		}
		cleanupHistory();
		return true;
	}

	public static void recordError(String path) {
		recentOpenHistory.remove(path);
	}
}
