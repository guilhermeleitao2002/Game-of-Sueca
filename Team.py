from __future__ import annotations

from typing import TYPE_CHECKING, Any

if TYPE_CHECKING:
    from Player import Player


class Team:
    '''
        Team ->
            - name: team name
            - players: list of Player objects
            - score: points captured during the game (0 - 120)
            - initial_points: points held in the two hands the team was dealt (0 - 120)
    '''

    def __init__(self, name: str) -> None:
        self.name = name
        self.players = []
        self.score = 0
        self.initial_points = 0

    def add_player(self, player: Player) -> None:
        '''
            Add player to the team
        '''

        self.players.append(player)

    def get_partner(self, player: Player) -> Player | None:
        '''
            Get the partner of a player
        '''

        for teammate in self.players:
            if teammate.name != player.name:
                return teammate

        return None

    def dump_to_json(self) -> dict[str, Any]:
        '''
            Dump the team information to a dictionary
        '''

        return {
            'name': self.name,
            'players': [{'name': player.name, 'strategy': player.get_strategy()} for player in self.players],
            'score': self.score,
            'initial_points': self.initial_points,
        }
