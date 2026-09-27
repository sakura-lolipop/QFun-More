# DexKit 锚点档案（杂项目录迁移，QQ 9.3.15 / versionCode 14880 验证）

> 本档案覆盖 `hook/MainHook.kt`、`hook/device`、`hook/entry`、`hook/file`、`hook/msg`、
> `hook/patch`、`hook/troop`、`hook/ui` 各文件的硬编码类名 → DexKit 迁移（分支 feat/dexkit）。
> 验证方法：python + androguard 遍历 `base.apk` 与 `tinker_classN.apk` **全部 77 个 dex**，
> 统计锚点字符串被哪些类引用（const-string / const-string/jumbo），
> 两 APK 结果一致且只命中目标类 = UNIQUE。脚本存于
> `qstory/qstory_menu_analysis/dexkit_migration/`（verify_driver.py + scan_one.py + verify_anchor_data.py）。

## 1. 已迁移（hook 目标类查找 → DexKit）

| 文件 | 原硬编码 | 缓存 key | 锚点 | 唯一性验证证据（双 APK 一致） |
|---|---|---|---|---|
| device/DisableEarpieceSwitch.kt | `com.tencent.mobileqq.qqaudio.audioplayer.impl.AudioDeviceServiceImpl`（.clazz） | `DisableEarpieceSwitch->newImpl` | usingStrings `"notifyAllDeviceStatusChanged: audioDeviceListener="` + searchPackages `com.tencent.mobileqq.qqaudio.audioplayer.impl` | UNIQUE：仅 AudioDeviceServiceImpl（classes9.dex）引用，base/tinker 一致 |
| file/DownloadRedirect.kt | `com.tencent.mobileqq.app.AppConstants`（toClass） | `DownloadRedirect->appConstants` | usingStrings `"QQEditPic/OriginPic/"` + `"/Tencent/QQ_Secretfile/"`（双串 AND）+ searchPackages `com.tencent.mobileqq.app` | 两串各自 UNIQUE：仅 AppConstants（classes.dex）引用 |
| file/ShowDownloadTimes.kt | `group_file.group_file_common.repo.GroupFileListRepo`（.clazz，注意无包前缀，类名本就如此） | `ShowDownloadTimes->groupFileListRepo` | usingStrings `"[syncExtraInfoV2]: start"` + `"[syncExtraInfoV2]: end, count="`（双串 AND）+ searchPackages `group_file.group_file_common.repo` | 两串各自 UNIQUE：仅 GroupFileListRepo（classes23.dex）引用；其 ContinuationImpl 内部类不含任何字符串 |
| troop/RememberLastRead.kt | `com.tencent.mvi.base.route.VMMessenger`（toClass） | `RememberLastRead->vmMessenger` | usingStrings `"sendR must be access at Main Thread"` + searchPackages `com.tencent.mvi.base.route` | UNIQUE：仅 VMMessenger（classes6.dex） |
| troop/RememberLastRead.kt | `com.tencent.mobileqq.aio.event.MsgNavigationEvent$NavigateBySeqEvent`（toClass，用于实例化） | `RememberLastRead->navigateBySeqEvent` | usingStrings `"NavigateBySeqEvent(source="` + searchPackages `com.tencent.mobileqq.aio.event` | UNIQUE：仅该类（classes6.dex）。⚠️ 备选锚点 eventName 串 `...MsgNavigationEvent.NavigateBySeqEvent` 被 5 个类引用（msgrepo/q、summary/h、msgsummary/e 等），已弃用 |

已在此前迁移、本次核查无需改动（已是 DexKitTask）：`msg/BypassArkLimit.kt`（ArkConfigModel）、
`msg/RecordsReplyCheck.kt`（repo）、`troop/SimpleTroopManagement.kt`（listener）、
`troop/AntiAtAll.kt`（troopToDo）、`entry/QQSettingInject.kt`（ConfigProvider/ItemProcessor）、
`ui/LiquidGlassTabBar.kt`（needShowTabHostDivider/initTabLayoutSwitch）。

## 2. 保留项及原因（DEXKIT-EXEMPT）

| 文件 | 保留的硬编码 | 原因 |
|---|---|---|
| MainHook.kt | `com.tencent.imcore.message.BaseQQMessageFacade`（账号引导钩子） | DEXKIT-EXEMPT: 启动关键路径，先于 DexKit 扫描（首次运行缓存为空不能让引导失败） |
| patch/DisableHotPatch.kt | `com.tencent.rfix.lib.download.PatchDownloadTask` / `com.tencent.rfix.lib.config.PatchConfig` / `com.tencent.rfix.lib.engine.PatchEngineBase` / `com.tencent.mobileqq.msf.core.net.patch.PatchReporter` | DEXKIT-EXEMPT: 启动关键路径，先于 DexKit 扫描（热补丁框架启动早期即生效，必须稳定名第一时间挂上） |
| device/DisableEarpieceSwitch.kt | `com.tencent.qqnt.audio.play.player.AudioPlayerDeviceHelper`（OLD 回退分支） | 9.3.15 双包（base+tinker）中均不存在该类，为旧版 QQ 专用回退；锚点无法静态验证 |
| entry/QQSettingInject.kt | `NewSettingConfigProvider` / `MainSettingConfigProvider`（versionCode<12288 回退） | 同上：9.3.15 双包中均不存在，旧版回退分支无法验证锚点 |
| file/ShowDownloadTimes.kt | `TroopFileShowAdapter::class.java`（编译期桩引用）+ `ADAPTER_CLASS_NAME` | 适配器类可能由插件 troop_plugin.apk 提供，DexKit 只扫宿主 APK，扫不到插件 dex；桩类缺失时走插件兜底是既有降级链 |

## 3. 分诊后确认无需改动的类别

- 运行时视图树/标签比较：`ui/LiquidGlassTabBarView.kt` 的 tabNameMap（tab tag → 标题映射，
  与运行时 `getTag()` 值比较，非类查找）。
- DexKit 锚点串：`LiquidGlassTabBar.kt` 的 `"needShowTabHostDivider"`/`"initTabLayoutSwitch"` 等已存在的 usingStrings。
- 非宿主类名：`kotlin.Unit`、`kotlin.coroutines.Continuation`、`androidx.recyclerview.widget.RecyclerView$Adapter`（stdlib/androidx，非 QQ 类）。
- 协议/资源串：`ShowDownloadTimes` 的 proto 字段号、`RememberLastRead` 的 `"preAct"`/`"NTChatHistoryActivity"` intent 串。

## 4. 风险备忘

- `NavigateBySeqEvent(source=` 为 Kotlin data class toString 前缀，若该类属性重命名会失效（失效=功能静默关闭，不会误 hook）。
- `QQEditPic/OriginPic/`、`/Tencent/QQ_Secretfile/`、`[syncExtraInfoV2]: *`、
  `notifyAllDeviceStatusChanged: audioDeviceListener=`、`sendR must be access at Main Thread`
  均为 QQ 自家日志/常量串，QQ 大版本更新可能改动；DexKitFinder 用 singleOrNull，多命中/未命中均为静默不缓存 → 功能优雅失效，需按本档案重找锚点。
- 启动时序：switch 类 hook 的 onInit 仅在 `DexKitCache.initCache()` 成功后执行（Startup.kt），
  首次运行无缓存时走 doFind+重启，故业务期 onInit 依赖缓存是安全的；仅 §2 的启动关键路径豁免。

## 5. 对抗审查修正（2026-09-27）

- P1-2 兜底恢复：DownloadRedirect / RememberLastRead（vmMessenger + navigateBySeqEvent）/
  DisableEarpieceSwitch（NEW_IMPL）/ ShowDownloadTimes 四文件补回 `?: 原硬编码` 降级链，
  与全仓统一范式一致（此前锚点失效=功能静默禁用，现恢复为回退硬编码）。
- RememberLastRead 时序备注：navigateBySeqEvent 由原 hookAfter 内逐条消息解析改为
  onInit 一次解析（缺类时原=每条消息抛错刷日志，现=功能关闭），失败模式改善，非零改动。
- DisableBusCard 补 DEXKIT-EXEMPT 标注（9.3.15 双包已无 buscard 类，功能下线恒降级）。
