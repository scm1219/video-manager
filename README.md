# Video Manager

一个用于在多个移动硬盘上快速查找和管理视频文件的桌面应用程序。

## 功能特性

- **视频文件索引**: 快速扫描和索引多个移动硬盘上的视频文件
- **跨磁盘搜索**: 支持在多个磁盘间统一搜索视频
- **SQLite 数据库**: 每个磁盘拥有独立的索引文件，基于相对路径实现磁盘索引的可移植性
- **图形化界面**: 提供树形视图和表格视图，方便浏览和管理视频文件
- **SMART 信息检测**: 可选功能，支持检测磁盘健康状态
- **中文支持**: 支持中文文件名和简体中文界面

## 系统要求

- Java 21 或更高版本
- Windows 操作系统
- 64 位 JDK/JRE

## 使用方法

### 基本使用

1. **创建索引标记文件**
   
   将 `.disk.needindex` 文件复制到需要索引的分区根目录，或者在该目录下创建一个名为 `.disk.needindex` 的空文件。

2. **运行程序**
   
   ```bash
   java -jar video-manager-0.0.1-SNAPSHOT-shaded.jar
   ```
   
   或直接运行编译后的可执行文件：
   ```bash
   video-manager.exe
   ```

3. **程序将自动**
   - 检测带有 `.disk.needindex` 标记的磁盘
   - 扫描并索引视频文件
   - 在界面上显示文件树和搜索功能

### SMART 信息检测（可选）

如需启用磁盘健康状态检测：

1. 从 [smartmontools 官网](https://www.smartmontools.org) 下载 smartmontools
2. 将 `smartctl` 可执行文件复制到 Windows 的 `system32` 目录下
3. 对于非移动硬盘，SMART 检测可能需要管理员权限，可以以管理员身份运行 video-manager

## 编译打包

项目使用 Maven 进行构建管理，执行以下命令：

```bash
mvn clean package
```

编译成功后，将在 `target` 目录下生成：
- `video-manager-0.0.1-SNAPSHOT-shaded.jar` - 包含所有依赖的可执行 JAR 文件
- `video-manager.exe` - Windows 可执行文件

## 技术栈

- **Java**: 21
- **构建工具**: Maven
- **数据库**: SQLite (sqlite-jdbc 3.51.1.0)
- **GUI 框架**: Java Swing
- **中文转换**: OpenCC4j 1.14.0
- **日志**: SLF4J + Logback
- **工具库**: Apache Commons Lang3, Apache Commons IO

## 项目结构

```
src/
├── main/
│   ├── java/com/github/scm1219/
│   │   ├── utils/           # 工具类
│   │   ├── video/
│   │   │   ├── domain/      # 领域模型
│   │   │   ├── gui/         # 图形界面
│   │   │   └── VideoManagerApp.java  # 主程序入口
│   └── resources/           # 资源文件
└── test/                    # 测试代码
```

## 许可证

Copyright 2022 springtour.cn
