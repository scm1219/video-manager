package com.github.scm1219.video.gui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import javax.swing.UnsupportedLookAndFeelException;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

/**
 * 主题管理器 - 管理 FlatLaf 主题的加载、切换和配置持久化
 *
 * 使用方法：
 * 1. 应用启动时调用 applyTheme() 加载上次保存的主题
 * 2. 用户切换主题时调用 applyTheme(themeName) 并保存配置
 *
 * @author FlatLaf Integration
 * @version 1.0.0
 */
public class ThemeManager {

    private static ThemeManager instance;
    private Properties config;
    private File configFile;

    // 主题常量
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";
    public static final String THEME_AUTO = "auto";

    // 配置文件路径
    private static final String CONFIG_DIR = ".video-manager";
    private static final String CONFIG_FILE = "theme.properties";
    private static final String THEME_KEY = "theme";

    /**
     * 私有构造函数，实现单例模式
     */
    private ThemeManager() {
        String userHome = System.getProperty("user.home");
        File configDir = new File(userHome, CONFIG_DIR);

        // 确保配置目录存在
        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        this.configFile = new File(configDir, CONFIG_FILE);
        this.config = new Properties();

        // 加载配置文件
        loadThemeConfig();
    }

    /**
     * 获取 ThemeManager 单例实例
     *
     * @return ThemeManager 实例
     */
    public static synchronized ThemeManager getInstance() {
        if (instance == null) {
            instance = new ThemeManager();
        }
        return instance;
    }

    /**
     * 从配置文件加载主题配置
     * 如果配置文件不存在或读取失败，使用默认主题（light）
     */
    private void loadThemeConfig() {
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                config.load(fis);
            } catch (IOException e) {
                // 配置文件读取失败，使用默认值
                System.err.println("读取主题配置失败: " + e.getMessage());
            }
        }
    }

    /**
     * 保存主题配置到文件
     *
     * @param themeName 主题名称（light/dark/auto）
     * @return 保存是否成功
     */
    public boolean saveThemeConfig(String themeName) {
        config.setProperty(THEME_KEY, themeName);

        try (FileOutputStream fos = new FileOutputStream(configFile)) {
            config.store(fos, "Theme Configuration\nAvailable values: light, dark, auto");
            return true;
        } catch (IOException e) {
            System.err.println("保存主题配置失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 获取当前保存的主题名称
     * 如果未配置，返回默认主题（light）
     *
     * @return 主题名称
     */
    public String getCurrentTheme() {
        return config.getProperty(THEME_KEY, THEME_LIGHT);
    }

    /**
     * 应用指定的 FlatLaf 主题
     *
     * @param themeName 主题名称（light/dark/auto）
     * @return 应用是否成功
     */
    public boolean applyTheme(String themeName) {
        try {
            switch (themeName.toLowerCase()) {
                case THEME_DARK:
                    FlatDarkLaf.setup();
                    break;
                case THEME_AUTO:
                    // 使用 FlatLaf 的系统主题检测
                    boolean isDark = FlatLaf.isLafDark();
                    if (isDark) {
                        FlatDarkLaf.setup();
                    } else {
                        FlatLightLaf.setup();
                    }
                    break;
                case THEME_LIGHT:
                default:
                    FlatLightLaf.setup();
                    break;
            }

            // 保存主题配置
            saveThemeConfig(themeName);

            return true;
        } catch (Exception e) {
            System.err.println("应用主题失败: " + e.getMessage());
            // 回退到系统默认 Look and Feel
            try {
                javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
            } catch (ClassNotFoundException | InstantiationException |
                     IllegalAccessException | UnsupportedLookAndFeelException ex) {
                System.err.println("回退到系统主题失败: " + ex.getMessage());
            }
            return false;
        }
    }

    /**
     * 更新所有窗口的 UI 外观
     * 用于在运行时切换主题后刷新界面
     */
    public static void updateUI() {
        FlatLaf.updateUI();
    }

    /**
     * 获取可用的主题列表
     *
     * @return 主题名称数组
     */
    public static String[] getAvailableThemes() {
        return new String[]{THEME_LIGHT, THEME_DARK, THEME_AUTO};
    }

    /**
     * 获取主题的显示名称
     *
     * @param themeName 主题名称
     * @return 显示名称
     */
    public static String getThemeDisplayName(String themeName) {
        switch (themeName.toLowerCase()) {
            case THEME_DARK:
                return "深色主题";
            case THEME_AUTO:
                return "跟随系统";
            case THEME_LIGHT:
            default:
                return "浅色主题";
        }
    }
}
