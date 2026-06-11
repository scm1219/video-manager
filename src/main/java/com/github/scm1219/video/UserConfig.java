package com.github.scm1219.video;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import lombok.extern.slf4j.Slf4j;

/**
 * 通用用户配置管理器
 * <p>
 * 管理所有用户偏好设置，统一存储在 {@code ~/.video-manager/config.properties}。
 * 首次加载时自动从旧版 theme.properties 迁移主题配置。
 * </p>
 *
 * <p>配置键：</p>
 * <ul>
 *   <li>{@code theme} → 主题名称（light/dark/auto）</li>
 *   <li>{@code offlineIndex} → 离线索引开关（true/false）</li>
 * </ul>
 *
 * @author scm12
 */
@Slf4j
public class UserConfig {

	// 配置键常量
	public static final String KEY_THEME = "theme";
	public static final String KEY_OFFLINE_INDEX = "offlineIndex";

	private static final UserConfig instance = new UserConfig();

	private final Properties properties;
	private final File configFile;

	private UserConfig() {
		this.configFile = AppConfig.getConfigFile();
		this.properties = new Properties();
		load();
		migrateFromOldThemeConfig();
	}

	public static UserConfig getInstance() {
		return instance;
	}

	// ========== 读取方法 ==========

	/**
	 * 读取字符串配置
	 *
	 * @param key          配置键
	 * @param defaultValue 默认值
	 * @return 配置值，未找到返回默认值
	 */
	public String getString(String key, String defaultValue) {
		return properties.getProperty(key, defaultValue);
	}

	/**
	 * 读取布尔配置
	 *
	 * @param key          配置键
	 * @param defaultValue 默认值
	 * @return 配置值，未找到返回默认值
	 */
	public boolean getBoolean(String key, boolean defaultValue) {
		String value = properties.getProperty(key);
		if (value == null) {
			return defaultValue;
		}
		return Boolean.parseBoolean(value);
	}

	// ========== 写入方法 ==========

	/**
	 * 写入字符串配置并立即持久化
	 *
	 * @param key   配置键
	 * @param value 配置值
	 */
	public void setString(String key, String value) {
		properties.setProperty(key, value);
		save();
	}

	/**
	 * 写入布尔配置并立即持久化
	 *
	 * @param key   配置键
	 * @param value 配置值
	 */
	public void setBoolean(String key, boolean value) {
		properties.setProperty(key, String.valueOf(value));
		save();
	}

	// ========== 内部方法 ==========

	private void load() {
		if (configFile.exists() && configFile.length() > 0) {
			try (FileInputStream fis = new FileInputStream(configFile);
					InputStreamReader reader = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
				properties.load(reader);
			} catch (IOException e) {
				log.error("加载配置文件失败: {}", configFile.getAbsolutePath(), e);
			}
		}
	}

	private void save() {
		try (FileOutputStream fos = new FileOutputStream(configFile);
				OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
			properties.store(writer, "Video Manager - User Configuration");
		} catch (IOException e) {
			log.error("保存配置文件失败: {}", configFile.getAbsolutePath(), e);
		}
	}

	/**
	 * 从旧版 theme.properties 迁移主题配置到 config.properties
	 * <p>
	 * 迁移完成后删除旧文件。
	 * </p>
	 */
	private void migrateFromOldThemeConfig() {
		File oldFile = AppConfig.getOldThemeFile();
		if (!oldFile.exists()) {
			return;
		}

		log.info("检测到旧版主题配置文件，开始迁移...");
		Properties oldProps = new Properties();
		try (FileInputStream fis = new FileInputStream(oldFile);
				InputStreamReader reader = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
			oldProps.load(reader);

			// 仅在 config.properties 中尚未设置主题时才迁移
			String themeValue = oldProps.getProperty(KEY_THEME);
			if (themeValue != null && !properties.containsKey(KEY_THEME)) {
				properties.setProperty(KEY_THEME, themeValue);
				save();
				log.info("已迁移主题配置: theme={}", themeValue);
			}

			// 删除旧文件
			if (oldFile.delete()) {
				log.info("已删除旧版主题配置文件: {}", oldFile.getAbsolutePath());
			}
		} catch (IOException e) {
			log.error("迁移旧版主题配置失败", e);
		}
	}
}
