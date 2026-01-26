package com.github.scm1219.video.gui;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.swing.Icon;
import javax.swing.filechooser.FileSystemView;

public class IconCache {
    private static final int MAX_CACHE_SIZE = 2000;
    private static final Map<String, Icon> iconCache = new HashMap<>();
    private static final Map<String, String> displayNameCache = new HashMap<>();
    private static final Map<String, String> typeDescriptionCache = new HashMap<>();
    private static final FileSystemView fileSystemView = FileSystemView.getFileSystemView();

    public static Icon getSystemIcon(File file) {
        String key = file.getAbsolutePath();
        Icon icon = iconCache.get(key);
        if (icon == null) {
            icon = fileSystemView.getSystemIcon(file);
            if (iconCache.size() >= MAX_CACHE_SIZE) {
                iconCache.clear();
            }
            iconCache.put(key, icon);
        }
        return icon;
    }

    public static String getSystemDisplayName(File file) {
        String key = file.getAbsolutePath();
        String name = displayNameCache.get(key);
        if (name == null) {
            name = fileSystemView.getSystemDisplayName(file);
            displayNameCache.put(key, name);
        }
        return name;
    }

    public static String getSystemTypeDescription(File file) {
        String key = file.getAbsolutePath();
        String description = typeDescriptionCache.get(key);
        if (description == null) {
            description = fileSystemView.getSystemTypeDescription(file);
            typeDescriptionCache.put(key, description);
        }
        return description;
    }

    public static void clear() {
        iconCache.clear();
        displayNameCache.clear();
        typeDescriptionCache.clear();
    }

    public static int getIconCacheSize() {
        return iconCache.size();
    }

    public static int getDisplayNameCacheSize() {
        return displayNameCache.size();
    }

    public static int getTypeDescriptionCacheSize() {
        return typeDescriptionCache.size();
    }
}
