# 日志与取证文件位置（诊断必读，避免反复猜）

> 全部命令已在魅族 582QUGHG222LM 实测。adb 全路径含空格要引号。

## 1. LSPosed 模块日志（诊断主通道）

QFun 的 `LogUtils.e(tag, throwable)` 写入这里；**`LogUtils.d` 不写**（别用 d 做诊断）。

```bash
# 拉取（文件名以 ls 为准，QQ 清数据后会变）
adb -s 582QUGHG222LM exec-out su -c "cat /data/adb/lspd/log/modules_1970-12-14T01:39:24.606409.log" > lspd.log

# 列出日志文件
adb -s 582QUGHG222LM shell su -c "ls -la /data/adb/lspd/log/"
```

- 判读：`HookItem 名 + Error occurred` = 该功能抛异常（禁用但不拖垮其他）；
  `ModuleLoader init` + `宿主启动` = 模块加载成功；
  **无 Error ≠ 生效**（钩子注册但没人调用 = 静默失效，须行为验证）。

## 2. 诊断日志的正确打法

- **调试构建自带 MenuDiag 自诊断**（OnMenuBuild，`BuildConfig.DEBUG` 控制，release 无痕迹）：
  排查菜单显示问题时 `assembleDebug` 装机 → 复现一次 → `grep MenuDiag`，
  一条日志给出 items 数 / msgType / chatType / 开关数 / 插入数。
  纪律：先拿事实再改码，禁止"理论→改码→装机试证"盲循环（lessons.md §13）。
- 状态打包进假异常的 message（必进 LSPosed 日志）：
  ```kotlin
  LogUtils.e("QzoneTabDirect.arm", IllegalStateException("clazz=${clazz != null} methods=${ms?.size}"))
  ```
- ⚠️ 钩子回调里的异常会被外层 runCatching 吞掉 → 必须在 catch 前主动 LogUtils.e，
  否则静默失效无痕（本工程实测踩坑：margin 清零被吞导致"修了但没生效"）。
- ⚠️ 布局计算期禁止 `layoutParams =` 赋值（抛异常），margin 原地改字段即可。

## 3. 进程内取证 dump（视图树等临时文件）

QFun 代码写入 **QQ 自己的 filesDir**（应用私有，root 读取）：

| 文件 | 写入者 | 内容 |
|---|---|---|
| `qfun_bg_dump.txt` | QzoneTabDirect.dumpViewTreeWithBgOnce | 空间页视图树（类名/坐标/背景类型/margin），持续覆盖写 |
| `qfun_vh*.txt`（旧） | 历史调试版 | 同上旧格式 |

```bash
# 拉取
adb -s 582QUGHG222LM exec-out su -c "cat /data/data/com.tencent.mobileqq/files/qfun_bg_dump.txt" > dump.txt
# 触发方式：打开空间动态页后，impl 钩子每 20 次调用自动刷新一次
```

## 4. 其他日志位置速查

| 日志 | 位置 | 用途 |
|---|---|---|
| QStory 自身日志 | `/data/data/com.tencent.mobileqq/files/QStory/Log/` | 对照 QStory 行为（历史参考） |
| QFun 崩溃记录 | `QQ数据目录/QFun/<uin>/crash/` | CrashMonitor / SwallowSubThreadCrash 落盘 |
| QFun 自动备份 | `/sdcard/Download/QFun/QFunBackup_*.zip` | B15 每日备份产物 |
| QFun 配置 | `/data/data/com.tencent.mobileqq/shared_prefs/QFun_Config_<uin>.xml` | 开关状态（key = HookItem 对象名） |

## 5. 纪律

- 临时取证文件用完即删（QQ filesDir 内勿长期堆积）
- 诊断代码合入前移除或降级（持续 dump 有 I/O 开销）
- 用户正在用手机时不要 tap 屏幕；取证请用户配合操作后拉文件
