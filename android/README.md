# Sueca Engine for Android

The simulator in this repository, turned into something you can hold: a Sueca engine that tells
you which card to play, the way a chess engine tells you which move to make, plus a camera that
reads real cards off the table.

Everything runs on the phone. No account, no network, no server.

**[Download the latest APK](https://github.com/guilhermeleitao2002/Game-of-Sueca-Engine/releases/latest)**
— Android 7.0 or newer. You will have to allow installing from outside the Play Store.

## What it does

| Screen | What it is for |
| --- | --- |
| **Table advisor** | You are playing with real cards. Scan or tap in your hand, record what everybody plays, and the engine tells you what to play next and why. |
| **Play a game** | A full game against three agents, with the engine advising you as you go. |
| **Scan cards** | The camera pipeline on its own: point it at cards and watch rank and suit come back. |
| **Train your deck** | Show the camera a few of your own cards so it learns how *your* deck prints its pips and index. |
| **Simulator** | Agent against agent for N games, reporting the same figures as `results/`. |
| **The agents** | What each of the six strategies actually does. |

The advising agent defaults to the **Deck Predictor** and can be changed to any of the six, at
any point, including mid game.

## Requirements

- **Android Studio** (Ladybug or newer) or a standalone Android SDK with platform 35 and
  build tools 35.
- **JDK 17 or 21.** Not newer. Gradle 8.9 and AGP 8.7 both refuse anything above 21, and the
  way they refuse is unhelpful:

  ```
  FAILURE: Build failed with an exception.
  * What went wrong:
  25.0.3
  ```

  That bare version number means `JAVA_HOME` (or the default `java`) is a JDK Gradle cannot
  run on. Point it at a 17 or a 21:

  ```bash
  sudo apt install openjdk-21-jdk                      # or download Temurin 21
  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64  # add to ~/.zshrc or ~/.bashrc
  export PATH="$JAVA_HOME/bin:$PATH"
  java -version                                        # must print 21.x or 17.x
  ```

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

# Minified release APK -> app/build/outputs/apk/release/
./gradlew :app:assembleRelease
```

### Signing a release

`assembleRelease` produces `app-release-unsigned.apk` until it is given a key, and an unsigned
APK cannot be installed. Make one once:

```bash
keytool -genkeypair -v -keystore sueca-release.jks -alias sueca \
        -keyalg RSA -keysize 2048 -validity 10000
cp keystore.properties.example keystore.properties     # then fill in the passwords
```

Both the keystore and `keystore.properties` are gitignored, and the build falls back to an
unsigned APK when they are absent, so a fresh clone still compiles. Signing details can also
come from `SUECA_KEYSTORE`, `SUECA_KEYSTORE_PASSWORD`, `SUECA_KEY_ALIAS` and `SUECA_KEY_PASSWORD`
in the environment, which is what CI would use.

Keep the keystore. An update signed with a different key cannot replace an installed app.

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

### Installing over Wi-Fi

Android 11 and newer can take an install with no cable at all, which is also the only practical
route from WSL, where USB devices are not visible to Linux.

On the phone: *Settings → About phone*, tap **Build number** seven times, then
*Developer options → Wireless debugging → on → Pair device with pairing code*. It shows an
address and a six digit code.

On the computer, on the same Wi-Fi:

```bash
adb pair 192.168.1.42:37129     # the address from the *pairing* dialog, then type the code
adb connect 192.168.1.42:41253  # the address from the Wireless debugging *main* screen
adb devices                     # should list the phone as "device"
./gradlew :app:installDebug
```

The two ports are different, and both change when the phone reboots or wireless debugging is
toggled — `adb connect` again when that happens. Pairing by QR code and `adb mdns` discovery do
not work from WSL, since its NAT hides mDNS; the explicit `adb pair <ip>:<port>` above does.

## Layout

```
android/
  engine/     pure Kotlin, no Android at all: rules, agents, beliefs, search,
              suit outlines and the classifier core. 78 unit tests.
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
sightings, and forgets it when the camera moves away. The analysis stream runs at 720p rather
than CameraX's 480p default, because at 480p a corner pip lands on about a dozen pixels, and
the pip is looked for at three different depths below the rank, since decks disagree about how
much white sits in between.

All of that is pure Kotlin in `:engine`, so it is unit tested against synthetic frames rather
than only on a device.

### Training a deck

Recognition that works on one deck and not another is not a bug to be tuned away: decks really
do print different pips, in different inks, with different fonts on the index. So the app can be
shown a deck instead of guessing at it.

*Home → Train your deck*, or the mortarboard in the scanner's toolbar. Make a deck, hold a card
up, tap which card it is. That single tap files two things:

- the **pip**, normalised into the template box, under the suit you tapped, and
- whatever the text recogniser made of the **index**, under the rank you tapped — which is how
  "this deck's queen reads as O" stops being a failure and becomes a rule.

Ink colour is learned along the way, so a deck whose red is closer to brown ends up with its own
boundary between red and black rather than the shipped one.

One card per suit already helps; the four suits between them are what `DeckProfile.isUsable`
means. Up to eight examples per suit are kept, and when that fills up the new one replaces
whichever stored example it most duplicates, so the set drifts towards covering the deck's
variation rather than one lucky angle. The live reading on the training screen uses the profile
as it grows, which is the quickest way to see whether it is working.

Profiles live in `filesDir/deck-profiles/*.deck` as readable text, and *Settings → Card scanner*
picks which one is active (or none, for the built-in shapes).

**Why this and not reinforcement learning.** A correction carries the answer, not a reward, and
there is no sequence of actions to assign credit across — so the problem is supervised, and the
cheapest supervised method that works from four examples is nearest neighbour over prototypes.
It needs no gradients, no training loop and no hyperparameters, it runs in microseconds on the
phone, and one confirmed card changes behaviour immediately. An RL formulation would have to
manufacture a reward out of the label it already has, and then need orders of magnitude more
examples to recover what the label told it directly.

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
