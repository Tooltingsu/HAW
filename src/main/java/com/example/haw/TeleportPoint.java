package com.example.haw;

import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.Set;

public class TeleportPoint {
    public double x, y, z;
    public float yaw, pitch;
    public String dimensionId;
    public String comment;
    public long timestamp; // 新增：创建时间戳

    public TeleportPoint(ServerPlayerEntity player, String comment) {
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
        this.yaw = player.getYaw();
        this.pitch = player.getPitch();
        this.dimensionId = player.getWorld().getRegistryKey().getValue().toString();
        this.comment = comment;
        this.timestamp = System.currentTimeMillis(); // 自动记录当前时间
    }

    public void teleport(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        RegistryKey<World> dimKey = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(this.dimensionId));
        ServerWorld targetWorld = server.getWorld(dimKey);

        if (targetWorld == null) {
            targetWorld = server.getOverworld();
        }

        player.teleport(targetWorld, this.x, this.y, this.z, Set.of(), this.yaw, this.pitch, false);
    }
}