package com.github.scm1219.video;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.StandardOpenOption;

import lombok.extern.slf4j.Slf4j;

/**
 * 应用程序单实例锁管理器
 *
 * <p>通过文件锁机制确保同一时间只有一个应用实例运行。
 * 使用 Java NIO FileLock 实现 OS 级别的文件锁，保证可靠性。</p>
 *
 * <p>锁文件位置：{user.home}/.video-manager/.lock</p>
 *
 * @author scm1219
 * @since 1.1.0
 */
@Slf4j
public class AppLock {

    /** 单例实例 */
    private static volatile AppLock instance;

    /** 用户目录下的应用配置目录 */
    private static final String APP_DIR = ".video-manager";

    /** 锁文件名称 */
    private static final String LOCK_FILE_NAME = ".lock";

    /** 锁文件对象 */
    private File lockFile;

    /** 文件通道 */
    private FileChannel channel;

    /** 文件锁 */
    private FileLock lock;

    /**
     * 私有构造函数（单例模式）
     */
    private AppLock() {
    }

    /**
     * 获取单例实例（双重检查锁定）
     *
     * @return AppLock 单例实例
     */
    public static AppLock getInstance() {
        if (instance == null) {
            synchronized (AppLock.class) {
                if (instance == null) {
                    instance = new AppLock();
                }
            }
        }
        return instance;
    }

    /**
     * 尝试获取应用程序锁
     *
     * <p>如果成功获取锁，将注册 JVM 关闭钩子以确保锁在程序退出时释放。</p>
     *
     * @return true 如果成功获取锁，false 如果锁已被其他实例持有
     */
    public boolean acquire() {
        try {
            // 1. 确保用户目录存在
            ensureUserDirectoryExists();

            // 2. 创建锁文件对象
            String userHome = System.getProperty("user.home");
            lockFile = new File(userHome, APP_DIR + File.separator + LOCK_FILE_NAME);

            // 3. 直接打开文件通道（避免中间流资源泄漏告警）
            channel = FileChannel.open(lockFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);

            // 4. 尝试获取独占锁（非阻塞）
            lock = channel.tryLock();

            if (lock == null) {
                // 锁已被占用，说明已有实例运行
                log.warn("应用程序已在运行，无法获取锁: {}", lockFile.getAbsolutePath());
                closeChannel(channel);
                return false;
            }

            // 5. 成功获取锁，注册关闭钩子
            log.info("成功获取应用锁: {}", lockFile.getAbsolutePath());
            registerShutdownHook();
            return true;

        } catch (OverlappingFileLockException e) {
            // 同一 JVM 多次调用 acquire()
            log.warn("同一 JVM 重复尝试获取锁", e);
            return false;
        } catch (IOException e) {
            log.error("获取应用锁时发生 IO 异常", e);
            closeChannel(channel);
            return false;
        }
    }

    /**
     * 释放应用程序锁
     *
     * <p>释放文件锁并关闭文件通道。通常由 JVM 关闭钩子自动调用，
     * 但也可以手动调用以提前释放锁。</p>
     */
    public void release() {
        if (lock != null) {
            try {
                lock.release();
                log.info("应用锁已释放");
            } catch (IOException e) {
                log.error("释放文件锁时发生异常", e);
            } finally {
                lock = null;
            }
        }

        closeChannel(channel);
        channel = null;
        lockFile = null;
    }

    /**
     * 确保用户目录存在
     *
     * <p>如果 {user.home}/.video-manager 目录不存在，则创建它。</p>
     */
    private void ensureUserDirectoryExists() {
        String userHome = System.getProperty("user.home");
        File appDir = new File(userHome, APP_DIR);

        if (!appDir.exists()) {
            if (appDir.mkdirs()) {
                log.info("创建应用配置目录: {}", appDir.getAbsolutePath());
            } else {
                log.error("无法创建应用配置目录: {}", appDir.getAbsolutePath());
            }
        }
    }

    /**
     * 注册 JVM 关闭钩子
     *
     * <p>确保在 JVM 退出时自动释放锁。</p>
     */
    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("JVM 关闭钩子触发，释放应用锁");
            release();
        }, "AppLock-ShutdownHook"));
    }

    /**
     * 安全关闭文件通道
     *
     * @param fileChannel 要关闭的文件通道
     */
    private void closeChannel(FileChannel fileChannel) {
        if (fileChannel != null) {
            try {
                fileChannel.close();
            } catch (IOException e) {
                log.error("关闭文件通道时发生异常", e);
            }
        }
    }

    /**
     * 获取锁文件对象
     *
     * @return 锁文件对象，可能为 null
     */
    public File getLockFile() {
        return lockFile;
    }

    /**
     * 检查是否已持有锁
     *
     * @return true 如果已持有锁，false 否则
     */
    public boolean isLocked() {
        return lock != null && lock.isValid();
    }
}
