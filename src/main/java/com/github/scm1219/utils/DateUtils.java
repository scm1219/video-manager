package com.github.scm1219.utils;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DateUtils {

	public static String getDateString(long datetime) {
		SimpleDateFormat sdf = new SimpleDateFormat(" yyyy/MM/dd/ HH:mm:ss");
        Date date = new Date(datetime);
        return sdf.format(date);
	}
}
