# DexKit 锚点档案 —— hook/purify 迁移（QQ 9.3.15 验证）

> 迁移范围：`hook/purify/` 全部硬编码 QQ 类名查找 → `runCatching { requireClass(key) }.getOrNull() ?: 原硬编码`
> 降级范式（缓存未命中/首次未扫描时行为与迁移前逐字节一致）。
> 锚点唯一性验证：`qstory/qstory_menu_analysis/dexkit_migration/verify_purify_anchors.py`
> （及 round2/round3 补充脚本），对 `qq9315/base.apk` 与 `tinker_classN.apk` 全部 dex
> 逐一验证，两包各命中且仅命中目标类（UNIQUE）。`searchPackages` 为前缀匹配含子包，
> 验证脚本按"包前缀超集"模拟，覆盖精确/前缀两种语义。
> 缓存 key 规则：`"<对象simpleName>-><key>"`（DexKitFinder 写入，DexKitCache 读取）。

## 已迁移锚点

| 文件 | 原硬编码 | 缓存 key | 锚点 | 唯一性验证证据 | 保留项及原因 |
|---|---|---|---|---|---|
| AntiInteractivePop | `...springhb.interactive.ui.InteractivePopManager` | `AntiInteractivePop->interactivePopManager` | usingStrings `"showInteractivePop playFile isNull!!"` + searchPackages `com.tencent.mobileqq.springhb.interactive` | base+tinker 各 UNIQUE=1 | `androidx.fragment.app.Fragment`、`kotlin.jvm.functions.Function0` 保留：androidx/kotlin 框架类，非 QQ 业务类 |
| AntiInteractivePop | `...aio.animation.pag.PagEasterEggPopManager` | `AntiInteractivePop->pagEasterEggPopManager` | usingStrings `"showPop playFile isNull"` + searchPackages `com.tencent.mobileqq.aio.animation.pag` | base+tinker 各 UNIQUE=1 | — |
| BlockAIOEffects | `...aio.animation.AIOAnimationContainer` | `BlockAIOEffects->aioAnimationContainer` | usingStrings `"[concurrentStartAnimation] create new animator, type: "` + searchPackages `com.tencent.mobileqq.aio.animation` | base+tinker 各 UNIQUE=1 | — |
| BlockQQToast | `...widget.QQToast` | `BlockQQToast->qqToast` | usingStrings `"9225_qqtoast_disable_freewindow_check"`（类自有 MMKV key）+ searchPackages `com.tencent.mobileqq.widget` | base+tinker 各 UNIQUE=1 | — |
| BlockTempSessionPopup | `...managers.TempMsgManager` | `BlockTempSessionPopup->tempMsgManager` | usingStrings `"can't tempMsgInfo, insert a new tempMsgInfo! type="` + searchPackages `com.tencent.mobileqq.managers` | base+tinker 各 UNIQUE=1 | — |
| DisableCrashReport | `...qqperf.monitor.crash.QQCrashReportManager` | `DisableCrashReport->crashManager` | usingStrings `"CrashReport init finished."` + searchPackages `com.tencent.qqperf.monitor.crash` | base+tinker 各 UNIQUE=1 | — |
| DisableLogWriteThread | `QLogItemManager$WriteHandler` | `DisableLogWriteThread->writeHandler` | usingStrings `"QLog clear logs"` + searchPackages `com.tencent.qphone.base.util` | base+tinker 各 UNIQUE=1 | — |
| DisableLogWriteThread | `...base.util.QLog` | `DisableLogWriteThread->qLog` | usingStrings `"Business log tag, over 1024 !!!"` + searchPackages `com.tencent.qphone.base.util` | base+tinker 各 UNIQUE=1 | — |
| HideContactCard | `...newfriend.ui.builder.NewFriendBindContactGuideBuilderV3` | `HideContactCard->contactGuideBuilder` | usingStrings `"leakOptSwitch："`（全角冒号日志串）+ searchPackages `com.tencent.mobileqq.newfriend` | base+tinker 各 UNIQUE=1 | — |
| HideFriendInteractIdentify | `...profilecard.component.ProfileIntimateComponent` | `HideFriendInteractIdentify->intimateComponent` | usingStrings `"makeOrRefreshUI fail, card is null"` + searchPackages `com.tencent.mobileqq.profilecard` | base+tinker 各 UNIQUE=1 | 备选 `map_key_intimate` MULTI(7) 弃用 |
| HideRelationshipIdentification | `...profilecard.component.ProfileInStepComponent` | `HideRelationshipIdentification->inStepComponent` | usingStrings `"bindCardData fail, card or listview is null"` + searchPackages `com.tencent.mobileqq.profilecard` | base+tinker 各 UNIQUE=1 | `RFWCardListView` 保留：运行时视图类型检查（getObjectByTypeOrNull），非 dex 定位 |
| HideRelationshipIdentification | `...profilecard.data.ProfileCardInfo` | `HideRelationshipIdentification->profileCardInfo` | 结构锚点 methods `putBusinessInfo` + `getBusinessInfo` + searchPackages `com.tencent.mobileqq.profilecard.data` | base+tinker 各 UNIQUE=1 | 该类无字符串常量，只能结构定位 |
| HideSystemTime | `AIOItemComponentProvider$mComponentList$2` | `HideSystemTime->componentListLambda` | 结构锚点 superClass `kotlin.jvm.internal.Lambda` + methods add(name=`invoke`, returnType=`android.util.SparseArray`, usingNumbers(13)) + searchPackages `com.tencent.mobileqq.aio.msglist.holder` | base+tinker 各 UNIQUE=1 | 姊妹 lambda `mLazyComponentList$2` 结构全同仅 put 8 号位，用组件类型常量 13 区分 |
| HideTroopNickDecorate | `...editservice.EditTroopMemberNickService` | `HideTroopNickDecorate->nickService` | usingStrings `"initGetTroopNickRule EditMode isTroopNick"` + searchPackages `com.tencent.mobileqq.activity.editservice` | base+tinker 各 UNIQUE=1 | — |
| PurifyNewYearBanner | `...springhb.entry.module.SpringTabBannerModule` | `PurifyNewYearBanner->springTabBannerModule` | usingStrings `"onMsgTabBannerShow, data = "` + searchPackages `com.tencent.mobileqq.springhb.entry` | base+tinker 各 UNIQUE=1 | `QbossADImmersionBannerManager` 保留（见豁免表） |
| PurifyNewYearBanner | `...springhb.entry.model.MsgTabBannerData` | `PurifyNewYearBanner->msgTabBannerData` | usingStrings `"', liuhaiBgUrl='"` + searchPackages `com.tencent.mobileqq.springhb.entry` | base+tinker 各 UNIQUE=1 | `MsgTabBannerData{id='` 会同命中 MsgTabTitleIconData（MULTI(2)）弃用 |
| PurifyNewYearBanner | `...bannerprocessor.VasADBannerProcessor` | `PurifyNewYearBanner->vasADBannerProcessor` | usingStrings `"[Diag-715] handleCustomBanner empty, hide banner"` + searchPackages `com.tencent.mobileqq.activity.recent` | base+tinker 各 UNIQUE=1 | — |
| RemoveBrowserMenuQQBrowser | `...swift.component.SwiftBrowserShareMenuHandler` | `RemoveBrowserMenuQQBrowser->shareMenuHandler` | usingStrings `"menuItem:openWithQQBrowser"` + searchPackages `com.tencent.mobileqq.webview.swift` | base+tinker 各 UNIQUE=1 | — |
| RemoveEmoPanelAITab | `...emoticonview.EmoticonPanelController` | `RemoveEmoPanelAITab->emoticonPanelController` | usingStrings `"EmoticonPanelController"` + `"OnDestory"`（AND 收紧）+ searchPackages `com.tencent.mobileqq.emoticonview` | base+tinker 各 UNIQUE=1 | 单串亦 UNIQUE，双串防未来他类引用同名 TAG |
| RemoveEmoPanelBigEmoji | 同上 | `RemoveEmoPanelBigEmoji->emoticonPanelController` | 同上 | base+tinker 各 UNIQUE=1 | — |
| RemoveEmoReplyMenu | `...msgtail.AIOGeneralMsgTailContentComponent` | `RemoveEmoReplyMenu->msgTailComponent` | usingStrings `"not in EmoReplyWhiteList"` + searchPackages `...msglist.holder.component.msgtail` | base+tinker 各 UNIQUE=1 | 备选 `em_bas_aio_expression_responds_result_area` MULTI(3) 弃用 |
| RemoveEmoReplyMenu | `...aio.api.impl.AIOEmoReplyMenuApiImpl` | `RemoveEmoReplyMenu->emoReplyMenuApi` | usingStrings `"[getSeparateEmoReplyMenuView] context is null"` + searchPackages `com.tencent.qqnt.aio.api.impl` | base+tinker 各 UNIQUE=1 | — |
| RemoveQQShow | `...ai.avatar.api.impl.AIAvatarSwitchApiImpl` | `RemoveQQShow->aiAvatarSwitchApi` | usingStrings `"[isQQShowEnable] uin=="` + searchPackages `com.tencent.mobileqq.ai.avatar` | base+tinker 各 UNIQUE=1 | — |
| RemoveVoicePlayPopup | `PttHelper$getPhoneLowVolumeListener$onLowVolume$1` | `RemoveVoicePlayPopup->lowVolumeListener` | 结构锚点 superClass `kotlin.jvm.internal.Lambda` + methods add(name=`invoke`, paramTypes=`boolean`, returnType=`void`) + searchPackages `com.tencent.mobileqq.aio.helper` | base+tinker 各 UNIQUE=1 | lambda 类无字符串常量；外层类已混淆为 `bs`，只能结构定位 |
| MiniProgramSkipAds（残留补迁） | `...motivevideo.GdtMvVideoViewController` | `MiniProgramSkipAds->videoViewController` | usingStrings `"[doPauseAction] mIsVideoPlayCompleted:"` + searchPackages `com.tencent.gdtad.basics.motivevideo` | base+tinker 各 UNIQUE=1 | 姊妹类 `GdtMvPictureViewController` 共享其余日志串，此串独有 |

## DEXKIT-EXEMPT（保留硬编码，均已在源码内注明）

| 位置 | 类 | 原因 |
|---|---|---|
| BlockDailyAdDialog | `GdtMotiveBrowsingDialog`、`GdtMvVideoDialog` | 9.3.15 双包均不存在，无锚点可验证；onInit `any{}` 已按"全缺则静默禁用"降级 |
| BlockDropEmoji | `AioAnimationConfigHelper`（两个包路径候选） | 同上 |
| HideTroopRiskTipsBar | `TroopSecurityTipsBar`（三个包路径候选） | 同上 |
| PurifyNewYearBanner | `cooperation.vip.qqbanner.QbossADImmersionBannerManager` | 9.3.15 双包不存在；原 `.clazz?.` 语法本就静默跳过 |
| MiniProgramSkipAds | `GdtMotiveBrowsingDialog` | 9.3.15 双包不存在，无锚点可验证 |
| DisableX5Kernel | `com.tencent.smtt.sdk.QbSdk` | X5 SDK 公开 API 类，非 QQ 混淆业务类，SDK 命名空间跨版本稳定，迁移无收益 |

## 保留（非迁移对象，按分诊规则）

- AntiInteractivePop 的 `"androidx.fragment.app.Fragment"` / `"kotlin.jvm.functions.Function0"`：androidx/kotlin 框架类，方法参数类型匹配用，非 QQ 类名。
- HideRelationshipIdentification 的 `RFWCardListView`：运行时视图树类型检查，与 dex 定位无关。
- 所有 `X::class.java` 直接引用（AIOMsgItem、Bundle、ViewGroup、EditText、Long 等）：编译期引用，无需迁移。

## 验证产物

- `dump_purify_targets.py` / `.out`：目标类存在性 + 自有字符串 dump（base/tinker）
- `verify_purify_anchors.py` / `.out`：字符串/结构锚点唯一性主验证
- `verify_purify_round2.py` / `.out`：MsgTabBannerData 换锚 + 双 lambda 结构 dump
- `verify_purify_round3.py`：HideSystemTime usingNumbers(13) 判别谓词最终验证（双包 UNIQUE）
