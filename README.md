# LowDragLib2 - 1.20.1 Forge 移植版

这是 `LowDragLib2` 的 **Minecraft 1.20.1 Forge 非官方移植版**。

这个仓库主要是为了让仍在使用 1.20.1 Forge 的整合包、服务器和模组项目，也能继续使用 LDLib2 的 UI、渲染、同步、持久化和游戏内编辑器相关能力。

## 开源与来源声明

本项目是基于原项目 `LDLib2` 进行的移植与兼容维护，不是原作者发布的官方版本。

- **原作者 / 版权所有者：** KilaBash / Low-Drag-MC
- **原项目仓库：** https://github.com/Low-Drag-MC/LDLib2
- **原项目文档：** https://low-drag-mc.github.io/LowDragMC-Doc/en/ldlib2/
- **当前移植目标：** Minecraft 1.20.1 + Forge 47.x
- **开源协议：** GNU Lesser General Public License v3.0，见仓库根目录 `LICENSE`


## 现在能做什么

LDLib2 是一个面向模组开发者的基础库，不是单独给玩家游玩的内容模组。它主要提供这些能力：

- 游戏内 UI 框架：组件、布局、样式、XML UI、数据绑定和事件
- 游戏内编辑器：资源管理器、检视面板、UI 模板编辑、预览和保存
- 渲染工具：GUI 纹理、模型/场景渲染、Shader 相关封装
- 同步与持久化：NBT、Codec、字段同步、RPC、网络包
- 资源系统：颜色、贴图、UI 模板、渲染器等资源的加载和编辑
- 常见模组生态兼容：JEI、REI、EMI、KubeJS 等相关接口

## 适配进度

当前分支以 **Minecraft 1.20.1 + Forge 47.4.10** 为目标环境，已经完成基础运行和多人服验证。

已验证项目：

- 客户端可以启动并进入游戏
- Dedicated Server 可以启动
- 客户端和服务端双端安装后可以正常进服
- `/ldlib2_ui_editor` 可以在多人服务器中打开 UI 编辑器
- UI 编辑器基础渲染、资源面板、检视面板可以显示
- 已修复 1.20.1 Forge 下的部分 Mixin、网络包、字体和编辑器显示问题

本项目目前只围绕原项目做 Minecraft 1.20.1 Forge 的版本兼容与必要修复，不加入额外功能，也不改变原项目方向。

## 使用方式

客户端和服务端都需要安装本模组。

推荐环境：

- Minecraft：`1.20.1`
- Forge：`47.4.10`，或同系列 47.x
- Java：`17+`

把构建出的 `-all.jar` 放入客户端和服务端的 `mods` 文件夹即可。

> 注意：这个库属于双端模组。多人服务器上使用时，客户端和服务端需要保持同一个版本，否则可能出现网络通道不匹配或连接失败。

## 开发构建

```bash
./gradlew build
```

如果 ForgeGradle 证书检查导致依赖下载失败，可以使用：

```bash
./gradlew -Dnet.minecraftforge.gradle.check.certs=false build
```

构建产物在：

```text
build/libs/
```

服务器和客户端测试时优先使用文件名带 `-all.jar` 的包。

## 游戏内编辑器

有 OP 权限的玩家可以在服务器内执行：

```text
/ldlib2_ui_editor
```

这个命令会在客户端打开 LDLib2 的 UI 编辑器。它主要用于开发和调试 UI 模板，不是普通玩家菜单。

编辑器里可以做的事：

- 新建或打开 UI 项目
- 编辑 XML UI 配置
- 查看 UI 预览
- 管理 UI / 颜色 / 贴图资源
- 在检视面板里调整元素属性
- 保存 UI 模板

## 关于赞助和维护

这个 1.20.1 Forge 移植版后续如果继续维护，主要依赖社区自愿支持。

赞助只用于补贴开发、测试和兼容维护所花的时间，不会设置付费墙，也不会把开源版本变成私有项目。本项目会保持公开、免费，并继续遵守原项目的 LGPL-3.0 协议。

## 鸣谢

感谢 KilaBash 和 Low-Drag-MC 带来的 LDLib / LDLib2。这个库里很多设计都很扎实，移植工作也是建立在原项目长期积累的基础上。

如果你需要最新的官方版本、完整文档或上游更新，请优先查看原项目：

- https://github.com/Low-Drag-MC/LDLib2
- https://low-drag-mc.github.io/LowDragMC-Doc/en/ldlib2/

## 协议

本项目遵循 **GNU Lesser General Public License v3.0**。

完整协议文本见仓库根目录的 `LICENSE` 文件。
