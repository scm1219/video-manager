package com.github.scm1219.video;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class DiskSmartInfo {

	public static void main(String[] args) {
		try {
			ProcessBuilder processBuilder = new ProcessBuilder("smartctl.exe", "-d", "ata", "-A", "F:");
			Process process = processBuilder.start();
			BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line = null;
			StringBuffer b = new StringBuffer();
			while ((line = br.readLine()) != null) {
				b.append(line + "\n");
			}
			System.out.println(b.toString());
			process.waitFor();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
}
