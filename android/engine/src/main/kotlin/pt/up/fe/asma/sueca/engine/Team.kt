package pt.up.fe.asma.sueca.engine

/**
 * The two hard coded teams of the simulator. Player ids run 1, 2 for [SPORTING] and 3, 4 for
 * [BENFICA]; that ordering is what indexes the belief tables, so it matters.
 */
enum class TeamId(val displayName: String, val playerNames: List<String>) {
    SPORTING("Sporting", listOf("Leitao", "Fred")),
    BENFICA("Benfica", listOf("Pedro", "Sebas"));

    val firstPlayerId: Int get() = 1 + 2 * ordinal

    val opponent: TeamId get() = if (this == SPORTING) BENFICA else SPORTING
}

/** Ported from `Team.py`. */
class Team(val id: TeamId, val agent: AgentKind) {

    val name: String get() = id.displayName

    val players: MutableList<Player> = mutableListOf()

    /** Points captured during the game (0..120). */
    var score: Int = 0

    /** Points held in the two hands the team was dealt (0..120). */
    var initialPoints: Int = 0

    fun addPlayer(player: Player) {
        players.add(player)
    }

    fun partnerOf(player: Player): Player? = players.firstOrNull { it !== player }

    /**
     * How much the team over or under performed relative to the hand luck it was dealt. This,
     * rather than the raw score, is the metric the report compares strategies on.
     */
    val convertedPoints: Int get() = score - initialPoints
}
