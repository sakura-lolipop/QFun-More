# DexKit 锚点档案（chat 目录迁移，QQ 9.3.15 / versionCode 14880 验证）

> 本档案覆盖 `hook/chat/` 下 10 个文件的硬编码类名 → DexKit 迁移（分支 feat/dexkit）。
> 验证方法：python + androguard（`logger.remove()`）遍历 `base.apk` 与 `tinker_classN.apk`
> **全部 77 个 dex**（base 38 个 + tinker 39 个，运行时以 tinker 热修 dex 为准），统计锚点字符串被哪些类
> 引用（const-string / const-string/jumbo），两 APK 结果一致且只命中目标类 = UNIQUE。
> 脚本存于 `qstory/qstory_menu_analysis/dexkit_migration/`
> （dump_chat_targets.py 定位目标类并 dump 自身字符串 → verify_chat_anchors.py 全 dex 唯一性验证）。
>
> 降级链统一范式：`runCatching { requireClass/requireMethod(...) }.getOrNull() ?: 硬编码`——
> 缓存未命中（首次启动未扫描）时行为与迁移前完全一致；DexKit 命中时语义相同（锚点唯一命中目标类）。

## 1. 已迁移（hook 目标类查找 → DexKit）

| 文件 | 原硬编码 | 缓存 key | 锚点 | 唯一性验证证据（双 APK 一致） | 保留项及原因 |
|---|---|---|---|---|---|
| DisablePullDownMiniApp.kt | `com.tencent.qqnt.chats.view.MiniOldStyleHeaderNew`（.clazz，?: 旧版 `MiniOldStyleHeader`） | `DisablePullDownMiniApp->miniAppHeader` | usingStrings `"finishTwoLevel(...)"` **AND** methods{name(`"onConfigurationChanged"`)} + searchPackages `com.tencent.qqnt.chats.view` | 字符串串在该包内被 2 个类引用（New 与旧版，classes4/13.dex），AND 上仅 New 声明的框架回调 `onConfigurationChanged`（旧版方法表无）后合取集 = {MiniOldStyleHeaderNew}，唯一 | 两个旧硬编码名保留为链尾兜底；`com.tencent.mobileqq.activity.home.Conversation` 兜底保留（`initMiniAppEntry` DexKit 主路径之后的降级链尾，class+方法名双约束） |
| FastReSend.kt | `com.tencent.mobileqq.aio.input.draft.InputDraftVMDelegate`（toClass） | `FastReSend->inputDraftVMDelegate` | usingStrings `"saveDrafts start editableText?.length = "` + searchPackages `com.tencent.mobileqq.aio.input.draft` | UNIQUE：仅 InputDraftVMDelegate（classes6.dex）引用；备选 `"recover draft task is cancelled"`、`"onEditTextChanged return, mSaveDraftDelay = "` 亦 UNIQUE；`"...InputDraftMsgIntent.RecoverMsgElements"` 被 2 类引用弃用 | 硬编码 toClass 保留为兜底（缓存未命中时抛错→init 捕获→isAvailable=false，与迁移前相同） |
| ForwardPtt.kt | `com.tencent.mobileqq.sharepanel.forward.handler.AIOForwardHandler`（toClass，仅新版分支） | `ForwardPtt->AIOForwardHandler` | usingStrings `"fixAIAssistantForwardContacts: prepareForwardSession="` + searchPackages `com.tencent.mobileqq.sharepanel.forward.handler` | UNIQUE：仅 AIOForwardHandler（classes8.dex）；备选 `"targetContactItems"` 被 12 类引用弃用 | 硬编码 toClass 保留为兜底；旧版分支 `NtMsgForwardUtils::class.java` 为 Kotlin 直接类引用，无需迁 |
| MultiRecall.kt | `com.tencent.mobileqq.aio.input.multiselect.MultiSelectBarVB`（toClass）+ 派生内层 `$$"$$className$mOperationLayout$2"`（TIM 分支 `TimMultiSelectBarVB`） | `MultiRecall->multiSelectBarVB` | usingStrings `"em_bas_aio_multi_selection_operation"`（埋点事件名）+ searchPackages `com.tencent.mobileqq.aio.input.multiselect` | 串全 dex 4 处引用（baby_q/compose/multiselect×2、aio/comic/ce、目标类），仅目标类在 searchPackages 包内（classes10.dex）→ 包内唯一；`multiselect_operation_bar_height` 包内 2 处、`touch_bar_visible` 包内 4 处均弃用 | TIM 分支硬编码保留为兜底（TIM dex 无法用 QQ 双包验证锚点，requireClass 必然 miss → 走 TIM 硬编码，行为不变）；内层类名派生逻辑一行未改，外层类名改由解析结果提供 |
| RemoveSendPicLimit.kt | `com.tencent.qqnt.qbasealbum.base.model.UniversalItemClickListener`（.clazz） | `RemoveSendPicLimit->universalItemClickListener` | usingStrings `"key_show_toast_interval"`（点击处理器自身的 MMKV key，即上限 toast 逻辑的特征串）+ searchPackages `com.tencent.qqnt.qbasealbum.base.model` | 串全 dex 5 处引用（by5/c$a、Wink 两个、qbasealbum/filter/a、目标类），仅目标类在 base.model 包内（classes8.dex）→ 包内唯一；`toAddedMedia`/`toRemovedMedia` 各 6 处、`selectedMediaViewModel` 17 处弃用 | 硬编码 .clazz 保留为兜底；**懒挂载结构、5 参 int 方法签名查找、`armCanSelectHook` 内 `returnConstant(this, true)` 语义全部未动** |
| SystemEmoji.kt | `com.tencent.mobileqq.text.EmotcationConstants`（toClass） | `SystemEmoji->emotcationConstants` | usingStrings `"setEmojiMap EMOJI_MAP size="` + searchPackages `com.tencent.mobileqq.text` | UNIQUE：仅 EmotcationConstants（classes.dex）；`DOUBLE_EMOJI_MAP=`、`EMOJI_CODES.length=`、`"\[emoji:0x[a-fA-F0-9]{5}"`、`"old init cost:"`、`"[sysEmo:%03d-%03d"` 亦 UNIQUE（备选池） | 硬编码 toClass 保留为兜底（异常→init 捕获→功能禁用，与迁移前失败语义一致）；getSingleEmoji/getDoubleEmoji 方法名查找未动 |
| UnlockLeftSwipeReply.kt | `com.tencent.mobileqq.aio.msglist.holder.component.leftswipearea.AIOContentLeftSwipeHelper`（toClass） | `UnlockLeftSwipeReply->leftSwipeHelper` | usingStrings `"onTouchEvent onLeftSwipeEnd"` + searchPackages `com.tencent.mobileqq.aio.msglist.holder.component.leftswipearea` | UNIQUE：仅 AIOContentLeftSwipeHelper（classes11.dex）；备选 `"judgeMoveState state="`、`"AIOContentLeftSwipeHelper"`（自名串被内部类 $2 引用）各 2 处弃用 | 硬编码 toClass 保留为兜底；onInit 查找位置、setSwipeEnable 签名（void + 1 参 boolean）未动 |

已在之前批次迁移、本次核查无残留硬编码 hook 目标（无需改动）：
`MixedEmoAndText.kt`（ImageUtils 已 DexKit；`DelegateMediator`/`InputReplyUIState` 为编译期桩直接引用）、
`RemoveReplyAt.kt`（reply 已 DexKit）。

## 2. 分诊后确认无需迁移的类别

- **Kotlin/Java 直接类引用（编译期桩，`X::class.java`，无需迁）**：
  `ChatInputHint.kt` 的 `AIOEditText::class.java`；`MixedEmoAndText.kt` 的
  `DelegateMediator::class.java`、`InputReplyUIState`；`ForwardPtt.kt` 的
  `AIOPttContentComponent::class.java`、`NtMsgForwardUtils::class.java`；
  `MultiRecall.kt` 的 `FrameworkVM::class.java`、`BaseVM`。
- **运行时视图树检查（非 dex 查找）**：
  `ChatInputHint.kt` 的 `hasAioInputAncestor` 中 `javaClass.name.startsWith("com.tencent.mobileqq.aio.input")`
  ——运行时祖先链包名判断，hook 的作用域防护，保留。
- **既有 DexKit 锚点/搜索包约束（保留）**：各文件 getQueryMap 内已有 usingStrings/searchPackages。
- **方法匹配器的类型输入（非 hook 目标，开源库名多年稳定）**：
  `DisablePullDownMiniApp.kt` findStateChangedMethod 中
  `com.scwang.smart.refresh.layout.api.RefreshLayout` /
  `android.support.v4.widget.SwipeRefreshLayout`——仅用于按参数类型匹配 onStateChanged，
  是 SmartRefreshLayout/androidx 开源库类，非混淆 QQ 业务类。

## 3. 风险与备注

- `miniAppHeader` 锚点含结构条件（onConfigurationChanged）：QQ 若删除该 override 且改掉
  `finishTwoLevel(...)` 串，查询 0 命中 → 走硬编码兜底（等价迁移前行为），不会误挂。
- MultiRecall 内层类名派生依赖 dex 命名规则 `外层全名$mOperationLayout$2`；QQ 改混淆器导致
  匿名类命名变化时，派生名 loadClass 失败 → init 捕获 → 功能禁用（与迁移前同类失败）。
- RemoveSendPicLimit 的 `requireClass` 在 onHook 中调用（懒挂载链入口不变）；相册类懒加载，
  DexKit 缓存存的是 dex 描述符，`DexClass.getInstance(hostClassLoader)` 与原 `.clazz`
  走同一宿主 classloader，加载时机语义一致。
- 编译自检：`./gradlew compileDebugKotlin` 通过（0 error）。
