package com.github.scm1219.video.domain;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.filechooser.FileSystemView;

import com.github.houbb.opencc4j.util.ZhConverterUtil;
import com.github.scm1219.video.domain.IndexCacheManager.CachedSearchResult;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DiskManager {

    private static DiskManager instance = new DiskManager();
    private static FileSystemView fileSystemView = FileSystemView.getFileSystemView();

    // 预热 OpenCC4j（首次调用有初始化开销，避免用户搜索时等待）
    static {
        Thread warmupThread = new Thread(() -> ZhConverterUtil.toSimple("test"));
        warmupThread.setDaemon(true);
        warmupThread.start();
    }

    public static DiskManager getInstance() {
        return instance;
    }

    private DiskManager() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            log.error("SQLite JDBC 驱动加载失败", e);
        }
        log.info("sqlite jdbc 驱动加载完成");
    }

    private final List<Disk> disks = new ArrayList<>();
    private final Map<String, Disk> diskMap = new HashMap<>();

    public void loadDisks() {
        diskMap.clear();
        File[] f = File.listRoots();
        for (File file : f) {
            Disk disk = new Disk(file);
            if (disk.needIndex()) {
                if (!disks.contains(disk)) {
                    disks.add(disk);
                }
                diskMap.put(disk.getPath().substring(0, 2).toUpperCase(), disk);
            } else {
                log.info("因未发现" + Disk.FLAG_FILE + "文件，忽略磁盘" + disk.getPath());
            }
        }
        Collections.sort(disks, (o1, o2) -> {
            String d1 = fileSystemView.getSystemDisplayName(o1.getRoot());
            String d2 = fileSystemView.getSystemDisplayName(o2.getRoot());
            return d1.compareTo(d2);
        });

        // 自动同步已挂载磁盘的索引到本地缓存
        syncMountedDisks();
    }

    /**
     * 异步同步所有已挂载磁盘的索引到本地缓存
     */
    private void syncMountedDisks() {
        CompletableFuture.runAsync(() -> {
            log.info("开始异步同步磁盘索引缓存...");
            for (Disk disk : disks) {
                if (disk.hasIndex()) {
                    try {
                        disk.syncToCache();
                    } catch (Exception e) {
                        log.warn("同步磁盘索引缓存失败: {}", disk.getPath(), e);
                    }
                }
            }
            log.info("异步同步磁盘索引缓存完成");
        });
    }

    public Disk findDisk(File file) {
        String path = file.getAbsolutePath();
        if (path.length() >= 2) {
            return diskMap.get(path.substring(0, 2).toUpperCase());
        }
        return null;
    }

    /**
     * 跨磁盘搜索文件（包含在线和离线结果）
     * <p>
     * 已挂载磁盘查询实时数据，未挂载磁盘查询本地缓存。
     * 返回结果通过 SearchResultItem 包装，携带磁盘名和在线/离线状态。
     * </p>
     *
     * @param fileName 文件名关键词
     * @return 搜索结果列表
     */
    public List<SearchResultItem> searchAllFilesWithDiskInfo(String fileName) {
        List<SearchResultItem> allResults = Collections.synchronizedList(new ArrayList<>());
        Set<String> mountedUuids = ConcurrentHashMap.newKeySet();

        // 第一阶段：搜索已挂载磁盘（实时数据）
        disks.parallelStream().forEach(disk -> {
            List<File> findFiles = disk.getIndex().findFiles(fileName);
            String diskName = disk.getDisplayName();
            String uuid = disk.getUuid();
            if (uuid != null) {
                mountedUuids.add(uuid);
            }
            for (File file : findFiles) {
                allResults.add(new SearchResultItem(file, diskName, true));
            }
        });

        // 第二阶段：搜索未挂载磁盘的本地缓存
        List<CachedSearchResult> offlineResults = IndexCacheManager.getInstance()
                .searchOfflineFiles(fileName, mountedUuids);
        for (CachedSearchResult cr : offlineResults) {
            // 离线文件使用相对路径构造 File 对象（仅用于展示，不可访问）
            File offlineFile = new File(cr.relativePath);
            allResults.add(new SearchResultItem(offlineFile, cr.diskName, false));
        }

        return allResults;
    }

    /**
     * 跨磁盘搜索目录（包含在线和离线结果）
     *
     * @param dirName 目录名关键词
     * @return 搜索结果列表
     */
    public List<SearchResultItem> searchAllDirsWithDiskInfo(String dirName) {
        List<SearchResultItem> allResults = Collections.synchronizedList(new ArrayList<>());
        Set<String> mountedUuids = ConcurrentHashMap.newKeySet();

        // 已挂载磁盘
        disks.parallelStream().forEach(disk -> {
            List<File> findFiles = disk.getIndex().findDirs(dirName);
            String diskName = disk.getDisplayName();
            String uuid = disk.getUuid();
            if (uuid != null) {
                mountedUuids.add(uuid);
            }
            for (File file : findFiles) {
                allResults.add(new SearchResultItem(file, diskName, true));
            }
        });

        // 未挂载磁盘的本地缓存
        List<CachedSearchResult> offlineResults = IndexCacheManager.getInstance()
                .searchOfflineDirs(dirName, mountedUuids);
        for (CachedSearchResult cr : offlineResults) {
            File offlineFile = new File(cr.relativePath);
            allResults.add(new SearchResultItem(offlineFile, cr.diskName, false));
        }

        return allResults;
    }

    /**
     * 兼容旧接口：跨磁盘搜索文件（仅返回 File 列表）
     */
    public List<File> searchAllFiles(String fileName) {
        List<File> allFiles = Collections.synchronizedList(new ArrayList<>());
        disks.parallelStream().forEach(disk -> {
            List<File> findFiles = disk.getIndex().findFiles(fileName);
            allFiles.addAll(findFiles);
        });
        return allFiles;
    }

    public List<File> searchAllDirs(String dirName) {
        List<File> allFiles = Collections.synchronizedList(new ArrayList<>());
        disks.parallelStream().forEach(disk -> {
            List<File> findFiles = disk.getIndex().findDirs(dirName);
            allFiles.addAll(findFiles);
        });
        return allFiles;
    }

    public List<Disk> listDisk() {
        return Collections.unmodifiableList(disks);
    }

    public void reloadDisks() {
        disks.clear();
        loadDisks();
    }

    /**
     * 搜索结果项（包含磁盘信息和在线/离线状态）
     */
    public static class SearchResultItem {
        /** 文件对象（在线为完整路径，离线为相对路径） */
        public final File file;
        /** 磁盘显示名称 */
        public final String diskName;
        /** 是否在线（磁盘已挂载） */
        public final boolean online;

        public SearchResultItem(File file, String diskName, boolean online) {
            this.file = file;
            this.diskName = diskName;
            this.online = online;
        }
    }
}
