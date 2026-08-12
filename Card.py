############################################# Card Model #############################################

SUITS = ('hearts', 'diamonds', 'clubs', 'spades')

# Ranks in ascending trick taking strength: a rank's index here is its Card.order.
# NOTE: deliberately different from the point values below (a 7 beats a King but an Ace beats both).
RANKS = ('2', '3', '4', '5', '6', 'Q', 'J', 'K', '7', 'A')

# Points a card is worth to whoever captures it. Ranks left out are worth nothing.
RANK_VALUES = {'A': 11, '7': 10, 'K': 4, 'J': 3, 'Q': 2}

# Lookup tables derived from the above, so no module has to hardcode them again.
SUIT_INDEX = {suit: index for index, suit in enumerate(SUITS)}
RANK_ORDER = {rank: index for index, rank in enumerate(RANKS)}
ORDER_VALUES = tuple(RANK_VALUES.get(rank, 0) for rank in RANKS)   # indexed by Card.order


class Card:
    '''
        Card ->
            - name: card name (e.g. "A_of_hearts")
            - suit: card suit (hearts, diamonds, clubs, spades)
            - rank: card rank (2, 3, 4, 5, 6, Q, J, K, 7, A)
            - order: trick taking strength (0 - 9), the rank's index in RANKS
            - value: points the card is worth (0, 2, 3, 4, 10, 11)
    '''

    __slots__ = ('name', 'suit', 'rank', 'order', 'value')

    def __init__(self, suit: str, rank: str) -> None:
        self.suit = suit
        self.rank = rank
        self.name = f'{rank}_of_{suit}'
        self.order = RANK_ORDER[rank]
        self.value = RANK_VALUES.get(rank, 0)

    def beats(self, current_best: 'Card', trump_suit: str) -> bool:
        '''
            Whether this card would take a round currently being won by current_best.

            Only a higher card of the same suit or a trump played on a non trump wins,
            so a card of the leading suit never beats a round that has been cut.
        '''

        if self.suit == current_best.suit:
            return self.order > current_best.order

        return self.suit == trump_suit

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, Card):
            return NotImplemented

        return self.suit == other.suit and self.rank == other.rank

    def __hash__(self) -> int:
        return hash((self.suit, self.rank))

    def __str__(self) -> str:
        return self.name

    def __repr__(self) -> str:
        return f'Card({self.name})'


def new_deck() -> list[Card]:
    '''
        Build the 40 card Sueca deck: every rank of every suit
    '''

    return [Card(suit, rank) for rank in RANKS for suit in SUITS]
