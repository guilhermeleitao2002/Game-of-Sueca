from __future__ import annotations

from random import choice, randint, shuffle
from time import sleep
from typing import Any, NamedTuple

from termcolor import colored

from Card import Card, new_deck
from Player import (BeliefPlayer, CooperativePlayer, GreedyPlayer, MaximizePointsPlayer,
                    MaximizeRoundsWonPlayer, Player, PredictorPlayer, RandomPlayer)
from Team import Team

# Command line strategy name -> the class that implements it.
# Adding a strategy means adding its class here and nothing else.
STRATEGIES = {
    'random': RandomPlayer,
    'greedy': GreedyPlayer,
    'maxpointswon': MaximizePointsPlayer,
    'maxroundswon': MaximizeRoundsWonPlayer,
    'cooperative': CooperativePlayer,
    'predictor': PredictorPlayer,
}

# Player ids are 1 and 2 for the first team and 3 and 4 for the second, and are used to
# index the belief arrays, so the order of this table matters.
TEAMS = (('Sporting', ('Leitao', 'Fred')), ('Benfica', ('Pedro', 'Sebas')))

# The player a person controls in human mode
HUMAN_PLAYER = 'Leitao'

CARDS_PER_HAND = 10
ROUNDS_PER_GAME = 10


class RoundResult(NamedTuple):
    '''
        points: total value of the cards on the table
        card: the card winning the round so far
        winner: index of that card in the list of cards played
    '''

    points: int
    card: Card
    winner: int


class Game:
    '''
        Game ->
            - teams: list of Team objects
            - playersOrder: list of Player objects sorted by order to play
            - deck: list of Card objects
            - trump: trump card for that game
            - current_round: index of the round being played (0 - 9)
            - game_info: dictionary with game information
            - verbose: boolean to print game details
            - mode: string with the mode of the game (auto or human)
    '''

    def __init__(self, team_1_strategy: str, team_2_strategy: str, v: bool, mode: str) -> None:
        self.verbose = v
        self.mode = mode

        self.trump = None
        self.current_round = 0

        # Initialize game information
        self.game_info = {'Teams': [], 'Rounds': {}}

        # Create teams and their players
        self.teams = [self.create_team(name, names, strategy)
                      for (name, names), strategy in zip(TEAMS, (team_1_strategy, team_2_strategy))]

        # Randomize players and team to start
        for team in self.teams:
            shuffle(team.players)
        first_team = choice(self.teams)
        second_team = self.teams[1] if first_team is self.teams[0] else self.teams[0]

        # Order players, alternating between teams
        self.playersOrder = [player for pair in zip(first_team.players, second_team.players) for player in pair]

        # Create deck
        self.deck = self.create_deck()

    def create_team(self, team_name: str, player_names: tuple[str, ...], strategy: str) -> Team:
        '''
            Create a team whose players all follow the given strategy
        '''

        if strategy not in STRATEGIES:
            raise ValueError(f'Invalid strategy: {strategy}')

        player_class = STRATEGIES[strategy]
        team = Team(team_name)

        # Ids run 1, 2 for the first team and 3, 4 for the second
        first_id = 1 + 2 * [name for name, _ in TEAMS].index(team_name)
        for offset, name in enumerate(player_names):
            player = player_class(first_id + offset, name, team, self.verbose)
            player.is_human = self.mode == 'human' and name == HUMAN_PLAYER
            team.add_player(player)

        return team

    def get_partner(self, player_name: str) -> Player | None:
        '''
            Get the partner of the player with the given name
        '''

        for team in self.teams:
            for player in team.players:
                if player.name == player_name:
                    return team.get_partner(player)

        return None

    def create_deck(self) -> list[Card]:
        '''
            Create a deck of 40 cards (see Card.py for the ranks, orders and values)
        '''

        return new_deck()

    def rotate_order_to_winner(self, playersOrderList: list[Player], winner: Player) -> list[Player]:
        '''
            Rotate the list of players to the winner of the round
        '''

        winner_index = playersOrderList.index(winner)

        return playersOrderList[winner_index:] + playersOrderList[:winner_index]

    def evaluate_round(self, cardsPlayedInRound: list[Card]) -> RoundResult:
        '''
            Calculate the points on the table and which of the cards is winning them.

            Also used by the strategies to score rounds that are still incomplete, and
            hypothetical ones.
        '''

        winner = 0
        for i, card in enumerate(cardsPlayedInRound[1:], start=1):
            if card.beats(cardsPlayedInRound[winner], self.trump.suit):
                winner = i

        points = sum(card.value for card in cardsPlayedInRound)

        return RoundResult(points, cardsPlayedInRound[winner], winner)

    def hand_cards(self) -> None:
        '''
            Distribute the cards between the players. The last card dealt is the trump
        '''

        card = None
        for player in self.playersOrder:
            for _ in range(CARDS_PER_HAND):
                # Pop a card at random
                card = self.deck.pop(randint(0, len(self.deck) - 1))
                player.add_card(card)
                player.team.initial_points += card.value

                # Update beliefs of the player
                if isinstance(player, BeliefPlayer):
                    player.update_beliefs_initial(card)

        self.trump = card

        # Print game details
        if self.verbose and self.mode == 'auto':
            for player in self.playersOrder:
                print(colored(f'{player.name} -> {player.team.name}', 'yellow', attrs=['underline']))
                print(colored(player.get_strategy(), attrs=['bold']))
                for card in player.hand:
                    print(card.name)
                print('\n')

        if self.verbose or self.mode == 'human':
            print(colored(f'Trump card: {self.trump.name}\n', 'blue', attrs=['bold']))

    def update_beliefs(self, cardPlayed: Card, round_suit: str, player: Player) -> None:
        '''
            Update the beliefs of the players except the one that played the card (no need!)
        '''

        for other in self.playersOrder:
            if other is not player and isinstance(other, BeliefPlayer):
                other.update_beliefs(cardPlayed, round_suit, player, self.mode)

    def ask_human_card(self, player: Player, position: int, round_suit: str, suggestion: Card) -> Card:
        '''
            Show the engine's suggestion and let the person pick a legal card instead
        '''

        # The suggestion was only a suggestion: put it back before asking
        player.add_card(suggestion)

        # Following suit is mandatory whenever the player can
        legal_cards = player.get_cards_by_suit(round_suit) if position > 0 else []
        if not legal_cards:
            legal_cards = list(player.hand)

        print(colored('Your current hand:', 'yellow'))
        for card in player.hand:
            print(colored(card.name, attrs=['bold']))
        print('\nThe engine suggests you play', end=' ')
        print(colored(suggestion.name, 'blue', attrs=['bold']))

        while True:
            card = player.get_card(input(colored('> ', attrs=['bold'])))
            if card is None:
                print(colored('Invalid card! Try again', 'red'))
            elif card not in legal_cards:
                print(colored(f'You have to follow {round_suit}! Try again', 'red'))
            else:
                break

        player.hand.remove(card)
        print(colored(f'You played {card.name}', 'green', attrs=['bold']))

        return card

    def play_round(self, num_round: int) -> dict[str, Any]:
        '''
            Play a round of the game
        '''

        self.current_round = num_round
        roundSuit = ''
        cardsPlayedInRound = []

        for position, player in enumerate(self.playersOrder):
            card_played, roundSuit = player.play_round(position, roundSuit, cardsPlayedInRound, self)

            # In human mode the engine only advises, the person decides
            if player.is_human:
                card_played = self.ask_human_card(player, position, roundSuit, card_played)
                if position == 0:
                    roundSuit = card_played.suit

            cardsPlayedInRound.append(card_played)

            # Update the beliefs of the players
            self.update_beliefs(card_played, roundSuit, player)

            # Give the person time to follow what happened
            if self.mode == 'human':
                sleep(2)

        # Get the total points played in the round and the respective winner
        result = self.evaluate_round(cardsPlayedInRound)
        winner = self.playersOrder[result.winner]
        winner.team.score += result.points

        if self.verbose or self.mode == 'human':
            print(colored('You win the round' if winner.is_human else f'{winner.name} wins the round',
                          'blue', attrs=['bold']))

        # Rotate the players order to the winner of the round
        self.playersOrder = self.rotate_order_to_winner(self.playersOrder, winner)

        return {'Winner': winner.name, 'Points': result.points}

    def play_game(self) -> str:
        '''
            Play the game of Sueca and return the name of the winning team, or "ties"
        '''

        for num_round in range(ROUNDS_PER_GAME):
            if self.verbose or self.mode == 'human':
                print(colored(f'\nRound {num_round + 1}:', 'green', attrs=['underline']))

            self.game_info['Rounds'][num_round + 1] = self.play_round(num_round)

        sporting, benfica = self.teams
        self.game_info['Teams'] = [sporting.dump_to_json(), benfica.dump_to_json()]

        if self.verbose or self.mode == 'human':
            print(colored(f'\n{sporting.name} score: {sporting.score}', 'green'))
            print(colored(f'{benfica.name} score: {benfica.score}', 'red'))
            print()

            if sporting.score > benfica.score:
                print(colored(f'{sporting.name} wins', 'white', 'on_green'))
            elif sporting.score < benfica.score:
                print(colored(f'{benfica.name} wins!', 'white', 'on_red'))
            else:
                print(colored("It's a tie!", 'white', 'on_dark_grey'))

        if sporting.score > benfica.score:
            return sporting.name
        if sporting.score < benfica.score:
            return benfica.name

        return 'ties'
