# Home and Warp (HAW)

![Fabric](https://img.shields.io/badge/Loader-Fabric-bebebe)
![MC Version](https://img.shields.io/badge/Minecraft-1.21%2B-green)
![License](https://img.shields.io/badge/License-MIT-blue)

**Home and Warp (简称 haw)** 是一个适用于 Minecraft Fabric 1.21+ 的轻量级服务端传送模组。它提供了完善的个人传送点（Home）和共享传送点（Warp）系统，支持点击交互、注释搜索、时间排序以及数量限制管理。

## ✨ 主要功能

*   **个人传送点 (Home)**：玩家可以设置私有的传送点。
*   **共享传送点 (Warp)**：管理员或拥有特定权限的玩家可设置全服可见的传送点。
*   **交互式列表**：
    *   列表按**创建时间**排序，并在末尾显示具体日期时间。
    *   支持**点击传送**，支持点击翻页。
    *   列表美化：`ID(黄) 名称(绿) 注释(白) 时间(灰)`。
*   **管理功能**：
    *   重命名 (Rename) 和 修改注释 (Renote)。
    *   详细查询 (Look) 和 模糊搜索 (Found)。
    *   限制玩家最大设置数量 (全局配置)。
    *   特定的 Warp 管理员权限系统。

## 🛠️ 安装说明

1.  安装 [Fabric Loader](https://fabricmc.net/)。
2.  下载本模组的 `.jar` 文件。
3.  下载对应版本的 [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api)。
4.  将本模组和 Fabric API 放入游戏目录的 `mods` 文件夹中。
5.  启动游戏/服务器。

## 📖 指令列表

### 🏠 个人传送点 (/home)
所有玩家默认可用。

| 指令 | 描述 |
| :--- | :--- |
| `/home create <name> [注释]` | 创建一个名为 `name` 的传送点（支持中文注释）。 |
| `/home tp <name>` | 传送到指定的传送点（也可以在列表中点击传送）。 |
| `/home delete <name>` | 请求删除指定传送点。 |
| `/home confirm` | 二次确认删除操作。 |
| `/home list [页码]` | 查看传送点列表（按创建时间排序）。 |
| `/home rename <旧名> <新名>` | 重命名传送点。 |
| `/home renote <name> <新注释>` | 修改传送点的注释。 |
| `/home look <name>` | 查看指定传送点的详细信息（ID、时间等）。 |
| `/home found <文字>` | 搜索注释中包含指定文字的传送点。 |

### 🌏 共享传送点 (/warp)
所有玩家可使用 `tp`, `list`, `look`, `found`。创建和管理需要 OP 或 Warp 权限。

| 指令 | 描述 |
| :--- | :--- |
| `/warp tp <name>` | 传送到共享点。 |
| `/warp list [页码]` | 查看共享点列表。 |
| `/warp look <name>` | 查看共享点详情。 |
| `/warp found <文字>` | 搜索共享点。 |
| `/warp create <name> [注释]` | **(管理)** 创建共享点。 |
| `/warp delete <name>` | **(管理)** 删除共享点。 |
| `/warp confirm` | **(管理)** 确认删除共享点。 |
| `/warp rename ...` | **(管理)** 重命名共享点。 |
| `/warp renote ...` | **(管理)** 修改共享点注释。 |

### ⚙️ 管理员指令 (/haw)
仅 3级以上管理员 (OP) 可用。

| 指令 | 描述 |
| :--- | :--- |
| `/haw op add <玩家>` | 给予玩家 Warp 管理权限（允许非OP玩家管理Warp）。 |
| `/haw op delete <玩家>` | 移除玩家的 Warp 管理权限。 |
| `/haw op list` | 查看拥有特殊权限的玩家列表。 |
| `/haw set homenum <数字>` | 设置每个人最多能创建多少个 Home (`-1` 为无限)。 |
| `/haw set warpnum <数字>` | 设置服务器最多能创建多少个 Warp (`-1` 为无限)。 |

## 📂 配置文件

模组配置文件位于 `config/haw/` 目录下：

*   `config.json`: 存储全局设置（如最大数量限制）。
*   `homes.json`: 存储所有玩家的 Home 数据。
*   `warps.json`: 存储所有 Warp 数据。
*   `permissions.json`: 存储拥有额外 Warp 权限的玩家列表。

*数据文件为 JSON 格式，支持在关服状态下手动编辑。*

## 🔨 开发构建

如果你想自己编译源码：

1.  克隆本仓库。
2.  确保安装了 JDK 21 (对应 MC 1.21)。
3.  在项目根目录运行命令：
    *   Windows: `.\gradlew build`
    *   Linux/Mac: `./gradlew build`
4.  编译后的文件位于 `build/libs/` 目录。

## 📝 开源协议

MIT License
