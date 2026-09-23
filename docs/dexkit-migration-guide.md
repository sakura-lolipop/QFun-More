# DexKit 迁移规范（硬编码类名 → 语义定位）

> 策略（用户拍板）："坏了才迁" + 新功能优先 DexKit。禁止无收益的全量预迁移。
> 本文档 = 迁移 SOP + 已迁移清单 + 锚点验证规范。

## 1. 为什么迁移（与不迁移的理由）

DexKit 按**语义特征**（字符串锚点、方法签名、继承关系）在 QQ dex 里定位类/方法，
**不依赖类名**——QQ 升级改了名也能自动重定位。代价：

- 首次"查找方法"要全 dex 扫描（慢，之后按 `CacheMap_<QQ版本>_<模块版本>` 缓存）；
- 锚点选错会**误定位**（hook 到错误方法）——比"找不到"更危险；
- 稳定名类（qzone reborn 业务名、qzonehub API 层）多年未混淆，硬编码零风险。

结论：**稳定名继续硬编码；混淆名（如 feedx/widget/j）必须 DexKit；失效的稳定名按 SOP 迁。**

## 2. 迁移 SOP（失效功能 → DexKit 化）

```
1. 失效定位：LSPosed 日志（无报错类失效 = 行为验证：实际操作看效果）
2. 旧类名验证：qstory/qq9315/dexquery.py 查 base.apk + tinker（qstory/qq9315/tinker_classN.apk）
   ├─ 类还在 → 签名/调用点变了 → 放宽匹配器即可（分钟级）
   └─ 类没了 → 走 DexKit 语义重定位
3. 选锚点（优先级）：
   a. 类引用的独特字符串常量（QQ 埋点/日志串，如 "init Mini App, cost="）
   b. 结构特征（继承 ItemDecoration + 双方法声明，见 find_onDraw.py 思路）
   c. 方法签名 + 包路径约束
4. 锚点唯一性验证：静态确认全 dex 只有一个匹配（防止误 hook）
5. 实现：照 QFun DexKitTask 范例（ForwardPtt.kt / DisablePullDownMiniApp.kt）
6. 装机验证 + 归档锚点到本文档
```

## 3. 实现范例（QFun 内已验证的写法）

- `hook/chat/ForwardPtt.kt`：DexKitTask + requireClass + findMethod 组合
- `hook/chat/DisablePullDownMiniApp.kt`（本项目迁移范例：3 锚点 `"init Mini App, cost="` 等）
- `hook/purify/MiniProgramSkipAds.kt`（本项目迁移范例：4 路降级 + GDT 双锚点）
- 基础设施：`utils/dexkit/DexKitFinder.kt`（doFind 触发"查找方法"）、`DexKitCache.kt`
  （缓存 `CacheMap_<QQ版本>_<模块版本>`，QQ 或模块升版自动重扫）

## 4. 已知锚点档案（9.3.15 验证）

| 目标 | 锚点 | 说明 |
|---|---|---|
| 小程序入口布局 | usingStrings `"init Mini App, cost="` | B6 |
| GDT 广告回调 | usingStrings `"GdtMvTitleHelper"` + `"bar == null"` | B5 路A |
| frame 决策链 | usingStrings `"FrameControllerInjectImpl.setQzoneLebaTab"` | B2 决策链 |
| 空间列表装饰器 | 类名 `com.qzone.reborn.feedx.widget.j`（9.3.15 混淆名） | ⚠️ **未 DexKit 化**，升级后用 find_onDraw.py 重定位（双声明+灰色 Paint 特征） |

## 5. QzoneTabDirect（B2）的特殊性

B2 的三管 hook 中，impl/facade 门面方法定位支持 DexKit 化，但：
- 灰带手术（surgeryOnBind）是**运行时视图树操作**（findViewById + 遍历），
  与 dex 无关，无法 DexKit 化——它依赖运行时结构（BOTTOM_AREA_ID 0x7f0a6619），
  该资源 ID 在同一 APK 内稳定，跨 QQ 版本需用 aapt2 dump resources 重新确认。
- bindData/c0 方法名：混淆敏感，已验证 9.3.15 存在；QQ 升级若失效，
  重定位方式 = dump_methods.py 查基类方法列表（名称变化的按"参数+调用特征"重认）。
