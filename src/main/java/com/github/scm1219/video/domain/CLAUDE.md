# Domain 模块 - 领域层

[根目录](../../../../../CLAUDE.md) > [src](../../../../) > [main](../../../) > [java](../../) > [com](../) > [github](../../) > [scm1219](../../../) > [video](../../../../) > **domain**

---

## 变更记录 (Changelog)

### 2026-06-10
- **新增本地统一索引缓存功能**
  - `IndexCacheManager` - 新建缓存管理器（单例），管理本地索引副本和注册表
  - `IndexRepository` 新增 `disk_meta` 表及 `getMeta/setMeta/getDiskUuid/getDiskName` 方法
  - `Disk` 新增 `uuid`/`displayName` 字段、`ensureUuid()`/`syncToCache()` 方法
  - `Index.create()` 在索引创建时自动确保 UUID 生成
  - `DiskManager` 新增 `searchAllFilesWithDiskInfo()`/`searchAllDirsWithDiskInfo()` 方法
  - `DiskManager.SearchResultItem` 内部类封装搜索结果元数据（磁盘名、在线/离线状态）
  - `DiskManager.loadDisks()` 自动调用 `syncToCache()` 同步缓存
  - 缓存目录：`~/.video-manager/indexes/{uuid}.sqlite`
  - 注册表：`~/.video-manager/registry.json`（Properties 格式）

### 2026-02-01 11:05:55
- **新增索引验证和清理功能**
  - `Index.validateAndCleanup()` - 验证索引记录并删除无效记录
  - 支持取消操作（每100条记录检查一次）
  - 返回详细统计信息（总记录数、删除数、耗时）
- **新增索引取消异常类**
  - `IndexCancelledException` - 索引操作取消专用异常
- **完善索引取消功能**
  - `Index.cancel()` 方法已实现
  - `Disk.findVideoDir()` 增加取消检查点
- **Disk 新增验证入口**
  - `Disk.performValidateAndCleanup()` - GUI 调用入口
- **新增取消机制**
  - `volatile boolean isCancelled` 标志位
  - `checkCancelled()` 方法检查并抛出异常
  - `Connection activeConnection` 用于事务控制

### 2026-01-29 18:35:16
- 初始化领域层模块文档
- 识别核心领域模型：Disk、Index、DiskManager

---

## 模块职责

**领域层（Domain Layer）** 负责封装应用程序的核心业务逻辑和数据模型，独立于 GUI 层和工具层。

### 核心职责
- 管理磁盘列表和索引状态
- 创建和更新视频文件索引
- 提供跨磁盘文件搜索功能
- 解析磁盘 SMART 健康信息
- 验证和清理索引中的无效记录
- 支持索引操作的取消机制

---

## 入口与启动

### 主入口类
- **DiskManager** (`DiskManager.java`)
  - 单例模式，管理所有已识别的磁盘
  - 提供全局搜索接口：`searchAllFiles()`、`searchAllDirs()`

### 初始化流程
```java
// 在 VideoManagerApp.main() 中
DiskManager m = DiskManager.getInstance();
m.loadDisks();  // 扫描所有磁盘根目录，查找 .disk.needindex 标记
```

---

## 对外接口

### DiskManager
```java
// 获取单例实例
public static DiskManager getInstance()

// 重新加载磁盘列表
public void reloadDisks()

// 根据文件查找所属磁盘
public Disk findDisk(File file)

// 跨磁盘搜索文件
public List<File> searchAllFiles(String fileName)
public List<File> searchAllDirs(String dirName)

// 获取磁盘列表（只读）
public List<Disk> listDisk()
```

### Disk
```java
// 获取磁盘根目录
public File getRoot()

// 获取索引对象
public Index getIndex()

// 检查是否需要索引
public boolean needIndex()

// 列出包含视频的目录
public List<File> listVideoDir(JProgressBar bar)

// 创建完整磁盘索引
public void createIndex()

// 初始化空数据库表
public void initEmptyDatabase()

// 执行索引验证和清理（新增）
public void performValidateAndCleanup(FileExplorerWindow window)
```

### Index
```java
// 检查索引是否存在
public boolean exists()

// 检查是否正在索引
public synchronized boolean isIndexing()

// 创建完整磁盘索引
public void create(Disk disk)

// 为指定目录创建索引（支持进度条）
public IndexStatistics createForDirectory(File directory, JProgressBar bar)

// 验证并清理索引中的无效记录（新增）
public IndexStatistics validateAndCleanup(JProgressBar bar)

// 取消索引操作（已实现）
public void cancel(FileExplorerWindow window)

// 搜索文件
public List<File> findFiles(String name)
public List<File> findDirs(String dirName)

// 获取索引信息字符串
public String getInfoString()

// 初始化空数据库表
public void initEmptyTables()
```

---

## 关键依赖与配置

### 依赖库
- **SQLite JDBC** (3.51.1.0)：嵌入式数据库
- **OpenCC4j** (1.14.0)：简繁体转换
- **Lombok**：注解生成样板代码
- **SLF4J**：日志接口

### 数据库 Schema
```sql
CREATE TABLE files (
    fileName VARCHAR(255),   -- 文件名（简体中文、小写）
    dirName VARCHAR(255),    -- 所属目录名（简体中文、小写）
    filePath VARCHAR(255),   -- 文件完整路径（无盘符）
    dirPath VARCHAR(255)     -- 目录完整路径（无盘符）
);

CREATE INDEX idx_filename ON files (fileName);
CREATE INDEX idx_dirname ON files (dirName);
```

### 索引文件位置
- **标记文件**：`<磁盘根目录>/.disk.needindex`（空文件，标记需要索引）
- **索引文件**：`<磁盘根目录>/.disk.sqlite`（SQLite 数据库）
- **锁文件**：`~/.video-manager/.lock`（单实例锁）

### 路径处理策略
- **便携性设计**：所有路径去除盘符前缀（如 `C:\`）
- **统一分隔符**：使用 `/` 替代 `\`
- **中文标准化**：使用 OpenCC4j 转换为简体中文
- **大小写统一**：所有文件名转为小写

---

## 数据模型

### Disk（磁盘实体）
```java
public class Disk {
    private File disk;              // 磁盘根目录
    private Index index;            // 索引对象
    private boolean needIndex;      // 是否需要索引

    // 常量
    public static final String FLAF_FILE = ".disk.needindex";
    public static final String INDEX_FILE = ".disk.sqlite";
}
```

### Index（索引实体）
```java
public class Index {
    private File indexFile;         // 索引文件路径
    private boolean exists;         // 索引是否存在
    private boolean isIndexing;     // 是否正在索引

    // 新增字段
    private File backupFile;                    // 备份文件引用（用于取消回滚）
    private volatile boolean isCancelled;        // 取消标志位
    private Connection activeConnection;         // 当前活跃的数据库连接
    private int scannedFileCount;                // 实际扫描的文件计数器

    // 内部类
    public static class IndexStatistics {
        private int totalCount;      // 扫描文件总数
        private int addedCount;      // 新增文件数
        private int deletedCount;    // 删除记录数
        private long scanTime;       // 扫描耗时(ms)
    }
}
```

### DiskManager（磁盘管理器）
```java
public class DiskManager {
    private static DiskManager instance;  // 单例实例
    private List<Disk> disks;             // 磁盘列表

    // 预热 OpenCC4j（避免首次调用延迟）
    static {
        new Thread(() -> ZhConverterUtil.toSimple("test")).start();
    }
}
```

### SmartInfo（磁盘健康信息）
```java
public class SmartInfo {
    private int id;
    private String attributeName;
    private int flag;
    private long value;
    private long worst;
    private long thresh;
    private String whenFailed;
    private String rawValue;

    // 解析 smartctl 输出
    public static SmartInfo parseFromSmartCtl(String line)
}
```

### IndexCancelledException（索引取消异常）
```java
public class IndexCancelledException extends RuntimeException {
    // 索引操作取消专用异常
    // 用于区别于其他运行时异常，便于上层精确处理
}
```

---

## 核心功能实现

### 1. 索引验证和清理（2026-02-01 新增）✨

**功能描述**：遍历所有索引记录，检查对应的文件是否真实存在，删除无效记录。

**实现原理**：
1. 获取所有索引记录的总数
2. 逐条查询文件路径，检查文件是否存在
3. 收集无效记录的路径
4. 使用事务批量删除无效记录
5. 每处理100条记录检查一次取消标志

**代码位置**：`Index.validateAndCleanup()` (行 779-900+)

**关键代码片段**：
```java
public IndexStatistics validateAndCleanup(JProgressBar bar) {
    long startTime = System.currentTimeMillis();
    IndexStatistics stats = new IndexStatistics();

    try(Connection conn = getConnection()) {
        conn.setAutoCommit(false);

        // 步骤1：查询所有索引记录
        List<String> invalidPaths = new ArrayList<>();
        int totalRecords = 0;
        int checkedCount = 0;

        // 获取总记录数
        try(Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM files");
            if(rs.next()) {
                totalRecords = rs.getInt(1);
            }
        }

        // 查询所有文件路径并验证
        String sql = "SELECT filePath FROM files";
        try(Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql)) {

            while(rs.next()) {
                String filePath = rs.getString(1);
                String fullPath = currentDrive + ":" + filePath;
                File file = new File(fullPath);
                checkedCount++;

                // 更新进度
                if(bar != null && totalRecords > 0) {
                    int progress = checkedCount * 100 / totalRecords;
                    bar.setValue(progress);
                    bar.setString("验证中 " + checkedCount + "/" + totalRecords);
                }

                // 检查文件是否存在
                if(!file.exists()) {
                    invalidPaths.add(filePath);
                    log.debug("发现无效记录: {}", fullPath);
                }

                // 每100条记录检查一次取消
                if(checkedCount % 100 == 0) {
                    checkCancelled();
                }
            }
        }

        // 步骤2：删除无效记录
        if(!invalidPaths.isEmpty()) {
            stats.setDeletedCount(invalidPaths.size());

            try(PreparedStatement pstmt = conn.prepareStatement(
                    "DELETE FROM files WHERE filePath = ?")) {

                int count = 0;
                for(String path : invalidPaths) {
                    pstmt.setString(1, path);
                    pstmt.addBatch();

                    if(++count % 1000 == 0) {
                        pstmt.executeBatch();
                    }
                }
                pstmt.executeBatch();
            }

            conn.commit();
        }

        stats.setTotalCount(totalRecords);
        stats.setScanTime(System.currentTimeMillis() - startTime);
    }

    return stats;
}
```

**统计信息**：
- 总记录数
- 删除的无效记录数
- 总耗时（毫秒）

---

### 2. 索引取消机制（2026-02-01 完善）✨

**功能描述**：支持用户在索引进度窗口中取消正在进行的索引操作。

**实现原理**：
1. `Index` 类维护 `volatile boolean isCancelled` 标志位
2. `cancel()` 方法设置取消标志并关闭数据库连接
3. 索引循环中定期调用 `checkCancelled()` 检查标志
4. 如果检测到取消，抛出 `IndexCancelledException` 终止操作

**代码位置**：
- `Index.cancel()` - 取消方法
- `Index.checkCancelled()` - 检查方法
- `Disk.findVideoDir()` - 检查点（行 74-78）

**关键代码片段**：
```java
// 取消方法
public void cancel(FileExplorerWindow window) {
    isCancelled = true;
    log.info("索引操作已被取消");

    // 关闭活跃的数据库连接以终止操作
    if(activeConnection != null) {
        try {
            activeConnection.close();
            log.info("已关闭活跃的数据库连接");
        } catch (SQLException e) {
            log.error("关闭数据库连接时发生异常", e);
        }
    }
}

// 检查方法
private void checkCancelled() {
    if(isCancelled) {
        log.info("检测到取消标志，抛出 IndexCancelledException");
        throw new IndexCancelledException();
    }
}

// 在索引循环中检查
public List<File> listVideoDir(JProgressBar bar) {
    File base = disk;
    List<File> result = new ArrayList<>();
    findVideoDir(base, result, bar, true);
    return result;
}

private boolean findVideoDir(File parent, List<File> result,
                             JProgressBar bar, boolean isTop) {
    File[] subDirs = parent.listFiles();
    boolean currentVideo = hasVideoFiles(parent);

    if(subDirs != null) {
        for(File subDir : subDirs) {
            // 检查是否取消（每个目录都检查）
            if(index.isCancelled()) {
                log.info("已经取消检查");
                throw new IndexCancelledException();
            }

            if(bar != null && isTop) {
                bar.setString("检查" + subDir.getAbsolutePath() + "是否需要扫描");
            }
            if(subDir.isDirectory()) {
                boolean subHasVideo = findVideoDir(subDir, result, bar, false);
                if(subHasVideo) {
                    currentVideo = true;
                }
            }
        }
    }

    if(currentVideo) {
        result.add(parent);
    }
    return currentVideo;
}
```

---

### 3. 目录级索引创建（2026-01-27）✨

**功能描述**：为指定目录创建索引，支持增量更新和进度显示。

**实现原理**：
1. 先删除该目录下的所有旧索引记录
2. 递归扫描目录，收集所有视频文件
3. 批量插入新记录到数据库
4. 返回详细统计信息

**代码位置**：`Index.createForDirectory()` (行 324-454)

**关键代码片段**：
```java
public IndexStatistics createForDirectory(File directory, JProgressBar bar) {
    long startTime = System.currentTimeMillis();
    IndexStatistics stats = new IndexStatistics();

    try(Connection conn = getConnection()) {
        conn.setAutoCommit(false);

        String relativeDirPath = getRelativePath(directory);
        String drive = getDriveLetter(directory);

        // 步骤1：删除该目录下的旧索引记录
        String deleteSql = "DELETE FROM files WHERE dirPath = ?";
        try(PreparedStatement pstmt = conn.prepareStatement(deleteSql)) {
            pstmt.setString(1, relativeDirPath);
            int deletedCount = pstmt.executeUpdate();
            stats.setDeletedCount(deletedCount);
            log.info("删除旧索引记录: {} 条", deletedCount);
        }

        // 步骤2：递归扫描目录
        List<File> videoFiles = new ArrayList<>();
        scanDirectoryForVideos(directory, videoFiles, bar);
        stats.setTotalCount(videoFiles.size());

        // 步骤3：批量插入新记录
        String insertSql = "INSERT INTO files (fileName, dirName, filePath, dirPath) VALUES (?, ?, ?, ?)";
        try(PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
            int count = 0;
            for(File videoFile : videoFiles) {
                String fileName = ZhConverterUtil.toSimple(videoFile.getName().toLowerCase());
                String dirName = ZhConverterUtil.toSimple(videoFile.getParentFile().getName().toLowerCase());
                String filePath = getRelativePath(videoFile);
                String dirPath = getRelativePath(videoFile.getParentFile());

                pstmt.setString(1, fileName);
                pstmt.setString(2, dirName);
                pstmt.setString(3, filePath);
                pstmt.setString(4, dirPath);
                pstmt.addBatch();

                if(++count % 1000 == 0) {
                    pstmt.executeBatch();
                }

                // 更新进度
                if(bar != null) {
                    int progress = count * 100 / videoFiles.size();
                    bar.setValue(progress);
                    bar.setString("索引中 " + count + "/" + videoFiles.size());
                }
            }
            pstmt.executeBatch();
        }

        conn.commit();
        stats.setAddedCount(videoFiles.size());
        stats.setScanTime(System.currentTimeMillis() - startTime);
    }

    return stats;
}
```

**统计信息**：
- 扫描文件总数
- 新增文件数
- 删除旧记录数
- 扫描耗时（毫秒）

---

## 测试与质量

### 当前状态
- **无单元测试**：缺少 JUnit 测试用例
- **手动测试**：通过 GUI 运行验证功能

### 测试建议
1. **DiskManagerTest**
   - 测试磁盘加载逻辑
   - 测试跨磁盘搜索
   - 测试并发安全性（`parallelStream`）

2. **IndexTest**
   - 测试索引创建
   - 测试文件搜索（包含中文）
   - 测试路径处理（去盘符、转分隔符）
   - 测试索引验证和清理功能
   - 测试索引取消机制

3. **DiskTest**
   - 测试标记文件检测
   - 测试视频目录发现
   - 测试索引验证入口

---

## 常见问题 (FAQ)

### Q1: 为什么使用相对路径存储索引？
**A**: 为了实现索引的便携性。移动硬盘在不同电脑上可能挂载到不同盘符（如 `E:\` 变成 `F:\`），使用相对路径可以让索引文件保持有效。

### Q2: 索引验证如何检查文件是否存在？
**A**: 通过拼接当前盘符和索引中的相对路径，构造完整路径后调用 `File.exists()` 检查。

### Q3: 索引取消机制如何工作？
**A**:
1. 用户点击"取消"按钮 → 调用 `Index.cancel()`
2. 设置 `isCancelled = true` 并关闭数据库连接
3. 索引循环中调用 `checkCancelled()` 检查标志
4. 如果已取消，抛出 `IndexCancelledException`
5. GUI 捕获异常并显示"已取消"消息

### Q4: 索引创建时如何处理重复文件？
**A**: 当前逻辑是"先删除后插入"（`createForDirectory` 方法），确保索引与实际文件系统一致。全盘索引则是清空表后重建。

### Q5: 为什么使用 OpenCC4j 预热？
**A**: OpenCC4j 首次调用有初始化开销。在 `DiskManager` 静态块中启动线程预热，避免用户搜索时等待。

### Q6: 并发搜索是否安全？
**A**: `searchAllFiles()` 使用 `parallelStream()` 和 `Collections.synchronizedList()`，保证线程安全。但索引创建使用 `synchronized` 锁，防止并发索引。

### Q7: 如何支持新的视频格式？
**A**: 修改 `VideoFileFilter` 类的 `videoExtends` 数组，添加新扩展名（如 `.avi`、`.mov`）。

### Q8: 索引验证的性能如何优化？
**A**:
- 使用事务批量删除无效记录（每1000条提交一次）
- 每100条记录检查一次取消标志
- 使用 `PreparedStatement` 批量操作提升性能

---

## 相关文件清单

### 核心文件
- `DiskManager.java` - 磁盘管理器（单例）
- `Disk.java` - 磁盘实体
- `Index.java` - 索引管理（900+ 行，含验证和清理）
- `SmartInfo.java` - SMART 信息解析
- `IndexCancelledException.java` - 索引取消异常（新增）

### 工具类依赖
- `../../utils/FileUtils.java` - 文件操作工具
- `../../utils/VideoFileFilter.java` - 视频文件过滤器
- `../../utils/DateUtils.java` - 日期格式化

### GUI 层调用
- `../gui/FileExplorerWindow.java` - 主窗口（使用 DiskManager）
- `../gui/tree/FileTree.java` - 文件树（使用 Disk）
- `../gui/tree/FileUpdateProcesser.java` - 索引进度窗口（使用 Index）
- `../gui/tree/IndexValidationProcesser.java` - 索引验证窗口（使用 Index.validateAndCleanup）(新增)

### 主入口
- `../VideoManagerApp.java` - 主程序入口
- `../AppLock.java` - 单实例锁（新增）

---

## 下一步改进建议

1. **添加单元测试**：使用 JUnit 5 + H2 内存数据库测试索引逻辑
2. **性能优化**：大目录索引使用多线程
3. **错误处理**：磁盘拔出时的异常处理
4. **功能扩展**：支持增量索引（仅扫描变更文件）
5. **验证优化**：支持后台定时验证，自动清理无效记录
