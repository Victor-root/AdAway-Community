package org.adaway.vpn

/**
 * Spaces out the automatic rebuilds of the tunnel: the first one is immediate, then each rebuild
 * coming soon after the previous one waits twice as long, from 1 second up to 128 seconds. After
 * 128 seconds without a rebuild, the next one is immediate again. User actions and a network
 * coming back call [reset] so that they never wait.
 */
class ReconnectThrottle(private val clock: () -> Long = System::currentTimeMillis) {
    private var delay = 0L
    private var lastAttempt: Long? = null

    fun reset() {
        delay = 0
        lastAttempt = null
    }

    /** Returns how many milliseconds to wait before the next rebuild, and counts it. */
    fun nextDelay(): Long {
        val now = clock()
        val last = lastAttempt
        delay = when {
            last == null || now - last >= MAX_DELAY_MILLIS -> 0
            delay == 0L -> FIRST_DELAY_MILLIS
            else -> (delay * 2).coerceAtMost(MAX_DELAY_MILLIS)
        }
        lastAttempt = now + delay
        return delay
    }

    private companion object {
        const val FIRST_DELAY_MILLIS = 1_000L
        const val MAX_DELAY_MILLIS = 128_000L
    }
}
