package me.yxp.qfun.conf

import kotlinx.serialization.Serializable

@Serializable
data class ChatHintConfig(
    val hintText: String = "在这里输入你想说的内容"
)
