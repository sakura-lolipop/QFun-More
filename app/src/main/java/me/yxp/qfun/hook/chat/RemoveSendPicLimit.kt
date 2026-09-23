package me.yxp.qfun.hook.chat

import me.yxp.qfun.BuildConfig
import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.hook.hookBefore
import me.yxp.qfun.utils.hook.returnConstant
import me.yxp.qfun.utils.log.LogUtils
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethod

@HookItemAnnotation(
    "去除发送图片限制",
    "解除相册一次最多选择/发送20张图片的上限",
    HookCategory.CHAT
)
object RemoveSendPicLimit : BaseSwitchHookItem() {

    // V1()Z 语义（9.3.1.5 反汇编 + QStory 2.6.4 同款实现双重验证）：
    // 返回 (已选数 + r) < q，true = 未达上限、可以继续选。
    // QQ 在点击处理器（弹上限提示）与 P1 添加（拒收）两处消费它，
    // 恒 true = 两道门同时常开，等价于 QStory 的 before setResult(TRUE)。
    // 懒挂载：相册类懒加载且插件 classloader 启动后才建立，
    // 在点击处理器回调里从运行时实例的类上取方法，保证同源（lessons §1）
    private var armed = false

    override fun onHook() {
        val listener =
            "com.tencent.qqnt.qbasealbum.base.model.UniversalItemClickListener".clazz
                ?: return

        listener.findMethod {
            paramCount = 5
            returnType = int
        }.hookBefore(this) { param ->
            val vm = param.args.getOrNull(3) ?: return@hookBefore
            armCanSelectHook(vm.javaClass)
        }
    }

    @Synchronized
    private fun armCanSelectHook(vmClass: Class<*>) {
        if (armed) return
        runCatching {
            val canSelect = vmClass.findMethod {
                returnType = boolean
                paramCount = 0
            }
            canSelect.returnConstant(this, true)
            armed = true
            if (BuildConfig.DEBUG) {
                LogUtils.e("PicLimitDiag", IllegalStateException("armed ${canSelect.name} = true"))
            }
        }.onFailure {
            if (BuildConfig.DEBUG) LogUtils.e("PicLimitDiag", it)
        }
    }
}
