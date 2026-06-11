package com.github.scm1219.video.domain;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.sql.Connection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import com.github.scm1219.video.AppConfig;

import lombok.extern.slf4j.Slf4j;

/**
 * 本地索引缓存管理器
 * <p>
 * 管理移动硬盘索引文件在本地用户目录的缓存副本，
 * 支持磁盘挂载时自动同步和离线搜索。
 * </p>
 *
 * <p>注册表使用 Properties 格式存储，键格式：</p>
 * <ul>
 *   <li>{@code {uuid}.name} → 磁盘显示名称</li>
 *   <li>{@code {uuid}.lastSync} → 最后同步时间</li>
 * </ul>
 *
 * @author scm12
 */
@Slf4j
public class IndexCacheManager {

    private static final IndexCacheManager instance = new IndexCacheManager();

    private Properties registry;

    private IndexCacheManager() {
        loadRegistry();
    }

    public static IndexCacheManager getInstance() {
        return instance;
    }

    // ========== 注册表操作 ==========

    private void loadRegistry() {
        registry = new Properties();
        File registryFile = AppConfig.getRegistryFile();
        if (registryFile.exists() && registryFile.length() > 0) {
            try (FileInputStream fis = new FileInputStream(registryFile);
                    InputStreamReader reader = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
                registry.load(reader);
            } catch (IOException e) {
                log.error("加载注册表失败", e);
            }
        }
    }

    private void saveRegistry() {
        File registryFile = AppConfig.getRegistryFile();
        try (FileOutputStream fos = new FileOutputStream(registryFile);
                OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            registry.store(writer, "Video Manager - Disk Index Registry");
        } catch (IOException e) {
            log.error("保存注册表失败", e);
        }
    }

    /**
     * 更新注册表中的磁盘信息
     *
     * @param uuid     磁盘 UUID
     * @param diskName 磁盘显示名称
     */
    public void updateRegistry(String uuid, String diskName) {
        registry.setProperty(uuid + ".name", diskName);
        registry.setProperty(uuid + ".lastSync", java.time.LocalDateTime.now().toString());
        saveRegistry();
        log.info("更新注册表: uuid={}, name={}", uuid, diskName);
    }

    /**
     * 获取所有已缓存的磁盘 UUID 列表
     */
    public Set<String> getCachedUuids() {
        return registry.stringPropertyNames().stream()
                .filter(key -> key.endsWith(".name"))
                .map(key -> key.substring(0, key.length() - 5))
                .collect(Collectors.toSet());
    }

    /**
     * 根据 UUID 获取磁盘显示名称
     *
     * @param uuid 磁盘 UUID
     * @return 磁盘名，未找到返回 "未知磁盘"
     */
    public String getDiskNameByUuid(String uuid) {
        return registry.getProperty(uuid + ".name", "未知磁盘");
    }

    /**
     * 根据 UUID 获取缓存索引文件路径
     *
     * @param uuid 磁盘 UUID
     * @return 缓存文件，不存在返回 null
     */
    public File getCachedIndexFile(String uuid) {
        File file = new File(AppConfig.getIndexesDir(), uuid + ".sqlite");
        return file.exists() ? file : null;
    }

    /**
     * 获取最后同步时间
     *
     * @param uuid 磁盘 UUID
     * @return 最后同步时间字符串，未找到返回 null
     */
    public String getLastSyncTime(String uuid) {
        return registry.getProperty(uuid + ".lastSync");
    }

    // ========== 同步操作 ==========

    /**
     * 将磁盘的索引文件同步到本地缓存目录
     * <p>
     * 通过比对 disk_meta 中的 last_modified 时间戳判断是否需要复制。
     * 如果源索引无变化（时间戳与缓存一致），跳过文件复制。
     * </p>
     *
     * @param disk 已挂载的磁盘对象
     */
    public void syncToLocal(Disk disk) {
        if (disk.getUuid() == null) {
            log.warn("磁盘 {} 缺少 UUID，跳过同步", disk.getPath());
            return;
        }

        File sourceFile = new File(disk.getPath() + Disk.INDEX_FILE);
        if (!sourceFile.exists() || sourceFile.length() == 0) {
            log.warn("磁盘 {} 的索引文件不存在或为空，跳过同步", disk.getPath());
            return;
        }

        File targetFile = new File(AppConfig.getIndexesDir(), disk.getUuid() + ".sqlite");

        // 比对 last_modified 时间戳
        String sourceLastModified = readLastModified(sourceFile);
        if (targetFile.exists() && sourceLastModified != null) {
            String cachedLastModified = readLastModified(targetFile);
            if (sourceLastModified.equals(cachedLastModified)) {
                log.debug("索引无变化，跳过同步: {}", disk.getDisplayName());
                return;
            }
        }

        try {
            Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            updateRegistry(disk.getUuid(), disk.getDisplayName());
            log.info("同步索引完成: {} → {}", sourceFile.getAbsolutePath(), targetFile.getAbsolutePath());
        } catch (IOException e) {
            log.error("同步索引失败: {}", sourceFile.getAbsolutePath(), e);
        }
    }

    /**
     * 读取索引文件中 disk_meta 表的 last_modified 值
     *
     * @param indexFile 索引文件
     * @return last_modified 值，不存在或读取失败返回 null
     */
    private String readLastModified(File indexFile) {
        try {
            IndexRepository repo = new IndexRepository(indexFile);
            try (Connection conn = repo.getConnection()) {
                return repo.getLastModified(conn);
            }
        } catch (Exception e) {
            log.debug("读取 last_modified 失败: {}", indexFile.getAbsolutePath(), e);
            return null;
        }
    }

    // ========== 离线搜索 ==========

    /**
     * 在本地缓存的离线索引中搜索文件
     *
     * @param keyword          搜索关键词（已标准化）
     * @param mountedDiskUuids 已挂载磁盘的UUID集合（排除在线磁盘，避免重复）
     * @return 搜索结果列表
     */
    public List<CachedSearchResult> searchOfflineFiles(String keyword, Set<String> mountedDiskUuids) {
        List<CachedSearchResult> allResults = new ArrayList<>();

        for (String uuid : getCachedUuids()) {
            if (mountedDiskUuids != null && mountedDiskUuids.contains(uuid)) {
                continue;
            }

            File cachedFile = getCachedIndexFile(uuid);
            if (cachedFile == null) {
                continue;
            }

            String diskName = getDiskNameByUuid(uuid);
            IndexRepository repository = new IndexRepository(cachedFile);

            try {
                List<String> paths = repository.findFilePathsByName(keyword);
                for (String path : paths) {
                    allResults.add(new CachedSearchResult(uuid, diskName, path, false));
                }
            } catch (Exception e) {
                log.error("离线搜索失败: uuid={}, diskName={}", uuid, diskName, e);
            }
        }

        return allResults;
    }

    /**
     * 在本地缓存的离线索引中搜索目录
     *
     * @param keyword          搜索关键词（已标准化）
     * @param mountedDiskUuids 已挂载磁盘的UUID集合
     * @return 搜索结果列表
     */
    public List<CachedSearchResult> searchOfflineDirs(String keyword, Set<String> mountedDiskUuids) {
        List<CachedSearchResult> allResults = new ArrayList<>();

        for (String uuid : getCachedUuids()) {
            if (mountedDiskUuids != null && mountedDiskUuids.contains(uuid)) {
                continue;
            }

            File cachedFile = getCachedIndexFile(uuid);
            if (cachedFile == null) {
                continue;
            }

            String diskName = getDiskNameByUuid(uuid);
            IndexRepository repository = new IndexRepository(cachedFile);

            try {
                List<String> paths = repository.findDistinctDirPathsByName(keyword);
                for (String path : paths) {
                    allResults.add(new CachedSearchResult(uuid, diskName, path, false));
                }
            } catch (Exception e) {
                log.error("离线搜索目录失败: uuid={}, diskName={}", uuid, diskName, e);
            }
        }

        return allResults;
    }

    // ========== 清理操作 ==========

    /**
     * 清理本地缓存中指定磁盘的数据
     *
     * @param uuid 磁盘 UUID
     */
    public void removeCache(String uuid) {
        File cachedFile = new File(AppConfig.getIndexesDir(), uuid + ".sqlite");
        if (cachedFile.exists()) {
            cachedFile.delete();
        }
        registry.remove(uuid + ".name");
        registry.remove(uuid + ".lastSync");
        saveRegistry();
        log.info("已清理缓存: uuid={}", uuid);
    }

    // ========== 数据结构 ==========

    /**
     * 缓存搜索结果
     */
    public static class CachedSearchResult {
        /** 磁盘 UUID */
        public final String uuid;
        /** 磁盘显示名称 */
        public final String diskName;
        /** 文件相对路径（无盘符） */
        public final String relativePath;
        /** 是否在线 */
        public final boolean online;

        public CachedSearchResult(String uuid, String diskName, String relativePath, boolean online) {
            this.uuid = uuid;
            this.diskName = diskName;
            this.relativePath = relativePath;
            this.online = online;
        }
    }
}
