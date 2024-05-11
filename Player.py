import numpy as np

from random import randint
from Card import Card
import copy


class Player:
    '''
        Player ->
            - name: player name
            - id: id of the player
            - hand: list of Card objects the player has (initially 10)
            - team: team object to which the player belongs
            - add_card(card): add card to player hand and sort it by order
            - get_cards_by_suit(suit): get all cards of a given suit
    '''

    def __init__(self, id, name, team) -> None:
        self.id = id
        self.name = name
        self.hand = []
        self.team = team

    def add_card(self, card) -> None:
        '''
            Add card to player hand and sort it by order to facilitate strategy implementation
        '''

        self.hand.append(card)
        self.hand = sorted(self.hand, key=lambda x: x.order)

    def get_cards_by_suit(self, suit) -> list:
        '''
            Get all cards of a given suit
        '''

        filtered_hand = []
        # For each card
        for card in self.hand:
            if card.suit == suit:
                filtered_hand.append(card)

        return filtered_hand
    
    def get_partner(self) -> 'Player':
        '''
            Get the partner of the player
        '''

        return self.team.get_partner(self)

class BeliefPlayer(Player):
    '''
        BeliefPlayer ->
            - name: player name
            - team: team object to which the player belongs
            - play_round(i, cards_played, round_suit): play a round of the game
    '''

    def __init__(self, id, name, team, others) -> None:
        super().__init__(id, name, team)

        # Store the belief at each timestamp [player, suit, card]
        self.beliefs = np.ones((4,4,10)) / 3
        # Set the beliefs of the player itself to 0
        self.beliefs[self.id - 1] = 0

    def obtain_suit_index(self, suit: str) -> int:
        '''
            Returns the index of a given suit
        '''

        match suit:
            case "hearts":
                return 0
            case "diamonds":
                return 1
            case "clubs":
                return 2
            case "spades":
                return 3
            case _ :
                raise ValueError("Invalid Suit")
            
    def update_beliefs_initial(self, card) -> None:
        '''
            Update the beliefs of the player after the initial handing of cards
        '''

        self.beliefs[:, self.obtain_suit_index(card.suit), card.order] = 0
        self.beliefs[self.id - 1, self.obtain_suit_index(card.suit), card.order] = 1

    def update_beliefs(self, card: Card, round_suit: str, player: Player) -> None:
        '''
            Updates the new belief of the player, after a card has been spotted
        '''

        print(f"Player {self.name} saw {card.name}")
        
        # After a card spotted no one will have it in their hand
        suit = self.obtain_suit_index(card.suit)
        self.beliefs[:, suit, card.order] = 0

        # If the card is not of the round suit, the player no longer has any card of the round suit
        if card.suit != round_suit:
            round_suit = self.obtain_suit_index(round_suit)
            self.beliefs[player.id - 1, round_suit, :] = 0

        # For each card
        for i in range(10):
            # For each suit
            for j in range(4):
                # Number of players that may still have the card
                num_players = np.count_nonzero(self.beliefs[:, j, i])
                # For each player
                for p in range(4):
                    if self.beliefs[p, j, i] != 0:
                        self.beliefs[p, j, i] = 1 / num_players


        # NOTE: This is a simple check to see if the beliefs are valid (REMOVE AFTERWARDS)
        for j in range(4):
            for k in range(10):
                if not np.sum(self.beliefs[:, j, k]) == 1 and not np.sum(self.beliefs[:, j, k]) == 0:
                    raise ValueError("Invalid Belief")


class RandomPlayer (Player):
    '''
        RandomPlayer ->
            - name: player name
            - team: team object to which the player belongs
            - play_round(i, cards_played, round_suit): play a round of the game
    '''

    def __init__(self, id, name, team) -> None:
        super().__init__(id, name, team)

    def play_round(self, i, cards_played, round_suit, players_order) -> Card:
        '''
            Play a round of the game of Sueca, selecting a card at random in each -round
        '''

        if i == 0:  # if the player is the first to play, play a random card
            cardPlayed = self.hand.pop(randint(0, len(self.hand) - 1))
            round_suit = cardPlayed.suit
        else:       # if the player is not the first to play, play a card of the same suit if possible
            cardsOfTheSameSuit = self.get_cards_by_suit(round_suit)
            if len(cardsOfTheSameSuit) != 0:    # if the player has cards of the same suit
                cardPlayed = cardsOfTheSameSuit[randint(
                    0, len(cardsOfTheSameSuit) - 1)]
                self.hand.remove(cardPlayed)
            else:                               # if the player does not have cards of the same suit
                cardPlayed = self.hand.pop(randint(0, len(self.hand) - 1))

        cards_played.append(cardPlayed)

        print(self.name + " played " + cardPlayed.name)
        return cardPlayed, round_suit

    def get_strategy(self) -> str:
        '''
            Return the strategy of the player
            In this case, the strategy is just random
        '''
        return 'Random Agent'


class MaximizePointsPlayer(Player):

    def __init__(self, id, name, team) -> None:
        super().__init__(id, name, team)

    def play_round(self, i, cards_played, round_suit, players_order, game) -> Card:
        if i == 0:
            cardPlayed = self.hand.pop(randint(0, len(self.hand) - 1))
            round_suit = cardPlayed.suit
        else:
            cardsOfTheSameSuit = self.get_cards_by_suit(round_suit)
            _, winner = game.calculate_round_points(cards_played, round_suit)
            player_winner = players_order[winner[1]]
            if player_winner.team == self.team:  # if the same team
                if cardsOfTheSameSuit:  # play strongest card from same suit
                    cardPlayed = cardsOfTheSameSuit[-1]
                    self.hand.remove(cardPlayed)
                else:
                    # else play strongest from another suit
                    cardPlayed = self.hand[-1]
                    self.hand.remove(cardPlayed)
            else:  # if different team
                if cardsOfTheSameSuit:  # if have cards from suit
                    cardPlayed = cardsOfTheSameSuit[-1]
                    if cardPlayed.order > winner[0].order:  # if can win
                        self.hand.remove(cardPlayed)  # play strongest card
                    else:
                        # else play weakest card
                        cardPlayed = cardsOfTheSameSuit[0]
                        self.hand.remove(cardPlayed)
                else:
                    trumpCards = self.get_cards_by_suit(game.trump.suit)
                    if trumpCards:  # if has trump, play the strongest trump card
                        cardPlayed = trumpCards[-1]
                        self.hand.remove(cardPlayed)
                    else:
                        cardPlayed = self.hand[0]  # play weakest card
                        self.hand.remove(cardPlayed)

        print(cardPlayed.name)
        cards_played.append(cardPlayed)
        return cardPlayed, round_suit

    def get_strategy(self) -> str:
        '''
            Return the strategy of the player
        '''
        return 'Maximize Points Won'


class MaximizeRoundsWonPlayer(Player):

    def __init__(self, id, name, team) -> None:
        super().__init__(id, name, team)

    def play_round(self, i, cards_played, round_suit, players_order, game):
        if i == 0:
            cardPlayed = self.hand.pop(randint(0, len(self.hand) - 1))
            round_suit = cardPlayed.suit
        else:
            cardsOfTheSameSuit = self.get_cards_by_suit(round_suit)
            _, winner = game.calculate_round_points(cards_played, round_suit)
            player_winner = players_order[winner[1]]
            if player_winner.team == self.team:  # if the same team
                if cardsOfTheSameSuit:  # play weakest card from the same suit
                    # preserves all strong cards
                    cardPlayed = cardsOfTheSameSuit[0]
                    self.hand.remove(cardPlayed)
                else:
                    # else play weakest from other suit
                    cardPlayed = self.hand[0]
                    self.hand.remove(cardPlayed)
            else:
                cardPlayed = None  # if different team
                if cardsOfTheSameSuit:  # if have cards from suit
                    for card in cardsOfTheSameSuit:
                        # Search for the lowest card taht can win
                        if card.order > winner[0].order:
                            cardPlayed = card
                            self.hand.remove(card)
                            break
                    if not cardPlayed:  # if cant win
                        cardPlayed = cardsOfTheSameSuit[0]  # play weakest card
                        self.hand.remove(cardPlayed)
                else:
                    trumpCards = self.get_cards_by_suit(game.trump.suit)
                    if trumpCards:  # if has trump, play the weakest trump card
                        cardPlayed = trumpCards[0]
                        self.hand.remove(cardPlayed)
                    else:
                        cardPlayed = self.hand[0]  # play weakest card
                        self.hand.remove(cardPlayed)

        print(cardPlayed.name)
        cards_played.append(cardPlayed)
        return cardPlayed, round_suit

    def get_strategy(self) -> str:
        '''
            Return the strategy of the player
        '''
        return 'Maximize Rounds Won'


class CooperativePlayer(BeliefPlayer):
    '''
        CooperativePlayer ->
            - name: player name
            - team: team object to which the player belongs
            - play_round(i, cards_played, round_suit): play a round of the game
    '''

    def __init__(self,id,  name, team, others) -> None:
        super().__init__(id, name, team, others)

    def update_beliefs_initial(self, card) -> None:
        '''
            Update the beliefs of the player after the initial handing of cards
        '''

        return super().update_beliefs_initial(card)

    def update_beliefs(self, card, round_suit, player) -> None:
        '''
            The card was seen so we now know that no player no longer has it
        '''

        super().update_beliefs(card, round_suit, player)

    def play_round(self, i, cards_played_in_round, round_suit, players_order) -> tuple[Card, str]:
        '''
            Play a round of the game of Sueca, selecting the card, considering
            the cards that its partner has, acting as a "team player"
        '''

        if i == 0:  # if the player is the first to play, play a random card
            cardPlayed = self.hand.pop(randint(0, len(self.hand) - 1))
            round_suit = cardPlayed.suit
        else:       # if the player is not the first to play, play a card of the same suit if possible
            cardsOfTheSameSuit = self.get_cards_by_suit(round_suit)
            if len(cardsOfTheSameSuit) != 0:    # if the player has cards of the same suit
                cardPlayed = cardsOfTheSameSuit[randint(
                    0, len(cardsOfTheSameSuit) - 1)]
                self.hand.remove(cardPlayed)
            else:                               # if the player does not have cards of the same suit
                cardPlayed = self.hand.pop(randint(0, len(self.hand) - 1))

        cards_played_in_round.append(cardPlayed)

        print(self.name + " played " + cardPlayed.name)

        return cardPlayed, round_suit

    def get_strategy(self) -> str:
        '''
            Return the strategy of the player
        '''
        return 'Cooperative Player'


class PredictorPlayer(BeliefPlayer):

    def __init__(self,id,  name, team, others) -> None:
        super().__init__(id, name, team, others)

    def update_beliefs_initial(self, card) -> None:
        '''
            Update the beliefs of the player after the initial handing of cards
        '''

        return super().update_beliefs_initial(card)

    def update_beliefs(self, card, round_suit, player) -> None:
        '''
            Update the beliefs of the player after a card has been spotted
        '''

        super().update_beliefs(card, round_suit, player)

    def get_player_possible_cards(self, player, suit='all') -> list:
        '''
            Returns the cards of a given player
        '''

        if suit != 'all':
            player_cards = player.get_cards_by_suit(suit)
            if player_cards == []:
                player_cards = copy.deepcopy(player.hand)
        else:
            player_cards = copy.deepcopy(player.hand)

        return player_cards

    def play_round(self, i, cards_played_in_round, round_suit, players_order, game) -> tuple[Card, str]:
        '''
            Play a round of the game of Sueca, selecting the card, considering
            the cards that its partner has, acting as a "team player"
        '''

        # Build the vectors containing the cards of the players into a dictionary
        cards_to_play = {}
        if i == 0:  # if the player is the first to play, play a random card
            for player in players_order:
                cards_to_play[player.id] = self.get_player_possible_cards(player)
        else:       # if the player is not the first to play, play a card of the same suit if possible
            for player in players_order:
                cards_to_play[player.id] = self.get_player_possible_cards(player, round_suit)

        # Build the utility per card for the player
        utility_per_card = {}

        for card in cards_to_play[self.id]:
            # Calculate the utility of the card
            utility = 0

            for player in players_order:
                if player.id == self.id:    # Skip
                    continue

                # Calculate the utility of the card
                utility += game.calculate_card_utility(card, cards_played_in_round, player, round_suit)

            utility_per_card[card] = utility                     

        cards_played_in_round.append(cardPlayed)

        print(self.name + " played " + cardPlayed.name)

        return cardPlayed, round_suit

    def get_strategy(self) -> str:
        '''
            Return the strategy of the player
            In this case, the strategy is just random
        '''
        return 'Deck Predictor'


### TODO: Implement more strategies
### TODO: Implement more strategies
### TODO: Implement more strategies
