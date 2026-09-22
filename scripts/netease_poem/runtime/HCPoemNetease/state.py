# -*- coding: utf-8 -*-
"""Deterministic presentation state; no Minecraft imports or combat effects."""
from HCPoemNetease.config import BUFFER_SECONDS, RESTART_SECONDS, RECOVERY_SECONDS, PLAY_STYLE, PLAYBACK_SPEED
from HCPoemNetease.library import DATA


class Chain(object):
    def __init__(self):
        self.cursors = {'ground': 0, 'air': 0, 'sprint': 0}
        self.clip = 0
        self.started = 0.0
        self.last_finished = -100.0
        self.last_input = -100.0
        self.queued = None
        self.queue_until = -100.0
        self.serial = 0
        self.start_serial = 0
        self.auto_left = 0

    def _start(self, context, now):
        sequence = DATA[context]
        index = self.cursors[context] % len(sequence)
        self.clip = sequence[index]
        self.cursors[context] = (index + 1) % len(sequence)
        self.started = now
        self.queued = None
        self.queue_until = -100.0
        self.start_serial = self.serial
        return 'start'

    def input(self, context, now, serial):
        if context not in self.cursors:
            context = 'ground'
        self.serial = max(self.serial, serial)
        self.last_input = now
        if not self.clip:
            if now - self.last_finished > RESTART_SECONDS:
                self.cursors = {'ground': 0, 'air': 0, 'sprint': 0}
            if PLAY_STYLE == 'full' and context == 'ground':
                self.auto_left = len(DATA['ground']) - 1
            return self._start(context, now)
        length = DATA['clips'][self.clip]['chain_at'] / PLAYBACK_SPEED
        if now - self.started >= length:
            return self._start(context, now)
        self.queued = context
        self.queue_until = now + BUFFER_SECONDS
        return None

    def tick(self, now):
        if not self.clip:
            return None
        elapsed = now - self.started
        length = DATA['clips'][self.clip]['chain_at'] / PLAYBACK_SPEED
        if elapsed + 1e-7 >= length:
            if self.queued and now <= self.queue_until + 1e-7:
                return self._start(self.queued, now)
            if self.auto_left > 0:
                self.auto_left -= 1
                return self._start('ground', now)
            if elapsed + 1e-7 >= length + RECOVERY_SECONDS:
                self.clip = 0
                self.last_finished = now
                self.queued = None
                return 'stop'
        return None

    def cancel(self, now):
        was_active = bool(self.clip)
        self.clip = 0
        self.queued = None
        self.auto_left = 0
        self.cursors = {'ground': 0, 'air': 0, 'sprint': 0}
        self.last_finished = now - RESTART_SECONDS - 1.0
        return 'stop' if was_active else None

    def snapshot(self, now):
        return {'clip': self.clip, 'elapsed': max(0.0, now - self.started) if self.clip else 0.0,
                'serial': self.serial, 'startSerial': self.start_serial,
                'cursors': dict(self.cursors)}

    def accept(self, payload, now, preserve_time=False):
        old_clip = self.clip
        self.clip = int(payload.get('clip', 0))
        if self.clip not in DATA['clips']:
            self.clip = 0
        if not preserve_time or self.clip != old_clip:
            elapsed = max(0.0, float(payload.get('elapsed', 0.0)))
            if self.clip:
                elapsed = min(elapsed, DATA['clips'][self.clip]['length'] / PLAYBACK_SPEED + RECOVERY_SECONDS)
            self.started = now - elapsed
        self.serial = max(self.serial, int(payload.get('serial', 0)))
        self.start_serial = int(payload.get('startSerial', 0))
        for context in self.cursors:
            self.cursors[context] = int(payload.get('cursors', {}).get(context, self.cursors[context])) % len(DATA[context])
        if not self.clip:
            self.last_finished = now
            self.queued = None
