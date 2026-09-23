# 反混淆指南（QStory 2.6.4 / QQ 9.3.15）

> 目标读者：需要在混淆产物里定位类/方法/字符串的 agent 或开发者。
> 配套工具：`xiaomi/catcrypt/`（喵语编解码）、`qstory/qq9315/`（取证工具链）、`qstory/pool_264.json`（3403 条全解密字符串池）。

## 1. 喵呜字符串加密（QStory 自研，已完全破解）

QStory 2.6.4 的所有自身字符串（功能名/配置 key/日志/锚点文案）都加密存储：

```
明文 UTF-8 字节 → XOR 重复密钥 "suzhelan" → 每字节拆 8 位
→ 喵 = 0、呜 = 1（高位在前）→ 字节之间用 '~' 分隔
```

- 解密工具：`xiaomi/catcrypt/catcrypt.py`
  - `python catcrypt.py encode "文本"` / `decode "喵呜~..."` / `verify`（自检）
  - 密钥固定 `suzhelan`（309 组运行时捕获 + 3403 条池串全量解密 0 失败）
- **全量已解密字符串池**：`qstory/pool_264.json`（key = 池索引十进制字符串）
  —— 找 QStory 功能名、配置 key、日志文案**先搜这里**，不用再解密。
- 原始形态：smali 里以 `\u55b5`（喵）`\u545c`（呜）转义存储，grep 时注意。

**已知边界**：17 条池串里 7 条用 suzhelan 解不开 = QStory 还有其他密钥池（未影响任何修复）。

## 2. 飘花类名混淆（QStory 自身）

QStory 自己的类/方法/字段名 = 固定字符集 `飘花落叶言 + 楪世子苏哲兰`（偶尔混入蝶花舞）
的随机排列，R8 重命名后约 200 个包目录全是排列名。

- **跨版本不可按名对应**（每次构建字符排列重新洗牌）。
- 定位 QStory 类的正确姿势：按**调用结构**（如解密器链路）、按**池索引引用**
  （`grep "0x8d1"` 类似常量）、或按 `top/suzhelan/qstory/` 下未混淆的包名 + 方法名
  （loadHook / getItemPath 等框架方法名未混淆）。

## 3. QQ 侧的混淆情况（9.3.15 实测）

| 类别 | 例子 | 稳定性 | 定位方式 |
|---|---|---|---|
| **稳定名**（可直接硬编码） | `com.qzone.reborn.feedx.itemview.*`、`com.tencent.qzonehub.api.impl.*`、`com.tencent.mobileqq.activity.leba.QzoneFrame`、`com.tencent.biz.subscribe.part.block.base.RefreshHeaderView` | QQ 长期未混淆这些业务名 | `.clazz` 直查 |
| **混淆名**（随版本必变） | `com.qzone.reborn.feedx.widget.j`（列表装饰器）、`飘花落叶言*`（QStory 自身） | 每版本重排 | DexKit 语义锚点 / 双声明特征扫描 / 字符串常量定位 |
| 资源 | layout 名在 arsc 有原名（`qzone_feedx_item_default_text`）但 APK 内真实路径混淆为 `r/i/xxx.xml` | — | aapt2 dump resources 反查真实路径 |

## 4. 混淆名重定位三法（按优先级）

### 法一：双声明特征扫描（定位装饰器/绘制类）

同时声明 `getItemOffsets(Rect, View, RecyclerView, State)` 和 `onDraw(Canvas, ...)` 的类
= RecyclerView.ItemDecoration 实现。工具：`qstory/qq9315/find_onDraw.py`
（灰带真凶 `feedx/widget/j` 就是这么找到的；QQ 升级后改 HINTS 前缀重跑）。

### 法二：字符串锚点（定位功能类/方法）

类引用的**字符串常量**（QQ 自己的文案/埋点串不加密，如 `"init Mini App, cost="`、
`"FrameControllerInjectImpl.setQzoneLebaTab"`、`"inputRoot"`）比类名稳定得多。
工具：`dexquery.py`（字符串→类）、`find_input_root.py`（方法级双锚点）。
注意事项：
- 两个锚点字符串可能分属不同 dex（方法级 AND 匹配会落空，需类级聚合再人工判定）；
- DexKit 的 usingStrings 是 AND 语义，锚点必须同 dex。

### 法三：调用链反查（定位决策点）

从 perf 埋点字符串（`"FrameControllerInjectImpl.setQzoneLebaTab"` 等）或日志 tag
反查调用方，逐指令读调用链（参考 B2 的 checkBusinessSwitch 定位过程）。

## 5. tinker 热修覆盖（运行时差异源）

QQ 运行时类 = base.apk + tinker 热修覆盖。**静态 base.apk 与运行时可能不一致**：

- 拉取：`adb exec-out su -c "cat /data/user/0/com.tencent.mobileqq/tinker/patch-*/dex/tinker_classN.apk" > tinker_classN.apk`
- 判定某类运行时版本：在 tinker 反汇编里找同名类，找到 = tinker 版本生效，找不到 = base 版本生效。
- 实录：需要 needShowQzoneFrame 的判断曾因没查 tinker 走错一次。

## 6. QUI 语义色体系（视觉修复相关）

QQ 的 QUI 组件库用**语义色**而非硬编码色值：
`qui_common_fill_light_secondary_bg`（浅色次要填充 ≈ #dedddc，即灰带底色）、
`qui_common_fill_standard_primary_bg_corner_4` 等。

- 颜色随主题/深色模式变化 → **禁止按色值匹配做修复**；
- 正确做法：按视图层级/结构定位后置空背景或让页面级皮肤透出（见 gray-band-postmortem.md）。
- 色值采样（像素级）只用于**确认问题**，不用于实现。

## 7. 反混淆工作流（汇总）

```
遇到找不到的类/方法/字符串
→ 1. 是 QStory 自身？→ pool_264.json 搜功能名 → 注册表反查类
→ 2. 是 QQ 稳定名？→ dexquery.py 验证存在性与签名
→ 3. 是混淆名？→ find_onDraw.py（装饰器）/ 字符串锚点法二 / 调用链反查法三
→ 4. base 与 tinker 不一致？→ 以 tinker（运行时）为准
→ 5. 全部落档：类名 + 方法签名 + 所在 dex + 锚点，供下个版本重定位
```
