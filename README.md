# Sueca Game Simulator

This Python script simulates games of Sueca, a popular card game. The simulator allows you to run games between two teams with different strategic approaches and modes of play. You can customize the number of games, strategies, and operation modes, and record the results in a specified output file.

## Installation

Ensure you have Python 3.10 or newer installed on your system. You will also need the following Python packages:
- `numpy`
- `matplotlib`
- `termcolor`

You can install the required packages using pip:

```bash
pip3 install numpy matplotlib termcolor
```

## Usage

Run the script from the command line by navigating to the script's directory and typing:

```bash
python3 sueca.py [options]
```

### Options

You must specify the following command line arguments when running the script:

 - `-o` or `--output`: Path to the output file to save the game log;
 - `-s` or `--sporting`: Strategy for team Sporting. Options include:
    - `random`: Random strategy;
    - `greedy`: Greedy strategy;
    - `maxpointswon`: Strategy that maximizes the points won per round;
    - `maxroundswon`: Strategy that maximizes the number of rounds won;
    - `cooperative`: Strategy that predicts the cards of the teammate;
    - `predictor`: Strategy that predicts the cards of the other team as well.
 - `-b` or `--benfica`: Strategy for team Benfica. Options are the same as for team Sporting.
 - `-n` or `--num_games`: Number of games to simulate (default is 1).
 - `-v` or `--verbose`: Print the game log to the console.
 - `-m` or `--mode`: Operation mode. Options include:
    - `auto`: Run the simulation without user interaction;
    - `human`: Allow the user to interact with the game.

### Example

To run a simulation with 100 games between two teams using the greedy strategy and save the results to a file named `output.json`, you would use the following command:

```bash
python sueca.py -o output.json -s greedy -b greedy -n 100
```

To run the simulations described in `paper/report.pdf`, you can simply run the following script:

```bash
./run_simulation.sh
```

It plays every pairing of strategies for 10000 games each, writing a `.json` log, a `.txt`
summary and a `.png` bar plot per pairing into `results/`. Set `NUM_GAMES` to use a smaller
number, for example `NUM_GAMES=100 ./run_simulation.sh`.

#### Note

The content present in the `results/` directory is the exact output behind the tables in `paper/report.pdf`. Re-running the script overwrites it, and since the simulations are not seeded the numbers will differ slightly from the ones reported in the paper.

## Android app

`android/` holds a Jetpack Compose app built on a Kotlin port of this simulator: a Sueca engine
that recommends a card the way a chess engine recommends a move, with any of the six agents
driving the recommendation (the Deck Predictor by default), and a camera that reads real cards
so it can advise on a game being played with a physical deck.

```bash
cd android
./gradlew :engine:test        # rules, agents and vision maths on the JVM, no SDK needed
./gradlew :app:installDebug   # build and install onto a connected device
```

See [android/README.md](android/README.md) for the toolchain requirements, the mapping between
the Python modules and their Kotlin counterparts, and how card recognition works.