# Sueca Engine for Android

The simulator in this repository, turned into something you can hold: a Sueca engine that tells
you which card to play, the way a chess engine tells you which move to make, plus a camera that
reads real cards off the table.

Everything runs on the phone. No account, no network, no server.

## What it does

| Screen | What it is for |
| --- | --- |
| **Table advisor** | You are playing with real cards. Scan or tap in your hand, record what everybody plays, and the engine tells you what to play next and why. |
| **Play a game** | A full game against three agents, with the engine advising you as you go. |
| **Scan cards** | The camera pipeline on its own: point it at cards and watch rank and suit come back. |
| **Simulator** | Agent against agent for N games, reporting the same figures as `results/`. |
| **The agents** | What each of the six strategies actually does. |

The advising agent defaults to the **Deck Predictor** and can be changed to any of the six, at
any point, including mid game.

## Requirements

- **Android Studio** (Ladybug or newer) or a standalone Android SDK with platform 35 and
  build tools 35.
- **JDK 17** (Android Studio bundles one; `JAVA_HOME` must not point at a JDK newer than 21,
  which the Android Gradle Plugin does not accept).
- A device or emulator running **Android 7.0 (API 24)** or newer. The scanner needs a camera,
  everything else works without one.

## Commands

From this `android/` directory:

```bash
# Rules, agents, beliefs and the vision maths, on the JVM. No SDK needed, ~2 seconds.
./gradlew :engine:test

# Debug APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleDebug

# Build and install onto the connected device or running emulator
./gradlew :app:installDebug

# Minified release APK (unsigned) -> app/build/outputs/apk/release/
./gradlew :app:assembleRelease
```

If Gradle cannot find the SDK, either open the project once in Android Studio (it writes
`local.properties` for you), or do it by hand:

```bash
cp local.properties.example local.properties
# then edit sdk.dir=/path/to/Android/Sdk
```

or export `ANDROID_HOME=/path/to/Android/Sdk` before running Gradle.

To install the APK on a phone over USB, with developer mode and USB debugging turned on:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Layout

```
android/
  engine/     pure Kotlin, no Android at all: rules, agents, beliefs, search,
              suit outlines and the classifier core. 64 unit tests.
  app/        Jetpack Compose UI, CameraX, ML Kit.
```

`:engine` deliberately has no Android dependency, so the whole of the interesting logic is
testable with a plain `./gradlew :engine:test` and none of it needs a device.

## How the port relates to the Python

| Python | Kotlin |
| --- | --- |
| `Card.py` | `engine/Card.kt` — same rank order, same point values |
| `Team.py` | `engine/Team.kt` |
| `Player.py` strategies | `engine/Strategies.kt`, one object per agent |
| `BeliefPlayer.beliefs` (4x4x10 numpy) | `engine/Beliefs.kt`, same shape, same update rules |
| `Game.py` | `engine/Game.kt`, as a state machine instead of a blocking loop |
| `sueca.py` aggregation | `engine/Simulation.kt` |

Two deliberate differences, both switchable:

- **Strategies are stateless objects** rather than `Player` subclasses. A seat holds a
  strategy instead of being one, which is what lets the app ask *every* agent what it would
  play in the current position, and lets you change your advisor without restarting the game.
- **The Deck Predictor does not have to peek.** `PredictorPlayer.get_player_possible_cards`
  reads the other players' actual hands; at a real table nobody can. The search takes its
  candidates from a `HandOracle`, which is either `PerfectInfoOracle` (identical to the Python,
  used by the Simulator so the numbers stay comparable to `results/`) or `BeliefOracle` (only
  what the beliefs allow, used everywhere a person is involved). Settings → *Fair play*
  chooses which one the agents in a game use.

Also inherited on purpose, so simulator numbers keep matching: the `current_round < 2` trump
penalty, ties going to the weakest card, and `SelfBeliefPolicy.LEGACY`, which reproduces the
quirk that a player's own belief row keeps describing the hand it was *dealt*. The advisor uses
`TRACK_OWN_PLAYS` instead, because there correctness beats fidelity.

## The engine, and the bars

Two numbers are on screen at once, and they are not the same thing:

- **The recommendation** comes from whichever agent you picked. The Greedy Player recommends
  its highest card because that is what the Greedy Player is.
- **The bars** always come from the expected trick search: for every legal card, walk the
  cartesian product of what the players still to act could answer with, weigh each combination
  by how likely they are to hold those cards, and add up the trick points signed by whoever
  would take it. Green to the right means the points land on your side.

Because the yardstick is shared, "the Greedy Player is about to hand over 12 points" is visible
at a glance, which is most of the point of a comparison.

The search is the same maths as `PredictorPlayer.choose_card`, with the sum normalised by the
total probability mass so the number means *expected trick points* rather than an unnormalised
weight. Normalising divides every card by the same constant, so the ranking is untouched.

Blind positions (the advisor's, where an opponent could hold any of thirty cards) are kept
affordable by `EngineConfig.maxBranch` and `searchBudget`, which trim each opponent's candidate
list to a representative sample: the strongest and weakest card of each suit, then whatever
carries points. With perfect information a hand is at most ten cards, nothing is trimmed, and
the search is bit for bit the Python one.

## The camera

The pipeline is split in two, because the two halves of a card's index corner are different
problems:

1. **The rank is a glyph.** ML Kit's bundled Latin text recogniser finds candidates; the Sueca
   deck has no 8, 9 or 10, so `RankReader` throws those away instead of guessing, and accepts
   Portuguese decks that print R/D/V for Rei/Dama/Valete.
2. **The suit is a silhouette**, which no text model reads. The strip below each rank is
   thresholded against the card's own background, the pip comes out as a connected component,
   and it is matched against masks rendered from *the same vector outlines the app draws its
   cards with* — see `shapes/SuitShapes.kt`, which both the renderer and the classifier read.
   The templates are rendered at several sizes, because a pip filling 14 pixels comes out
   visibly fatter than the same outline filling 60.

Matching combines shape overlap (Jaccard, after normalising into a 32x32 box) with a small
feature vector — fill ratio, ink runs crossed by scanlines at five heights, width profile,
centroid. The runs are the giveaway: a heart is the only suit crossed twice near the top, a
club the only one crossed twice across the middle. Both halves are measured against the
templates rather than against hardcoded constants, so changing an outline keeps the classifier
honest by itself.

No frame is trusted on its own: `ScanAccumulator` only offers a card after several consecutive
sightings, and forgets it when the camera moves away.

All of that is pure Kotlin in `:engine`, so it is unit tested against synthetic frames rather
than only on a device.

## Notes and limitations

- The APK is large (~45 MB release) because the ML Kit text model is **bundled**, which is what
  makes scanning work offline. Swapping `com.google.mlkit:text-recognition` for
  `com.google.android.gms:play-services-mlkit-text-recognition` moves the model into Play
  Services and takes most of that back, at the cost of a first run download.
- The scanner expects a French suited 40 card deck (the usual Sueca deck) with printed index
  corners. Spanish suited decks are not recognised.
- Card recognition is a helper, not an oracle. Every screen that accepts scanned cards also
  accepts them tapped in by hand, and anything the scanner gets wrong can be thrown out with a
  tap.
- The app is portrait only and ships one theme. A card table is green.
