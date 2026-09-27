# DexKit 锚点档案 —— hook/social 与 hook/api（9.3.15 验证）

> 迁移范围：hook/social/ 7 文件 + hook/api/ 3 文件，QQ 9.3.15（base.apk + tinker_classN.apk 双包全 dex 验证）。
> 范式：`runCatching { requireClass("key") }.getOrNull() ?: 原硬编码` —— 缓存未命中（未触发扫描）时行为与迁移前逐字节一致。
> 验证脚本：`qstory/qstory_menu_analysis/dexkit_migration/verify_social_api_anchors.py`（含 round2/round3），
> dump 脚本 `dump_social_api_targets.py`、`dump_widget_j.py`。

## 1. 迁移锚点（17 项，全部 UNIQUE）

| 文件 | 原硬编码 | 缓存 key | 锚点 | 唯一性验证证据 |
|---|---|---|---|---|
| AllowAbnormalProfileCard | ProfileDataServiceImpl | profileDataService | searchPackages(profilecard.api.impl) + usingStrings("getProfileCard from db. uin=%s card=%s", "getProfileCard from new. uin=%s card=%s") | base+tinker 双包 UNIQUE（classes.dex） |
| AllowAbnormalProfileCard | ProfileSecureProcessor | profileSecureProcessor | searchPackages(profilecard.processor) + usingStrings("onProcessProfileCard, get card null", "onProcessProfileCard, card isForbidAccount=") | 双包 UNIQUE |
| HideProfileGiftEntry | ProfileBottomContainer | profileBottomContainer | searchPackages(profilecard.base.container) + usingStrings("give_gift_button_in_profile_url") | 双包 UNIQUE |
| HideQzoneAd | QZoneAdBaseFeedItemView | adFeedxBase | searchPackages(feedx.itemview.ad) + usingStrings("getPresenterList(...)", "getActivityFromView(...)") | 双包 UNIQUE（基类仅引用 Kotlin 判空助手串） |
| HideQzoneAd | QZoneAdBaseMediaFeedProItemView | adProMediaBase | searchPackages(feedpro.itemview.ad) + usingStrings("mBottomOperateContainer", "mAdContainer") | 双包 UNIQUE |
| HideQzoneAd | QzoneFeedProGeneralBigCardItemView | adProGeneralBigCard | searchPackages(feedpro.itemview) + usingStrings("QzoneFeedProGeneralBigCardItemView")（getLogTag 自串） | 双包 UNIQUE |
| HideQzoneAd | QZoneAdFeedProForwardMixPicVideoItemView | adProForwardMix | searchPackages(feedpro.itemview) + usingStrings("QZoneAdFeedProForwardMixPicVideoItemView") | 双包 UNIQUE |
| HideQzoneAd | QZoneFeedProDetailBottomAdBlockView | adProDetailBottom | searchPackages(feedpro.widget.comment) + usingStrings("bindWxStoreLogo() wxLogoUrl=") | 双包 UNIQUE（exposure 串被姊妹类共享已弃用，见 §3） |
| HideQzoneAd | QZoneAdRewardFeedItemView | adRewardFeed | searchPackages(feedx.itemview.ad) + usingStrings("QZoneAdRewardFeedItemView") | 双包 UNIQUE |
| HideQzoneAd | QZoneAdFeedDataExtKt | adFeedDataExt | searchPackages(proxy.feedcomponent.model.gdt) + usingStrings("[getAdFirstCategory] error, e:") | 双包 UNIQUE |
| MultiOnlineNudge | OnlineStatusNudgeManager | onlineStatusNudgeManager | searchPackages(onlinestatus.proto) + usingStrings("[sendOnlineFriendNudge] sent, targetUin=") | 双包 UNIQUE |
| QzoneTabDirect | QZoneApiProxyImpl | qzoneApiProxyImpl | searchPackages(qzonehub.api.impl) + usingStrings("initEnv: loadQZoneClass") | 双包 UNIQUE（docs §5 许可的 impl 目标） |
| QzoneTabDirect | feedx/widget/j（混淆名） | feedListDecoration | searchPackages(feedx.widget) + superClass(RecyclerView$ItemDecoration) + methods(name=getItemOffsets, name=onDraw) | 双包 UNIQUE；结构特征即 find_onDraw.py 的"双声明"，灰条颜色常量未内联进该类故不加 usingNumbers |
| RemoveForwardLimit | ForwardRecentActivity | forwardRecentActivity | searchPackages(mobileqq.activity) + usingStrings("refreshRecentList, filter deleted friend, uin = ") | 双包 UNIQUE |
| RemoveForwardLimit | ForwardFriendListActivity | forwardFriendListActivity | searchPackages(mobileqq.activity) + usingStrings("forwardOption is null, return.") | 双包 UNIQUE（extra_shield_robot_friend 串 MULTI 已弃用） |
| RemoveForwardLimit | ForwardTroopListFragment | forwardTroopListFragment | searchPackages(mobileqq.activity) + superClass(PublicBaseFragment) + usingStrings("choose_fri_multiple_page_type") | 双包 UNIQUE（round3：choose_* 常量与 FriendList 共享，按基类区分） |
| OnTroopJoin | TroopMemberAddPushProcessor | troopMemberAddProcessor | searchPackages(qqnt.push.processor) + usingStrings("onPushReceive: byte troopUin=") | 双包 UNIQUE |

已迁移（本次之前）：RemoveForwardLimit/ChatSelectorConfig、OnMenuBuild/CopyMenuItem、OnReceiveMsg/msgService、
OnTroopJoin/handleJoin（declaredClass 直引用 + "handleMemberAdd addMemberUin:"）。

本次新加入 DexKitTask 的 object：AllowAbnormalProfileCard、HideProfileGiftEntry、HideQzoneAd、MultiOnlineNudge、QzoneTabDirect
（接口实现不影响 hook 行为；getQueryMap 仅在模块内"查找方法"扫描时执行）。

## 2. 保留项及原因（DEXKIT-EXEMPT / 直引用）

| 文件 | 保留项 | 原因 |
|---|---|---|
| CheckForbidCard | TimFriendProfileCardActivity | TIM 专属类，QQ 9.3.15 双包均不存在，无法静态验证锚点唯一性 |
| CheckForbidCard | FriendProfileCardActivity::class.java | Kotlin 编译期直引用，非字符串查找 |
| HideQzoneAd | AlbumRecommendAdvController | 9.3.15 双包均不存在（该 hook 点已失效），无锚点可验证 |
| QzoneTabDirect | cooperation.qzone.api.QZoneApiProxy（facade） | docs §5：facade 懒注册三管结构为灰带修复定稿，严禁动；稳定未混淆名 |
| QzoneTabDirect | mqq.app.AppRuntime | 非 hook 目标，仅作 needShowQzoneFrame 签名匹配的类型 token；平台级基础类自始未混淆 |
| QzoneTabDirect | TabFrameControllerImpl | docs §5 定稿三管（checkBusinessSwitch 状态覆写）结构不动；稳定名 |
| QzoneTabDirect | QzoneFrame | 仅 presence gate（缺失即禁用功能），非 hook 目标；稳定名 |
| QzoneTabDirect | RefreshHeaderView | 灰带1 定稿结构不动；稳定名 |
| QzoneTabDirect | QZoneBaseFeedItemView | 灰带手术 bindData/c0 双方法挂载定稿；稳定名 |
| QzoneTabDirect | BOTTOM_AREA_ID 0x7f0a6619 | 资源 ID 非 dex 目标（docs §5），跨版本需 aapt2 dump resources 重确认 |
| OnMenuBuild | QQCustomMenuExpandableLayout、AIOMsgItem、MsgRecord | 编译期直引用；CopyMenuItem 已 DexKit 化 |
| OnReceiveMsg | MsgRecord、Contact | 编译期直引用；msgService 已 DexKit 化 |
| OnTroopJoin | TroopOnlinePushHandler、ITroopMemberListRepoApi | 编译期直引用；SERVICE_CMD 协议串不在本文件 |

## 3. 验证中弃用的候选锚点（MULTI，禁止使用）

| 候选 | 结果 | 替代 |
|---|---|---|
| usingStrings("[doOriginalExposureReport] cancel, has exposed")（adProDetailBottom） | MULTI(2)：姊妹类 QZoneFeedProDetailCommentAdItemView 同引用 | bindWxStoreLogo() wxLogoUrl= |
| usingStrings("extra_shield_robot_friend")（forwardFriend） | MULTI(2)：specialcare.SpecailCareListActivity 同引用 | forwardOption is null, return. |
| usingStrings("initMiniWindow ") / "contacts.fragment.TroopFragment"（forwardTroop） | MULTI(2)/MULTI(9) | superClass(PublicBaseFragment) + choose_fri_multiple_page_type |
| choose_* / pg_choose_fri_multiple 等 6 组纯串组合（round2） | 全部 MULTI(2)：与 ForwardFriendListActivity 共享常量 | 同上（round3 基类约束） |

## 4. 升级重定位提示

- feedListDecoration：QQ 升级后若 unique 失效（新增同构 ItemDecoration），按 find_onDraw.py 重扫
  feedx/widget 树的双声明集合，必要时补 usingNumbers（灰色条 #dedddc 若改为常量内联）。
- 多命中/未命中时 Finder 用 singleOrNull() 静默不缓存 → 全部功能自动回退硬编码，不会误 hook；
  装机后功能失效先查 CacheMap_<QQ版本>_<模块版本> 是否生成。

## 5. 对抗审查补迁（2026-09-27）

RemoveFavPreviewLimit（全仓扫荡发现的原漏网项，com.qqfav 前缀不在原盘点重点包路径）：

| 缓存key | 原硬编码 | 锚点（双包验证 UNIQUE） |
|---|---|---|
| RemoveFavPreviewLimit->favoriteService | com.qqfav.FavoriteService | usingStrings `"addNewFavoriteInternal|invalid arg"` + searchPackages com.qqfav |
| RemoveFavPreviewLimit->favoriteData | com.qqfav.data.FavoriteData | usingStrings `"checkRebuildFileExData, rebuild FileExData list"` + searchPackages com.qqfav |

注：锚点由扫荡 agent（sweep/verify_fav_anchors.py）验证，红队未二次抽验（低风险：com.qqfav 独立包 + 双串均冷僻）。
