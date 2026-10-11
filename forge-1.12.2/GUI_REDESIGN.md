# VeinMiner Plus GUI 改进方案

## 📋 概述
重新设计了配置界面，使用 **Section 分组设计**，将配置参数按功能分类，大幅提升视觉层次和用户体验。

## ✨ 主要改进

### 1. **分Section显示** 
配置分为4个逻辑组，每组有清晰的标题和边框：
- **⛏️ 普通挖矿** - Normal mining 参数
- **💣 高级爆炸挖矿** - Blast mining 相关参数
- **⚡ 性能优化** - Performance 和 TPS 设置  
- **👤 玩家行为** - 饥饿值、挖矿模式等

### 2. **改进排版**
- **更大的间距** - COLUMN_GAP 从 8px 增加到 12px，SECTION_SPACING 新增 14px
- **更好的垂直节奏** - 每个section之间有15px间距，避免拥挤感
- **对齐优化** - 使用网格布局（grid），确保字段整齐对齐

### 3. **增强视觉反馈**
- **Section 标题** - 新增 SECTION_TITLE_COLOR，左边框用 accent 色突出
- **Toggle 按钮指示器** - 左侧 2px 竖条在开启时显示 accent 色
- **Hover 效果** - 按钮 hover 时背景色变亮，边框变成 accent 色
- **Focus 状态** - 输入框获得焦点时border变成 accent 色，加发光效果

### 4. **改进配色** 
新增配色常量：
```java
private static final int SECTION_PANEL_COLOR = 0xF0141C26;  // 稍深的panel色
private static final int SECTION_TITLE_COLOR = 0xFF59D8BC;   // 与border相同
```

这样section标题和border色保持一致，视觉更统一。

### 5. **响应式设计**
- 窗口高度 < 350px 时自动切换紧凑模式
- fieldRowHeight 自动调整（28px compact vs 30px normal）
- fieldHeight 自动调整（16px compact vs 18px normal）

## 🔄 实现细节

### 修改的常量
```java
private static final int COLUMN_GAP = 12;              // 从 8 改为 12
private static final int SECTION_SPACING = 14;         // 新增
private static final int SECTION_HEADER_HEIGHT = 18;   // 新增
private static final int SECTION_PANEL_COLOR = 0xF0141C26;  // 新增
private static final int SECTION_TITLE_COLOR = 0xFF59D8BC;   // 新增
```

### 新增方法
```java
private void addSectionLabel(int x, int y, String translationKey)
// 占位符方法，用于在initGui中标记section位置

private void drawSectionHeader(int x, int y, int right, String translationKey)
// 绘制section标题和边框

private void drawFieldSection(int left, int startY, int columnWidth, int right, int startField, int endField)
// 绘制一组字段及其背景panel
```

### initGui 重构
现在使用 section-based 布局：

```
Normal Mining Section
├─ Field 0: Normal Limit
├─ Field 1: Normal Speed

Blast Mining Section  
├─ Field 2: Blast Limit
├─ Field 3: Blast Speed
├─ Field 4: Blast Distance
├─ Button 100: Manhattan
└─ Button 101: Auto Radius

Performance Section
├─ Field 5: Blast Scan Speed
├─ Field 6: Low TPS

Player Behavior Section
├─ Button 102: Consume Hunger
└─ Button 103: Chain Mode
```

## 🎨 视觉效果对比

### 旧设计的问题
- ❌ 所有字段堆在一起，无分类
- ❌ 按钮和字段混在一起，结构混乱
- ❌ 间距太小，看起来拥挤
- ❌ 没有明显的功能区分
- ❌ 配置项多时容易迷失

### 新设计的优势
- ✅ 清晰的功能分组
- ✅ 每个section独立，易于查找
- ✅ 更好的垂直间距和呼吸感
- ✅ Section 标题快速导航
- ✅ 视觉层次明确，优先级清晰

## 📝 翻译键要求

需要在语言文件中添加以下翻译键：
```properties
screen.veinminerplus.config.section.normal=⛏️ 普通挖矿
screen.veinminerplus.config.section.blast=💣 高级爆炸挖矿
screen.veinminerplus.config.section.performance=⚡ 性能优化
screen.veinminerplus.config.section.player=👤 玩家行为
```

（或者不用emoji，改为纯文本：Normal Mining, Blast Mining, Performance, Player）

## 🔧 兼容性

- **MC 1.12.2**：完全兼容旧API（GuiScreen, GuiButton, GuiTextField）
- **MC 1.20.1**：已有类似的现代化实现（参考已有的1.20.1版本代码）
- **响应式**：自动适应各种分辨率

## 📚 后续优化建议

1. **添加帮助提示** - 每个字段 hover 时显示范围提示（已有实现）
2. **Smooth scroll** - 内容多时支持平滑滚动
3. **搜索功能** - 快速查找配置项
4. **预设配置** - 提供几个快速配置预设
5. **暗黑模式** - （已支持）亮色模式切换

## 演示

已生成HTML预览页面 `gui_preview.html`，可在浏览器中打开查看完整的UI设计效果。
