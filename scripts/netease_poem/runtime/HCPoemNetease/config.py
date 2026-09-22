# -*- coding: utf-8 -*-
ITEM = 'herobrine_companion:poem_of_the_end'
QUERY = 'query.mod.hc_poem_v1_'
PREFIX = 'hc_poem_v1_'
TICK_SECONDS = 1.0 / 30.0  # ModSDK OnScriptTickClient / OnScriptTickServer.
PLAYBACK_SPEED = 0.8
INPUT_DEBOUNCE = 0.085
BUFFER_SECONDS = 0.50
RESTART_SECONDS = 1.10
RECOVERY_SECONDS = 0.14
# "buffered": fresh attack inputs extend the combo; no damage is generated here.
# Optional local project setting: "full" plays the 22-part ground chain once.
PLAY_STYLE = 'buffered'
# Only used when the engine geometry name cannot identify the slim skin.
DEFAULT_SLIM_ARMS = False
# Shared local preference for both NetEase packs; available in Mod Settings.
DEFAULT_ANIMATIONS_ENABLED = True
SETTINGS_STORAGE = 'hc_poem_netease_client_v1'
SETTINGS_KEY = 'hc_poem_netease_animations'
# Small server-side follow-through impulses, in engine motion-vector units.
# These are minimum forward speeds, not additive boosts or teleport distances.
STEP_FORWARD_SPEED = {'ground': 0.12, 'sprint': 0.20, 'air': 0.055}
STEP_MIN_INTERVAL = 0.25
STEP_EXTERNAL_SPEED = 0.35
