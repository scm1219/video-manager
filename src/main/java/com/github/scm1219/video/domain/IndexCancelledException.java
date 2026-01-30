package com.github.scm1219.video.domain;

/**
 * 索引创建取消异常
 * <p>
 * 当用户主动取消索引创建操作时抛出此异常，
 * 用于区别于其他运行时异常，便于上层进行精确的错误处理。
 * </p>
 *
 * @author scm12
 */
public class IndexCancelledException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造一个索引取消异常
     */
    public IndexCancelledException() {
        super("索引创建被用户取消");
    }

    /**
     * 构造一个带自定义消息的索引取消异常
     *
     * @param message 异常消息
     */
    public IndexCancelledException(String message) {
        super(message);
    }
}
