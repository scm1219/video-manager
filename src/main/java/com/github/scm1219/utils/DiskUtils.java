package com.github.scm1219.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.apache.commons.lang3.StringUtils;

import com.github.scm1219.video.domain.Disk;
import com.github.scm1219.video.domain.SmartInfo;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DiskUtils {

	public static String getSmartInfo(Disk disk) {
		try {
			ProcessBuilder processBuilder = new ProcessBuilder("smartctl.exe", "-d", "ata", "-A", disk.getVolumeName());
			Process process = processBuilder.start();

			StringBuilder sb = new StringBuilder();
			boolean start = false;
			try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String line;
				while ((line = br.readLine()) != null) {
					if (start && StringUtils.isNotBlank(line)) {
						SmartInfo smart = SmartInfo.parseFromSmartCtl(line);
						String data = smart.getSimpleSmartInfo();
						if (StringUtils.isBlank(data)) {
							sb.append(line).append("\n");
						} else {
							sb.append(data).append("\n");
						}
					}
					if (!start && line.startsWith("ID#")) {
						start = true;
					}
				}
			}
			return sb.toString();
		} catch (Exception e) {
			log.error("获取SMART信息失败: {}", disk.getVolumeName(), e);
		}
		return "无法获取SMART数据";
	}
}
