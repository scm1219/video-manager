package com.github.scm1219.utils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class DateUtils {

	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

	public static String getDateString(long datetime) {
		return Instant.ofEpochMilli(datetime)
				.atZone(ZoneId.systemDefault())
				.format(FORMATTER);
	}
}
