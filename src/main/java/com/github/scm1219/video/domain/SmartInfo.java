package com.github.scm1219.video.domain;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import lombok.Data;

@Data
public class SmartInfo {
	
	private static final Map<String,String> smartInfo = new HashMap<>();
	
	static {
		
		smartInfo.put("Raw Read Error Rate", "底层数据读取错误率");
		smartInfo.put("Throughput Performance", "磁盘读写通量性能");
		smartInfo.put("Spin Up Time", "主轴起旋时间");
		smartInfo.put("Start Stop Count", "启停计数");
		smartInfo.put("Reallocated Sector Ct", "重映射扇区计数");
		smartInfo.put("Seek Error Rate", "寻道错误率");
		smartInfo.put("Seek Time Performance", "寻道性能");
		smartInfo.put("Power On Hours", "通电时间累计");
		smartInfo.put("Spin Retry Count", "主轴起旋重试次数");
		smartInfo.put("Power Cycle Count", "通电周期计数");
		smartInfo.put("G-Sense Error Rate", "冲击错误率");
		//192
		smartInfo.put("Power-Off Retract Count", "断电返回计数");
		//193
		smartInfo.put("Load Cycle Count", "磁头加载/卸载计数");
		//194
		smartInfo.put("Temperature Celsius", "温度");
		//196
		smartInfo.put("Reallocated Event Count", "重映射事件计数");
		//197
		smartInfo.put("Current Pending Sector", "当前待映射扇区计数");
		smartInfo.put("Offline Uncorrectable", "脱机无法校正的扇区计数");
		smartInfo.put("UDMA CRC Error Count", "UltraATA访问校验错误率");
		smartInfo.put("Disk Shift", "盘片偏移量");
		//222
		smartInfo.put("Loaded Hours", "磁头寻道时间累计");
		//223
		smartInfo.put("Load Retry Count", "磁头加载/卸载重试计数");
		//224
		smartInfo.put("Load Friction", "磁头阻力");
		smartInfo.put("Load-in Time", "磁头加载时间累计");
		smartInfo.put("Head Flying Hours", "磁头飞行时间");
	}

	private Integer id;
	
	private String attrName;
	
	private String flag;
	
	private String value;
	
	private String worst;
	
	private String thresh;
	
	private String type;
	
	private String updated;
	
	private String whenFailed;
	
	private String rawValue;
	
	public static SmartInfo parseFromSmartCtl(String line) {
		String[] item = line.trim().split(" ");
		List<String> values = new ArrayList<>();
		for (int i = 0; i < item.length; i++) {
			if(StringUtils.isNotBlank(item[i])) {
				values.add(item[i]);
			}
		}
		SmartInfo info = new SmartInfo();
		Field[] declaredFields = SmartInfo.class.getDeclaredFields();
		for (int i = 0; i < 10; i++) {
			
			try {
				Field f = declaredFields[i+1];
				if(f.getType().equals(Integer.class)) {
					f.set(info, Integer.valueOf(values.get(i)));
				}else {
					f.set(info, values.get(i));
				}
			} catch (Exception e) {
				System.err.println(line);
				e.printStackTrace();
			}
		}
		return info;
	}
	
	public String getSimpleSmartInfo() {
		
		String ids = String.format("%03d", id);
		String attname = attrName.replace('_', ' ');
		String attrCn = smartInfo.get(attname)==null?attrName: smartInfo.get(attname);
		
		return String.join(" ", ids,attrCn,rawValue,value,worst,thresh,type);
	}
	
}
