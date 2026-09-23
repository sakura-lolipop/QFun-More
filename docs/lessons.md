# 踩坑经验全集（QFun-More 开发实录）

> 每条 = 现象 → 根因 → 正确做法。全部来自真机调试实录，违反任何一条都会返工。

## §1 类加载时序：qzone 侧类启动期不可加载

**现象**：onInit 里 `"com.qzone.reborn.xxx".clazz` 返回 null，钩子被 `?.` 静默跳过，功能整体失效且无任何日志。

**根因**：QQ 启动早期（ModuleLoader init 阶段）只加载了部分 dex；qzone/feedx 相关类要等对应功能首次渲染才加载。`cooperation.qzone.api.QZoneApiProxy`（facade）就是典型——它比 impl 类加载晚得多。

**正确做法**：懒挂载模式（见 QzoneTabDirect.armFeedHooks/armListDecoration）——
- 启动期只注册**必经路径**的钩子（impl needShowQzoneFrame：feed 渲染必经，实测会触发）；
- 在该钩子的回调里**重试**懒挂载：目标类 `.clazz` 可加载时才注册真正的功能钩子；
- 未加载时不消耗任何一次性标志位（见 §5）。

**已实证可启动期加载**：impl 类（QZoneApiProxyImpl）、TabFrameControllerImpl、APP_RUNTIME。
**已实证需懒挂载**：facade 类、（推测）qzone.reborn.feedx.widget.j 等 feed 渲染期类。

## §2 静态验证 ≠ 运行时可用

**现象**：三个启动失败（StripOrigPicGps 无日志、HideContactCard/RemoveVoicePlayPopup NoSuchMethod）——全部在静态 base.apk 里"验证过签名存在"。

**根因**：①QQ 走 tinker 热修（`/data/user/0/com.tencent.mobileqq/tinker/patch-*/dex/tinker_classN.apk`），运行时类可能与 base.apk 不同；②方法签名在重载/桥接方法间匹配失败（invoke(Z)V vs invoke():Boolean）；③老版 QStory 规格里的签名在新版已变（h(int) → h(int,View)）。

**正确做法**：
- 静态验证用**真机实际加载的产物**：base.apk（qstory/qq9315/）+ tinker 热修包（拉取法见 qq-9315-toolchain.md）比对；
- 匹配器放宽：按方法名+参数个数，或按"同时引用锚点字符串"定位，避免硬编码完整签名；
- 上线前加一条 LogUtils.e 诊断（§4），首次运行确认钩子真的命中。

## §3 布局计算期禁止 `layoutParams =` 赋值

**现象**：margin 清零"修了但问题依旧"，LSPosed 日志里 clear 抛异常被 runCatching 吞掉。

**根因**：bind 钩子在 RecyclerView 布局计算期间触发，此时 `v.layoutParams = lp` 会抛
"Cannot call this method while RecyclerView is computing a layout or scrolling"，
被 runCatching 吞掉后**整个函数剩余部分都没执行**。

**正确做法**：布局期内**只原地改字段**（`lp.topMargin = 0`），不赋值不 requestLayout，
下一轮布局自然生效。需要立即重排时用 `v.post { ... }` 延迟到布局后。
同理：所有 runCatching 吞掉的路径必须留 LogUtils.e 痕迹（§4），否则静默失效无法排查。

## §4 诊断日志通道：只有 LogUtils.e 进 LSPosed 日志

- `LogUtils.e(tag, throwable)` / `LogUtils.e(hookItem, t)` → 写入 `/data/adb/lspd/log/modules_*.log` ✓
- `LogUtils.d(...)` → **不进** LSPosed 日志（自建通道，读不到）
- 诊断技巧：把状态打包进 IllegalStateException 的 message 里用 LogUtils.e 抛出
  （例：`LogUtils.e("X.arm", IllegalStateException("clazz=${clazz != null} methods=${ms?.size}"))`）
- 拉取：`adb exec-out su -c "cat /data/adb/lspd/log/modules_*.log" > local.log`

## §5 一次性标志位会被非目标事件消耗

**现象**：dump 的 once-flag 在启动期被非目标事件消耗（dump 出主界面而不是空间页），之后永远不再尝试。

**正确做法**：条件不满足时**归还标志位**（`flag.set(false)`），或干脆去掉 once 语义改为
持续覆盖写 + 内容过滤（写之前检查内容，不匹配就不写也不消耗）。

## §6 同一类被多处复用 → 实例作用域问题

**现象**：输入框提示（AIOEditText 构造器 hook）泄漏到消息页顶部搜索条——搜索条复用了同一个类。

**要点**：这是**运行时视图实例的作用域**问题，DexKit（dex 级定位）管不了。
正确做法：构造时不处理，挂 attach 监听，**挂树瞬间检查祖先链**限定作用域
（例：ChatInputHint 只在祖先含 `com.tencent.mobileqq.aio.input` 时生效）。

## §7 源码修改禁止 python 正则补丁

**现象**：连续多次构建失败——字符字面量 `'\n'` 被写成真实换行、锚点失配、触发器重复插入、括号失衡。

**根因**：python 脚本经 bash heredoc + JSON 双层传递，反斜杠转义必被剥一层；
多个补丁叠同一文件时锚点漂移。

**正确做法**：源码修改只用 **Write 整文件重写** 或 **Edit 精确锚点**（工具会校验"被修改过"防冲突）。
Python 只用于：二进制/资源解析（androguard）、生成数据文件——且脚本本身用 Write 落盘再执行，
不写 heredoc。

## §8 先分清"哪层画什么"，再动视觉

**现象**：为消灰带做布局收缩（Space GONE、行高收缩、margin 归零），用户反馈"你砍的背景"；
卡片背景置空后灰条"跑到背景上面"。

**正确做法**：空间页视觉分四层——页面皮肤（QZoneBaseBlockContainer 的 SkinnableBitmapDrawable）
→ 卡片 ColorDrawable → 内容子视图 → ItemDecoration.onDraw 绘制层。
动任何一层前先想清楚：目标灰是哪层画的？动它会不会连带裁掉皮肤呈现区？
修复灰带的完整推导见 gray-band-postmortem.md。

## §9 蓝图/二手结论必须二次实证

三次翻车实录：
- B2 蓝图写 h(int)，9.3.15 实际是 h(int,View)（QStory 规格是老版 QQ 的）；
- B11 蓝图称"置空 Make/Model"，解密实证只有 GPS 两项；
- "QStory 2.6.4 hook needShowQzoneFrame"来自 2.6.2 的实现，2.6.4 另有做法。

**正确做法**：蓝图/二手结论只是线索；动手前用 dexquery.py / androguard / 字符串池解密
在**目标版本的真实产物**上验证。已验证可用的取证工具链见 qq-9315-toolchain.md。

## §10 LSPosed 日志判读

- `HookItem 名 + Error occurred` = 该功能 onInit/onHook 抛异常（功能禁用但不拖垮其他）；
- **无 Error ≠ 生效**：钩子注册成功但没人调用 = 静默失效（例：底栏直达），
  必须行为验证（实际点一下 tab）；
- `ModuleLoader init` + `宿主启动` = 模块加载成功（每次重启都有）；
- QQ 重启时机：install -r 会杀进程，重开 QQ 即加载新模块，无需额外 force-stop。

## §11 环境与工具坑速查

- adb 全路径含空格要引号：`"/c/Users/littl/applemusic TV/tools/scrcpy-win64-v3.3.4/adb.exe"`
- 设备路径防 Git Bash 转换：`MSYS_NO_PATHCONV=1`；二进制用 `exec-out`（shell 会 CRLF 污染）
- baksmali：不能用 `-jar`（无主清单），要 `java -cp "C:/.../tools/*" org.jf.baksmali.Main d ...`
  （-cp 必须 Windows 风格路径；`/c/...` Java 不认）；多 dex 要逐个反汇编
- aapt2 dump xmltree：QQ 资源路径被混淆，需先用 arsc 解析出真实路径（`r/i/xxx.xml`）
- uiautomator dump 在 QQ 上返回 0 节点（拿不到 idle）；dumpsys activity top 会缺 QQ 段
  且混入其他应用 → 进程内视图 dump 才可靠（参考 dumpViewTreeWithBgOnce）
- androguard 记得 `logger.remove()`；ARSCParser 喂 bytes
- 用户正在用手机时**停止 UI 自动化**（tap 会误触）；取证改由用户配合操作

## §12 msgType 数字白名单区分不了同类消息：元素级 accept() 精过滤

**现象**：长按任何消息（文本/图片/表情）都显示"撤回重发/保存表情/复制图链"，点了没功能；
诊断日志显示文本/图片/表情三种场景下全列表项 msgType 恒=2。

**根因**：QQNT 里文本、MarketFace 表情、图文混排同属 msgType=2（`ForwardPtt.kt` 的
`MSG_TYPE_TEXT = 2` 早已写在源码里）——一个数字在**原理上**无法区分纯文本和表情，
menuKey 白名单 `[2,9]` 想表达"图片和表情"，实际放行的是"文本类+9"。
QStory 菜单实现全程不读 msgType，只按 elements 判空（getPicElement / getPttElement /
elementType==1）。

**正确做法**：`MenuClickListener.accept(msgData)` 精过滤钩子——白名单做粗筛，
功能按消息元素自判能否处理（图片类功能 accept = 存在 picElement），默认 true 向后兼容。
**修 msgType 类 bug 前先 grep 本仓库确认值语义（一分钟），别逆向想象（三轮弯路）**。

功能作用域同理：菜单显示过滤还要含"消息发送者"维度（撤回类功能仅自己发的消息显示，
`accept = userUin == currentUin`）——过滤不完整时"显示但必无效"与"不显示"手感天差地别。

## §13 调试循环禁令：先诊断拿事实，再改码

**现象**：菜单 bug 连续四轮"理论→修复→装上→用户试→问题依旧"
（反射读取→WeakHashMap→顺序调整→逆向对照），每轮理论都自洽，每轮都死。

**根因**：修复的预期效果从未与实际观察强制校验。用户验证是最贵的循环（要人配合），
日志是最便宜的循环（装机复现一次全部事实到手）。失败循环发生在**调试期**，
pre-commit 类事后检查根本够不着。

**正确做法**：
- **debug 构建自带运行时自诊断**（OnMenuBuild 的 MenuDiag，`BuildConfig.DEBUG` 控制，
  release 常量折叠剔除零痕迹）：排查 hook 行为问题一律先 `assembleDebug` 装机，
  复现一次后 `grep MenuDiag` 拿事实（msgType 实际值/开关数/插入数）；
- 每个假设落笔时写明"**看到 X 则假设废弃**"，与日志对照，不满足即弃；
- 有对照实现（QStory/上游）时，**逆向对照是第一动作**而非兜底：本次逆向 35 分钟定案，
  胜过三轮盲修；QStory 与 QFun 的菜单消息获取同源（菜单项→AIOMsgItem→getMsgRecord），
  差异只在类型判别走 elements；
- 用户反驳（"图片上怎么也显示"）是第一手证据，**触发理论重检**，
  不要把新观察解读进旧框架续命。
