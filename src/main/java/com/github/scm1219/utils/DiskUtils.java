package com.github.scm1219.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import com.github.scm1219.video.domain.Disk;

public class DiskUtils {

	public static String getSmartInfo(Disk disk) {
		
		Runtime runtime = Runtime.getRuntime();
		try {
			String cmd = "smartctl.exe -A "+disk.getVolumeName();
			BufferedReader br = new BufferedReader(new InputStreamReader(runtime.exec(cmd).getInputStream()));
			String line = null;
			StringBuffer b = new StringBuffer();
			while ((line = br.readLine()) != null) {
				b.append(line + "\n");
			}
			return b.toString();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "无法获取SMART数据";
	}
	
	
}
