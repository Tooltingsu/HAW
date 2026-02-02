package com.example.haw;

import com.google.common.reflect.TypeToken;
import java.io.*;
import java.lang.reflect.Type;
import java.util.*;

public class HawDataManager {
    private static final File CONFIG_DIR = new File("config/haw");
    private static final File HOMES_FILE = new File(CONFIG_DIR, "homes.json");
    private static final File WARPS_FILE = new File(CONFIG_DIR, "warps.json");
    private static final File PERMS_FILE = new File(CONFIG_DIR, "permissions.json");
    private static final File CONFIG_FILE = new File(CONFIG_DIR, "config.json"); // 新增全局配置

    private static Map<UUID, Map<String, TeleportPoint>> homesCache = new HashMap<>();
    private static Map<String, TeleportPoint> warpsCache = new HashMap<>();
    private static Set<UUID> warpOpsCache = new HashSet<>();

    // 全局配置数据
    public static class HawConfig {
        public int maxHomes = -1; // 默认无限
        public int maxWarps = -1; // 默认无限
    }
    private static HawConfig configCache = new HawConfig();

    public static void load() {
        if (!CONFIG_DIR.exists()) CONFIG_DIR.mkdirs();

        homesCache = loadData(HOMES_FILE, new TypeToken<Map<UUID, Map<String, TeleportPoint>>>(){}.getType());
        if (homesCache == null) homesCache = new HashMap<>();

        warpsCache = loadData(WARPS_FILE, new TypeToken<Map<String, TeleportPoint>>(){}.getType());
        if (warpsCache == null) warpsCache = new HashMap<>();

        Set<UUID> loadedPerms = loadData(PERMS_FILE, new TypeToken<Set<UUID>>(){}.getType());
        if (loadedPerms != null) warpOpsCache = loadedPerms;

        // 加载配置
        HawConfig loadedConfig = loadData(CONFIG_FILE, HawConfig.class);
        if (loadedConfig != null) configCache = loadedConfig;
        else saveConfig(); // 如果不存在则保存默认
    }

    // --- Config Methods ---
    public static void setMaxHomes(int num) {
        configCache.maxHomes = num;
        saveConfig();
    }
    public static int getMaxHomes() { return configCache.maxHomes; }

    public static void setMaxWarps(int num) {
        configCache.maxWarps = num;
        saveConfig();
    }
    public static int getMaxWarps() { return configCache.maxWarps; }

    private static void saveConfig() { saveData(CONFIG_FILE, configCache); }

    // --- Home Methods ---
    public static void addHome(UUID uuid, String name, TeleportPoint point) {
        homesCache.computeIfAbsent(uuid, k -> new HashMap<>()).put(name, point);
        saveJson(HOMES_FILE, homesCache);
    }
    public static TeleportPoint getHome(UUID uuid, String name) {
        return homesCache.getOrDefault(uuid, Collections.emptyMap()).get(name);
    }
    public static void removeHome(UUID uuid, String name) {
        if (homesCache.containsKey(uuid)) {
            homesCache.get(uuid).remove(name);
            saveJson(HOMES_FILE, homesCache);
        }
    }
    public static Map<String, TeleportPoint> getHomes(UUID uuid) {
        return homesCache.getOrDefault(uuid, new HashMap<>());
    }

    public static boolean renameHome(UUID uuid, String oldName, String newName) {
        Map<String, TeleportPoint> userHomes = homesCache.get(uuid);
        if (userHomes == null || !userHomes.containsKey(oldName) || userHomes.containsKey(newName)) {
            return false;
        }
        TeleportPoint point = userHomes.remove(oldName);
        userHomes.put(newName, point);
        saveJson(HOMES_FILE, homesCache);
        return true;
    }

    public static boolean renoteHome(UUID uuid, String name, String newComment) {
        TeleportPoint point = getHome(uuid, name);
        if (point == null) return false;
        point.comment = newComment;
        saveJson(HOMES_FILE, homesCache);
        return true;
    }

    // --- Warp Methods ---
    public static void addWarp(String name, TeleportPoint point) {
        warpsCache.put(name, point);
        saveJson(WARPS_FILE, warpsCache);
    }
    public static TeleportPoint getWarp(String name) {
        return warpsCache.get(name);
    }
    public static void removeWarp(String name) {
        warpsCache.remove(name);
        saveJson(WARPS_FILE, warpsCache);
    }
    public static Map<String, TeleportPoint> getWarps() {
        return warpsCache;
    }

    public static boolean renameWarp(String oldName, String newName) {
        if (!warpsCache.containsKey(oldName) || warpsCache.containsKey(newName)) {
            return false;
        }
        TeleportPoint point = warpsCache.remove(oldName);
        warpsCache.put(newName, point);
        saveJson(WARPS_FILE, warpsCache);
        return true;
    }

    public static boolean renoteWarp(String name, String newComment) {
        TeleportPoint point = warpsCache.get(name);
        if (point == null) return false;
        point.comment = newComment;
        saveJson(WARPS_FILE, warpsCache);
        return true;
    }

    // --- Permission Methods ---
    public static void addWarpOp(UUID uuid) {
        warpOpsCache.add(uuid);
        saveJson(PERMS_FILE, warpOpsCache);
    }
    public static void removeWarpOp(UUID uuid) {
        warpOpsCache.remove(uuid);
        saveJson(PERMS_FILE, warpOpsCache);
    }
    public static boolean isWarpOp(UUID uuid) {
        return warpOpsCache.contains(uuid);
    }
    public static Set<UUID> getWarpOps() {
        return warpOpsCache;
    }

    // --- IO Helper ---
    private static <T> T loadData(File file, Type type) {
        if (!file.exists()) return null;
        try (Reader reader = new FileReader(file)) {
            return HomeAndWarp.GSON.fromJson(reader, type);
        } catch (IOException e) {
            HomeAndWarp.LOGGER.error("Failed to load data from " + file.getName(), e);
            return null;
        }
    }

    private static void saveData(File file, Object data) {
        try (Writer writer = new FileWriter(file)) {
            HomeAndWarp.GSON.toJson(data, writer);
        } catch (IOException e) {
            HomeAndWarp.LOGGER.error("Failed to save data to " + file.getName(), e);
        }
    }

    private static void saveJson(File file, Object data) {
        saveData(file, data);
    }
}