package me.yxp.qfun.utils.security

/**
 * 猫语混淆（CatCrypt）——QStory 2.6.4 字符串混淆的完全兼容实现。
 *
 * 格式: 明文UTF-8字节 → XOR 重复密钥 → 每字节8字符（喵=0 呜=1，高位在前），字节间 '~' 分隔。
 * 已验证: 与 QStory 运行时 dump 309/309 往返一致，且能逐位解密其 dex 内真实池串。
 *
 * 注意: 这是防 strings 扫描的混淆，不是加密——重复密钥 XOR 抗不住已知明文。
 * 用法: val s = CatCrypt.encrypt("防撤回"); val t = CatCrypt.decrypt(s)
 */
object CatCrypt {

    private const val MEOW = '\u55b5'  // 喵 = 0
    private const val WOO = '\u545c'   // 呜 = 1
    private const val SEP = "~"
    const val DEFAULT_KEY = "suzhelan"

    fun encrypt(text: String, key: String = DEFAULT_KEY): String {
        val kb = key.toByteArray(Charsets.UTF_8)
        return text.toByteArray(Charsets.UTF_8)
            .mapIndexed { i, b -> (b.toInt() xor kb[i % kb.size].toInt()).toByte() }
            .joinToString(SEP) { byte ->
                (0 until 8).joinToString("") { n ->
                    if ((byte.toInt() shr (7 - n)) and 1 == 0) MEOW.toString() else WOO.toString()
                }
            }
    }

    fun decrypt(cat: String, key: String = DEFAULT_KEY): String {
        val kb = key.toByteArray(Charsets.UTF_8)
        val data = cat.split(SEP).filter { it.isNotEmpty() }.mapIndexed { i, group ->
            var b = 0
            for (ch in group) {
                b = (b shl 1) or if (ch == MEOW) 0 else 1  // 与 QStory parseData 一致：非喵即 1
            }
            (b xor kb[i % kb.size].toInt()).toByte()
        }.toByteArray()
        return String(data, Charsets.UTF_8)
    }
}
