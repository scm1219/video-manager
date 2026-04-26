package com.github.scm1219.video.gui.tree;

import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

import com.github.scm1219.video.domain.ProgressCallback;

/**
 * Swing 进度条回调适配器
 * <p>
 * 将 {@link JProgressBar} 适配为 {@link ProgressCallback}，
 * 确保进度更新在 EDT（事件分发线程）上执行
 * </p>
 */
public class ProgressBarCallback implements ProgressCallback {

	private final JProgressBar progressBar;

	public ProgressBarCallback(JProgressBar bar) {
		this.progressBar = bar;
	}

	@Override
	public void update(int percent, String message) {
		if (progressBar != null) {
			if (SwingUtilities.isEventDispatchThread()) {
				progressBar.setValue(percent);
				progressBar.setString(message);
			} else {
				SwingUtilities.invokeLater(() -> {
					progressBar.setValue(percent);
					progressBar.setString(message);
				});
			}
		}
	}

	/**
	 * 工厂方法，从 JProgressBar 创建回调实例
	 *
	 * @param bar 进度条（可为 null）
	 * @return 回调实例，如果 bar 为 null 则返回 null
	 */
	public static ProgressBarCallback of(JProgressBar bar) {
		return bar != null ? new ProgressBarCallback(bar) : null;
	}
}
