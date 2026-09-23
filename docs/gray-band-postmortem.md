# 灰带问题复盘（QZone 直开模式视觉修复）

> B2"底栏直接打开空间动态"的附带视觉修复：QQ 9.3.15 空间好友动态页的多条灰色横带。
> 结论先行：灰带 = 三层独立的灰色来源叠加，修复 = 各层精准处理，不改任何布局尺寸。

## 一、分层模型（最终正确理解，全部有 dump/XML 证据）

```
QZoneBaseBlockContainer bg=SkinnableBitmapDrawable   ← ① 用户装扮皮肤（网格+装饰圆+英文）
  └─ NestScrollRecyclerView（透明）                    ← feed 列表浮在皮肤上
      ├─ QZoneDefaultTextFeedItemView bg=ColorDrawable ← ② 卡片默认灰底（#dedddc 系）
      │    ├─ 内容子视图（头像/正文/图片）              ← ← ③ 网格皮肤其实由内容区重新呈现
      │    └─ 底部区 LinearLayout（评论条，无背景）
      │         ├─ Space 21px                          ← 露②的灰
      │         └─ 输入行 138px（输入框只占 102px）      ← 露②的灰 36px
      └─ feedx/widget/j（ItemDecoration）               ← ④ getItemOffsets 预留间隙
                                                         + onDraw 画 #dedddc 实心条
```

- **灰带1（36px）**：`QzoneConciseHeaderView` 内空的订阅刷新头容器
  （`RefreshHeaderView` 内容 0 高但容器占 36px 灰底）。
- **灰带2（24px+评论区）**：widget/j 的 offsets 预留间隙 + onDraw 实心条，
  以及底部区各 ViewStub 子布局的 QUI 语义填充底色
  （`qui_common_fill_light_secondary_bg` ≈ #dedddc，`qui_common_fill_standard_primary_bg_corner_4`）。
- **为什么"卡片有色、评论条没色"**：装扮皮肤的网格是页面级背景（容器层），
  feed 卡片透明、内容直接浮在皮肤上；评论输入条是独立功能区块，
  QUI 设计系统给它配了固定填充色，装扮通道不覆盖交互控件。QQ 原生直开用户同样如此。

## 二、修复方案（QzoneTabDirect.kt 当前实现）

| 层 | 修复 | 机制 |
|---|---|---|
| 灰带1 | RefreshHeaderView 构造器挂 attach 监听 → 挂树瞬间 GONE 父容器 | 内容非空自动恢复；作用域限定 QzoneConciseHeaderView 祖先 |
| 灰带2-绘制 | `feedx/widget/j` 的 getItemOffsets 清零 + onDraw 拦截 | 懒挂载（见下）；混淆名 `j` 随版本变，重定位工具见下 |
| 灰带2-QUI 填充 | 绑定钩子（bindData/c0 hookAfter）→ surgeryOnBind：底部区子树背景清空、卡片 ColorDrawable 置空、margin 归零 | 皮肤（容器层）透出；卡片 margin 原地改字段（见坑 3） |

关键约束：**surgeryOnBind 在布局计算期间被调用**，此时
`card.layoutParams = lp` 赋值会抛
"Cannot call this method while RecyclerView is computing a layout"
且被 runCatching 吞掉 → margin 从未生效（表现为"修了但问题依旧"）。
必须原地改 `lp.topMargin/bottomMargin` 字段，下一轮布局自然生效。

## 三、定位方法论（为什么绕了弯路 + 最终正解）

1. **像素取样**：灰带 #dedddc 实心 vs 皮肤 #fcfaf9+网格线 → 确认是独立色块。
2. **进程内视图树 dump**（含背景 drawable 类名 + margin）：背景清空后灰条
   "跑到背景上面" → 证明灰条是被**画**出来的（ItemDecoration.onDraw），
   不是视图背景。
3. **静态扫描**（`qstory/qq9315/find_onDraw.py`）：feedx 包内唯一同时声明
   getItemOffsets + onDraw(Canvas) 的装饰器 = `feedx/widget/j`。
   注意 picmixvideo/j$a、util/v$a 是**别的装饰器**（已另行清零 offsets，无害）。
4. **布局 XML 静态解析**：aapt2 dump xmltree 需用 arsc 里的**真实混淆路径**
   （`r/i/qzone_feedx_item_default_text.xml`），原始资源名只是 arsc 映射。
5. QUI 填充色实锤：底部区子布局背景 = `qui_common_fill_light_secondary_bg`
   （QQ 主题语义色，随深色模式变化，勿写死颜色值处理）。

## 四、教训

- **改背景 vs 砍布局是两条路**：用户皮肤由页面级背景 + 内容子视图共同呈现，
  收缩布局（Space GONE、行高收缩、margin 归零的旧版手术）会把皮肤呈现区一并裁掉
  ——用户反馈"你砍的背景"即此。最终方案只动"背景可见性"（置空让皮肤透出），零布局改动。
- **透明化会暴露被盖住的绘制层**：卡片背景置空后，decoration onDraw 的灰条
  透出来（"跑到背景上面"）——此时才需要处理绘制层，顺序不能反。
- **QUI 语义色勿写死**：浅色次要填充随主题/深色模式变化，按"视图所在层级"
  处理而非按色值匹配。

## 五、相关文件

- 实现：`app/src/main/java/me/yxp/qfun/hook/social/QzoneTabDirect.kt`
- 装饰器扫描：`qstory/qq9315/find_onDraw.py`
- 布局树取证：`qstory/qq9315/dump_methods.py` / `dexquery.py` / base.apk
- 完整踩坑经验：`docs/lessons.md`
