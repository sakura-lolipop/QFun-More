# QQ 9.3.15 静态取证工具链

> 所有脚本在 `qstory/qq9315/`，产物 `base.apk`（403MB，从魅族 /data/app 拉取）。
> 面向对象：需要在"不装机、不运行 QQ"的前提下定位类/方法/字符串/资源的 agent。

## 环境前置

- Python 3.13 + androguard（记得 `from loguru import logger; logger.remove()`，否则 debug 刷屏）
- Windows Python 读不了 Git Bash 的 `/tmp`、`/c/...` 路径——脚本一律用 `C:/...` 或 `Path(__file__)`
- baksmali：`java -cp "C:/Users/littl/xiaomi/tools/*" org.jf.baksmali.Main d <dex/apk> -o <outdir>`
  - ⚠️ `-cp` 必须 **Windows 风格路径**（`/c/...` Java 不认）
  - ⚠️ `baksmali-2.5.2.jar` 无主清单不能 `-jar`；`baksmali-fat.jar` 是坏文件（9 字节）
  - ⚠️ baksmali 处理 APK **只拆 classes.dex**，多 dex 要先 unzip 再逐个反汇编（合并输出目录即可）
- aapt2：`C:/Users/littl/AppData/Local/Android/Sdk/build-tools/36.0.0/aapt2.exe`
  - `dump resources base.apk`：资源全表（ID→名称→值）
  - `dump xmltree base.apk --file <真实路径>`：布局树。⚠️ QQ 资源路径已混淆，
    arsc 里的原名（`qzone_feedx_item_default_text`）对应真实路径 `r/i/qzone_feedx_item_default_text.xml`，
    必须先从 `dump resources` 反查真实路径

## 脚本清单（都可直接改目标复用）

| 脚本 | 用途 | 关键点 |
|---|---|---|
| `dexquery.py` | 字符串→类定位（raw 字节预筛 dex，androguard 精查） | 先 raw grep 再解析，省时间 |
| `dump_methods.py` | 按类名列出全部方法签名（验证 hook 目标签名） | 类名子串匹配，多 dex 全扫 |
| `find_input_class.py` | 包名+字符串常量组合筛类（定位布局/常量引用） | 指令级 const-string 遍历较慢，先缩小 dex |
| `find_input_root.py` / `find_res_ref.py` | 方法级双锚点 / 资源 ID 引用定位 | 注意 AND 匹配跨 dex 会落空 |
| `find_arsc_hint.py` | arsc 资源值反查（文案→资源） | 或直接 aapt2 dump resources + grep |
| `find_onDraw.py` | **混淆名装饰器定位**（getItemOffsets+onDraw 双声明特征） | 灰带真凶 widget/j 就靠它 |
| `dump_two.py` | 两个类的方法签名并排对比（验证重载/桥接差异） | |

## 常用流程

### 1. 验证某类/方法在 9.3.15 是否存在及真实签名
```python
# dump_methods.py 改 TARGETS 即可；输出含完整描述符（参数类型+返回值）
```
用途：hook 前验证签名（h(int) vs h(int,View) 教训）、tinker 与 base 差异比对。

### 2. 字符串锚点定位功能类
QQ 自身字符串（埋点、日志、文案）不加密且比类名稳定：
```python
# 例：找同时引用 "FrameControllerInjectImpl.setQzoneLebaTab" 的类
# → raw 字节筛 dex → 遍历类的指令找 const-string → 锁定方法
```
已知有效锚点（9.3.15）：
- `"FrameControllerInjectImpl.setQzoneLebaTab"` → frame 决策链
- `"init Mini App, cost="` → 小程序入口布局
- `"GdtMvTitleHelper"` + `"bar == null"` → GDT 广告回调

### 3. tinker 热修差异取证
运行时类 = base.apk + tinker 覆盖。**静态查不到时先确认 tinker**：
```bash
adb exec-out su -c "cat /data/user/0/com.tencent.mobileqq/tinker/patch-*/dex/tinker_classN.apk" > tinker.apk
# 解出 dex 逐个 baksmali；目标类在 tinker 里 = 运行时用 tinker 版本（签名可能不同）
```
实证案例：QzoneTabDirect 的 needShowQzoneFrame 闸门在 tinker 的 FrameControllerInjectImpl
中被"消费一次后不再询问"——这就是 QStory 原方案失效的根因。

### 4. 资源三级反查（文案 → 资源名 → 真实路径）
```
aapt2 dump resources base.apk | grep "文案"     # 得资源 ID + 名称
aapt2 dump xmltree base.apk --file "<arsc 里的真实混淆路径>"
```
注意：arsc 原名与 APK 内真实路径可能都被混淆，两级都要反查。

## 相关路径速查

| 路径 | 内容 |
|---|---|
| `qstory/qq9315/base.apk` | QQ 9.3.15 全量（403MB，魅族拉取） |
| `qstory/qq9315/tinker_classN.apk` | 真机热修覆盖包（369MB） |
| `qstory/qq9315/*.py` | 上述全部脚本 |
| `qstory/sm264_1/` `sm264_2/` | QStory 2.6.4 全量反汇编（qqso 字体目录等干扰项注意排除） |
| `qstory/pool_264.json` | QStory 2.6.4 解密字符串池 3403 条 |
