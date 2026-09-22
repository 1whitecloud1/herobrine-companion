"""Exercise actual shipped ModSDK scripts with documented API fakes."""
import ast
import importlib
import math
import shutil
import sys
import types

from common import BP, ROOT, WORK, write

shutil.copytree(ROOT/'scripts/netease_poem/runtime/HCPoemNetease', BP/'HCPoemNetease',dirs_exist_ok=True)
sys.path.insert(0,str(BP))
from HCPoemNetease.state import Chain
from HCPoemNetease.library import DATA
from HCPoemNetease.config import (ITEM, QUERY, RECOVERY_SECONDS, SETTINGS_KEY,
    SETTINGS_STORAGE, PLAYBACK_SPEED, STEP_FORWARD_SPEED, TICK_SECONDS)

checks = []


def check(condition, label):
    assert condition, label
    checks.append(label)


class Engine:
    def __init__(self):
        self.now = 0.0
        self.items = {'local': ITEM, 'remote': ITEM}
        self.health = {'local':20.,'remote':20.}
        self.values = {}
        self.hidden = set()
        self.events = []
        self.calls = []
        self.fail = None
        self.systems = {}
        self.sprinting = False
        self.configs = {}
        self.config_writes = []
        self.settings_available = True
        self.settings_instances = {}
        self.toggle_adds = 0
        self.emit_init_callback = False
        self.restore_failure = False
        self.motions = {}
        self.motion_calls = []
        self.rotations = {}
        self.direction_calls = []
        self.motion_failure = False
        self.armor = {'local':{0:'minecraft:netherite_helmet',1:'minecraft:iron_chestplate',2:'minecraft:diamond_leggings',3:'minecraft:golden_boots'}}


engine = Engine()


class Component:
    def __init__(self, entity):
        self.entity = entity

    def GetPlayerItem(self, where, slot):
        if where == 1:
            name = engine.armor.get(self.entity,{}).get(slot)
            return {'newItemName':name} if name else None
        return {'newItemName':engine.items.get(self.entity)} if engine.items.get(self.entity) else None

    def GetAttrValue(self, attr):
        return engine.health.get(self.entity,0)

    def GetMolangValue(self, key):
        if (self.entity,key) in engine.values:
            return engine.values[(self.entity,key)]
        if key == 'query.life_time':return engine.now
        if key in ('query.is_alive','query.is_on_ground'):return 1.
        return 0.

    def Register(self,key,value):
        engine.calls.append(('Register',key,value))
        return True

    def Set(self,key,value):
        engine.values[self.entity,key] = value
        return True

    def isSprinting(self):
        return engine.sprinting

    def GetRot(self):
        return engine.rotations.get(self.entity,(0.,0.))

    def GetMotion(self):
        return engine.motions.get(self.entity,(0.,0.,0.))

    def SetPlayerMotion(self,motion):
        assert isinstance(motion,tuple) and len(motion)==3
        engine.motion_calls.append((self.entity,motion,engine.now))
        if engine.motion_failure:
            return False
        engine.motions[self.entity]=motion
        return True

    def GetActorRenderParams(self,entity,kind,detail):
        return ['geometry.humanoid.customSlim']

    def SetPlayerItemInHandVisible(self,visible,mode):
        check(mode == 0,'item visibility covers both camera views')
        if visible and engine.restore_failure:
            return False
        if visible:engine.hidden.discard(self.entity)
        else:engine.hidden.add(self.entity)
        return True

    def GetConfigData(self, name, isGlobal=False):
        return dict(engine.configs.get((name,isGlobal),{}))

    def SetConfigData(self, name, value, isGlobal=False):
        engine.configs[name,isGlobal]=dict(value)
        engine.config_writes.append((name,isGlobal,dict(value)))
        return True


class SettingInst:
    def __init__(self):
        self.defaults = {}
        self.values = {}
        self.callbacks = {}
        self.labels = {}
        self.texts = {'existing_control':'other settings remain intact'}

    # Exact current official signatures, including callback before default.
    def AddToggle(self, key, name, callback, priority=None, default=False):
        assert callable(callback) and isinstance(default,bool)
        engine.toggle_adds += 1
        self.defaults[key]=default
        self.labels[key]=name.decode('utf-8') if isinstance(name,bytes) else name
        self.values.setdefault(key,default)
        self.callbacks[key]=callback
        if engine.emit_init_callback:
            callback(key,self.values[key])
        return self

    def AddText(self, key, name, priority=None):
        self.texts[key]=name
        return self

    def SetToggleDefault(self,key,value):
        self.defaults[key]=value
        return self

    def SetToggleValue(self,key,value):
        self.values[key]=value
        return self

    def change(self,key,value):
        self.values[key]=value
        self.callbacks[key](key,value)


class SettingsWindow:
    def RegisterSettingInst(self, namespace, name=None, icon=None):
        engine.calls.append(('RegisterSettingInst',namespace,name,icon))
        if not engine.settings_available:
            return None
        return engine.settings_instances.setdefault(namespace,SettingInst())


def renderer_method(name):
    def call(self,*args):
        engine.calls.append((name,)+args)
        return name != engine.fail
    return call


for name in ('AddPlayerGeometry','AddPlayerRenderMaterial','AddPlayerTexture','AddPlayerAnimation',
             'AddPlayerAnimationController','AddPlayerScriptAnimate','AddPlayerRenderController','RebuildPlayerRender'):
    setattr(Component,name,renderer_method(name))


class Factory:
    CreateItem = CreateAttr = CreateQueryVariable = CreateActorRender = CreatePlayer = CreateConfigClient = staticmethod(Component)
    CreateRot = CreateActorMotion = staticmethod(Component)
    CreateNeteaseWindow = staticmethod(lambda entity:SettingsWindow())


class Base:
    def __init__(self,namespace,system):
        self.namespace,self.system = namespace,system
        self.listeners = []

    def ListenForEvent(self,namespace,system,event,obj,method):
        self.listeners.append((namespace,system,event,method))

    def UnListenForEvent(self,namespace,system,event,obj,method):
        self.listeners.remove((namespace,system,event,method))

    def NotifyToServer(self,event,args):
        engine.events.append(('server',event,dict(args)))

    def BroadcastToAllClient(self,event,args):
        engine.events.append(('all',event,dict(args)))

    def NotifyToClient(self,target,event,args):
        engine.events.append((target,event,dict(args)))


def api_module(name,side):
    module = types.ModuleType(name)
    module.GetEngineCompFactory = lambda: Factory()
    module.GetServerSystemCls = module.GetClientSystemCls = lambda: Base
    module.GetLocalPlayerId = lambda:'local'
    module.GetLevelId = lambda:'level'
    module.GetEngineNamespace = lambda:'Minecraft'
    module.GetEngineSystemName = lambda:'Engine'
    module.GetMinecraftEnum = lambda:types.SimpleNamespace(ItemPosType=types.SimpleNamespace(CARRIED=2,ARMOR=1),AttrType=types.SimpleNamespace(HEALTH=0))
    def direction(rotation):
        engine.direction_calls.append(rotation)
        pitch,yaw=map(math.radians,rotation)
        return (-math.sin(yaw)*math.cos(pitch),-math.sin(pitch),math.cos(yaw)*math.cos(pitch))
    module.GetDirFromRot=direction
    module.GetSystem = lambda namespace,system:engine.systems.get((side,namespace,system))
    def register(namespace,system,path):
        key=(side,namespace,system)
        check(key not in engine.systems,'one runtime owner when both packs load')
        engine.systems[key]=path
    module.RegisterSystem=register
    return module


for name in ('mod','mod.client','mod.server'):
    sys.modules[name]=types.ModuleType(name)
sys.modules['mod.client.extraClientApi']=api_module('mod.client.extraClientApi','client')
sys.modules['mod.server.extraServerApi']=api_module('mod.server.extraServerApi','server')
from HCPoemNetease.client import PoemClient
from HCPoemNetease.server import PoemServer
from HCPoemNetease import register_client,register_server


def server_ticks(server,count=1):
    for _ in range(count):
        engine.now=server.now+TICK_SECONDS
        server.on_tick()


def start_server(context='ground'):
    global engine
    engine=Engine()
    server=PoemServer('HCPoemLongComboV1','PoemServer')
    server.on_input({'__id__':'local','serial':1,'context':context})
    return server


def verify_playback():
    global engine
    for clip,move in DATA['clips'].items():
        chain=Chain()
        chain.accept({'clip':clip,'elapsed':0.,'serial':1,'startSerial':1},0.)
        real_end=move['length']/PLAYBACK_SPEED
        check(chain.tick(move['length']) is None and chain.clip==clip,
              'clip %02d continues beyond its former duration at 0.8 speed' % clip)
        check(chain.tick(real_end+RECOVERY_SECONDS-.001) is None and
              chain.tick(real_end+RECOVERY_SECONDS+.001)=='stop',
              'clip %02d uses 1.25x attack time and only 0.14 real seconds of recovery' % clip)

    engine=Engine()
    client=PoemClient('HCPoemLongComboV1','PoemClient')
    check(('Register',QUERY+'rate',.8) in engine.calls,'Molang playback rate is registered at 0.8')
    client._bind('local');client._bind('remote')
    client.on_click({})
    client.now=.25;engine.now=.25
    client._draw('local',client.chains['local'])
    check(abs(engine.values['local',QUERY+'time']-.2)<1e-9 and
          engine.values['local',QUERY+'rate']==.8 and engine.values['local',QUERY+'stamp']==.25,
          'local render phase slows down while interpolation timestamp stays in real seconds')

    # Late join during the part that the former 1x timeout would have discarded.
    clip=max(DATA['clips'],key=lambda value:DATA['clips'][value]['length'])
    move=DATA['clips'][clip];real_end=move['length']/PLAYBACK_SPEED
    phase=(move['length']+RECOVERY_SECONDS+.1+real_end)/2
    server=PoemServer('HCPoemLongComboV1','PoemServer')
    remote=Chain();remote.accept({'clip':clip,'serial':1,'startSerial':1},0.)
    server.chains['remote']=remote;server.now=phase
    server.on_ready({'__id__':'local'})
    snapshot=[e[2] for e in engine.events if e[1]=='PoemV1Snapshot'][-1]
    check(abs(snapshot['states'][0]['elapsed']-phase)<1e-9,'network snapshots continue to carry real elapsed seconds')
    client.now=10.;engine.now=10.;client.on_snapshot(snapshot)
    check(abs(client.now-client.chains['remote'].started-phase)<1e-9 and
          abs(engine.values['remote',QUERY+'time']-phase*.8)<1e-9,
          'late-join snapshot preserves the slowed attack phase without clamping to the old duration')
    client.on_tick()
    check(client.chains['remote'].clip==clip and engine.values['remote',QUERY+'active']==1,
          'remote attack remains visible after the former timeout')
    client.now=client.chains['remote'].started+real_end+RECOVERY_SECONDS+.101-TICK_SECONDS
    client.on_tick()
    check(not client.chains['remote'].clip and engine.values['remote',QUERY+'active']==0,
          'remote fallback cleanup occurs after the slowed duration and recovery')
    client.Destroy();server.Destroy()


def verify_steps():
    global engine
    for context in ('ground','sprint','air'):
        engine=Engine();engine.rotations['local']=(-75.,-90.)
        engine.motions['local']=(0.,-.18,.025)
        server=PoemServer('HCPoemLongComboV1','PoemServer')
        packet={'__id__':'local','serial':1,'context':context}
        server.on_input(packet)
        chain=server.chains['local'];move=DATA['clips'][chain.clip]
        due=move['step_at']/PLAYBACK_SPEED
        engine.rotations['local']=(80.,90.)
        server.on_input(packet)
        server.on_input(dict(packet,serial=2))
        while server.now+TICK_SECONDS<due-1e-7:
            server_ticks(server)
        check(not engine.motion_calls,context+' follow-through waits for the slowed attack wind-up')
        server_ticks(server)
        check(len(engine.motion_calls)==1 and due<=engine.motion_calls[0][2]<due+TICK_SECONDS+1e-7,
              context+' follow-through starts within one server tick of the attack contact lead-in')
        vx,vy,vz=engine.motions['local']
        check(abs(vx-STEP_FORWARD_SPEED[context])<1e-9 and vy==-.18 and abs(vz-.025)<1e-9 and
              engine.direction_calls==[(0.,-90.)],
              context+' step follows initial horizontal facing, retaining falling and lateral velocity through a spin')
        for _ in range(4):
            engine.motions['local']=(0.,-.18,.025)
            server_ticks(server)
        check(len(engine.motion_calls)==1,context+' duplicate and buffered inputs cannot repeat or stack the same step')
        server.Destroy()
    check(STEP_FORWARD_SPEED['air']<STEP_FORWARD_SPEED['ground']<STEP_FORWARD_SPEED['sprint'],
          'air follow-through is lighter and sprint follow-through is stronger')

    # Exercise every shipped clip through actual server ticks and repeated input.
    for context in ('ground','air'):
        server=start_server(context);seen=[];serial=1;last_click=0.
        while server.now<60.:
            chain=server.chains['local']
            if not seen or seen[-1]!=chain.clip:
                seen.append(chain.clip)
            if len(seen)>len(DATA[context]):
                break
            if server.now-last_click>=.13:
                serial+=1;last_click=server.now
                server.on_input({'__id__':'local','serial':serial,'context':context})
            # Stand still again after each impulse so a duplicate cannot hide
            # behind the existing-forward-speed check.
            engine.motions['local']=(0.,-.1 if context=='air' else 0.,0.)
            server_ticks(server)
        check(seen==DATA[context]+[DATA[context][0]],
              context+' sequence reaches every attack and loops continuously at 0.8 speed')
        check(len(engine.motion_calls)==len(DATA[context]),
              context+' sequence applies exactly one follow-through per attack, including immediate-start clips')
        server.Destroy()

    for label,context,velocity in (
            ('existing running speed','sprint',(0.,.15,.3)),
            ('external knockback or skill','ground',(.4,-.2,0.)),
            ('deliberate retreat','ground',(.01,0.,-.1))):
        server=start_server(context);engine.motions['local']=velocity
        server_ticks(server,12)
        check(not engine.motion_calls and engine.motions['local']==velocity,label+' is preserved without an extra impulse')
        server.Destroy()

    server=start_server();engine.motion_failure=True
    server_ticks(server,7);engine.motion_failure=False
    server_ticks(server,4)
    check(len(engine.motion_calls)==1 and 'local' not in server.steps.pending,
          'a rejected engine motion is consumed once instead of replayed later')
    server.Destroy()
    server=start_server();engine.motions['local']=None
    server_ticks(server,7);engine.motions['local']=(0.,0.,0.);server_ticks(server,4)
    check(not engine.motion_calls,'temporarily unavailable motion does not cause a delayed shove')
    server.Destroy()

    server=start_server();server_ticks(server,6)
    previous_motion=engine.motions['local']
    server.on_client_cancel({'__id__':'local'})
    check(engine.motions['local']==previous_motion and len(engine.motion_calls)==1,
          'cancellation leaves already-applied physics intact without forcibly stopping the player')
    engine.motions['local']=(0.,0.,0.)
    server.on_input({'__id__':'local','serial':2,'context':'ground'})
    server_ticks(server,6)
    check(len(engine.motion_calls)==1,'cancel and immediate restart cannot bypass the step cooldown')
    server.on_leave({'id':'local'})
    check('local' not in server.steps.last_step and 'local' not in server.steps.pending,
          'leaving removes both pending steps and per-player cooldown state')
    server.Destroy()

    for reason in ('setting_off','item_change','death','dimension_change','leave','destroy'):
        server=start_server()
        if reason=='setting_off':server.on_client_cancel({'__id__':'local'})
        elif reason=='item_change':engine.items['local']='minecraft:stick'
        elif reason=='death':engine.health['local']=0
        elif reason=='dimension_change':server.on_cancel({'playerId':'local'})
        elif reason=='leave':server.on_leave({'id':'local'})
        else:server.Destroy()
        server_ticks(server,12)
        check(not engine.motion_calls and 'local' not in server.steps.pending,
              reason+' cancels follow-through before any motion is applied')
        if reason!='destroy':server.Destroy()


def verify_settings():
    global engine
    engine=Engine()
    engine.settings_available=False
    client=PoemClient('HCPoemLongComboV1','PoemClient')
    check(client.enabled and not engine.settings_instances,'new players default to enabled; UI is not registered during __init__')
    check(any(e[2]=='UiInitFinished' for e in client.listeners),'native mod setting is connected to the real UI lifecycle event')
    client.on_ui_init()
    check(client.settings_pending,'UI not ready schedules registration retry')
    engine.settings_available=True;client.ticks=29;client.on_tick()
    control=engine.settings_instances[ITEM]
    check(control.labels[SETTINGS_KEY]=='网易版终末之诗动作' and control.values[SETTINGS_KEY] is True,
          'native mod settings contains the named switch with the correct default')
    client.on_ui_init();client.on_loaded({})
    check(engine.toggle_adds==1 and 'existing_control' in control.texts,
          'repeated UI initialization adds one switch and preserves other settings')
    client._bind('local');client._bind('remote')
    client.on_click({});client.now+=.2;client.on_click({})
    check(client.chains['local'].queued=='ground','test starts with a buffered continuation')
    remote=Chain();remote.input('air',client.now,4)
    remote_state=dict(remote.snapshot(client.now),playerId='remote')
    client.on_state(remote_state)
    local_state=dict(client.chains['local'].snapshot(client.now),playerId='local')
    check(engine.hidden=={'local','remote'},'test starts with both local and remote attack proxies visible')
    server=PoemServer('HCPoemLongComboV1','PoemServer')
    for event in [e for e in engine.events if e[1]=='PoemV1Input']:
        server.on_input(dict(event[2],__id__='local'))
    control.change(SETTINGS_KEY,False)
    server.on_client_cancel(dict([e[2] for e in engine.events if e[1]=='PoemV1Cancel'][-1],__id__='local'))
    check(not server.chains['local'].clip and server.chains['local'].queued is None and not server.steps.pending,
          'switching off cancels the local combo and its pending server follow-through')
    check(not client.enabled and not client.chains and not engine.hidden and
          all(engine.values[p,QUERY+'active']==0 for p in ('local','remote')),
          'switching off immediately restores both players and weapons and discards queued attacks')
    check(engine.configs[SETTINGS_STORAGE,True][SETTINGS_KEY] is False and control.values[SETTINGS_KEY] is False,
          'switch value is saved globally for this client and reflected in native settings')
    inputs_before=len([e for e in engine.events if e[1]=='PoemV1Input'])
    client.now+=1
    client.on_click({});client.on_hit({'playerId':'local'})
    engine.values['local','variable.attack_time']=.6
    client.on_tick()
    client.on_state(local_state);client.on_state(remote_state)
    client.on_snapshot({'lastSerial':12,'states':[dict(local_state,serial=12),remote_state]})
    check(not client.chains and not engine.hidden and
          len([e for e in engine.events if e[1]=='PoemV1Input'])==inputs_before,
          'disabled attacks, held input, remote packets and snapshots cannot reactivate animations')
    check(client.serial==12,'disabled snapshots still keep input sequence numbers synchronized')
    client.Destroy();server.Destroy()
    # Simulate a stale native UI value and an initialization callback: neither
    # may overwrite the authoritative saved preference before registration ends.
    control.values[SETTINGS_KEY]=True;engine.emit_init_callback=True
    client=PoemClient('HCPoemLongComboV1','PoemClient');client.on_loaded({})
    check(not client.enabled and control.values[SETTINGS_KEY] is False,
          'saved off preference survives client/world reload and stale native UI defaults')
    client.on_created({'playerId':'remote'});client.on_skin({'playerId':'remote'});client.on_tick()
    check(not client.bound and not engine.hidden,'renderer creation and skin updates stay inactive while disabled')
    client.on_snapshot({'lastSerial':12,'states':[]})
    control.change(SETTINGS_KEY,True)
    client.on_snapshot({'lastSerial':12,'states':[dict(local_state,serial=12),remote_state]})
    client.on_tick()
    check('local' not in client.chains and 'local' not in engine.hidden and 'remote' in client.chains,
          're-enabling rejects old local attacks while resynchronizing remote playback')
    check(len([e for e in engine.events if e[1]=='PoemV1Input'])==inputs_before,
          're-enabling during an old vanilla swing does not create a fresh attack')
    client.on_click({})
    check(client.serial==13 and client.chains['local'].clip==DATA['ground'][0],
          'next deliberate click works immediately and restarts at the first attack')
    old_state=dict(client.chains['local'].snapshot(client.now),playerId='local')
    control.change(SETTINGS_KEY,False);control.change(SETTINGS_KEY,True)
    client.on_state(old_state)
    check(not client.chains and 'local' not in engine.hidden,
          'quick off/on cannot replay a delayed acknowledgement from before the switch')
    ready_before=len([e for e in engine.events if e[1]=='PoemV1Ready'])
    for _ in range(8):client.on_tick()
    check(len([e for e in engine.events if e[1]=='PoemV1Ready'])==ready_before+1,
          're-enable retries snapshot once beyond the server ready debounce')
    for context in ('ground','sprint','air'):
        engine.sprinting=context=='sprint'
        engine.values['local','query.is_on_ground']=0. if context=='air' else 1.
        control.change(SETTINGS_KEY,True);client.now+=1.;client.on_click({})
        check(client.chains['local'].clip==DATA[context][0],context+' animation starts normally with switch enabled')
        control.change(SETTINGS_KEY,False)
        check(not client.chains and not engine.hidden,context+' animation restores immediately when disabled')
    control.change(SETTINGS_KEY,True);client.now+=1;client.on_click({})
    engine.restore_failure=True;control.change(SETTINGS_KEY,False)
    check('local' in client.hidden_items and engine.values['local',QUERY+'active']==0,
          'failed held-item restoration is retained for retry without keeping the body proxy')
    engine.restore_failure=False;client.on_tick()
    check(not engine.hidden and not client.hidden_items,'disabled client retries and completes held-item restoration')
    saved_count=len(engine.config_writes);client.Destroy()
    control.change(SETTINGS_KEY,True)
    check(len(engine.config_writes)==saved_count and not client.enabled,
          'obsolete UI callbacks after system destruction cannot change saved or live state')


def main():
    c=Chain(); c.input('ground',0,1);first=c.clip;end=DATA['clips'][first]['length']/PLAYBACK_SPEED
    check(c.tick(end-.001) is None and c.clip==first,'one tap keeps its full attack')
    check(c.tick(end+RECOVERY_SECONDS+.001)=='stop' and c.clip==0,'one tap recovers without auto-playing a whole chain')
    check(c.input('ground',end+RECOVERY_SECONDS+.1,2)=='start' and c.clip==DATA['ground'][1],'short pause continues next distinct attack')
    c.cancel(2);c.input('ground',2,3)
    check(c.clip==DATA['ground'][0],'cancellation resets the chain')
    c.input('ground',2+end-.05,4)
    check(c.tick(2+end)=='start' and c.clip==DATA['ground'][1],'fresh input buffers the next attack')
    c=Chain();c.input('air',0,1)
    check(c.clip==DATA['air'][0],'airborne input selects the airborne library')
    c.cancel(1);c.input('sprint',1,2)
    check(c.clip==DATA['sprint'][0],'sprinting input selects a run attack')
    c=Chain();seen=[];now=0
    c.input('ground',now,1)
    for number in range(23):
        seen.append(c.clip)
        end=c.started+DATA['clips'][c.clip]['length']/PLAYBACK_SPEED
        c.input('ground',end-.05,number+2)
        check(c.tick(end)=='start','fresh repeated clicks continue without a recovery gap')
        now=end
    check(seen[:22]==DATA['ground'] and seen[22]==DATA['ground'][0],'all 22 distinct ground segments, then a smooth loop')
    c=Chain();c.input('ground',0,1)
    c.tick(DATA['clips'][c.clip]['length']/PLAYBACK_SPEED+RECOVERY_SECONDS+.01)
    c.input('ground',3,2)
    check(c.clip==DATA['ground'][0],'long pause restarts cleanly')
    c=Chain();c.input('air',0,1);c.clip=DATA['air'][5];c.started=0
    c.input('air',.1,2)
    check(c.tick(DATA['clips'][c.clip]['length']/PLAYBACK_SPEED) is None,'stale input does not force another long attack after stopping')

    register_client();register_client();register_server();register_server()
    check(len(engine.systems)==2,'two installed packs share one client and one server system')
    client=PoemClient('HCPoemLongComboV1','PoemClient');server=PoemServer('HCPoemLongComboV1','PoemServer')
    check(client._bind('local'),'all documented render registrations succeed')
    check(engine.values['local',QUERY+'slim']==1,'slim player geometry is retained')
    check([engine.values['local',QUERY+'armor_'+str(i)] for i in range(4)]==[6,3,5,4],
          'equipped armor textures follow the bent player geometry')
    engine.fail='AddPlayerAnimation';check(not client._bind('remote'),'failed animation registration never activates the proxy')
    engine.fail=None
    client.on_click({})
    inputs=[e for e in engine.events if e[1]=='PoemV1Input'];check(len(inputs)==1,'left click submits one animation input')
    client.on_hit({'playerId':'local'});check(len([e for e in engine.events if e[1]=='PoemV1Input'])==1,'click and hit notifications are deduplicated')
    check('local' in engine.hidden,'active weapon proxy replaces the original held model')
    packet=inputs[-1][2];packet.update({'__id__':'local','playerId':'remote'})
    server.on_input(packet)
    check('local' in server.chains and 'remote' not in server.chains,'server uses authenticated __id__, not supplied playerId')
    first_state=[e[2] for e in engine.events if e[1]=='PoemV1State'][-1]
    old_start=client.chains['local'].started
    client.now=.1;client.on_state(first_state)
    check(client.chains['local'].started==old_start,'server acknowledgement does not restart local animation')
    server.on_input(packet)
    check(server.chains['local'].queued is None,'duplicate packet cannot enqueue another attack')
    server.on_input({'__id__':'local','serial':2,'context':'ground'})
    check(server.chains['local'].queued=='ground','network-batched legitimate input is buffered rather than dropped')
    for invalid in (True,'9',-1,1000000000):
        server.on_input({'__id__':'remote','serial':invalid})
    check('remote' not in server.chains,'malformed client inputs are rejected')
    server.now=.25;server.on_ready({'__id__':'remote'})
    snapshot=[e[2] for e in engine.events if e[1]=='PoemV1Snapshot'][-1]
    check(snapshot['states'][0]['elapsed']==.25,'late join receives current playback phase')
    client.on_snapshot({'lastSerial':8,'states':[]});check(client.serial==8,'client reload restores its monotonic input serial')
    engine.items['local']='minecraft:stick';client.on_item({})
    check('local' not in engine.hidden and engine.values['local',QUERY+'active']==0,'switching items immediately restores player and held item')
    server.ticks=2;server.on_tick()
    check(server.chains['local'].clip==0,'server also cancels after weapon change')
    engine.items['local']=ITEM;client.now=.4;client.on_click({})
    client.on_dimension({'playerId':'local'})
    check(not engine.hidden and not client.chains,'dimension change removes stale poses and restores equipment')
    engine.items['remote']=ITEM;engine.health['remote']=0
    server.now=.5;server.on_input({'__id__':'remote','serial':1})
    check('remote' not in server.chains,'dead players cannot begin attack visuals')
    client.Destroy();server.Destroy()
    check(not client.listeners and not server.listeners and not engine.hidden,'system shutdown removes listeners and restores held models')

    verify_playback()
    verify_steps()
    verify_settings()

    forbidden={'SetPos','SetMotion','Hurt','SetAttrValue','SetCommand','Attack','SetDamage','SetPlayerGameType'}
    for path in (BP/'HCPoemNetease').glob('*.py'):
        tree=ast.parse(path.read_text(encoding='utf-8'))
        check(not any(isinstance(node,(ast.JoinedStr,ast.AsyncFunctionDef,ast.AnnAssign)) for node in ast.walk(tree)),'Python 2.7 compatible syntax: '+path.name)
        check(not any(isinstance(node,ast.Call) and isinstance(node.func,ast.Attribute) and node.func.attr in forbidden for node in ast.walk(tree)),'no teleport or damage mutation: '+path.name)
        if path.name!='motion.py':
            check(not any(isinstance(node,ast.Call) and isinstance(node.func,ast.Attribute) and node.func.attr=='SetPlayerMotion' for node in ast.walk(tree)),
                  'player motion is confined to the server follow-through component: '+path.name)
        check('numpy' not in path.read_text(encoding='utf-8') and 'scipy' not in path.read_text(encoding='utf-8'),'runtime has no offline scientific dependencies: '+path.name)
    write(WORK/'runtime_validation.json',{'passed':True,'checks':checks,'check_count':len(checks),
        'api_source':'Local NetEase ModSDK documentation; fake-based integration, not a live game test',
        'settings_api_source':'https://mc.163.com/dev/mcmanual/mc-dev/mcdocs/1-ModAPI/接口/自定义UI/通用设置.html#settinginst-下的方法',
        'settings_path':'模组设置 → 终末之诗 → 网易版终末之诗动作',
        'settings_default_enabled':True,'settings_scope':'client-local, global across worlds and both requested packs',
        'playback_speed':PLAYBACK_SPEED,'recovery_real_seconds':RECOVERY_SECONDS,
        'movement':'server_horizontal_follow_through','step_forward_speed':STEP_FORWARD_SPEED,
        'live_gameplay_tested':False},True)
    print('RUNTIME_OK',len(checks),'checks')


if __name__=='__main__':main()
