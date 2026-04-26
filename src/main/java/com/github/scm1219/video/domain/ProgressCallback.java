package com.github.scm1219.video.domain;

/**
 * 进度回调接口，用于解耦领域层与 Swing UI 组件
 * <p>
 * 替代直接传递 JProgressBar 的方式，使领域类不再依赖 Swing
 * </p>
 */
@FunctionalInterface
public interface ProgressCallback {

	/**
	 * 更新进度信息
	 *
	 * @param percent 进度百分比（0-100）
	 * @param message 进度描述信息
	 */
	void update(int percent, String message);
}
