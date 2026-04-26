package com.github.scm1219.video.gui.tree;

import com.github.scm1219.video.domain.Disk;
import com.github.scm1219.video.domain.Index.IndexStatistics;
import com.github.scm1219.video.domain.IndexCancelledException;
import com.github.scm1219.video.domain.ProgressCallback;

import lombok.extern.slf4j.Slf4j;

/**
 * 索引验证和清理进度窗口
 */
@Slf4j
public class IndexValidationProcesser extends AbstractProgressFrame {

	private static final long serialVersionUID = 1L;

	private final Disk disk;

	public IndexValidationProcesser(Disk disk) {
		super("开始验证", "确定要取消验证吗？", "正在取消验证...");
		this.disk = disk;
		initUI();
		updateLabels();
	}

	private void updateLabels() {
		setTitle("验证索引");
		label.setText("验证磁盘: " + disk.getPath());
		progressBar.setString("准备验证索引...");
		textArea.setText("点击【开始验证】开始检查索引记录的有效性\n");
		textArea.append("• 该操作将删除文件已不存在的索引记录\n");
		textArea.append("• 删除后不可恢复，只能重建索引\n");
	}

	@Override
	protected void onStart() {
		new Thread(() -> {
			textArea.setText("");
			setCancelButtonState();
			long startTime = System.currentTimeMillis();

			ProgressCallback callback = ProgressBarCallback.of(progressBar);

			try {
				IndexStatistics stats = disk.getIndex().validateAndCleanup(callback);
				long elapsed = System.currentTimeMillis() - startTime;

				progressBar.setString("验证完成");
				StringBuilder result = new StringBuilder();
				result.append("验证完成！\n\n");
				result.append("索引记录总数: ").append(stats.getTotalCount()).append("\n");
				result.append("删除无效记录: ").append(stats.getDeletedCount()).append("\n");
				result.append("总耗时: ").append(elapsed).append(" ms\n");

				if (stats.getDeletedCount() > 0) {
					result.append("\n已成功清理 ").append(stats.getDeletedCount()).append(" 条无效索引记录");
				} else {
					result.append("\n索引完整，无需清理");
				}

				textArea.setText(result.toString());
				setCloseButtonState();
			} catch (IndexCancelledException e) {
				progressBar.setString("验证已取消");
				textArea.setText("验证已取消");
				setCloseButtonState();
			} catch (Exception e) {
				progressBar.setString("验证失败");
				textArea.setText("错误: " + e.getMessage());
				setCloseButtonState();
				log.error("索引验证失败", e);
			}
		}).start();
	}

	@Override
	protected void onCancelRequested() {
		disk.getIndex().cancel(null);
	}
}
