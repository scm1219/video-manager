package com.github.scm1219.video;

/**
 * 应用程序全局配置常量
 */
public final class AppConfig {

    // 窗口尺寸
    public static final int WINDOW_WIDTH = 1024;
    public static final int WINDOW_HEIGHT = 768;

    // 搜索配置
    public static final int SEARCH_DEBOUNCE_MS = 700;

    // 索引配置
    public static final int INDEX_BATCH_SIZE = 100;
    public static final int CANCEL_CHECK_INTERVAL = 100;

    // 应用目录
    public static final String USER_DIR = ".video-manager";

    private AppConfig() {
    }
}
