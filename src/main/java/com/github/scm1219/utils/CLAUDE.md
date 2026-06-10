# Utils 模块 - 工具类层

[根目录](../../../../../CLAUDE.md) > [src](../../../../) > [main](../../../) > [java](../../) > [com](../) > [github](../../) > [scm1219](../../../) > **utils**

---

## 变更记录 (Changelog)

### 2026-01-29 18:35:16
- 初始化工具类模块文档
- 识别核心工具：FileUtils、DiskUtils、DateUtils、VideoFileFilter

---

## 模块职责

**工具类层（Utils）** 提供跨模块复用的通用功能，包括文件操作、磁盘信息获取、日期格式化等。

### 核心职责
- 文件类型判断（视频文件识别）
- 文件大小格式化
- 调用系统命令打开文件/文件夹
- 磁盘 SMART 信息获取
- 日期格式化

---

## 入口与启动

### 无需初始化
所有工具类均为静态方法集合，无需实例化：
```java
FileUtils.isVideoFile(file);          // 静态方法
DiskUtils.getSmartInfo(disk);         // 静态方法
DateUtils.getDateString(timestamp);   // 静态方法
```

---

## 对外接口

### FileUtils（文件操作工具）
```java
public class FileUtils {
    // 判断是否为视频文件
    public static boolean isVideoFile(File f);

    // 格式化文件大小（B/K/M/G）
    public static String formetFileSize(long fileS);

    // 打开视频文件（使用系统默认播放器）
    public static void openVideoFile(File f);

    // 打开文件夹
    public static void openDir(File dir);

    // 打开文件夹并选中文件
    public static void openDirAndSelectFile(File file);

    // 批量重命名文件（测试用）
    public static void renameFiles(String dirName, String prefix);
}
```

### DiskUtils（磁盘工具）
```java
public class DiskUtils {
    // 获取磁盘 SMART 信息
    public static String getSmartInfo(Disk disk);

    // 依赖：需要 smartctl.exe 在系统 PATH 中
}
```

### DateUtils（日期工具）
```java
public class DateUtils {
    // 格式化时间戳为字符串
    public static String getDateString(long time);

    // 格式：yyyy-MM-dd HH:mm:ss
}
```

### VideoFileFilter（视频文件过滤器）
```java
public class VideoFileFilter implements FilenameFilter {
    // 实现 FilenameFilter 接口
    public boolean accept(File dir, String name);
}
```

---

## 关键依赖与配置

### 依赖库
- **Apache Commons IO** (2.21.0)：文件集合操作（`FileUtils.listFiles()`）
- **Apache Commons Lang3** (3.20.0)：字符串判断（`StringUtils.isNotBlank()`）
- **SLF4J**：日志记录

### 支持的视频格式
```java
private static String[] videoExtends = {
    ".mp4", ".mkv", ".rm", ".rmvb",
    "wmv", ".flv", ".ogm"
};
```

### 系统命令调用
- **打开文件**：`rundll32 url.dll,FileProtocolHandler <文件路径>`
- **打开文件夹并选中**：`explorer /select,"<文件路径>"`
- **SMART 检测**：`smartctl -d ata -A <盘符>`

---

## 实现细节

### 文件大小格式化
```java
public static String formetFileSize(long fileS) {
    DecimalFormat df = new DecimalFormat("#.00");
    if (fileS < 1024) {
        return df.format(fileS) + "B";
    } else if (fileS < 1048576) {
        return df.format(fileS / 1024) + "K";
    } else if (fileS < 1073741824) {
        return df.format(fileS / 1048576) + "M";
    } else {
        return df.format(fileS / 1073741824) + "G";
    }
}
```

### 视频文件判断
```java
public static boolean isVideoFile(File f) {
    String fileName = f.getName().toLowerCase();
    for (String ext : videoExtends) {
        if (fileName.endsWith(ext)) {
            return true;
        }
    }
    return false;
}
```

### 打开文件夹并选中文件（Windows）
```java
public static void openDirAndSelectFile(File file) {
    String command = "explorer /select,\"" + filePath + "\"";
    ProcessBuilder processBuilder = new ProcessBuilder("cmd", "/c", command);
    processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);  // 避免创建 nul 文件
    processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
    processBuilder.start();
}
```

### SMART 信息解析
```java
// 调用 smartctl 命令
ProcessBuilder processBuilder = new ProcessBuilder(
    "smartctl.exe", "-d", "ata", "-A", disk.getVolumeName()
);

// 解析输出（跳过表头，解析关键属性）
SmartInfo smart = SmartInfo.parseFromSmartCtl(line);
String data = smart.getSimpleSmartInfo();  // 仅显示重要属性
```

---

## 测试与质量

### 当前状态
- **无单元测试**：工具类缺少测试覆盖
- **手动测试**：通过 GUI 调用验证功能

### 测试建议
1. **FileUtilsTest**
   - 测试文件大小格式化（边界值：0、1023、1024、1048575、1048576）
   - 测试视频文件识别（大小写、空格、特殊字符）
   - 测试系统命令调用（需要模拟 ProcessBuilder）

2. **DiskUtilsTest**
   - Mock ProcessBuilder 测试 SMART 信息解析
   - 测试异常处理（smartctl 不存在）

3. **DateUtilsTest**
   - 测试日期格式化（时区、边界值）

---

## 常见问题 (FAQ)

### Q1: 为什么使用 `rundll32` 而不是 `Desktop.open()`？
**A**: `Desktop.open()` 在某些 Windows 配置下可能失败或不关联默认播放器。`rundll32` 是更底层的系统调用，兼容性更好。

### Q2: 如何添加新的视频格式？
**A**: 修改 `FileUtils.videoExtends` 数组，添加新扩展名（如 `.avi`、`.mov`）。

### Q3: 为什么重定向输出流到 `DISCARD`？
**A**: 避免创建临时文件（如 `nul`）。旧代码使用 `redirectError(new File("nul"))` 会在项目目录下创建空文件。

### Q4: SMART 检测依赖 smartctl，如何处理用户未安装的情况？
**A**: 当前代码返回 "无法获取SMART数据"。建议改进：检测 smartctl 是否存在，并提示用户安装。

### Q5: 文件大小格式化为什么不用 `FileUtils.byteCountToDisplaySize()`？
**A**: Apache Commons IO 的格式化方法使用国际单位制（KiB、MiB），而国内更习惯使用 K、M、G（1024 进制）。

---

## 相关文件清单

### 核心文件
- `FileUtils.java` - 文件操作工具（视频判断、大小格式化、打开文件）
- `DiskUtils.java` - 磁盘工具（SMART 信息获取）
- `DateUtils.java` - 日期格式化
- `VideoFileFilter.java` - 视频文件过滤器（实现 `FilenameFilter`）

### 调用方
- `../video/domain/Index.java` - 使用 `FileUtils.isVideoFile()`
- `../video/gui/FileExplorerWindow.java` - 使用 `FileUtils.openVideoFile()`、`openDirAndSelectFile()`
- `../video/gui/tree/FileTree.java` - 使用 `DiskUtils.getSmartInfo()`
- `../video/domain/SmartInfo.java` - 使用 `DiskUtils` 解析结果

---

## 下一步改进建议

1. **添加单元测试**：使用 JUnit 5 + Mockito 测试工具类
2. **跨平台支持**：检测操作系统，调用不同的系统命令（macOS/Linux）
3. **异常处理**：改进系统命令调用的错误处理和用户提示
4. **格式化配置**：将视频格式列表提取到配置文件，支持运行时扩展
5. **日志增强**：添加更详细的日志记录（如文件打开失败的原因）
