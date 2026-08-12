#!/usr/bin/env bash
# Runs every strategy against every other one, as described in paper.pdf.
# Override the number of games per pairing with NUM_GAMES=100 ./run_simulation.sh
set -euo pipefail

STRATEGIES=(random greedy maxpointswon maxroundswon cooperative predictor)
NUM_GAMES=${NUM_GAMES:-10000}
RESULTS_DIR=results

mkdir -p "$RESULTS_DIR"

for ((i = 0; i < ${#STRATEGIES[@]}; i++)); do
    for ((j = i + 1; j < ${#STRATEGIES[@]}; j++)); do
        sporting=${STRATEGIES[i]}
        benfica=${STRATEGIES[j]}
        echo "Running $sporting vs $benfica ($NUM_GAMES games)"
        python3 sueca.py -o "$RESULTS_DIR/${sporting}_${benfica}.json" \
                         -s "$sporting" -b "$benfica" -n "$NUM_GAMES" \
                         > "$RESULTS_DIR/${sporting}_${benfica}.txt"
    done
done
