本次发布提供三个独立 JAR，请选择与 Minecraft 版本及加载器相符的文件。

2026 年 10 月 4 日更新：三个附件已替换为完成 Forge 同步后的构建产物，保留各加载器的独立版本号。

| Minecraft | 加载器 | Mod 版本 | 文件 |
| --- | --- | --- | --- |
| 1.21.1 | NeoForge | 1.2.35 | veinminerplusG-1.21.1-neoforge-1.2.35.jar |
| 1.20.1 | Forge | 1.2.23 | veinminerplusG-1.20.1-forge-1.2.23.jar |
| 1.12.2 | Forge | 1.2.21-forge1122 | veinminerplusG-1.12.2-forge-1.2.21.jar |

### 功能与版本差异

- NeoForge 1.21.1：一行 9 格的透视矿石选择界面、JEI 拖放、按具体矿石方块匹配的空心边框、独立格子颜色，以及 RGB / HEX / 预设颜色编辑。
- Forge 1.20.1：同步上述透视选择、颜色配置、渐进扫描和轮廓渲染，并适配 Forge 事件与 JEI 15 接口。
- Forge 1.12.2：适配旧版客户端 API，提供透视选择、颜色配置、扫描和轮廓渲染。暂未集成 JEI 拖放；对准矿石后按英文单引号键打开界面，左键点击空槽可填入准星指向的矿石。
- 三个版本默认均使用英文单引号键（'）打开透视选择界面；选择内容和颜色保存在客户端 `config/veinminerplus-xray.json` 中。

Forge 同步源码提交：[c553137](https://github.com/GuHanDuRen/VeinMinerPLUS/commit/c553137ba4a1abdd565db67965d8caf51dde99e8)。本次更新附件未移动原有 `v1.2.35` 标签；包含 Forge 同步的源码请使用此提交。

### 验证

三个版本均已构建成功，两个 Forge 版本均执行了 `reobfJar`；已核对 JAR 元数据、ZIP 完整性和 SHA-256。未启动游戏，GUI、透视显示、JEI 拖放和剪贴板行为尚未进行游戏内验证。

### SHA-256

| 文件 | SHA-256 |
| --- | --- |
| veinminerplusG-1.21.1-neoforge-1.2.35.jar | C952FBBAF9A39A9B276537C1C1FD089F39D176782074949C6494A6B20C6BF495 |
| veinminerplusG-1.20.1-forge-1.2.23.jar | 0A14E950C1576FE6EAFB415D0FC3B6265F596C4FF2684655951561E1D315FDFB |
| veinminerplusG-1.12.2-forge-1.2.21.jar | 8261B510F8B825A05EE76DF5C6E392211781AFFA913860F9403254B3DFD9DD91 |
