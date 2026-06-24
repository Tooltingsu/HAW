package com.example.haw;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public final class TeleportPoint {
    public String name;
    public String note;
    public String world;
    public double x;
    public double y;
    public double z;
    public float yaw;
    public float pitch;
    public long createdAt;
    public String owner;

    public TeleportPoint() {
    }

    public TeleportPoint(String name, String note, ServerPlayerEntity player, String owner, ServerWorld world) {
        this.name = name;
        this.note = note == null ? "" : note;
        this.world = world.getRegistryKey().getValue().toString();
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
        this.yaw = player.getYaw(1.0F);
        this.pitch = player.getPitch(1.0F);
        this.createdAt = System.currentTimeMillis();
        this.owner = owner;
    }
}
