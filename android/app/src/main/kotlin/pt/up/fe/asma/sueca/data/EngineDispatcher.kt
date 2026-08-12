package pt.up.fe.asma.sueca.data

import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/**
 * One thread that owns the game.
 *
 * A `Game` is mutable, and two things want at it: the search that produces the advice, which is
 * slow enough to belong off the main thread, and the taps that play cards, which arrive on it.
 * Serialising both onto a single thread means a tap that lands mid search queues behind it
 * rather than mutating a hand somebody is iterating.
 */
fun engineDispatcher(): ExecutorCoroutineDispatcher =
    Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "sueca-engine") }
        .asCoroutineDispatcher()
