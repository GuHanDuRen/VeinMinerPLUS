# VeinMinerPlus v1.2.36 Release Notes

## 🐛 重要修复

### 修复爆破同类方块只能破坏一个的严重Bug
- **问题描述**：长时间连续爆破后，爆破同类方块模式只能破坏一个方块
- **根本原因**：持久化缓存污染 - 缓存中存储了已被破坏的方块位置
- **解决方案**：
  - 添加缓存验证机制，使用前检查方块状态
  - 过滤掉已被破坏的无效缓存条目
  - 添加详细的调试日志便于排查

### 修复按键松开响应延迟
- **问题描述**：松开按键后进度显示不消失，需要等待
- **解决方案**：
  - 按键释放时立即清除进度显示（actionbar）
  - 无需等待下一个tick
  - 提升用户体验流畅度

## ⚡ 性能优化

### LRU缓存策略优化
- 使用 `LinkedHashMap` 的真正LRU特性
- 自动淘汰最少使用的缓存条目
- 保留热数据，提升缓存命中率
- 避免手动管理导致的性能损失

### 代码清理
- 移除 `PENDING_JOBS` 的无效操作
- 添加 `LinkedHashMap` 导入
- 清理git仓库中的gradle缓存文件

## 📦 适用版本

- **Minecraft**: 1.21.1
- **Mod Loader**: NeoForge 21.1.233

## 🔧 安装方法

1. 下载对应版本的jar文件
2. 将jar文件放入 `.minecraft/mods` 目录
3. 启动游戏

## 🙏 致谢

本次修复由 Claude Opus 5.5 协助完成代码审查和优化。

## 📝 完整变更日志

- fix: 修复持久化缓存污染导致长时间爆破后只能破坏一个方块
- fix: 修复松开按键后进度显示残留和响应延迟
- perf: 优化缓存策略，使用LinkedHashMap实现真正的LRU淘汰
- refactor: 移除PENDING_JOBS无效操作
- chore: 添加缓存验证机制，过滤已破坏的方块位置
- chore: 清理git仓库无用文件

---

**下载**: [veinminerplusG-1.21.1-neoforge-1.2.36.jar](https://github.com/GuHanDuRen/VeinMinerPLUS/releases/download/v1.2.36/veinminerplusG-1.21.1-neoforge-1.2.36.jar)
