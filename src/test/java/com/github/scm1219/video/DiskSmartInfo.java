package com.github.scm1219.video;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class DiskSmartInfo {

	public static void main(String[] args) {
		Runtime runtime = Runtime.getRuntime();
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(runtime.exec("smartctl.exe -d ata -A F:").getInputStream()));
			// StringBuffer b = new StringBuffer();
			String line = null;
			StringBuffer b = new StringBuffer();
			while ((line = br.readLine()) != null) {
				b.append(line + "\n");
			}
			System.out.println(b.toString());
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
}
