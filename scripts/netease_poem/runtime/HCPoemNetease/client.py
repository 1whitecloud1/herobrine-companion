# -*- coding: utf-8 -*-
import mod.client.extraClientApi as api
from HCPoemNetease import NAMESPACE, CLIENT, SERVER
from HCPoemNetease.config import ITEM, QUERY, PREFIX, TICK_SECONDS, INPUT_DEBOUNCE, RECOVERY_SECONDS, DEFAULT_SLIM_ARMS, PLAYBACK_SPEED
from HCPoemNetease.library import DATA
from HCPoemNetease.state import Chain
from HCPoemNetease.settings import PoemSettings

Base = api.GetClientSystemCls()


class PoemClient(Base):
    def __init__(self, namespace, systemName):
        Base.__init__(self, namespace, systemName)
        self.factory = api.GetEngineCompFactory()
        self.local = api.GetLocalPlayerId()
        self.level = api.GetLevelId()
        self.now = 0.0
        self.ticks = 0
        self.serial = 0
        self.last_input = -100.0
        self.last_swing = 0.0
        self.bound = set()
        self.pending = {}
        self.chains = {}
        self.hidden_items = set()
        self.last_ack = {}
        self.cancel_serial = -1
        self.resync_at = None
        self.settings_pending = False
        self.settings = PoemSettings(self)
        self.enabled = self.settings.load()
        self._listeners = []
        register = self.factory.CreateQueryVariable(self.level)
        for key in ('active', 'clip', 'time', 'stamp', 'rate', 'slim', 'armor_0', 'armor_1', 'armor_2', 'armor_3'):
            register.Register(QUERY + key, PLAYBACK_SPEED if key == 'rate' else 0.0)
        for event, callback in [('OnScriptTickClient', self.on_tick),
                ('UiInitFinished', self.on_ui_init),
                ('AddPlayerCreatedClientEvent', self.on_created), ('UpdatePlayerSkinClientEvent', self.on_skin),
                ('RemovePlayerAOIClientEvent', self.on_removed), ('OnLocalPlayerStopLoading', self.on_loaded),
                ('LeftClickBeforeClientEvent', self.on_click), ('TapBeforeClientEvent', self.on_click),
                ('PlayerAttackEntityEvent', self.on_hit), ('OnCarriedNewItemChangedClientEvent', self.on_item),
                ('DimensionChangeClientEvent', self.on_dimension)]:
            self._listen(api.GetEngineNamespace(), api.GetEngineSystemName(), event, callback)
        self._listen(NAMESPACE, SERVER, 'PoemV1State', self.on_state)
        self._listen(NAMESPACE, SERVER, 'PoemV1Snapshot', self.on_snapshot)
        print('[HCPoemV1] client ready; attack repeatedly to extend the combo')

    def _listen(self, namespace, system, event, callback):
        self.ListenForEvent(namespace, system, event, self, callback)
        self._listeners.append((namespace, system, event, callback))

    def _query(self, player, name, default=0.0):
        try:
            value = self.factory.CreateQueryVariable(player).GetMolangValue(name)
            return default if value is None else value
        except Exception:
            return default

    def _holding(self, player):
        try:
            item = self.factory.CreateItem(player).GetPlayerItem(api.GetMinecraftEnum().ItemPosType.CARRIED, 0)
            return bool(item and (item.get('newItemName') or item.get('itemName')) == ITEM)
        except Exception:
            return False

    def _can_animate(self, player):
        return (self.enabled and self._holding(player) and self._query(player, 'query.is_alive', 1) > .5
                and self._query(player, 'query.is_sleeping') < .5
                and self._query(player, 'query.is_riding') < .5
                and self._query(player, 'query.is_swimming') < .5
                and self._query(player, 'query.is_gliding') < .5)

    def _bind(self, player):
        if not self.enabled:
            return False
        if player in self.bound:
            return True
        actor = self.factory.CreateActorRender(player)
        results = []
        for key, geometry in [('wide', 'player_wide'), ('slim', 'player_slim'),
                              ('weapon', 'scythe'), ('weapon_fp', 'scythe_fp')]:
            results.append(actor.AddPlayerGeometry(PREFIX + key, 'geometry.hc_poem_v1.' + geometry))
        results.append(actor.AddPlayerRenderMaterial(PREFIX + 'weapon', 'entity_emissive_alpha'))
        results.append(actor.AddPlayerRenderMaterial(PREFIX + 'armor', 'entity_alphatest'))
        results.append(actor.AddPlayerTexture(PREFIX + 'weapon', 'textures/hc_poem_v1/scythe'))
        for slot in range(4):
            results.append(actor.AddPlayerGeometry(PREFIX + 'armor_' + str(slot), 'geometry.hc_poem_v1.armor_' + str(slot)))
            for index, material in enumerate(('leather','chain','iron','gold','diamond','netherite','turtle'), 1):
                layer = 2 if slot == 2 and material != 'turtle' else 1
                results.append(actor.AddPlayerTexture(PREFIX + 'armor_' + str(slot) + '_' + str(index),
                                'textures/models/armor/' + material + '_' + str(layer)))
        for clip in DATA['clips'].values():
            results.append(actor.AddPlayerAnimation(clip['alias'], clip['animation']))
        results.append(actor.AddPlayerAnimation(PREFIX + 'mask', 'animation.hc_poem_v1.hide_original'))
        results.append(actor.AddPlayerAnimation(PREFIX + 'fp', 'animation.hc_poem_v1.fp'))
        results.append(actor.AddPlayerAnimationController(PREFIX + 'controller', 'controller.animation.hc_poem_v1.player'))
        active = QUERY + 'active > 0.5 && query.is_alive && !query.is_spectator && !variable.map_face_icon && !variable.is_paperdoll'
        third = '(' + active + ') && !variable.is_first_person'
        first = '(' + active + ') && variable.is_first_person'
        # Added after the original root; only our named bones and the temporary
        # visibility mask are affected. No player.entity.json override is used.
        actor.AddPlayerScriptAnimate(PREFIX + 'controller', '1.0', True)
        actor.AddPlayerScriptAnimate(PREFIX + 'mask', third, True)
        actor.AddPlayerScriptAnimate(PREFIX + 'fp', first, True)
        results.append(actor.AddPlayerRenderController('controller.render.hc_poem_v1.body', third + ' && !query.is_invisible'))
        results.append(actor.AddPlayerRenderController('controller.render.hc_poem_v1.weapon', third))
        results.append(actor.AddPlayerRenderController('controller.render.hc_poem_v1.weapon_fp', first))
        for slot in range(4):
            results.append(actor.AddPlayerRenderController('controller.render.hc_poem_v1.armor_' + str(slot),
                           third + ' && ' + QUERY + 'armor_' + str(slot) + ' > 0.5'))
        # A false API result is a failed registration, not a reason to hide the
        # original model. Retry only after a later skin-created notification/tick.
        if any(result is False for result in results):
            return False
        if actor.RebuildPlayerRender() is False:
            return False
        geometry_names = actor.GetActorRenderParams(player, 'geometry', True) or []
        names = ' '.join(str(name).lower() for name in geometry_names)
        slim = 1.0 if ('customslim' in names or 'alex' in names or DEFAULT_SLIM_ARMS) else 0.0
        query = self.factory.CreateQueryVariable(player)
        query.Set(QUERY + 'slim', slim)
        query.Set(QUERY + 'active', 0.0)
        query.Set(QUERY + 'clip', 0.0)
        query.Set(QUERY + 'rate', PLAYBACK_SPEED)
        self.bound.add(player)
        self._armor(player)
        return True

    def _armor(self, player):
        types = {'leather':1, 'chainmail':2, 'iron':3, 'golden':4, 'diamond':5, 'netherite':6, 'turtle':7}
        query = self.factory.CreateQueryVariable(player)
        for slot in range(4):
            try:
                item = self.factory.CreateItem(player).GetPlayerItem(api.GetMinecraftEnum().ItemPosType.ARMOR, slot)
                name = (item.get('newItemName') or item.get('itemName') or '') if item else ''
                prefix = name.split(':')[-1].split('_')[0] if name.startswith('minecraft:') else ''
                index = types.get(prefix, 0)
            except Exception:
                index = 0
            query.Set(QUERY + 'armor_' + str(slot), float(index))

    def _draw(self, player, chain):
        if not self.enabled:
            self._show_original(player)
            return
        if player not in self.bound:
            self.pending.setdefault(player, self.ticks + 1)
            return
        query = self.factory.CreateQueryVariable(player)
        if not chain.clip:
            self._show_original(player)
            return
        elapsed = max(0.0, self.now - chain.started) * PLAYBACK_SPEED
        query.Set(QUERY + 'time', elapsed)
        query.Set(QUERY + 'stamp', self._query(player, 'query.life_time', 0.0))
        query.Set(QUERY + 'clip', float(chain.clip))
        query.Set(QUERY + 'active', 1.0)
        if player not in self.hidden_items:
            if self.factory.CreateActorRender(player).SetPlayerItemInHandVisible(False, 0) is not False:
                self.hidden_items.add(player)

    def _show_original(self, player):
        query = self.factory.CreateQueryVariable(player)
        query.Set(QUERY + 'active', 0.0)
        query.Set(QUERY + 'clip', 0.0)
        if player in self.hidden_items:
            if self.factory.CreateActorRender(player).SetPlayerItemInHandVisible(True, 0) is not False:
                self.hidden_items.discard(player)

    def on_ui_init(self, args=None):
        self.settings_pending = not self.settings.register()

    def set_enabled(self, enabled):
        enabled = bool(enabled)
        if enabled == self.enabled:
            return
        self.enabled = enabled
        self.last_input = -100.0
        self.last_swing = float(self._query(self.local, 'variable.attack_time', 0.0))
        # Late acknowledgements for attacks sent before the toggle must not
        # revive them, even when the user switches off and immediately on.
        self.cancel_serial = max(self.cancel_serial, self.serial)
        if not enabled:
            self.resync_at = None
            for chain in self.chains.values():
                chain.cancel(self.now)
            for player in list(self.bound | self.hidden_items | set(self.chains)):
                self._show_original(player)
            self.chains.clear()
            self.NotifyToServer('PoemV1Cancel', {})
        else:
            self.NotifyToServer('PoemV1Ready', {})
            # Ready requests have a 0.2 s server debounce. One delayed refresh
            # also covers rapid toggling or a just-created player notification.
            self.resync_at = self.ticks + 8

    def _context(self):
        if self._query(self.local, 'query.is_on_ground', 1) < .5:
            return 'air'
        try:
            if self.factory.CreatePlayer(self.local).isSprinting():
                return 'sprint'
        except Exception:
            pass
        return 'ground'

    def on_click(self, args):
        # Do not cancel the event: damage, mining and the existing skill buttons
        # remain owned by Minecraft / the installed companion skill script.
        if args.get('cancel') or self.now - self.last_input < INPUT_DEBOUNCE - 1e-7 or not self._can_animate(self.local):
            return
        self.last_input = self.now
        self.serial += 1
        context = self._context()
        chain = self.chains.setdefault(self.local, Chain())
        chain.input(context, self.now, self.serial)
        self._armor(self.local)
        self._draw(self.local, chain)
        self.NotifyToServer('PoemV1Input', {'serial': self.serial, 'context': context})

    def on_hit(self, args):
        if args.get('playerId') == self.local:
            self.on_click({})

    def on_tick(self, args=None):
        self.now += TICK_SECONDS
        self.ticks += 1
        if self.settings_pending and self.ticks % 30 == 0:
            self.on_ui_init()
        swing = float(self._query(self.local, 'variable.attack_time', 0.0))
        if not self.enabled:
            self.last_swing = swing
            for player in list(self.hidden_items):
                self._show_original(player)
            return
        if self.resync_at is not None and self.ticks >= self.resync_at:
            self.resync_at = None
            self.NotifyToServer('PoemV1Ready', {})
        for player, due in list(self.pending.items()):
            if self.ticks < due:
                continue
            try:
                success = self._bind(player)
            except Exception as error:
                success = False
                if self.ticks % 90 == 0:
                    print('[HCPoemV1] waiting for player renderer: ' + str(error))
            if success:
                self.pending.pop(player, None)
            else:
                self.pending[player] = self.ticks + 30
        # Molang swing rollover covers held attack / controller input on engine
        # builds that do not emit another LeftClickBefore for repeated attacks.
        if self._holding(self.local) and ((swing > 0 and self.last_swing <= 0) or (swing > 0 and swing + .15 < self.last_swing)):
            self.on_click({})
        self.last_swing = swing
        for player, chain in list(self.chains.items()):
            if chain.clip:
                if self.ticks % 3 == 0:
                    self._armor(player)
                if self.ticks % 3 == 0 and not self._can_animate(player):
                    chain.cancel(self.now)
                    self._show_original(player)
                    if player == self.local:
                        self.NotifyToServer('PoemV1Cancel', {})
                    continue
                if player == self.local:
                    chain.tick(self.now)
                elif self.now - chain.started > DATA['clips'][chain.clip]['length'] / PLAYBACK_SPEED + RECOVERY_SECONDS + .1:
                    chain.clip = 0
                self._draw(player, chain)

    def on_created(self, args):
        player = args.get('playerId')
        if player:
            self.pending[player] = self.ticks + 1
            if player == self.local and not self.enabled:
                self._cancel_local()
            self.NotifyToServer('PoemV1Ready', {})

    def on_loaded(self, args):
        self.local = api.GetLocalPlayerId()
        self.on_ui_init()
        self.on_created({'playerId': self.local})

    def on_skin(self, args):
        player = args.get('playerId')
        if player:
            self._show_original(player)
            self.bound.discard(player)
            self.pending[player] = self.ticks + 5

    def on_removed(self, args):
        player = args.get('playerId')
        if player:
            self._show_original(player)
            self.chains.pop(player, None)
            self.pending.pop(player, None)
            self.bound.discard(player)
            self.last_ack.pop(player, None)

    def on_item(self, args):
        if not self._holding(self.local):
            self._cancel_local()

    def _cancel_local(self):
        chain = self.chains.get(self.local)
        if chain:
            chain.cancel(self.now)
        self._show_original(self.local)
        self.NotifyToServer('PoemV1Cancel', {})

    def on_dimension(self, args):
        if args.get('playerId') == self.local:
            self._cancel_local()
            for player in list(self.chains):
                self._show_original(player)
            self.chains.clear()
        else:
            self.on_removed(args)

    def on_state(self, payload):
        player = payload.get('playerId')
        if not player:
            return
        serial = int(payload.get('serial', 0))
        if serial < self.last_ack.get(player, -1):
            return
        self.last_ack[player] = serial
        if player == self.local:
            self.serial = max(self.serial, serial)
            if not self.enabled:
                self.cancel_serial = max(self.cancel_serial, serial)
        if not self.enabled:
            self._show_original(player)
            return
        if player == self.local and serial <= self.cancel_serial:
            return
        chain = self.chains.setdefault(player, Chain())
        if player == self.local and serial < chain.start_serial:
            return
        # An acknowledged local prediction must not restart its first frames.
        preserve = (player == self.local and chain.clip == payload.get('clip')
                    and chain.start_serial == payload.get('startSerial'))
        chain.accept(payload, self.now, preserve)
        self._armor(player)
        self._draw(player, chain)

    def on_snapshot(self, payload):
        self.serial = max(self.serial, int(payload.get('lastSerial', 0)))
        if not self.enabled:
            self.cancel_serial = max(self.cancel_serial, self.serial)
        for state in payload.get('states', []):
            self.on_state(state)

    def Destroy(self):
        self.settings.close()
        for player in list(self.bound | self.hidden_items):
            self._show_original(player)
        for namespace, system, event, callback in self._listeners:
            self.UnListenForEvent(namespace, system, event, self, callback)
        self._listeners = []
