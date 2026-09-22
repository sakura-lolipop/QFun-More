package me.yxp.qfun.conf

import kotlinx.serialization.Serializable

@Serializable
data class ChatHintConfig(
    val hintText: String = "Hello World."
)
