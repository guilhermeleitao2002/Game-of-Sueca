package pt.up.fe.asma.sueca.engine

import kotlin.random.Random

/**
 * The figures `sueca.py` prints at the end of a run, for one strategy pairing.
 *
 * [convertedPoints] is the difference between what a team captured and what it was dealt, so it
 * measures the strategy rather than the luck of the hand. That, not the raw win count, is the
 * interesting metric across pairings.
 */
data class SimulationSummary(
    val sportingAgent: AgentKind,
    val benficaAgent: AgentKind,
    val games: Int,
    val wins: Map<TeamId, Int>,
    val ties: Int,
    val points: Map<TeamId, Long>,
    val dealt: Map<TeamId, Long>,
    val elapsedMillis: Long,
) {

    fun winsOf(team: TeamId): Int = wins[team] ?: 0

    fun winRate(team: TeamId): Double = if (games == 0) 0.0 else winsOf(team).toDouble() / games

    fun tieRate(): Double = if (games == 0) 0.0 else ties.toDouble() / games

    fun averagePoints(team: TeamId): Double =
        if (games == 0) 0.0 else (points[team] ?: 0L).toDouble() / games

    fun convertedPoints(team: TeamId): Double =
        if (games == 0) 0.0 else ((points[team] ?: 0L) - (dealt[team] ?: 0L)).toDouble() / games

    fun agentOf(team: TeamId): AgentKind = if (team == TeamId.SPORTING) sportingAgent else benficaAgent

    companion object {
        fun empty(sporting: AgentKind, benfica: AgentKind) = SimulationSummary(
            sportingAgent = sporting,
            benficaAgent = benfica,
            games = 0,
            wins = TeamId.entries.associateWith { 0 },
            ties = 0,
            points = TeamId.entries.associateWith { 0L },
            dealt = TeamId.entries.associateWith { 0L },
            elapsedMillis = 0L,
        )
    }
}

/** Headless agent versus agent play, the equivalent of running `sueca.py -n`. */
object Simulation {

    /**
     * Plays [games] games of [sporting] against [benfica].
     *
     * @param seed null for a fresh random run, or a fixed value to reproduce one exactly.
     * @param onProgress called after every game; throw from it (or cancel the coroutine that
     *   called this) to stop early. The summary is only returned on a full run.
     */
    fun run(
        sporting: AgentKind,
        benfica: AgentKind,
        games: Int,
        config: EngineConfig = EngineConfig.SIMULATION,
        seed: Long? = null,
        onProgress: (played: Int) -> Unit = {},
    ): SimulationSummary {
        require(games >= 1) { "the number of games must be at least 1" }

        val random = if (seed == null) Random.Default else Random(seed)
        val wins = TeamId.entries.associateWith { 0 }.toMutableMap()
        val points = TeamId.entries.associateWith { 0L }.toMutableMap()
        val dealt = TeamId.entries.associateWith { 0L }.toMutableMap()
        var ties = 0
        val started = System.currentTimeMillis()

        repeat(games) { index ->
            val game = Game(sporting, benfica, config, random)
            game.deal()
            game.playToCompletion()

            val result = game.result()
            if (result.winner == null) ties++ else wins[result.winner] = (wins[result.winner] ?: 0) + 1
            for (team in TeamId.entries) {
                points[team] = (points[team] ?: 0L) + (result.scores[team] ?: 0)
                dealt[team] = (dealt[team] ?: 0L) + (result.dealt[team] ?: 0)
            }

            onProgress(index + 1)
        }

        return SimulationSummary(
            sportingAgent = sporting,
            benficaAgent = benfica,
            games = games,
            wins = wins,
            ties = ties,
            points = points,
            dealt = dealt,
            elapsedMillis = System.currentTimeMillis() - started,
        )
    }
}
