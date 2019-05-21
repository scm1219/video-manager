package com.github.scm1219.utils;

import java.io.File;
import java.io.FileFilter;

public class VideoFileFilter implements FileFilter{

	@Override
	public boolean accept(File pathname) {
		return FileUtils.isVideoFile(pathname);
	}

}
