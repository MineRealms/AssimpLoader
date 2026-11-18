# DCC MCP 选择与连接

推荐核对日期：2026-11-18。以下都是社区工具，不是 Blender/Autodesk 官方远程控制协议。首次使用时读取对应上游版本说明，复用玩家已有连接。

| 环境 | 推荐入口 | 本项目使用方式 |
| --- | --- | --- |
| Blender | 当前 Agent 已提供的 Blender 工具；没有时可选 [ahujasid/mcp-for-blender](https://github.com/ahujasid/mcp-for-blender) | 读取场景，然后在应用内运行 `blender_export.export_job(...)` |
| 3ds Max | [cl0nazepamm/3dsmax-mcp](https://github.com/cl0nazepamm/3dsmax-mcp) | 选择 Max 实例，通过桥接在应用内执行 `pymxs` 导出入口 |
| Maya | [chadrik/maya-mcp-server](https://github.com/chadrik/maya-mcp-server) | 发现 Maya session，通过 Python 执行入口调用导出器 |

这些推荐依据各上游直接提供的场景读取和脚本执行能力；不是跨版本实测排名。MCP 自身不会安装或授权 DCC 软件。

## 共用连接流程

1. 根据玩家选择和现有工具发现目标 DCC；不要因名称相似就连接另一个实例。
2. 已有可用 MCP 时直接读版本、场景文件和选择，不重复安装。
3. 缺桥接时读取上表上游的当前安装说明，安装到玩家使用的 Agent 客户端，并启用对应 DCC 端桥接。
4. 将服务绑定在本机。由应用的主线程调度器执行场景操作，避免网络线程直接修改场景。
5. 工具重新发现后执行只读握手。成功标准是读到目标应用版本、场景路径和选择，不是端口能连接。
6. 进入资产处理流程，处理完成后保留游戏客户端和供玩家检查的源副本。

Agent 客户端若不能动态加载新 MCP，配置完成后可能需要重新启动该客户端；这是工具注册限制，不能通过在终端单独运行 MCP 进程来绕过。记录已完成的安装和下一条握手操作，不能假称本轮已经取得新工具。

## Blender

优先使用当前会话的 Blender 工具。其工具名、参数和状态查询以实际发现的文档为准，不固定某一 `mcp__` 前缀。

上游 `blender-mcp` 已更名为 `mcp-for-blender`，旧安装可能保留旧名。新安装可按[上游 README](https://github.com/ahujasid/mcp-for-blender#quickstart)使用：

```text
uvx mcp-for-blender setup
```

这是上游的安装/配置入口；运行前核对所装版本的选项和目标 Agent。安装 DCC 插件并启用桥接后，先查询插件状态与场景，再调用 Python。

不要在连接探测时清空场景。材质节点按类型查找，不能依赖本地化的显示名称；枚举值读取当前 Blender API，不假定所有版本都支持同一参数。

## Maya

按[上游说明](https://github.com/chadrik/maya-mcp-server#installation)配置 stdio 启动命令：

```text
uvx maya-mcp-server
```

在目标 Maya 的 Python Script Editor 或已授权的启动脚本中打开本机 command port，例如：

```python
import maya.cmds as cmds
port = '127.0.0.1:7002'
if not cmds.commandPort(port, query=True):
    cmds.commandPort(name=port, sourceType='python')
```

端口只是示例，优先用该环境现有端口。上游提供 `list_sessions`、`add_session` 等发现操作；先读取实际工具 schema，再指定返回的 session key。场景修改通过该桥接的 Python 执行工具完成。

## 3ds Max

上游当前说明的原生桥接适配 Max 2023-2027。源代码安装入口为：

```text
git clone https://github.com/cl0nazepamm/3dsmax-mcp.git
cd 3dsmax-mcp
uv sync
uv run python install.py
```

Python、uv 和 Max 版本要求以[该版本 README](https://github.com/cl0nazepamm/3dsmax-mcp#requirements)为准。上游安装流程要求关闭 Max/Agent；先保存工作，再安排安装和重启，不能强制结束未保存会话。已有连接可跳过安装。

连接后发现并选择实例，读取 `maxVersion()`、源文件和选择，再调用 `max_export.export_job(...)`。`.max` 需实际 Max 打开；用户没有 Max 时应由有 Max 的环境先导出，或使用已有可解析交换文件。
