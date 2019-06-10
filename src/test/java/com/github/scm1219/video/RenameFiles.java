package com.github.scm1219.video;

import com.github.scm1219.utils.FileUtils;

public class RenameFiles {

	public static void renameFiles() {
		
		String dirName= "G:\\anime\\ddd\\S2";
		String prefix= "RANMA_2BUNNO1.S02E";
		FileUtils.renameFiles(dirName, prefix);
	}
}
