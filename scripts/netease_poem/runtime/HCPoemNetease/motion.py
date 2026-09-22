# -*- coding: utf-8 -*-
"""One small authoritative horizontal follow-through per attack segment."""
import math

from HCPoemNetease.config import (
    PLAYBACK_SPEED, STEP_FORWARD_SPEED, STEP_MIN_INTERVAL, STEP_EXTERNAL_SPEED)
from HCPoemNetease.library import DATA


class AttackStep(object):
    def __init__(self, factory, api):
        self.factory = factory
        self.api = api
        self.pending = {}
        self.last_step = {}

    def begin(self, player, chain):
        self.pending.pop(player, None)
        if not chain.clip:
            return
        try:
            rotation = self.factory.CreateRot(player).GetRot()
            if not rotation:
                return
            # Capture horizontal attack direction once; body spins and looking
            # up/down must not redirect the impulse or launch the player.
            x, unused_y, z = self.api.GetDirFromRot((0.0, rotation[1]))
            length = math.sqrt(x * x + z * z)
            if length < 1e-6:
                return
            self.pending[player] = (chain.clip, chain.start_serial, chain.started,
                                    x / length, z / length)
        except Exception:
            # A missing player component must not break attack playback.
            return

    def tick(self, player, chain, now):
        pending = self.pending.get(player)
        if not pending:
            return
        clip, serial, started, x, z = pending
        if (chain.clip, chain.start_serial, chain.started) != (clip, serial, started):
            self.pending.pop(player, None)
            return
        move = DATA['clips'][clip]
        if now - started + 1e-7 < move['step_at'] / PLAYBACK_SPEED:
            return
        # Consume once even if another movement effect takes precedence. Never
        # replay a missed impulse later in the recovery or stack rapid inputs.
        self.pending.pop(player, None)
        if now - self.last_step.get(player, -100.0) < STEP_MIN_INTERVAL:
            return
        try:
            component = self.factory.CreateActorMotion(player)
            current = component.GetMotion()
            if current is None or len(current) != 3:
                return
            vx, vy, vz = current
            speed = math.sqrt(vx * vx + vz * vz)
            forward = vx * x + vz * z
            target = STEP_FORWARD_SPEED[move['context']]
            # Retain running, knockback/skill motion and deliberate retreat.
            if speed > STEP_EXTERNAL_SPEED or forward >= target or forward < -0.04:
                return
            add = target - forward
            motion = (vx + x * add, vy, vz + z * add)
            if component.SetPlayerMotion(motion) is not False:
                self.last_step[player] = now
        except Exception as error:
            print('[HCPoemV1] attack step unavailable: ' + str(error))

    def cancel(self, player):
        self.pending.pop(player, None)

    def forget(self, player):
        self.cancel(player)
        self.last_step.pop(player, None)
