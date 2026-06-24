package com.example.haw;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.api.ModInitializer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import java.util.Collections;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class HomeAndWarp implements ModInitializer {
    private static final int PAGE_SIZE = 8;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    @Override
    public void onInitialize() {
        HawDataManager.init();
        CommandRegistrationCallback.EVENT.register(new CommandRegistrationCallback() {
            @Override
            public void register(CommandDispatcher<ServerCommandSource> dispatcher, net.minecraft.command.CommandRegistryAccess registryAccess, net.minecraft.server.command.CommandManager.RegistrationEnvironment environment) {
                registerCommands(dispatcher);
            }
        });
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("home")
                .then(literal("create").then(argument("name", StringArgumentType.word())
                        .executes(ctx -> createHome(ctx, ""))
                        .then(argument("note", StringArgumentType.greedyString()).executes(ctx -> createHome(ctx, StringArgumentType.getString(ctx, "note"))))))
                .then(literal("tp").then(argument("name", StringArgumentType.word()).executes(ctx -> tpHome(ctx))))
                .then(literal("delete").then(argument("name", StringArgumentType.word()).executes(ctx -> deleteHome(ctx))))
                .then(literal("confirm").executes(ctx -> confirmHome(ctx)))
                .then(literal("list").executes(ctx -> listHome(ctx, 1)).then(argument("page", IntegerArgumentType.integer(1)).executes(ctx -> listHome(ctx, IntegerArgumentType.getInteger(ctx, "page")))))
                .then(literal("rename").then(argument("old", StringArgumentType.word()).then(argument("name", StringArgumentType.word()).executes(ctx -> renameHome(ctx)))))
                .then(literal("renote").then(argument("name", StringArgumentType.word()).then(argument("note", StringArgumentType.greedyString()).executes(ctx -> renoteHome(ctx)))))
                .then(literal("look").then(argument("name", StringArgumentType.word()).executes(ctx -> lookHome(ctx))))
                .then(literal("found").then(argument("text", StringArgumentType.greedyString()).executes(ctx -> foundHome(ctx)))));

        dispatcher.register(literal("warp")
                .then(literal("create").then(argument("name", StringArgumentType.word())
                        .executes(ctx -> createWarp(ctx, ""))
                        .then(argument("note", StringArgumentType.greedyString()).executes(ctx -> createWarp(ctx, StringArgumentType.getString(ctx, "note"))))))
                .then(literal("tp").then(argument("name", StringArgumentType.word()).executes(ctx -> tpWarp(ctx))))
                .then(literal("delete").then(argument("name", StringArgumentType.word()).executes(ctx -> deleteWarp(ctx))))
                .then(literal("confirm").executes(ctx -> confirmWarp(ctx)))
                .then(literal("list").executes(ctx -> listWarp(ctx, 1)).then(argument("page", IntegerArgumentType.integer(1)).executes(ctx -> listWarp(ctx, IntegerArgumentType.getInteger(ctx, "page")))))
                .then(literal("rename").then(argument("old", StringArgumentType.word()).then(argument("name", StringArgumentType.word()).executes(ctx -> renameWarp(ctx)))))
                .then(literal("renote").then(argument("name", StringArgumentType.word()).then(argument("note", StringArgumentType.greedyString()).executes(ctx -> renoteWarp(ctx)))))
                .then(literal("look").then(argument("name", StringArgumentType.word()).executes(ctx -> lookWarp(ctx))))
                .then(literal("found").then(argument("text", StringArgumentType.greedyString()).executes(ctx -> foundWarp(ctx)))));

        dispatcher.register(literal("haw")
                .then(literal("op")
                        .then(literal("add").then(argument("player", StringArgumentType.word()).executes(ctx -> addManager(ctx))))
                        .then(literal("delete").then(argument("player", StringArgumentType.word()).executes(ctx -> removeManager(ctx))))
                        .then(literal("list").executes(ctx -> listManagers(ctx))))
                .then(literal("set")
                        .then(literal("homenum").then(argument("value", IntegerArgumentType.integer(-1)).executes(ctx -> setHomeNum(ctx))))
                        .then(literal("warpnum").then(argument("value", IntegerArgumentType.integer(-1)).executes(ctx -> setWarpNum(ctx))))));
    }

    private static int createHome(CommandContext<ServerCommandSource> ctx, String note) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        String playerId = player.getUuid().toString();
        String name = StringArgumentType.getString(ctx, "name");
        Map<String, TeleportPoint> homes = HawDataManager.homes(playerId);
        int limit = HawDataManager.maxHomes();
        if (!homes.containsKey(name) && limit >= 0 && homes.size() >= limit) {
            send(player, tr("haw.home.limit", limit));
            return 0;
        }
        homes.put(name, new TeleportPoint(name, note, player, playerId, ctx.getSource().getWorld()));
        HawDataManager.saveHomes();
        send(player, tr("haw.home.created", name));
        return 1;
    }

    private static int tpHome(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        TeleportPoint point = HawDataManager.homes(player.getUuid().toString()).get(StringArgumentType.getString(ctx, "name"));
        if (point == null) {
            send(player, tr("haw.home.missing"));
            return 0;
        }
        return teleport(ctx, player, point, "haw.home.teleported");
    }

    private static int deleteHome(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        String name = StringArgumentType.getString(ctx, "name");
        Map<String, TeleportPoint> homes = HawDataManager.homes(player.getUuid().toString());
        if (!homes.containsKey(name)) {
            send(player, tr("haw.home.missing"));
            return 0;
        }
        pendingHomeDelete = name;
        send(player, tr("haw.confirm", name));
        return 1;
    }

    private static int confirmHome(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (pendingHomeDelete == null) {
            send(player, tr("haw.nothing_confirm"));
            return 0;
        }
        HawDataManager.homes(player.getUuid().toString()).remove(pendingHomeDelete);
        HawDataManager.saveHomes();
        send(player, tr("haw.home.deleted", pendingHomeDelete));
        pendingHomeDelete = null;
        return 1;
    }

    private static String pendingHomeDelete;
    private static String pendingWarpDelete;

    private static int listHome(CommandContext<ServerCommandSource> ctx, int page) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        return list(player, HawDataManager.homes(player.getUuid().toString()), page, "/home", "haw.home.list", "haw.home.empty");
    }

    private static int renameHome(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        Map<String, TeleportPoint> homes = HawDataManager.homes(player.getUuid().toString());
        String old = StringArgumentType.getString(ctx, "old");
        String name = StringArgumentType.getString(ctx, "name");
        TeleportPoint point = homes.remove(old);
        if (point == null) {
            send(player, tr("haw.home.missing"));
            return 0;
        }
        point.name = name;
        homes.put(name, point);
        HawDataManager.saveHomes();
        send(player, tr("haw.renamed", old, name));
        return 1;
    }

    private static int renoteHome(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        TeleportPoint point = HawDataManager.homes(player.getUuid().toString()).get(StringArgumentType.getString(ctx, "name"));
        if (point == null) {
            send(player, tr("haw.home.missing"));
            return 0;
        }
        point.note = StringArgumentType.getString(ctx, "note");
        HawDataManager.saveHomes();
        send(player, tr("haw.renoted", point.name));
        return 1;
    }

    private static int lookHome(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        TeleportPoint point = HawDataManager.homes(player.getUuid().toString()).get(StringArgumentType.getString(ctx, "name"));
        if (point == null) {
            send(player, tr("haw.home.missing"));
            return 0;
        }
        send(player, detail(point));
        return 1;
    }

    private static int foundHome(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        return found(player, HawDataManager.homes(player.getUuid().toString()), StringArgumentType.getString(ctx, "text"), "/home");
    }

    private static int createWarp(CommandContext<ServerCommandSource> ctx, String note) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (!canManageWarp(ctx.getSource(), player)) {
            send(player, tr("haw.no_permission"));
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "name");
        Map<String, TeleportPoint> warps = HawDataManager.warps();
        int limit = HawDataManager.maxWarps();
        if (!warps.containsKey(name) && limit >= 0 && warps.size() >= limit) {
            send(player, tr("haw.warp.limit", limit));
            return 0;
        }
        warps.put(name, new TeleportPoint(name, note, player, player.getUuid().toString(), ctx.getSource().getWorld()));
        HawDataManager.saveWarps();
        send(player, tr("haw.warp.created", name));
        return 1;
    }

    private static int tpWarp(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        TeleportPoint point = HawDataManager.warps().get(StringArgumentType.getString(ctx, "name"));
        if (point == null) {
            send(player, tr("haw.warp.missing"));
            return 0;
        }
        return teleport(ctx, player, point, "haw.warp.teleported");
    }

    private static int deleteWarp(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (!canManageWarp(ctx.getSource(), player)) {
            send(player, tr("haw.no_permission"));
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "name");
        if (!HawDataManager.warps().containsKey(name)) {
            send(player, tr("haw.warp.missing"));
            return 0;
        }
        pendingWarpDelete = name;
        send(player, tr("haw.confirm", name));
        return 1;
    }

    private static int confirmWarp(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (!canManageWarp(ctx.getSource(), player)) {
            send(player, tr("haw.no_permission"));
            return 0;
        }
        if (pendingWarpDelete == null) {
            send(player, tr("haw.nothing_confirm"));
            return 0;
        }
        HawDataManager.warps().remove(pendingWarpDelete);
        HawDataManager.saveWarps();
        send(player, tr("haw.warp.deleted", pendingWarpDelete));
        pendingWarpDelete = null;
        return 1;
    }

    private static int listWarp(CommandContext<ServerCommandSource> ctx, int page) throws CommandSyntaxException {
        return list(ctx.getSource().getPlayer(), HawDataManager.warps(), page, "/warp", "haw.warp.list", "haw.warp.empty");
    }

    private static int renameWarp(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (!canManageWarp(ctx.getSource(), player)) {
            send(player, tr("haw.no_permission"));
            return 0;
        }
        String old = StringArgumentType.getString(ctx, "old");
        String name = StringArgumentType.getString(ctx, "name");
        TeleportPoint point = HawDataManager.warps().remove(old);
        if (point == null) {
            send(player, tr("haw.warp.missing"));
            return 0;
        }
        point.name = name;
        HawDataManager.warps().put(name, point);
        HawDataManager.saveWarps();
        send(player, tr("haw.renamed", old, name));
        return 1;
    }

    private static int renoteWarp(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (!canManageWarp(ctx.getSource(), player)) {
            send(player, tr("haw.no_permission"));
            return 0;
        }
        TeleportPoint point = HawDataManager.warps().get(StringArgumentType.getString(ctx, "name"));
        if (point == null) {
            send(player, tr("haw.warp.missing"));
            return 0;
        }
        point.note = StringArgumentType.getString(ctx, "note");
        HawDataManager.saveWarps();
        send(player, tr("haw.renoted", point.name));
        return 1;
    }

    private static int lookWarp(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        TeleportPoint point = HawDataManager.warps().get(StringArgumentType.getString(ctx, "name"));
        if (point == null) {
            send(player, tr("haw.warp.missing"));
            return 0;
        }
        send(player, detail(point));
        return 1;
    }

    private static int foundWarp(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        return found(ctx.getSource().getPlayer(), HawDataManager.warps(), StringArgumentType.getString(ctx, "text"), "/warp");
    }

    private static int addManager(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity executor = ctx.getSource().getPlayer();
        if (!isOperator(ctx.getSource(), executor)) {
            send(executor, tr("haw.no_permission"));
            return 0;
        }
        String player = StringArgumentType.getString(ctx, "player");
        HawDataManager.addWarpManager(player);
        send(executor, tr("haw.op.added", player));
        return 1;
    }

    private static int removeManager(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity executor = ctx.getSource().getPlayer();
        if (!isOperator(ctx.getSource(), executor)) {
            send(executor, tr("haw.no_permission"));
            return 0;
        }
        String player = StringArgumentType.getString(ctx, "player");
        HawDataManager.removeWarpManager(player);
        send(executor, tr("haw.op.removed", player));
        return 1;
    }

    private static int listManagers(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity executor = ctx.getSource().getPlayer();
        if (!isOperator(ctx.getSource(), executor)) {
            send(executor, tr("haw.no_permission"));
            return 0;
        }
        send(executor, lit(HawDataManager.warpManagers().toString()));
        return 1;
    }

    private static int setHomeNum(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity executor = ctx.getSource().getPlayer();
        if (!isOperator(ctx.getSource(), executor)) {
            send(executor, tr("haw.no_permission"));
            return 0;
        }
        int value = IntegerArgumentType.getInteger(ctx, "value");
        HawDataManager.setMaxHomes(value);
        send(executor, tr("haw.config.homenum", value));
        return 1;
    }

    private static int setWarpNum(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity executor = ctx.getSource().getPlayer();
        if (!isOperator(ctx.getSource(), executor)) {
            send(executor, tr("haw.no_permission"));
            return 0;
        }
        int value = IntegerArgumentType.getInteger(ctx, "value");
        HawDataManager.setMaxWarps(value);
        send(executor, tr("haw.config.warpnum", value));
        return 1;
    }

    private static boolean canManageWarp(ServerCommandSource source, ServerPlayerEntity player) {
        return isOperator(source, player) || HawDataManager.isWarpManager(player.getUuid().toString()) || HawDataManager.isWarpManager(playerName(player));
    }

    private static boolean isOperator(ServerCommandSource source, ServerPlayerEntity player) {
        return hasPermission(source, 3) || hasPermission(player, 3);
    }

    private static boolean hasPermission(Object target, int level) {
        for (java.lang.reflect.Method method : target.getClass().getMethods()) {
            if (method.getParameterTypes().length == 1
                    && method.getParameterTypes()[0] == int.class
                    && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
                try {
                    Object result = method.invoke(target, Integer.valueOf(level));
                    if (Boolean.TRUE.equals(result)) {
                        return true;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return false;
    }

    private static String playerName(ServerPlayerEntity player) {
        try {
            Object profile = player.getGameProfile();
            try {
                Object value = profile.getClass().getMethod("getName").invoke(profile);
                return String.valueOf(value);
            } catch (Exception ignored) {
                Object value = profile.getClass().getMethod("name").invoke(profile);
                return String.valueOf(value);
            }
        } catch (Exception ignored) {
            return "";
        }
    }

    private static int teleport(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity player, TeleportPoint point, String messageKey) {
        ServerWorld world = HawDataManager.findWorld(ctx.getSource().getWorld().getServer(), point.world);
        if (world == null) {
            send(player, tr("haw.world.missing", point.world));
            return 0;
        }
        player.teleport(world, point.x, point.y, point.z, Collections.<PositionFlag>emptySet(), point.yaw, point.pitch, true);
        send(player, tr(messageKey, point.name));
        return 1;
    }

    private static int list(ServerPlayerEntity player, Map<String, TeleportPoint> points, int page, String cmdBase, String titleKey, String emptyKey) {
        if (points.isEmpty()) {
            send(player, tr(emptyKey));
            return 0;
        }
        List<TeleportPoint> sorted = sorted(points);
        int maxPage = Math.max(1, (sorted.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.min(page, maxPage);
        send(player, tr(titleKey, current, maxPage));
        int start = (current - 1) * PAGE_SIZE;
        int end = Math.min(sorted.size(), start + PAGE_SIZE);
        for (int i = start; i < end; i++) {
            TeleportPoint point = sorted.get(i);
            MutableText line = lit("#" + (i + 1) + " ").formatted(Formatting.YELLOW);
            line.append(clickable(cmdBase, point.name));
            if (point.note != null && point.note.length() > 0) {
                line.append(lit("  " + point.note).formatted(Formatting.WHITE));
            }
            line.append(lit("  " + DATE_FORMAT.format(new Date(point.createdAt))).formatted(Formatting.GRAY));
            send(player, line);
        }
        return 1;
    }

    private static int found(ServerPlayerEntity player, Map<String, TeleportPoint> points, String text, String cmdBase) {
        int count = 0;
        String needle = text.toLowerCase(Locale.ROOT);
        for (TeleportPoint point : sorted(points)) {
            String haystack = ((point.name == null ? "" : point.name) + " " + (point.note == null ? "" : point.note)).toLowerCase(Locale.ROOT);
            if (haystack.contains(needle)) {
                MutableText line = lit("#" + (++count) + " ").formatted(Formatting.YELLOW);
                line.append(clickable(cmdBase, point.name));
                if (point.note != null && point.note.length() > 0) {
                    line.append(lit("  " + point.note).formatted(Formatting.WHITE));
                }
                send(player, line);
            }
        }
        if (count == 0) {
            send(player, tr("haw.no_match"));
        }
        return count;
    }

    private static List<TeleportPoint> sorted(Map<String, TeleportPoint> points) {
        List<TeleportPoint> sorted = new ArrayList<TeleportPoint>(points.values());
        sorted.sort(new Comparator<TeleportPoint>() {
            @Override
            public int compare(TeleportPoint left, TeleportPoint right) {
                return Long.compare(left.createdAt, right.createdAt);
            }
        });
        return sorted;
    }

    private static MutableText detail(TeleportPoint point) {
        return lit(point.name + " @ " + point.world + " " + format(point.x) + ", " + format(point.y) + ", " + format(point.z) + " " + DATE_FORMAT.format(new Date(point.createdAt)) + (point.note == null || point.note.length() == 0 ? "" : " - " + point.note));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static MutableText clickable(String cmdBase, String name) {
        String command = cmdBase + " tp " + name;
        return lit(name).formatted(Formatting.GREEN).styled(style -> style
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(tr("haw.point.hover", name))));
    }

    private static void send(ServerPlayerEntity player, Text text) {
        player.sendMessage(text, false);
    }

    private static MutableText lit(String value) {
        return Text.literal(value);
    }

    private static MutableText tr(String key, Object... args) {
        return Text.translatable(key, args);
    }
}
