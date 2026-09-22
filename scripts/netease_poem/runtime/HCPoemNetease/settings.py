# -*- coding: utf-8 -*-
"""Native NetEase Mod Settings, with a shared client-local saved preference."""
from HCPoemNetease.config import (
    ITEM, DEFAULT_ANIMATIONS_ENABLED, SETTINGS_STORAGE, SETTINGS_KEY)


class PoemSettings(object):
    def __init__(self, client):
        self.client = client
        self.instance = None
        self.registered = False
        self.registering = False
        self.closed = False
        self.data = {}
        self.last_error = None

    def load(self):
        try:
            data = self.client.factory.CreateConfigClient(self.client.level).GetConfigData(
                SETTINGS_STORAGE, True)
            if isinstance(data, dict):
                self.data = dict(data)
            value = self.data.get(SETTINGS_KEY)
            if isinstance(value, bool):
                return value
        except Exception as error:
            self._error('load', error)
        return DEFAULT_ANIMATIONS_ENABLED

    def _error(self, action, error):
        message = action + ': ' + str(error)
        if message != self.last_error:
            print('[HCPoemV1] settings ' + message)
            self.last_error = message

    def register(self):
        if self.closed:
            return False
        try:
            window = self.client.factory.CreateNeteaseWindow(self.client.level)
            setting = window.RegisterSettingInst(
                ITEM, u'终末之诗'.encode('utf-8'),
                'textures/items/herobrine_companion/poem_of_the_end')
            if not setting:
                return False  # UiInitFinished / the next retry will finish it.
            if self.registered and setting is self.instance:
                return True
            self.instance = setting
            self.registered = False
            self.registering = True
            # The full SettingInst documentation defines callback BEFORE
            # priority/default. The short RegisterSettingInst example is stale.
            setting.AddToggle(
                SETTINGS_KEY, u'网易版终末之诗动作'.encode('utf-8'),
                self.on_change, None, bool(self.client.enabled))
            setting.AddText(
                SETTINGS_KEY + '_description',
                u'以0.8倍速播放地面、疾跑和空中攻击，并附带小幅跟进。关闭立即停用动作和后续跟进；自动保存。'.encode('utf-8'))
            self._sync_control()
            self.registered = True
            self.last_error = None
            return True
        except Exception as error:
            self._error('register', error)
            return False
        finally:
            self.registering = False

    def _sync_control(self):
        if self.instance:
            # SetToggleValue persists native UI state without calling on_change.
            # The global ConfigClient value remains authoritative across packs.
            self.instance.SetToggleDefault(SETTINGS_KEY, bool(self.client.enabled))
            self.instance.SetToggleValue(SETTINGS_KEY, bool(self.client.enabled))

    def on_change(self, key, value):
        if self.closed or self.registering or key != SETTINGS_KEY or not isinstance(value, bool):
            return
        self.client.set_enabled(value)
        self.data[SETTINGS_KEY] = value
        try:
            saved = self.client.factory.CreateConfigClient(self.client.level).SetConfigData(
                SETTINGS_STORAGE, dict(self.data), True)
            if saved is False:
                self._error('save', 'SetConfigData returned False')
        except Exception as error:
            self._error('save', error)
        try:
            self._sync_control()
        except Exception as error:
            self._error('update control', error)

    def close(self):
        self.closed = True
        self.instance = None
