package com.github.scm1219.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.apache.commons.lang3.StringUtils;

import com.github.scm1219.video.domain.Disk;
import com.github.scm1219.video.domain.SmartInfo;

public class DiskUtils {
	
	public static String getSmartInfo(Disk disk) {
		
		Runtime runtime = Runtime.getRuntime();
		try {
			String cmd = "smartctl.exe -A "+disk.getVolumeName();
			BufferedReader br = new BufferedReader(new InputStreamReader(runtime.exec(cmd).getInputStream()));
			String line = null;
			StringBuffer b = new StringBuffer();
			boolean start=false;
			while ((line = br.readLine()) != null) {
				if(start && StringUtils.isNoneBlank(line)) {
					SmartInfo smart = SmartInfo.parseFromSmartCtl(line);
					String data = smart.getSimpleSmartInfo();
					if(StringUtils.isBlank(data)) {
						b.append(line + "\n");
					}else {
						b.append(data + "\n");
					}
				}
				if(!start && line.startsWith("ID#")) {
					start=true;
				}
			}
			return b.toString();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "无法获取SMART数据";
	}
	
	
}
