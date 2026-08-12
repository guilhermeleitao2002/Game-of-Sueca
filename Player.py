from __future__ import annotations

from itertools import product
from random import randint
from typing import TYPE_CHECKING

import numpy as np
from termcolor import colored

from Card import ORDER_VALUES, SUIT_INDEX, SUITS, Card

if TYPE_CHECKING:
    from Game import Game
    from Team import Team

############################################# Player General Classes #############################################


class Player:
    '''
        Player ->
            - name: player name
            - id: id of the player (1 - 4), also its index in the belief arrays
            - hand: list of Card objects the player has (initially 10), kept sorted by order
            - team: team object to which the player belongs
            - verbose: print the player actions
            - is_human: whether a person picks this player's cards (human mode only)
    '''

    def __init__(self, id: int, name: str, team: Team, v: bool) -> None:
        self.verbose = v
        self.id = id
        self.name = name
        self.hand = []
        self.team = team
        self.is_human = False

    def add_card(self, card: Card) -> None:
        '''
            Add card to player hand, sorted by order so that hand[0] is always the
            weakest card and hand[-1] the strongest. Every strategy relies on this.
        '''

        self.hand.append(card)
        self.hand.sort(key=lambda card: card.order)

    def get_cards_by_suit(self, suit: str) -> list[Card]:
        '''
            Get all cards of a given suit, weakest first
        '''

        return [card for card in self.hand if card.suit == suit]

    def get_partner(self) -> Player:
        '''
            Get the partner of the player
        '''

        return self.team.get_partner(self)

    def get_card(self, card_name: str) -> Card | None:
        '''
            Get the card object from the player's hand
        '''

        for card in self.hand:
            if card.name == card_name:
                return card

        return None

    def play_round(self, position: int, round_suit: str, cards_played: list[Card], game: Game) -> tuple[Card, str]:
        '''
            Play one card, whichever the strategy picks.

            Common to every strategy: the card leaves the hand, the first player of the
            round fixes its suit and the play is announced. Subclasses only decide *which*
            card to play, in choose_card.
        '''

        card = self.choose_card(position, round_suit, cards_played, game)
        self.hand.remove(card)

        if position == 0:
            round_suit = card.suit

        self.announce(card, game.mode)

        return card, round_suit

    def choose_card(self, position: int, round_suit: str, cards_played: list[Card], game: Game) -> Card:
        '''
            Pick the card to play from the hand. Implemented by each strategy
        '''

        raise NotImplementedError

    def get_strategy(self) -> str:
        '''
            Return the name of the strategy of the player
        '''

        raise NotImplementedError

    def announce(self, card: Card, mode: str) -> None:
        '''
            Print the card played, unless it is only the engine's suggestion to a human
        '''

        if self.is_human:
            return

        if self.verbose or mode == 'human':
            print(colored(f'{self.name} played {card.name}', 'green', attrs=['bold']))


class BeliefPlayer(Player):
    '''
        BeliefPlayer ->
            - beliefs: probability that a player still holds a card, as a
                       [player, suit, order] array. Cards this player was dealt sit at 1,
                       cards already seen at 0, the rest are split evenly between the
                       players that could still hold them.
    '''

    def __init__(self, id: int, name: str, team: Team, v: bool) -> None:
        super().__init__(id, name, team, v)

        # Any of the other three players may hold any card we have not seen
        self.beliefs = np.ones((4, 4, 10)) / 3
        # We know exactly what we hold, so our own row is filled in by update_beliefs_initial
        self.beliefs[self.id - 1] = 0

    def update_beliefs_initial(self, card: Card) -> None:
        '''
            Update the beliefs of the player after the initial handing of cards
        '''

        self.beliefs[:, SUIT_INDEX[card.suit], card.order] = 0
        self.beliefs[self.id - 1, SUIT_INDEX[card.suit], card.order] = 1

    def update_beliefs(self, card: Card, round_suit: str, player: Player, mode: str) -> None:
        '''
            Updates the new belief of the player, after a card has been spotted
        '''

        if self.verbose and mode == 'auto':
            print(f'Player {self.name} saw {card.name}')

        # Once a card is on the table nobody holds it anymore
        self.beliefs[:, SUIT_INDEX[card.suit], card.order] = 0

        # Not following suit proves the player has no card of the round suit left
        if card.suit != round_suit:
            self.beliefs[player.id - 1, SUIT_INDEX[round_suit], :] = 0

        # Split each remaining card evenly between the players that may still hold it
        holders = np.count_nonzero(self.beliefs, axis=0)
        share = np.divide(1.0, holders, out=np.zeros(holders.shape), where=holders > 0)
        self.beliefs = np.where(self.beliefs > 0, share, 0.0)


############################################# Player Sub Classes #############################################


class RandomPlayer(Player):
    '''
        Plays a legal card picked at random
    '''

    def choose_card(self, position: int, round_suit: str, cards_played: list[Card], game: Game) -> Card:
        if position == 0:
            return self.hand[randint(0, len(self.hand) - 1)]

        cards_of_the_same_suit = self.get_cards_by_suit(round_suit)
        if cards_of_the_same_suit:
            return cards_of_the_same_suit[randint(0, len(cards_of_the_same_suit) - 1)]

        return self.hand[randint(0, len(self.hand) - 1)]

    def get_strategy(self) -> str:
        return 'Random Agent'


class GreedyPlayer(Player):
    '''
        Always plays its highest ranked legal card
    '''

    def choose_card(self, position: int, round_suit: str, cards_played: list[Card], game: Game) -> Card:
        if position == 0:
            return self.hand[-1]

        cards_of_the_same_suit = self.get_cards_by_suit(round_suit)

        return cards_of_the_same_suit[-1] if cards_of_the_same_suit else self.hand[-1]

    def get_strategy(self) -> str:
        return 'Greedy Player'


class MaximizePointsPlayer(Player):
    '''
        Plays to capture as many points as possible in the current round
    '''

    def choose_card(self, position: int, round_suit: str, cards_played: list[Card], game: Game) -> Card:
        if position == 0:
            return self.hand[-1]

        cards_of_the_same_suit = self.get_cards_by_suit(round_suit)
        so_far = game.evaluate_round(cards_played)

        if game.playersOrder[so_far.winner].team is self.team:      # our side is winning it
            # play the strongest card we can, to pile points onto the round
            return cards_of_the_same_suit[-1] if cards_of_the_same_suit else self.hand[-1]

        if cards_of_the_same_suit:
            strongest = cards_of_the_same_suit[-1]
            if strongest.beats(so_far.card, game.trump.suit):
                return strongest

            return cards_of_the_same_suit[0]                        # cannot win, so give away as little as possible

        trump_cards = self.get_cards_by_suit(game.trump.suit)

        return trump_cards[-1] if trump_cards else self.hand[0]

    def get_strategy(self) -> str:
        return 'Maximize Points Won'


class MaximizeRoundsWonPlayer(Player):
    '''
        Plays to win as many rounds as possible, spending as little as it can on each
    '''

    def choose_card(self, position: int, round_suit: str, cards_played: list[Card], game: Game) -> Card:
        if position == 0:
            return self.hand[-1]

        cards_of_the_same_suit = self.get_cards_by_suit(round_suit)
        so_far = game.evaluate_round(cards_played)

        if game.playersOrder[so_far.winner].team is self.team:      # our side is winning it
            # preserve every strong card
            return cards_of_the_same_suit[0] if cards_of_the_same_suit else self.hand[0]

        if cards_of_the_same_suit:
            for card in cards_of_the_same_suit:                     # cheapest card that actually takes the round
                if card.beats(so_far.card, game.trump.suit):
                    return card

            return cards_of_the_same_suit[0]

        trump_cards = self.get_cards_by_suit(game.trump.suit)

        return trump_cards[0] if trump_cards else self.hand[0]

    def get_strategy(self) -> str:
        return 'Maximize Rounds Won'


class CooperativePlayer(BeliefPlayer):
    '''
        Plays as a team player, using what it believes its partner still holds
    '''

    def choose_card(self, position: int, round_suit: str, cards_played: list[Card], game: Game) -> Card:
        partner_belief = self.beliefs[self.get_partner().id - 1]
        own_belief = self.beliefs[self.id - 1]

        # Points we expect each side of the partnership to be holding, per suit and order
        card_points = np.array(ORDER_VALUES)
        partner_points = partner_belief * card_points
        player_points = own_belief * card_points

        if position == 0:
            return self.lead(partner_belief, partner_points + player_points, game)

        if position == 1:
            return self.play_second(round_suit, partner_belief, player_points, partner_points, cards_played, game)

        return self.play_late(round_suit, cards_played, game)

    def lead(self, partner_belief: np.ndarray, possible_points: np.ndarray, game: Game) -> Card:
        '''
            Open the round, ideally with a suit our partner can profit from
        '''

        trump_index = SUIT_INDEX[game.trump.suit]

        # A suit our partner is void in, while still holding trumps, is a suit they can cut
        for suit in SUITS:
            cards_of_the_same_suit = self.get_cards_by_suit(suit)
            if cards_of_the_same_suit and not partner_belief[SUIT_INDEX[suit]].any() \
                    and partner_belief[trump_index].any():
                return cards_of_the_same_suit[-1]

        # Otherwise lead the suit in which the partnership holds the most points
        for suit in SUITS:
            if not self.get_cards_by_suit(suit):
                possible_points[SUIT_INDEX[suit]] = 0

        if possible_points.any():
            best_suit = SUITS[int(np.argmax(possible_points.sum(axis=1)))]
            return self.get_cards_by_suit(best_suit)[-1]

        # TODO: Make the player save the trumps in case he has no more points
        return self.hand[-1]

    def play_second(self, round_suit: str, partner_belief: np.ndarray, player_points: np.ndarray,
                    partner_points: np.ndarray, cards_played: list[Card], game: Game) -> Card:
        '''
            Play right after the opening, with two opponents still to come
        '''

        cards_of_the_same_suit = self.get_cards_by_suit(round_suit)
        suit_index = SUIT_INDEX[round_suit]
        partner_can_cut = partner_belief[SUIT_INDEX[game.trump.suit]].any() and not partner_belief[suit_index].any()

        if partner_can_cut and cards_of_the_same_suit:
            # Our partner takes the round whatever we do, so feed them our best card
            return cards_of_the_same_suit[-1]

        if not cards_of_the_same_suit:
            trump_cards = self.get_cards_by_suit(game.trump.suit)
            if trump_cards:
                return trump_cards[0]

            # If our partner is cutting, discard the card worth the most to them
            return self.hand[-1] if partner_can_cut else self.hand[0]

        so_far = game.evaluate_round(cards_played)
        if so_far.card.suit == round_suit:
            # Only worth fighting for if the round has not been cut already
            for order in range(so_far.card.order + 1, 10):
                if player_points[suit_index][order] > 0 or partner_points[suit_index][order] > 0:
                    return cards_of_the_same_suit[-1]

        return cards_of_the_same_suit[0]

    def play_late(self, round_suit: str, cards_played: list[Card], game: Game) -> Card:
        '''
            Play third or fourth, when the round is nearly decided
        '''

        cards_of_the_same_suit = self.get_cards_by_suit(round_suit)
        so_far = game.evaluate_round(cards_played)

        if game.playersOrder[so_far.winner] is self.get_partner():
            return cards_of_the_same_suit[-1] if cards_of_the_same_suit else self.hand[-1]

        if cards_of_the_same_suit:
            strongest = cards_of_the_same_suit[-1]
            if strongest.beats(so_far.card, game.trump.suit):
                return strongest

            return cards_of_the_same_suit[0]

        trump_cards = self.get_cards_by_suit(game.trump.suit)

        return trump_cards[-1] if trump_cards else self.hand[0]

    def get_strategy(self) -> str:
        return 'Cooperative Player'


class PredictorPlayer(BeliefPlayer):
    '''
        Plays the card with the best expected round points, weighing every card the
        players yet to play might answer with by how likely they are to hold it
    '''

    # Utility penalty used to push a card to the bottom of the ranking
    AVOID = 1000

    def get_player_possible_cards(self, player: Player, suit: str = 'all') -> tuple[list[Card], list[float]]:
        '''
            The cards a player could answer with, and how likely they are to hold each
        '''

        player_cards = player.get_cards_by_suit(suit) if suit != 'all' else list(player.hand)
        if not player_cards:                        # void in the round suit, so anything goes
            player_cards = list(player.hand)

        cards_probability = [self.beliefs[player.id - 1, SUIT_INDEX[card.suit], card.order] for card in player_cards]

        return player_cards, cards_probability

    def choose_card(self, position: int, round_suit: str, cards_played: list[Card], game: Game) -> Card:
        players_order = game.playersOrder

        cards_to_play = {}
        cards_probability = {}
        for player in players_order:
            suit = 'all' if position == 0 or not player.get_cards_by_suit(round_suit) else round_suit
            cards_to_play[player.id], cards_probability[player.id] = self.get_player_possible_cards(player, suit)

        still_to_play = [player.id for player in players_order[position + 1:]]
        possible_plays = [cards_to_play[player_id] for player_id in still_to_play]
        possible_probabilities = [cards_probability[player_id] for player_id in still_to_play]

        utilities = []
        for card in cards_to_play[self.id]:
            expected_utility = 0

            # Cartesian product of every answer the remaining players could give
            for other_cards in product(*possible_plays):
                combination_probability = np.prod([
                    possible_probabilities[i][possible_plays[i].index(other_card)]
                    for i, other_card in enumerate(other_cards)
                ])

                simulated = game.evaluate_round(cards_played + [card] + list(other_cards))
                if players_order[simulated.winner].team is self.team:
                    expected_utility += simulated.points * combination_probability
                else:
                    expected_utility -= simulated.points * combination_probability

            utilities.append((card, expected_utility))

        ##############################################################
        # NOTE: In here, put individual strategies that you remember #
        ##############################################################
        if game.current_round < 2:
            # Hold on to the trumps early on, by pushing their utility down
            utilities = [(card, utility - self.AVOID if card.suit == game.trump.suit else utility)
                         for card, utility in utilities]
        ##############################################################
        # NOTE: In here, put individual strategies that you remember #
        ##############################################################

        # Highest utility wins. The sort is stable and the hand is ordered, so ties go to
        # the weakest card.
        utilities.sort(key=lambda entry: entry[1], reverse=True)

        if self.verbose and game.mode == 'auto':
            print(f'Player {self.name} has the following utilities: '
                  f'{[(card.name, utility) for card, utility in utilities]}')

        return utilities[0][0]

    def get_strategy(self) -> str:
        return 'Deck Predictor'
