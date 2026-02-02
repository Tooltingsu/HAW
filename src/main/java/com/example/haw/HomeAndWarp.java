package com.example.haw;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class HomeAndWarp implements ModInitializer {

	public static final String MOD_ID = "haw";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	// 时间格式化器
	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
			.withZone(ZoneId.systemDefault());

	private static final Map<UUID, String> pendingHomeDeletes = new HashMap<>();
	private static final Map<UUID, String> pendingWarpDeletes = new HashMap<>();

	@Override
	public void onInitialize() {
		LOGGER.info("Home and Warp (haw) initializing...");
		HawDataManager.load();
		CommandRegistrationCallback.EVENT.register(this::registerCommands);
	}

	private void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registry, CommandManager.RegistrationEnvironment environment) {

		// --- /haw 总指令 (OP管理用) ---
		dispatcher.register(literal("haw")
				.requires(source -> source.hasPermissionLevel(3)) // 仅管理员可用
				.then(literal("op")
						.then(literal("add")
								.then(argument("player", EntityArgumentType.player())
										.executes(this::addWarpOp)))
						.then(literal("delete")
								.then(argument("player", EntityArgumentType.player())
										.executes(this::removeWarpOp)))
						.then(literal("list")
								.executes(this::listWarpOps)))
				.then(literal("set")
						.then(literal("homenum")
								.then(argument("number", IntegerArgumentType.integer(-1))
										.executes(this::setHomeNum)))
						.then(literal("warpnum")
								.then(argument("number", IntegerArgumentType.integer(-1))
										.executes(this::setWarpNum))))
		);

		// --- /home 命令 ---
		dispatcher.register(literal("home")
				.then(literal("create")
						.then(argument("name", StringArgumentType.word())
								.executes(ctx -> createHome(ctx, ""))
								.then(argument("comment", StringArgumentType.greedyString())
										.executes(ctx -> createHome(ctx, StringArgumentType.getString(ctx, "comment"))))))
				.then(literal("tp")
						.then(argument("name", StringArgumentType.word())
								.executes(this::tpHome)))
				.then(literal("delete")
						.then(argument("name", StringArgumentType.word())
								.executes(this::requestDeleteHome)))
				.then(literal("confirm")
						.executes(this::confirmDeleteHome))
				.then(literal("rename")
						.then(argument("oldName", StringArgumentType.word())
								.then(argument("newName", StringArgumentType.word())
										.executes(this::renameHome))))
				.then(literal("renote")
						.then(argument("name", StringArgumentType.word())
								.then(argument("newComment", StringArgumentType.greedyString())
										.executes(this::renoteHome))))
				.then(literal("look")
						.then(argument("name", StringArgumentType.word())
								.executes(this::lookHome)))
				.then(literal("found")
						.then(argument("text", StringArgumentType.greedyString())
								.executes(this::foundHome)))
				.then(literal("list")
						.executes(ctx -> listHomes(ctx, 1))
						.then(argument("page", IntegerArgumentType.integer(1))
								.executes(ctx -> listHomes(ctx, IntegerArgumentType.getInteger(ctx, "page")))))
		);

		// --- /warp 命令 ---
		dispatcher.register(literal("warp")
				.then(literal("create")
						.then(argument("name", StringArgumentType.word())
								.executes(ctx -> createWarp(ctx, ""))
								.then(argument("comment", StringArgumentType.greedyString())
										.executes(ctx -> createWarp(ctx, StringArgumentType.getString(ctx, "comment"))))))
				.then(literal("tp")
						.then(argument("name", StringArgumentType.word())
								.executes(this::tpWarp)))
				.then(literal("delete")
						.then(argument("name", StringArgumentType.word())
								.executes(this::requestDeleteWarp)))
				.then(literal("confirm")
						.executes(this::confirmDeleteWarp))
				.then(literal("rename")
						.then(argument("oldName", StringArgumentType.word())
								.then(argument("newName", StringArgumentType.word())
										.executes(this::renameWarp))))
				.then(literal("renote")
						.then(argument("name", StringArgumentType.word())
								.then(argument("newComment", StringArgumentType.greedyString())
										.executes(this::renoteWarp))))
				.then(literal("look")
						.then(argument("name", StringArgumentType.word())
								.executes(this::lookWarp)))
				.then(literal("found")
						.then(argument("text", StringArgumentType.greedyString())
								.executes(this::foundWarp)))
				.then(literal("list")
						.executes(ctx -> listWarps(ctx, 1))
						.then(argument("page", IntegerArgumentType.integer(1))
								.executes(ctx -> listWarps(ctx, IntegerArgumentType.getInteger(ctx, "page")))))
		);
	}

	// --- /haw Admin Logic ---

	private int setHomeNum(CommandContext<ServerCommandSource> ctx) {
		int num = IntegerArgumentType.getInteger(ctx, "number");
		HawDataManager.setMaxHomes(num);
		String txt = num == -1 ? "无限" : String.valueOf(num);
		ctx.getSource().sendFeedback(() -> Text.literal("已设置每人最大Home数量为: " + txt).formatted(Formatting.GOLD), true);
		return 1;
	}

	private int setWarpNum(CommandContext<ServerCommandSource> ctx) {
		int num = IntegerArgumentType.getInteger(ctx, "number");
		HawDataManager.setMaxWarps(num);
		String txt = num == -1 ? "无限" : String.valueOf(num);
		ctx.getSource().sendFeedback(() -> Text.literal("已设置最大Warp数量为: " + txt).formatted(Formatting.GOLD), true);
		return 1;
	}

	// --- Home Logic ---

	private int createHome(CommandContext<ServerCommandSource> ctx, String comment) throws CommandSyntaxException {
		ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
		String name = StringArgumentType.getString(ctx, "name");
		Map<String, TeleportPoint> homes = HawDataManager.getHomes(player.getUuid());

		// 1. 查重
		if (homes.containsKey(name)) {
			ctx.getSource().sendError(Text.literal("创建失败：名为 " + name + " 的传送点已存在，请换一个名字。"));
			return 0;
		}

		// 2. 检查数量限制 (OP无视限制)
		int max = HawDataManager.getMaxHomes();
		if (max != -1 && homes.size() >= max && !player.hasPermissionLevel(3)) {
			ctx.getSource().sendError(Text.literal("创建失败：你的个人传送点数量已达上限 (" + max + ")。"));
			return 0;
		}

		HawDataManager.addHome(player.getUuid(), name, new TeleportPoint(player, comment));
		ctx.getSource().sendFeedback(() -> Text.literal("已创建个人传送点: " + name).formatted(Formatting.GREEN), false);
		return 1;
	}

	private int tpHome(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
		String name = StringArgumentType.getString(ctx, "name");

		TeleportPoint point = HawDataManager.getHome(player.getUuid(), name);
		if (point == null) {
			ctx.getSource().sendError(Text.literal("未找到名为 " + name + " 的传送点"));
			return 0;
		}
		point.teleport(player);
		ctx.getSource().sendFeedback(() -> Text.literal("已传送至: " + name).formatted(Formatting.GREEN), false);
		return 1;
	}

	private int renameHome(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
		String oldName = StringArgumentType.getString(ctx, "oldName");
		String newName = StringArgumentType.getString(ctx, "newName");

		boolean success = HawDataManager.renameHome(player.getUuid(), oldName, newName);
		if (success) {
			ctx.getSource().sendFeedback(() -> Text.literal("已将 " + oldName + " 重命名为 " + newName).formatted(Formatting.GREEN), false);
			return 1;
		} else {
			ctx.getSource().sendError(Text.literal("重命名失败：旧名称不存在或新名称已存在"));
			return 0;
		}
	}

	private int renoteHome(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
		String name = StringArgumentType.getString(ctx, "name");
		String newComment = StringArgumentType.getString(ctx, "newComment");

		boolean success = HawDataManager.renoteHome(player.getUuid(), name, newComment);
		if (success) {
			ctx.getSource().sendFeedback(() -> Text.literal("已更新 " + name + " 的注释").formatted(Formatting.GREEN), false);
			return 1;
		} else {
			ctx.getSource().sendError(Text.literal("未找到该传送点"));
			return 0;
		}
	}

	private int lookHome(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
		String name = StringArgumentType.getString(ctx, "name");
		Map<String, TeleportPoint> homes = HawDataManager.getHomes(player.getUuid());

		if (!homes.containsKey(name)) {
			ctx.getSource().sendError(Text.literal("未找到名为 " + name + " 的传送点"));
			return 0;
		}

		// 排序并计算ID
		List<Map.Entry<String, TeleportPoint>> sortedList = sortPoints(homes);
		int id = -1;
		for (int i = 0; i < sortedList.size(); i++) {
			if (sortedList.get(i).getKey().equals(name)) {
				id = i + 1;
				break;
			}
		}

		ctx.getSource().sendFeedback(() -> Text.literal("=== 传送点详情 ===").formatted(Formatting.GOLD), false);
		sendPointLine(ctx.getSource(), id, name, homes.get(name), "/home");
		return 1;
	}

	private int foundHome(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
		String query = StringArgumentType.getString(ctx, "text");
		Map<String, TeleportPoint> homes = HawDataManager.getHomes(player.getUuid());

		Map<String, TeleportPoint> matches = new HashMap<>();
		for (Map.Entry<String, TeleportPoint> entry : homes.entrySet()) {
			if (entry.getValue().comment != null && entry.getValue().comment.contains(query)) {
				matches.put(entry.getKey(), entry.getValue());
			}
		}

		if (matches.isEmpty()) {
			ctx.getSource().sendError(Text.literal("未找到注释中包含 \"" + query + "\" 的传送点"));
			return 0;
		}

		sendList(ctx.getSource(), matches, 1, "/home", "搜索结果");
		return 1;
	}

	private int requestDeleteHome(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
		String name = StringArgumentType.getString(ctx, "name");

		if (HawDataManager.getHome(player.getUuid(), name) == null) {
			ctx.getSource().sendError(Text.literal("传送点不存在"));
			return 0;
		}

		pendingHomeDeletes.put(player.getUuid(), name);
		ctx.getSource().sendFeedback(() -> Text.literal("请执行 /home confirm 以确认删除: " + name).formatted(Formatting.YELLOW), false);
		return 1;
	}

	private int confirmDeleteHome(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
		if (!pendingHomeDeletes.containsKey(player.getUuid())) {
			ctx.getSource().sendError(Text.literal("没有待确认的删除请求"));
			return 0;
		}

		String name = pendingHomeDeletes.remove(player.getUuid());
		HawDataManager.removeHome(player.getUuid(), name);
		ctx.getSource().sendFeedback(() -> Text.literal("已删除个人传送点: " + name).formatted(Formatting.GREEN), false);
		return 1;
	}

	private int listHomes(CommandContext<ServerCommandSource> ctx, int page) throws CommandSyntaxException {
		ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
		Map<String, TeleportPoint> homes = HawDataManager.getHomes(player.getUuid());
		sendList(ctx.getSource(), homes, page, "/home", "传送点列表");
		return 1;
	}

	// --- Warp Logic ---

	private boolean canManageWarp(ServerCommandSource source) {
		if (source.hasPermissionLevel(3)) return true;
		if (source.getEntity() instanceof ServerPlayerEntity player) {
			return HawDataManager.isWarpOp(player.getUuid());
		}
		return false;
	}

	private int createWarp(CommandContext<ServerCommandSource> ctx, String comment) throws CommandSyntaxException {
		if (!canManageWarp(ctx.getSource())) {
			ctx.getSource().sendError(Text.literal("你没有权限创建共享传送点"));
			return 0;
		}
		ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
		String name = StringArgumentType.getString(ctx, "name");
		Map<String, TeleportPoint> warps = HawDataManager.getWarps();

		// 1. 查重
		if (warps.containsKey(name)) {
			ctx.getSource().sendError(Text.literal("创建失败：名为 " + name + " 的共享点已存在，请换一个名字。"));
			return 0;
		}

		// 2. 检查数量限制 (OP无视限制)
		int max = HawDataManager.getMaxWarps();
		if (max != -1 && warps.size() >= max && !ctx.getSource().hasPermissionLevel(3)) {
			ctx.getSource().sendError(Text.literal("创建失败：共享传送点数量已达上限 (" + max + ")。"));
			return 0;
		}

		HawDataManager.addWarp(name, new TeleportPoint(player, comment));
		ctx.getSource().sendFeedback(() -> Text.literal("已创建共享传送点: " + name).formatted(Formatting.AQUA), true);
		return 1;
	}

	private int tpWarp(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
		String name = StringArgumentType.getString(ctx, "name");

		TeleportPoint point = HawDataManager.getWarp(name);
		if (point == null) {
			ctx.getSource().sendError(Text.literal("未找到共享传送点: " + name));
			return 0;
		}
		point.teleport(player);
		ctx.getSource().sendFeedback(() -> Text.literal("已传送至共享点: " + name).formatted(Formatting.AQUA), false);
		return 1;
	}

	private int renameWarp(CommandContext<ServerCommandSource> ctx) {
		if (!canManageWarp(ctx.getSource())) {
			ctx.getSource().sendError(Text.literal("你没有权限管理共享传送点"));
			return 0;
		}
		String oldName = StringArgumentType.getString(ctx, "oldName");
		String newName = StringArgumentType.getString(ctx, "newName");

		boolean success = HawDataManager.renameWarp(oldName, newName);
		if (success) {
			ctx.getSource().sendFeedback(() -> Text.literal("已将共享点 " + oldName + " 重命名为 " + newName).formatted(Formatting.AQUA), true);
			return 1;
		} else {
			ctx.getSource().sendError(Text.literal("重命名失败：旧名称不存在或新名称已存在"));
			return 0;
		}
	}

	private int renoteWarp(CommandContext<ServerCommandSource> ctx) {
		if (!canManageWarp(ctx.getSource())) {
			ctx.getSource().sendError(Text.literal("你没有权限管理共享传送点"));
			return 0;
		}
		String name = StringArgumentType.getString(ctx, "name");
		String newComment = StringArgumentType.getString(ctx, "newComment");

		boolean success = HawDataManager.renoteWarp(name, newComment);
		if (success) {
			ctx.getSource().sendFeedback(() -> Text.literal("已更新共享点 " + name + " 的注释").formatted(Formatting.AQUA), true);
			return 1;
		} else {
			ctx.getSource().sendError(Text.literal("未找到该传送点"));
			return 0;
		}
	}

	private int lookWarp(CommandContext<ServerCommandSource> ctx) {
		String name = StringArgumentType.getString(ctx, "name");
		Map<String, TeleportPoint> warps = HawDataManager.getWarps();

		if (!warps.containsKey(name)) {
			ctx.getSource().sendError(Text.literal("未找到共享传送点: " + name));
			return 0;
		}

		List<Map.Entry<String, TeleportPoint>> sortedList = sortPoints(warps);
		int id = -1;
		for (int i = 0; i < sortedList.size(); i++) {
			if (sortedList.get(i).getKey().equals(name)) {
				id = i + 1;
				break;
			}
		}

		ctx.getSource().sendFeedback(() -> Text.literal("=== 共享点详情 ===").formatted(Formatting.GOLD), false);
		sendPointLine(ctx.getSource(), id, name, warps.get(name), "/warp");
		return 1;
	}

	private int foundWarp(CommandContext<ServerCommandSource> ctx) {
		String query = StringArgumentType.getString(ctx, "text");
		Map<String, TeleportPoint> warps = HawDataManager.getWarps();

		Map<String, TeleportPoint> matches = new HashMap<>();
		for (Map.Entry<String, TeleportPoint> entry : warps.entrySet()) {
			if (entry.getValue().comment != null && entry.getValue().comment.contains(query)) {
				matches.put(entry.getKey(), entry.getValue());
			}
		}

		if (matches.isEmpty()) {
			ctx.getSource().sendError(Text.literal("未找到注释中包含 \"" + query + "\" 的共享点"));
			return 0;
		}

		sendList(ctx.getSource(), matches, 1, "/warp", "搜索结果");
		return 1;
	}

	private int requestDeleteWarp(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!canManageWarp(ctx.getSource())) {
			ctx.getSource().sendError(Text.literal("你没有权限删除共享传送点"));
			return 0;
		}
		ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
		String name = StringArgumentType.getString(ctx, "name");

		if (HawDataManager.getWarp(name) == null) {
			ctx.getSource().sendError(Text.literal("传送点不存在"));
			return 0;
		}

		pendingWarpDeletes.put(player.getUuid(), name);
		ctx.getSource().sendFeedback(() -> Text.literal("请执行 /warp confirm 以确认删除: " + name).formatted(Formatting.YELLOW), false);
		return 1;
	}

	private int confirmDeleteWarp(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		if (!canManageWarp(ctx.getSource())) return 0;
		ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();

		if (!pendingWarpDeletes.containsKey(player.getUuid())) {
			ctx.getSource().sendError(Text.literal("没有待确认的删除请求"));
			return 0;
		}

		String name = pendingWarpDeletes.remove(player.getUuid());
		HawDataManager.removeWarp(name);
		ctx.getSource().sendFeedback(() -> Text.literal("已删除共享传送点: " + name).formatted(Formatting.AQUA), true);
		return 1;
	}

	private int listWarps(CommandContext<ServerCommandSource> ctx, int page) {
		Map<String, TeleportPoint> warps = HawDataManager.getWarps();
		sendList(ctx.getSource(), warps, page, "/warp", "共享点列表");
		return 1;
	}

	// --- Haw Op Logic ---

	private int addWarpOp(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
		HawDataManager.addWarpOp(target.getUuid());
		ctx.getSource().sendFeedback(() -> Text.literal("已为 " + target.getName().getString() + " 添加Warp管理权限").formatted(Formatting.GOLD), true);
		return 1;
	}

	private int removeWarpOp(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
		HawDataManager.removeWarpOp(target.getUuid());
		ctx.getSource().sendFeedback(() -> Text.literal("已取消 " + target.getName().getString() + " 的Warp管理权限").formatted(Formatting.GOLD), true);
		return 1;
	}

	private int listWarpOps(CommandContext<ServerCommandSource> ctx) {
		Set<UUID> ops = HawDataManager.getWarpOps();
		if (ops.isEmpty()) {
			ctx.getSource().sendFeedback(() -> Text.literal("当前没有额外的Warp管理员").formatted(Formatting.YELLOW), false);
			return 1;
		}

		MutableText msg = Text.literal("=== Warp 管理员列表 ===\n").formatted(Formatting.GOLD);
		for (UUID uuid : ops) {
			Optional<GameProfile> profile = ctx.getSource().getServer().getUserCache().getByUuid(uuid);
			String name = profile.map(GameProfile::getName).orElse(uuid.toString());
			msg.append(Text.literal("- " + name + "\n").formatted(Formatting.WHITE));
		}
		ctx.getSource().sendFeedback(() -> msg, false);
		return 1;
	}

	// --- 列表与显示逻辑 ---

	// 辅助方法：将Map按时间戳排序
	private List<Map.Entry<String, TeleportPoint>> sortPoints(Map<String, TeleportPoint> points) {
		List<Map.Entry<String, TeleportPoint>> list = new ArrayList<>(points.entrySet());
		// 按创建时间排序（timestamp）
		list.sort(Comparator.comparingLong(entry -> entry.getValue().timestamp));
		return list;
	}

	private void sendList(ServerCommandSource source, Map<String, TeleportPoint> points, int page, String cmdBase, String listTitle) {
		int pageSize = 10;
		int total = points.size();
		int maxPages = (int) Math.ceil((double) total / pageSize);
		if (maxPages == 0) maxPages = 1;

		if (page < 1) page = 1;
		if (page > maxPages) page = maxPages;

		source.sendMessage(Text.literal("=== " + listTitle + " (第 " + page + "/" + maxPages + " 页) ===").formatted(Formatting.GOLD));

		// 使用时间排序的List
		List<Map.Entry<String, TeleportPoint>> sortedList = sortPoints(points);

		int start = (page - 1) * pageSize;
		int end = Math.min(start + pageSize, total);

		for (int i = start; i < end; i++) {
			Map.Entry<String, TeleportPoint> entry = sortedList.get(i);
			// i + 1 是它在按时间排序列表中的ID
			sendPointLine(source, i + 1, entry.getKey(), entry.getValue(), cmdBase);
		}

		// 翻页按钮
		Text prevBtn = Text.literal("<上一页> ")
				.setStyle(Style.EMPTY.withColor(page > 1 ? Formatting.GREEN : Formatting.GRAY)
						.withClickEvent(new ClickEvent.RunCommand(cmdBase + " list " + (page - 1))));

		Text info = Text.literal("第" + page + "页/共" + maxPages + "页 ").formatted(Formatting.WHITE);

		Text nextBtn = Text.literal("<下一页>")
				.setStyle(Style.EMPTY.withColor(page < maxPages ? Formatting.GREEN : Formatting.GRAY)
						.withClickEvent(new ClickEvent.RunCommand(cmdBase + " list " + (page + 1))));

		source.sendMessage(Text.empty().append(prevBtn).append(info).append(nextBtn));
	}

	private void sendPointLine(ServerCommandSource source, int id, String name, TeleportPoint p, String cmdBase) {
		String commentStr = (p.comment == null || p.comment.isEmpty()) ? "" : p.comment;
		String timeStr = TIME_FORMATTER.format(Instant.ofEpochMilli(p.timestamp));

		// 格式：ID:1 (黄色) | name (绿色,可点击) | comment (白色) | time (灰色)

		MutableText idPart = Text.literal("ID:" + id).setStyle(Style.EMPTY.withColor(Formatting.YELLOW));
		MutableText space = Text.literal(" ");

		MutableText namePart = Text.literal(name)
				.setStyle(Style.EMPTY
						.withColor(Formatting.GREEN)
						.withClickEvent(new ClickEvent.RunCommand(cmdBase + " tp " + name))
						.withHoverEvent(new HoverEvent.ShowText(Text.literal("点击传送至 " + name))));

		MutableText commentPart = Text.literal(commentStr).setStyle(Style.EMPTY.withColor(Formatting.WHITE));
		MutableText timePart = Text.literal(timeStr).setStyle(Style.EMPTY.withColor(Formatting.GRAY));

		source.sendMessage(idPart
				.append(space)
				.append(namePart)
				.append(space)
				.append(commentPart)
				.append(space)
				.append(timePart));
	}
}