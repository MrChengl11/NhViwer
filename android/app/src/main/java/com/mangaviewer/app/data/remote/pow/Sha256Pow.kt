package com.mangaviewer.app.data.remote.pow

import java.security.MessageDigest

/**
 * 工作量证明求解：寻找 nonce 使 sha256(challenge + nonce) 的**前导零比特数** ≥ difficulty。
 *
 * 注意：服务端返回的 difficulty 语义是「比特」（实测 difficulty=17 时约 50 万次尝试可解），
 * 不是「十六进制 0 的个数」。早期实现误按 nibble 处理，导致 difficulty>15 时永远解不出来。
 */
class Sha256Pow {

    fun solve(challenge: String, difficulty: Int, maxAttempts: Long = 200_000_000L): String {
        if (difficulty <= 0) return "0"
        val digest = MessageDigest.getInstance("SHA-256")
        var counter = 0L
        while (counter < maxAttempts) {
            val hash = digest.digest((challenge + counter).toByteArray(Charsets.UTF_8))
            if (leadingZeroBits(hash) >= difficulty) return counter.toString()
            counter++
        }
        error("PoW 求解失败（尝试 $maxAttempts 次）")
    }

    fun verify(challenge: String, nonce: String, difficulty: Int): Boolean {
        if (difficulty <= 0) return true
        val hash = MessageDigest.getInstance("SHA-256")
            .digest((challenge + nonce).toByteArray(Charsets.UTF_8))
        return leadingZeroBits(hash) >= difficulty
    }

    /** 返回摘要的前导零比特数。 */
    private fun leadingZeroBits(hash: ByteArray): Int {
        var bits = 0
        for (byte in hash) {
            val value = byte.toInt() and 0xFF
            if (value == 0) {
                bits += 8
                continue
            }
            var bit = 0x80
            while (bit > 0 && (value and bit) == 0) {
                bits++
                bit = bit shr 1
            }
            return bits
        }
        return bits
    }
}
