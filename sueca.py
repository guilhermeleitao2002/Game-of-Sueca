############################################# Libraries #############################################

from argparse import ArgumentParser, Namespace
from json import dumps
from os import makedirs
from os.path import dirname, join

from matplotlib.pyplot import close, subplots, xticks
from termcolor import colored

from Game import HUMAN_PLAYER, STRATEGIES, Game

RESULTS_DIR = 'results'


########################################## Helper Functions ##########################################

def parse_arguments() -> Namespace:
    '''
        Parses the command line arguments
    '''

    parser = ArgumentParser(description='Sueca game simulator')
    strategies = ', '.join(colored(strategy, 'green', attrs=['bold']) for strategy in STRATEGIES)
    modes = f'{colored("auto", "green", attrs=["bold"])} (machine vs machine) or ' \
            f'{colored("human", "green", attrs=["bold"])} (machine vs user)'

    parser.add_argument('-o', '--output', type=str, required=True, help='Output file to save the game log')
    parser.add_argument('-s', '--sporting', type=str, required=True, choices=list(STRATEGIES), metavar='STRATEGY',
                        help=f'Strategy for team Sporting: {strategies}')
    parser.add_argument('-b', '--benfica', type=str, required=True, choices=list(STRATEGIES), metavar='STRATEGY',
                        help=f'Strategy for team Benfica: {strategies}')
    parser.add_argument('-n', '--num_games', type=int, default=1, help='Number of games to simulate')
    parser.add_argument('-v', '--verbose', action='store_true', default=False,
                        help='Print the game information as it unfolds')
    parser.add_argument('-m', '--mode', type=str, default='auto', choices=['auto', 'human'], metavar='MODE',
                        help=f'Mode of the game: {modes}')

    args = parser.parse_args()

    if args.num_games < 1:
        parser.error('the number of games must be at least 1')

    return args


def plot_results(summary: dict[str, float], sporting_strategy: str, benfica_strategy: str) -> None:
    '''
        Plots the results of the games in a bar plot
    '''

    # Data to bar plot, dropping whichever outcome never happened
    bars = [(f'Benfica ({benfica_strategy})', summary['Benfica'], 'red'),
            (f'Sporting ({sporting_strategy})', summary['Sporting'], 'green'),
            ('ties', summary['ties'], 'grey')]
    bars = [bar for bar in bars if bar[1] > 0]
    if not bars:
        return

    labels, wins, colours = zip(*bars)

    # Plot
    figure, ax = subplots()
    ax.bar(labels, wins, color=colours)
    ax.set_ylabel('Wins')
    ax.set_title('Game Results')
    xticks(rotation=15)

    # Save the plot
    makedirs(RESULTS_DIR, exist_ok=True)
    figure.savefig(join(RESULTS_DIR, f'{sporting_strategy}_{benfica_strategy}.png'))
    close(figure)


def summarize(wins: dict[str, int], points: dict[str, int], dealt: dict[str, int], num_games: int) -> dict[str, float]:
    '''
        Turn the running totals into the figures reported at the end of the run.

        converted_points is the difference between what a team captured and what it was
        dealt, so it measures the strategy rather than the luck of the hand.
    '''

    return {
        'Sporting': wins['Sporting'],
        'Benfica': wins['Benfica'],
        'ties': wins['ties'],
        'average_points_per_game_sporting': points['Sporting'] / num_games,
        'average_points_per_game_benfica': points['Benfica'] / num_games,
        'converted_points_sporting': (points['Sporting'] - dealt['Sporting']) / num_games,
        'converted_points_benfica': (points['Benfica'] - dealt['Benfica']) / num_games,
    }


def play_games(args: Namespace, log) -> dict[str, float]:
    '''
        Play every game, streaming each one into the already open log file
    '''

    wins = {'Sporting': 0, 'Benfica': 0, 'ties': 0}
    points = {'Sporting': 0, 'Benfica': 0}
    dealt = {'Sporting': 0, 'Benfica': 0}
    games_played = 0

    try:
        for i in range(args.num_games):
            if args.verbose:
                print(colored(f'\nGAME {i + 1}', 'green', attrs=['bold', 'underline']))

            # Initialize the game
            game = Game(args.sporting, args.benfica, args.verbose, args.mode)

            # If the game is in human mode, print the player's partner
            if args.mode == 'human':
                print(f'\nYour partner is {colored(game.get_partner(HUMAN_PLAYER).name, "light_yellow")}')

            # Distribute the cards
            game.hand_cards()

            # Play the game
            wins[game.play_game()] += 1
            for team in game.teams:
                points[team.name] += team.score
                dealt[team.name] += team.initial_points

            game.game_info['Game'] = i + 1
            log.write((',\n' if games_played else '') + dumps(game.game_info, indent=4, sort_keys=True))
            games_played += 1
    finally:
        # Close the JSON array even if the run is interrupted, so the log stays readable
        log.write('\n]\n')

    return summarize(wins, points, dealt, games_played or 1)


########################################## Main Program #############################################

if __name__ == '__main__':
    try:
        args = parse_arguments()

        # Open and clean the output file
        makedirs(dirname(args.output) or '.', exist_ok=True)
        with open(args.output, 'w') as log:
            log.write('[\n')
            summary = play_games(args, log)

        print(colored(f'\nWins: {summary}', 'magenta', attrs=['bold']))

        if args.mode == 'auto':
            plot_results(summary, args.sporting, args.benfica)

    except KeyboardInterrupt:
        print(colored('\nGoodbye!', 'blue'))
        exit(0)
