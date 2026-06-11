package com.github.scm1219.video;

import java.io.File;

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
    public static final String INDEXES_DIR = "indexes";
    public static final String REGISTRY_FILE = "registry.properties";
    public static final String CONFIG_FILE = "config.properties";
    public static final String OLD_THEME_FILE = "theme.properties";

    private AppConfig() {
    }

    /**
     * 获取用户应用数据目录（~/.video-manager/）
     */
    public static File getUserDir() {
        File dir = new File(System.getProperty("user.home"), USER_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * 获取本地索引缓存目录（~/.video-manager/indexes/）
     */
    public static File getIndexesDir() {
        File dir = new File(getUserDir(), INDEXES_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * 获取注册表文件路径（~/.video-manager/registry.properties）
     */
    public static File getRegistryFile() {
        return new File(getUserDir(), REGISTRY_FILE);
    }

    /**
     * 获取用户配置文件路径（~/.video-manager/config.properties）
     */
    public static File getConfigFile() {
        return new File(getUserDir(), CONFIG_FILE);
    }

    /**
     * 获取旧版主题配置文件路径（~/.video-manager/theme.properties）
     * <p>仅用于迁移检测，迁移完成后应删除</p>
     */
    public static File getOldThemeFile() {
        return new File(getUserDir(), OLD_THEME_FILE);
    }
}
