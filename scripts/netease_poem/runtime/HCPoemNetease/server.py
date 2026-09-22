# -*- coding: utf-8 -*-
import mod.server.extraServerApi as api
from HCPoemNetease import NAMESPACE, CLIENT, SERVER
from HCPoemNetease.config import ITEM, TICK_SECONDS, RESTART_SECONDS
from HCPoemNetease.state import Chain
from HCPoemNetease.motion import AttackStep

try:
    integer_types = (int, long)
except NameError:
    integer_types = (int,)

Base = api.GetServerSystemCls()


class PoemServer(Base):
    def __init__(self, namespace, systemName):
        Base.__init__(self, namespace, systemName)
        self.factory = api.GetEngineCompFactory()
        self.now = 0.0
        self.ticks = 0
        self.chains = {}
        self.last_serial = {}
        self.ready_times = {}
        self.steps = AttackStep(self.factory, api)
        self._listeners = []
        for event, callback in [('OnScriptTickServer', self.on_tick), ('PlayerDieEvent', self.on_cancel),
                ('DelServerPlayerEvent', self.on_leave), ('DimensionChangeServerEvent', self.on_cancel)]:
            self._listen(api.GetEngineNamespace(), api.GetEngineSystemName(), event, callback)
        self._listen(NAMESPACE, CLIENT, 'PoemV1Input', self.on_input)
        self._listen(NAMESPACE, CLIENT, 'PoemV1Cancel', self.on_client_cancel)
        self._listen(NAMESPACE, CLIENT, 'PoemV1Ready', self.on_ready)
        print('[HCPoemV1] 30 motions, 22-part ground combo; 0.8x playback and short attack steps; server ready')

    def _listen(self, namespace, system, event, callback):
        self.ListenForEvent(namespace, system, event, self, callback)
        self._listeners.append((namespace, system, event, callback))

    def _holding(self, player):
        try:
            item = self.factory.CreateItem(player).GetPlayerItem(api.GetMinecraftEnum().ItemPosType.CARRIED, 0)
            return bool(item and (item.get('newItemName') or item.get('itemName')) == ITEM)
        except Exception:
            return False

    def _alive(self, player):
        try:
            value = self.factory.CreateAttr(player).GetAttrValue(api.GetMinecraftEnum().AttrType.HEALTH)
            return value is not None and value > 0
        except Exception:
            return False

    def _send(self, player, chain):
        payload = chain.snapshot(self.now)
        payload['playerId'] = player
        self.BroadcastToAllClient('PoemV1State', payload)

    def on_input(self, args):
        # ListenForEvent adds __id__ on the server. Never trust a client-supplied playerId.
        player = args.get('__id__')
        serial = args.get('serial')
        if not player or isinstance(serial, bool) or not isinstance(serial, integer_types) or not 0 < serial < 1000000000:
            return
        if serial <= self.last_serial.get(player, 0):
            return
        self.last_serial[player] = serial
        if not self._holding(player) or not self._alive(player):
            self._cancel(player)
            return
        chain = self.chains.setdefault(player, Chain())
        # Network batching can deliver two legitimate clicks in one server tick.
        # The monotonic serial rejects duplicates; Chain keeps only one pending
        # continuation, so arrival-time throttling would only lose valid input.
        context = args.get('context', 'ground')
        if context not in ('ground', 'air', 'sprint'):
            context = 'ground'
        event = chain.input(context, self.now, serial)
        if event:
            self.steps.begin(player, chain)
            self._send(player, chain)

    def on_tick(self, args=None):
        self.now += TICK_SECONDS
        self.ticks += 1
        for player, chain in list(self.chains.items()):
            if chain.clip and (self.ticks % 3 == 0 or player in self.steps.pending) and (not self._holding(player) or not self._alive(player)):
                self._cancel(player)
                continue
            event = chain.tick(self.now)
            if event:
                self.steps.begin(player, chain)
                self._send(player, chain)
            self.steps.tick(player, chain, self.now)
            if not chain.clip and self.now - chain.last_finished > RESTART_SECONDS + 5:
                self.chains.pop(player, None)

    def on_ready(self, args):
        player = args.get('__id__')
        if not player or self.now - self.ready_times.get(player, -100) < .2:
            return
        self.ready_times[player] = self.now
        self.NotifyToClient(player, 'PoemV1Snapshot', {'lastSerial': self.last_serial.get(player, 0), 'states': [dict(c.snapshot(self.now), playerId=p)
                            for p, c in self.chains.items() if c.clip]})

    def _cancel(self, player):
        self.steps.cancel(player)
        chain = self.chains.get(player)
        if chain and chain.cancel(self.now):
            self._send(player, chain)

    def on_cancel(self, args):
        self._cancel(args.get('playerId') or args.get('id'))

    def on_client_cancel(self, args):
        self._cancel(args.get('__id__'))

    def on_leave(self, args):
        player = args.get('id')
        self._cancel(player)
        self.chains.pop(player, None)
        self.last_serial.pop(player, None)
        self.ready_times.pop(player, None)
        self.steps.forget(player)

    def Destroy(self):
        for player in list(self.chains):
            self._cancel(player)
        for namespace, system, event, callback in self._listeners:
            self.UnListenForEvent(namespace, system, event, self, callback)
        self._listeners = []
        self.steps.pending.clear()
        self.steps.last_step.clear()
