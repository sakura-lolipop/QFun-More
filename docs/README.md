# QFun-More 文档目录

> 本项目 = QFun 的 fork（me.yxp.qfun），目标：复刻 QStory 功能并长期支持新版 QQ。
> 仓库：https://github.com/sakura-lolipop/QFun-More（分支 qfunory = 开发主线）。
>
> **新 agent / 新会话从这里开始**：先读本页，再按任务类型跳转对应文档。
> 历史背景（QStory 逆向、喵语解密等上游知识）见仓库外的 `qstory/HANDOFF.md`。

## 文档索引

| 文档 | 内容 | 什么时候读 |
|---|---|---|
| [lessons.md](lessons.md) | 全部踩坑经验：类加载时序、布局期限制、静态≠运行时、诊断通道、作用域问题 | **写任何 hook 代码之前必读** |
| [qq-9315-toolchain.md](qq-9315-toolchain.md) | QQ 9.3.15 静态取证工具链：base.apk + dexquery.py、tinker 热修拉取、baksmali/aapt2 用法与坑 | 定位类/方法/资源之前 |
| [deobfuscation-guide.md](deobfuscation-guide.md) | 反混淆指南：喵呜字符串加密算法与解密工具、飘花类名混淆、混淆名重定位三法、QUI 语义色 | 遇到混淆名/加密字符串/找不到类时 |
| [dexkit-migration-guide.md](dexkit-migration-guide.md) | DexKit 迁移规范："坏了才迁"策略、锚点验证 SOP、实现范例 | 功能失效需要迁 DexKit，或写新功能选定位方式时 |
| [gray-band-postmortem.md](gray-band-postmortem.md) | 灰带修复完整复盘（B2 直开模式视觉修复）：分层模型、三层灰源、修复方案、失败史 | 动空间页视觉，或想看完整调试方法论示范 |
| [logging.md](logging.md) | 日志与取证文件位置：LSPosed 日志拉取命令、诊断打法、进程内 dump 位置 | **任何诊断/取证之前必读** |

## 一页速查

- 构建：`JAVA_HOME=C:/Users/littl/xiaomi/tools/jdk-21.0.12.1+1 ./gradlew assembleRelease`
- 装机：`adb -s 582QUGHG222LM install -r app/build/outputs/apk/release/app-release.apk`（改完必须重启 QQ）
- 签名：testkey（local.properties 已配）；QFun 配置存储：QQ 数据目录 `shared_prefs/QFun_Config_<uin>.xml`（key = HookItem 对象名）
- 失效排查 SOP：LSPosed 日志（`/data/adb/lspd/log/modules_*.log`，搜 HookItem 名）→ dexquery 验证类/方法 → 懒挂载修正
- 诊断日志通道：**LogUtils.e** 才进 LSPosed modules log；LogUtils.d 不可见
- 灰带/视觉问题的分层模型与修复史：gray-band-postmortem.md

## 硬性纪律（历代踩坑总结，违反必返工）

1. 源码修改只用 Write 整文件 / Edit 精确锚点，**禁止 python 正则补丁源码**（转义地狱+结构损坏实录见 lessons.md §7）
2. hook 代码里禁止 `layoutParams =` 赋值出现在布局计算期（bind 钩子在布局期触发），margin 只原地改字段
3. qzone 侧类启动期不可加载 → 一切 qzone 类钩子走**懒挂载**（armFeedHooks/armListDecoration 模式）
4. 静态 base.apk 验证通过 ≠ 运行时可用（tinker 热修 + 类加载时序），必须真机 + LSPosed 日志确认
5. 蓝图/二手结论必须二次实证（B11 Make/Model、B2 h(int)、QStory 2.6.2≠2.6.4 三次翻车实录见 lessons.md §14）
