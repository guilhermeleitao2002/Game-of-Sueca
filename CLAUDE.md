# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Multi-agent simulator for Sueca (Portuguese trick-taking card game), built for the ASMA course. Two hard-coded teams — **Sporting** (players `Leitao`, `Fred`) and **Benfica** (`Pedro`, `Sebas`) — each play a single strategy for the whole game; the simulator pits strategy pairs against each other over N games and records win rates. `paper/` holds the ACM-format write-up (`report.tex` → `report.pdf`, built with `pdflatex` + `bibtex`); its result tables are transcribed from `results/`, so regenerating one means updating the other.

`android/` is a second front end onto the same rules: a Kotlin port of the engine plus a Compose app (advisor, play-against-agents, card scanner, simulator). It is a *port*, not a binding — the Python is still the reference for the published numbers, and behaviour changes must be made in both or explicitly flagged. See [android/README.md](android/README.md) and the Android section below.

## Running

No build, no package manager, no test suite, no linter. Plain scripts run directly.

```bash
pip3 install numpy matplotlib termcolor
python3 sueca.py -o results/out.json -s greedy -b random -n 100
python3 sueca.py -o log -s predictor -b random -m human   # interactive; you play as "Leitao"
./run_simulation.sh                                        # all 15 pairings, 10000 games each
NUM_GAMES=100 ./run_simulation.sh                          # same, but quick
```

Requires **Python 3.10+** (PEP 604 unions, PEP 585 generics).

`run_simulation.sh` writes into `results/`; `sueca.py` creates the directory for its `-o` path and for the plot, so it can be run from anywhere.

Strategy values for `-s`/`-b`: `random`, `greedy`, `maxpointswon`, `maxroundswon`, `cooperative`, `predictor` (argparse enforces the list).

## Architecture

[sueca.py](sueca.py) (CLI + aggregation) → [Game.py](Game.py) (rules, dealing, round resolution) → [Player.py](Player.py) (all six agent strategies) → [Card.py](Card.py) / [Team.py](Team.py) (data holders).

**Two number systems per card, easily confused.** `Card.value` is the *points* it scores (A=11, 7=10, K=4, J=3, Q=2, rest 0). `Card.order` is *trick strength* 0–9 over the rank sequence `2 3 4 5 6 Q J K 7 A`. Round winners are decided by `order`, scores by `value`. Both derive from the `RANKS` / `RANK_VALUES` tables in [Card.py](Card.py) — that module is the single source of truth, and `SUIT_INDEX` / `ORDER_VALUES` there exist so the belief agents never re-hardcode them.

**Never compare `order` directly to decide who is winning.** A card of the leading suit loses to any trump regardless of order, so `Card.beats(current_best, trump_suit)` is the only correct test. Both `Game.evaluate_round` and every strategy's "can I win this?" branch go through it.

**Hands are always sorted by `order`.** `Player.add_card` re-sorts on every insert, so every strategy relies on `hand[0]` = weakest, `hand[-1]` = strongest. Preserve that invariant if you touch hand manipulation.

**Strategies only implement `choose_card`.** `Player.play_round` (base class) is the template: it calls `choose_card(position, round_suit, cards_played, game)`, removes the card, lets the leader fix the round suit, and announces the play. A strategy that removes its own card or prints its own play is a bug.

**`Game.evaluate_round(cards)` scores partial rounds.** It returns a `RoundResult(points, card, winner)` where `winner` indexes into the list it was given — which lines up with `playersOrder` because the cards played so far always belong to the first N players in order. Strategies call it mid-round to ask "who is winning right now?", and `PredictorPlayer` calls it on hypothetical rounds.

**Belief agents.** `BeliefPlayer` holds `beliefs[player, suit, order]` as a 4×4×10 numpy array of probabilities (initialized to 1/3; own row filled in as cards are dealt). `update_beliefs` zeroes a card once seen, zeroes a player's whole suit row when they fail to follow suit, then renormalizes uniformly over remaining holders. `CooperativePlayer` uses only its partner's row; `PredictorPlayer` enumerates the cartesian product of every card the players still to act might answer with, which is why `predictor` runs far slower than the rest.

**Adding a strategy** means writing the subclass in `Player.py` and adding one line to the `STRATEGIES` dict in `Game.py`. That dict also drives argparse's `choices`, so nothing else needs touching.

## Output

`-o` writes a JSON array, streamed incrementally, and the closing `]` is written from a `finally` block so an interrupted run still leaves parseable JSON. Per game: `Rounds` (winner + points) and `Teams` (names, strategies, `score`, `initial_points`).

`initial_points` is the sum of card values *dealt* to a team, accumulated in `Game.hand_cards`. The `converted_points_*` figures printed at the end are `score - initial_points`: how much a strategy over- or under-performed relative to the hand luck it was given. That, not raw win count, is the interesting metric across strategy pairings.

Note that `results/*.txt` committed before August 2026 spell one key `average_points_per_game_sporing`; it is `..._sporting` now.

## Android port (`android/`)

Two Gradle modules: `:engine` (pure Kotlin/JVM — rules, agents, beliefs, search, suit outlines, card classifier) and `:app` (Compose UI, CameraX, ML Kit). `:engine` has no Android dependency on purpose, so `./gradlew :engine:test` runs the whole of the interesting logic — 64 tests — without an SDK or a device. Build with `./gradlew :app:assembleDebug`; needs JDK 17 and SDK 35.

**Structural differences from the Python, both deliberate.** Strategies are stateless `Strategy` objects that a `Player` *holds* rather than subclasses a player *is*, which is what lets the app ask every agent about the same position and swap the advising agent mid game. And `PredictorStrategy` takes its candidate cards from a `HandOracle`: `PerfectInfoOracle` reads the real hands exactly like `get_player_possible_cards` does (used by the simulator, keeps `results/` comparable), `BeliefOracle` uses only belief support (used whenever a person is at the table, since nobody can peek there). `EngineConfig.SIMULATION` / `.FAIR` / `.ADVISOR` bundle those choices.

**`Game` is a state machine, not a loop.** `currentPlayer` / `legalCards()` / `play(card)` / `playAgentTurn()`, with `playToCompletion()` recovering `Game.play_game`. UI code drives it one card at a time; `startFromKnownHand` sets up the advisor's one-known-hand case and `startFromHands` a fully known position.

**Faithfulness is a feature.** The `current_round < 2` trump penalty, stable-sort ties going to the weakest card, and `SelfBeliefPolicy.LEGACY` (a player's own belief row keeps describing the hand it was *dealt*, because `Game.update_beliefs` skips the player that just played) are all reproduced. `TrickSearch` normalises the expected-utility sum by the total probability mass, which is a constant factor and so leaves the ranking identical while making the number mean expected trick points. If you change any of this, the Simulator screen stops agreeing with `results/`.

**One source of truth for suit shapes.** `shapes/SuitShapes.kt` holds the four outlines as path commands; the app converts them to Compose `Path`s to draw cards, and `ShapeRasterizer` fills them into the binary masks `SuitClassifier` matches camera pips against. Changing an outline changes both, which is the point.

**The camera reads rank and suit separately.** ML Kit text recognition for the rank glyph (`RankReader` rejects 8/9/10 rather than guessing, accepts R/D/V), classical CV for the pip below it (`PipFinder` thresholds and takes a connected component, `SuitClassifier` matches multi-scale templates). Nothing is trusted until `ScanAccumulator` has seen it several frames running.

## Known quirks

Pre-existing and deliberately left alone; don't be surprised by them.

- `PredictorPlayer` reads the *actual* hands of the other players (`get_player_possible_cards`) and only weights them by belief probability, so it is not playing purely on inference.
- Its trump-avoidance heuristic fires on `game.current_round < 2` regardless of seat, although the comment describes it as applying only when leading.
- `CooperativePlayer.play_second` treats cards worth 0 points as unable to win a round, because it scores candidates by `belief * value`.
