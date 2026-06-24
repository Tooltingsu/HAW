package com.example.haw;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class HawDataManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type HOMES_TYPE = new TypeToken<Map<String, Map<String, TeleportPoint>>>() { }.getType();
    private static final Type WARPS_TYPE = new TypeToken<Map<String, TeleportPoint>>() { }.getType();
    private static final Type PERMISSIONS_TYPE = new TypeToken<Set<String>>() { }.getType();

    private static Path dataDir = Paths.get("haw");
    private static Map<String, Map<String, TeleportPoint>> homes = new LinkedHashMap<String, Map<String, TeleportPoint>>();
    private static Map<String, TeleportPoint> warps = new LinkedHashMap<String, TeleportPoint>();
    private static Set<String> warpManagers = new LinkedHashSet<String>();
    private static Config config = new Config();

    private HawDataManager() {
    }

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(new ServerLifecycleEvents.ServerStarted() {
            @Override
            public void onServerStarted(MinecraftServer server) {
                dataDir = server.getSavePath(WorldSavePath.ROOT).resolve("haw");
                loadAll();
            }
        });
    }

    public static synchronized Map<String, TeleportPoint> homes(String playerId) {
        Map<String, TeleportPoint> map = homes.get(playerId);
        if (map == null) {
            map = new LinkedHashMap<String, TeleportPoint>();
            homes.put(playerId, map);
        }
        return map;
    }

    public static synchronized Map<String, TeleportPoint> warps() {
        return warps;
    }

    public static synchronized boolean isWarpManager(String playerId) {
        return warpManagers.contains(playerId);
    }

    public static synchronized Set<String> warpManagers() {
        return new LinkedHashSet<String>(warpManagers);
    }

    public static synchronized void addWarpManager(String playerId) {
        warpManagers.add(playerId);
        savePermissions();
    }

    public static synchronized void removeWarpManager(String playerId) {
        warpManagers.remove(playerId);
        savePermissions();
    }

    public static synchronized int maxHomes() {
        return config.maxHomes;
    }

    public static synchronized int maxWarps() {
        return config.maxWarps;
    }

    public static synchronized void setMaxHomes(int value) {
        config.maxHomes = value;
        saveConfig();
    }

    public static synchronized void setMaxWarps(int value) {
        config.maxWarps = value;
        saveConfig();
    }

    public static synchronized void saveHomes() {
        write("homes.json", homes, HOMES_TYPE);
    }

    public static synchronized void saveWarps() {
        write("warps.json", warps, WARPS_TYPE);
    }

    public static synchronized ServerWorld findWorld(MinecraftServer server, String id) {
        for (ServerWorld world : server.getWorlds()) {
            if (world.getRegistryKey().getValue().toString().equals(id)) {
                return world;
            }
        }
        return null;
    }

    private static synchronized void loadAll() {
        try {
            Files.createDirectories(dataDir);
        } catch (Exception ignored) {
        }
        Map<String, Map<String, TeleportPoint>> loadedHomes = read("homes.json", HOMES_TYPE);
        Map<String, TeleportPoint> loadedWarps = read("warps.json", WARPS_TYPE);
        Set<String> loadedPermissions = read("permissions.json", PERMISSIONS_TYPE);
        Config loadedConfig = read("config.json", Config.class);
        homes = loadedHomes == null ? new LinkedHashMap<String, Map<String, TeleportPoint>>() : loadedHomes;
        warps = loadedWarps == null ? new LinkedHashMap<String, TeleportPoint>() : loadedWarps;
        warpManagers = loadedPermissions == null ? new LinkedHashSet<String>() : loadedPermissions;
        config = loadedConfig == null ? new Config() : loadedConfig;
        saveHomes();
        saveWarps();
        savePermissions();
        saveConfig();
    }

    private static synchronized void savePermissions() {
        write("permissions.json", warpManagers, PERMISSIONS_TYPE);
    }

    private static synchronized void saveConfig() {
        write("config.json", config, Config.class);
    }

    private static <T> T read(String name, Type type) {
        Path file = dataDir.resolve(name);
        if (!Files.exists(file)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            return GSON.fromJson(reader, type);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void write(String name, Object value, Type type) {
        try {
            Files.createDirectories(dataDir);
            Path file = dataDir.resolve(name);
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(value, type, writer);
            }
        } catch (Exception ignored) {
        }
    }

    private static final class Config {
        int maxHomes = -1;
        int maxWarps = -1;
    }
}
