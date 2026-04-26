package com.github.scm1219.video.gui.tree;

import java.io.File;

import com.github.scm1219.video.domain.Disk;
import com.github.scm1219.video.domain.Index.IndexStatistics;
import com.github.scm1219.video.domain.IndexCancelledException;
import com.github.scm1219.video.domain.ProgressCallback;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileUpdateProcesser extends AbstractProgressFrame {

	private static final long serialVersionUID = 1L;

	private final File targetDirectory;
	private final Disk disk;

	public FileUpdateProcesser(Disk disk) {
		super("开始", "确定要取消索引创建吗？\n\n将恢复到索引前的状态，已扫描的数据将丢失。", "正在取消索引...");
		this.disk = disk;
		this.targetDirectory = null;
		initUI();
		updateLabels();
	}

	public FileUpdateProcesser(Disk disk, File directory) {
		super("开始", "确定要取消索引创建吗？\n\n将恢复到索引前的状态，已扫描的数据将丢失。", "正在取消索引...");
		this.disk = disk;
		this.targetDirectory = directory;
		initUI();
		updateLabels();
	}

	private void updateLabels() {
		if (targetDirectory == null) {
			setTitle("更新索引");
			label.setText("更新" + disk.getPath() + "下的索引");
			progressBar.setString("准备更新目录" + disk.getPath());
		} else {
			setTitle("扫描目录");
			label.setText("扫描目录: " + targetDirectory.getAbsolutePath());
			progressBar.setString("准备扫描目录: " + targetDirectory.getName());
		}
	}

	@Override
	protected void onStart() {
		new Thread(() -> {
			textArea.setText("");
			setCancelButtonState();
			long startTime = System.currentTimeMillis();

			ProgressCallback callback = ProgressBarCallback.of(progressBar);

			try {
				if (targetDirectory == null) {
					disk.getIndex().create(disk, callback);
				} else {
					IndexStatistics stats = disk.getIndex().createForDirectory(targetDirectory, callback);
					long elapsed = System.currentTimeMillis() - startTime;
					progressBar.setString("扫描完成，耗时：" + elapsed + "ms");
					textArea.setText("目录: " + targetDirectory.getAbsolutePath() + "\n"
							+ "状态: 扫描完成\n"
							+ stats.toFormattedString()
							+ "总耗时: " + elapsed + "ms");
				}

				if (targetDirectory == null) {
					long elapsed = System.currentTimeMillis() - startTime;
					progressBar.setString("索引创建完成，耗时：" + elapsed + "ms");
					textArea.setText(disk.getIndex().getInfoString());
				}

				setCloseButtonState();
			} catch (IndexCancelledException e) {
				progressBar.setString("索引已取消，已恢复到原状态");
				textArea.setText("索引已取消\n已恢复到索引前的状态");
				setCloseButtonState();
			} catch (Exception e) {
				progressBar.setString("索引创建失败");
				textArea.setText("错误: " + e.getMessage());
				setCloseButtonState();
				log.error("索引创建失败", e);
			}
		}).start();
	}

	@Override
	protected void onCancelRequested() {
		disk.getIndex().cancel(null);
	}
}
