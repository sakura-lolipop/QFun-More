package me.yxp.qfun.hook.troop

import android.widget.FrameLayout
import com.tencent.mvi.base.route.MsgIntent
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.common.ModuleScope
import me.yxp.qfun.hook.api.AIOViewUpdateListener
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.plugin.view.PluginViewLoader
import me.yxp.qfun.utils.dexkit.DexKitTask
import me.yxp.qfun.utils.hook.hookAfter
import me.yxp.qfun.utils.io.ObjectStore
import me.yxp.qfun.utils.qq.QQCurrentEnv
import me.yxp.qfun.utils.reflect.findMethod
import me.yxp.qfun.utils.reflect.newInstanceWithArgs
import me.yxp.qfun.utils.reflect.toClass
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher
import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.reflect.Constructor
import java.lang.reflect.Method

@HookItemAnnotation(
    "记住上次查看位置",
    "记住群聊上次查看位置，进入聊天界面自动跳转",
    HookCategory.GROUP
)
object RememberLastRead : BaseSwitchHookItem(), DexKitTask, AIOViewUpdateListener {

    private lateinit var sendIntent: Method
    private lateinit var cotr: Constructor<*>

    private var navigateBySeqEvent: Class<*>? = null

    private val serializer = MapSerializer(String.serializer(), Long.serializer())
    private var seqMap = mutableMapOf<String, Long>()

    override fun onUpdate(
        frameLayout: FrameLayout,
        msgRecord: MsgRecord
    ) {
        if (msgRecord.chatType != 2 || isFromHistory()) return
        seqMap[msgRecord.peerUid] = msgRecord.msgSeq
    }

    override fun onInit(): Boolean {

        val vmMessenger = runCatching { requireClass("vmMessenger") }.getOrNull()
            ?: "com.tencent.mvi.base.route.VMMessenger".toClass
        navigateBySeqEvent = runCatching { requireClass("navigateBySeqEvent") }.getOrNull()
            ?: $$"com.tencent.mobileqq.aio.event.MsgNavigationEvent$NavigateBySeqEvent".toClass
        cotr = vmMessenger.constructors.single {
            it.parameterCount == 2 && it.parameterTypes.contains(Boolean::class.javaPrimitiveType)
        }
        sendIntent = vmMessenger.findMethod {
            returnType = void
            paramTypes(MsgIntent::class.java)
        }
        ObjectStore.load("data", name, serializer)?.let { seqMap = it as MutableMap<String, Long> }
        return super.onInit()
    }

    override fun onHook() {

        cotr.hookAfter(this) {
            val contact = PluginViewLoader.currentContact
            if (contact.chatType != 2 || !seqMap.contains(contact.peerUid)) return@hookAfter

            ObjectStore.save("data", name, seqMap, serializer)

            val intent = navigateBySeqEvent?.newInstanceWithArgs(
                "", seqMap[contact.peerUid], 0L, false, null, false, false, null, 228, null
            ) ?: return@hookAfter

            ModuleScope.launchMainDelayed(150) {
                if (isFromHistory()) return@launchMainDelayed
                sendIntent.invoke(it.thisObject, intent)
            }

        }

    }

    private fun isFromHistory(): Boolean {
        val intent = QQCurrentEnv.activity?.intent ?: return false
        return intent.getStringExtra("preAct") == "NTChatHistoryActivity"
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "vmMessenger" to FindClass().apply {
            searchPackages("com.tencent.mvi.base.route")
            matcher {
                usingStrings(listOf("sendR must be access at Main Thread"), matchType = StringMatchType.Equals)
            }
        },
        "navigateBySeqEvent" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.aio.event")
            matcher {
                usingStrings(listOf("NavigateBySeqEvent(source="), matchType = StringMatchType.Equals)
            }
        }
    )

}
