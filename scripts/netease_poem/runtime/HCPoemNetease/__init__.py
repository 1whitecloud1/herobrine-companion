# -*- coding: utf-8 -*-
"""Shared by both requested packs. The first loaded pack owns one system pair."""
NAMESPACE = 'HCPoemLongComboV1'
CLIENT = 'PoemClient'
SERVER = 'PoemServer'


def register_client():
    import mod.client.extraClientApi as api
    if api.GetSystem(NAMESPACE, CLIENT) is None:
        api.RegisterSystem(NAMESPACE, CLIENT, 'HCPoemNetease.client.PoemClient')


def register_server():
    import mod.server.extraServerApi as api
    if api.GetSystem(NAMESPACE, SERVER) is None:
        api.RegisterSystem(NAMESPACE, SERVER, 'HCPoemNetease.server.PoemServer')
