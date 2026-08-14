# 觉醒怪物彼此互动与语言文本设计

> 目标：让觉醒怪物之间像一群真的长期住在夜里的居民。
> 玩家看到的，不该只是它们对玩家有反应，而是它们本来就在聊天、搭伙、互相嫌弃、翻旧账，偶尔还会真打起来。

## 0. 实现状态

截至 2026-06-08，首版已实现：

- [x] `AwakenedMobProfile` 已加入 `same/casual/collab/gossip/conflict/scuffle/authority/reply` 的 peer key 列表。
- [x] `AwakenedMobAccessor` 与 `AwakenedMobMixin` 已加入 `nextPeerInteractionGameTime` 冷却字段。
- [x] 已新增 `AwakenedMobPeerScene`，只负责 peer 场景枚举和 key 选择。
- [x] 已新增 `AwakenedMobPeerInteractionService`，只负责找附近觉醒怪物、判断场景、显示固定台词、设置单体与 pair 冷却。
- [x] `AwakenedMobBrain.serverTick` 已接入 peer 对话，位置在 Herobrine 互动之后、普通 ambient 自言自语之前。
- [x] `zh_cn.json` 与 `en_us.json` 已补首版固定 peer 台词。
- [x] `scuffle` 已作为“已有冲突时的台词场景”接入：只有两只怪物已经互相设为目标时才会触发该类台词。
- [x] `reply` 延迟第二句已接入：普通 peer 对话有概率让听者在短延迟后回一句 `peer.reply.0`。
- [x] 特殊组合 key 已接入：服务会优先使用已登记的 `peer.to_<target>.<scene>.0`，找不到再回落通用 peer key。
- [x] 真正的短时打架行为已接入：`conflict` 场景有小概率让安全族群短暂互相设为目标，事件层会在快打死对方时取消致命伤并结束打架。
- [x] AI peer scene 已接入：`same/gossip/conflict/authority` 会在低概率、玩家旁听、配置开启时请求 AI 单句，失败或未命中时回落固定语言 key。
- [x] 关系表已接入行为层：`AwakenedMobPeerRelationRules` 会影响默认场景、近距离冲突概率、短打架概率和 AI prompt 的关系描述。
- [x] 特殊组合台词已扩展为多 key 随机：常见关系组合会优先使用 `peer.to_<target>.<scene>.<index>` 的口语化固定句。

暂未实现：

- 无。当前 md 里的首轮设计项已全部接入；后续重点是继续扩充更多固定文案、特殊组合和多句变体。

## 1. 当前基础

项目现有系统已经具备：

- `AwakenedMobProfiles`：按族群定义觉醒概率、名字、偏好物品、奖励物品和台词 key。
- `AwakenedMobBrain.serverTick`：每 20 tick 执行觉醒怪物逻辑。
- `AwakenedMobRelationState`：记录觉醒怪物对玩家的关系状态。
- `SpeechBubbleAccessor`：让实体头顶显示短句。
- `ActorDialogueManager`：支持低频 AI 台词，并已有觉醒怪物对玩家、对 Herobrine 的提示词。

当前缺口：

- `AwakenedMobProfile` 只有 ambient、player、gift、hero 等场景，没有真正的 peer 场景。
- `AwakenedMobAccessor` 只有 ambient/player/hero 三类冷却，没有怪物间互动冷却。
- 现有文案更像“巡逻汇报”，不像“怪物平时就在过自己的夜生活”。

本设计的重点不是多加几个对话气泡，而是把觉醒怪物之间的关系写活。

## 2. 想要的感觉

### 2.1 玩家是旁听者

玩家看到的怪物间互动，很多时候不需要围着玩家转。

理想体验是：

- 玩家路过时，正好听见两只怪物在说话。
- 它们说的内容有一半和玩家有关，另一半跟玩家没关系。
- 玩家会意识到：“它们不是在等我触发，它们本来就在聊。”

### 2.2 怪物像夜间住民，不像站岗机器

怪物之间应该出现这些内容：

- 谁占了谁的位置。
- 谁踩了谁的网，撞了谁的箱子，挡了谁的路。
- 谁昨天丢了人，谁今天状态差。
- 临时搭伙做点事。
- 一言不合直接吵起来，甚至动手。

不该全是：

- “我负责这边，你负责那边。”
- “命令到了。”
- “目标正在接近。”
- “继续克制。”

### 2.3 Herobrine 是背景压力，不必句句提他

Herobrine 仍然重要，但他不该占满每一段 peer 互动。

更合适的比例是：

- 大多数普通日常线：不提 Herobrine。
- 少量 authority 场景：Herobrine、Simmons、Jean 带来明显压场。
- 玩家偶尔能从普通闲聊里感到：这些怪物知道夜里有更高的意志，但它们平时也有自己的关系网。

### 2.4 文风规则

目标文风：

- 口语化。
- 句型多样。
- 允许打断、反问、嫌弃、冷笑、半截话。
- 偶尔短，偶尔拐一下，不要每句都四平八稳。

明确禁用的表达方向：

- `收住`、`封住`、`收紧`、`压下来了`、`记下了` 这类机械词。
- “更高的命令经过这里”“只等一句话”“让我替你证明失败”这类模板式威压。
- 每句都像开会汇报。
- 每只怪物都像一个腔调。

推荐句型：

- 抱怨句：`你又踩我这边了。`
- 问句：`你昨晚跑哪儿去了？`
- 顺口评价：`那家伙看着就没睡好。`
- 临时搭话：`让让，我先过。`
- 翻脸句：`再碰一下试试。`
- 无所谓式回应：`行啊，你先。`

## 3. 互动类型

### 3.1 同族闲聊

触发条件：

- `AwakenedMobProfiles.sharesFamily(first, second)` 为 true。
- 两者都是觉醒怪物。
- 附近没有正在进行的激烈战斗。

内容重点：

- 昨晚去哪了。
- 谁动作慢，谁状态差。
- 谁丢了箭，谁踩空了，谁嘴又碎了。

### 3.2 跨族搭话

触发条件：

- 不同族群在同一区域短暂停留。
- 没有明显敌对动作。

内容重点：

- 随口聊天。
- 吐槽环境。
- 互相打量。
- 因为常在一个区域活动，已经形成固定嘴脸。

### 3.3 临时协作

触发条件：

- 两个怪物正在追同一个目标，或同时盯着一个位置。
- 或者它们只是碰巧要一起做一件事。

内容重点：

- 搭一句就配合。
- 语气不要像军令，更像“你去那边，我顺手处理这边”。
- 协作里可以带嫌弃，不必显得训练有素。

### 3.4 背后议论

触发条件：

- 附近有玩家、Hero、别的怪物，或刚发生了一件怪事。

内容重点：

- 评论玩家外表、走路方式、运气、状态。
- 评论别的怪物刚才干了什么蠢事。
- 评论天气、味道、地形、声音。

说明：

- 旧版 `warning` 不再单独做成一种硬邦邦场景。
- 提醒、评论、幸灾乐祸，都归进 `gossip` 或 `collab`。

### 3.5 拌嘴

触发条件：

- 抢位置。
- 挡路。
- 气味、声音、习性互相看不顺眼。
- 同一个目标被别人先碰了。

内容重点：

- 可以刻薄。
- 可以烦躁。
- 可以很小心眼。
- 不需要升级到真打，也不需要处处留情。

### 3.6 动手

触发条件：

- 拌嘴升级。
- 目标被抢。
- 被推撞、误伤、强行贴脸。

内容重点：

- 首版可以只做短时间 scuffle，不打到死。
- 表现上更像“狠狠干一架又散开”，不是长期内战。
- 少数组合可以允许更凶一点，例如袭击者和野兽、烈焰和史莱姆、蜘蛛和苦力怕。

### 3.7 上位压场

触发条件：

- Simmons 或 Jean 靠近普通觉醒怪物。
- 远古守卫也可以作为小范围压场者，但权威弱于 Simmons/Jean。

内容重点：

- 普通怪物会暂时收声，但不是全都跪着说话。
- Simmons 和 Jean 的语气要像家族上位者，不像重复播放的命令广播。

## 4. 推荐实现结构

### 4.1 Profile 新增 key 列表

建议在 `AwakenedMobProfile` 中新增：

```java
List<String> peerSameFamilyKeys;
List<String> peerCasualKeys;
List<String> peerCollabKeys;
List<String> peerGossipKeys;
List<String> peerConflictKeys;
List<String> peerScuffleKeys;
List<String> peerAuthorityKeys;
List<String> peerReplyKeys;
```

对应 key：

```text
message.herobrine_companion.awakened_mob.<root>.peer.same.<index>
message.herobrine_companion.awakened_mob.<root>.peer.casual.<index>
message.herobrine_companion.awakened_mob.<root>.peer.collab.<index>
message.herobrine_companion.awakened_mob.<root>.peer.gossip.<index>
message.herobrine_companion.awakened_mob.<root>.peer.conflict.<index>
message.herobrine_companion.awakened_mob.<root>.peer.scuffle.<index>
message.herobrine_companion.awakened_mob.<root>.peer.authority.<index>
message.herobrine_companion.awakened_mob.<root>.peer.reply.<index>
```

占位符：

- `%1$s`：目标觉醒怪物名字。
- `%2$s`：可选，目标族群显示名。
- `%3$s`：可选，附近被议论的玩家名。

首版只用 `%1$s` 就够了。

### 4.2 特殊组合 key

少数高辨识度组合可以额外加 target-specific key：

```text
message.herobrine_companion.awakened_mob.<speaker>.peer.to_<target>.<scene>.<index>
```

建议优先做这些：

- `zombie -> skeleton`
- `skeleton -> zombie`
- `creeper -> zombie`
- `spider -> skeleton`
- `witch -> raider`
- `piglin -> beast`
- `blaze -> slime`
- `guardian -> enderman`
- `simmons -> any`
- `jean -> any`

没有特殊 key 时，回落到通用 peer key。

### 4.3 Accessor 新增冷却

建议新增：

```java
long herobrineCompanion$getNextPeerInteractionGameTime();
void herobrineCompanion$setNextPeerInteractionGameTime(long gameTime);
```

建议数值：

- 单只觉醒怪物 peer 冷却：`700 ~ 1200 tick`
- 同一对怪物 pair 冷却：`1600 ~ 2400 tick`
- scuffle 冷却：比普通对话更长，建议 `2400+ tick`

`nextPeerInteractionGameTime` 不需要写入 NBT。

### 4.4 触发流程

伪流程：

```text
serverTick(mob)
  if not awakened return
  if no nearby player audience return
  if now < mob.nextPeerInteraction return
  peer = findNearestAwakenedPeer(mob, 10)
  if peer == null return
  if pairCooldownActive(mob, peer) return
  scene = classifyPeerScene(mob, peer, nearbyContext)
  speaker = chooseSpeaker(mob, peer, scene)
  lineKey = choosePeerLine(speaker, peer, scene)
  speak(speaker, translatable(lineKey, peerName))
  maybe schedule reply
  maybe start short scuffle behavior
  update speaker, peer and pair cooldowns
```

### 4.5 AI 对话扩展

固定台词稳定后，再加：

```java
ActorDialoguePrompts.buildAwakenedPeerScene(
    Mob speaker,
    Mob target,
    AwakenedMobProfile speakerProfile,
    AwakenedMobProfile targetProfile,
    AwakenedMobPeerScene scene,
    Player audience
)
```

实现状态：

- [x] 已新增 `ActorDialoguePrompts.buildAwakenedPeerScene(...)`，只负责生成怪物对怪物的 AI prompt。
- [x] `AwakenedMobPeerInteractionService` 已在 `same/gossip/conflict/authority` 低概率调用 `ActorDialogueManager`。
- [x] `scuffle` 不走 AI，仍使用固定短句，避免打架场景被异步生成拖慢。
- [x] AI 结果只生成开场单句；固定台词路径仍保留延迟 `reply`，避免 AI 双句链条把同伴对话刷得太密。

AI 适合的场景：

- 同名怪物多次相遇后的闲聊。
- 吵架升级前的最后一句。
- 玩家经过时听见的背后议论。
- Simmons/Jean 对普通怪物的家族式压场。

AI 不适合的场景：

- 每次追击都来一句。
- 每次路过都生成长句。
- 一堆怪物一起抢着说。

## 5. 场景选择规则

优先级从高到低：

| 优先级 | 场景 | 条件 |
| --- | --- | --- |
| 1 | authority | Simmons/Jean 靠近普通觉醒怪物 |
| 2 | scuffle | 最近被碰撞、误伤、抢目标，或 conflict 升级 |
| 3 | conflict | 路径重叠、抢地盘、互相嫌弃 |
| 4 | collab | 正在追同一目标，或同时盯着一个点 |
| 5 | gossip | 附近有玩家、Hero，或有怪事刚发生 |
| 6 | same | 同族怪物普通闲聊 |
| 7 | casual | 默认跨族搭话 |

补充规则：

- `same` 和 `casual` 是常态。
- `collab`、`conflict`、`scuffle` 是事件驱动。
- `authority` 只给少数高位角色，不要滥用。

## 6. 怪物间关系表

这张表不是阵营硬规则，而是写台词和扩展特殊组合 key 的素材池。

关系可以按四条轴来写：

- 熟人轴：同族、常在同一区域刷新，像老邻居，能互损也能顺手帮忙。
- 习性轴：移动方式、声音、气味、光线、潮湿、火、箱子、视线，会天然引发小摩擦。
- 利益轴：谁抢目标，谁占位置，谁挡路，谁碰了箱子或药锅。
- 上位轴：Simmons、Jean、远古守卫这类角色会改变周围语气，但不必让普通怪全都像下属汇报。

| 组合 | 默认气氛 | 推荐场景 | 典型内容 |
| --- | --- | --- | --- |
| 僵尸 + 僵尸族 | 老街坊 | same/gossip | 谁昨晚掉队，谁身上少了块肉，谁又在井边站太久 |
| 僵尸 + 溺尸 | 湿冷亲戚 | same/conflict | 一个嫌对方滴水，一个嫌岸上太干，偶尔交换水边消息 |
| 僵尸 + 尸壳 | 慢吞吞的旧识 | same/casual | 一个带沙味，一个带泥味，互相嫌弃但会一起堵路 |
| 僵尸 + 骷髅 | 熟人同事 | collab/casual | 一个嫌慢，一个嫌吵，追人时配合很顺 |
| 僵尸 + 苦力怕 | 小心相处 | conflict/casual | 一个怕被炸，一个怕被推，聊天总隔着两步 |
| 僵尸 + 女巫 | 被使唤惯了 | collab/conflict | 女巫让它搬东西、试药、挡门；僵尸嘴上不服，脚还是过去 |
| 僵尸 + 袭击者 | 互相看不上 | conflict/gossip | 袭击者嫌僵尸没脑子，僵尸嫌袭击者吵得像铁锅 |
| 僵尸 + 史莱姆 | 迟钝组合 | casual/conflict | 一个走得慢，一个弹得乱，经常在窄路上互相堵 |
| 骷髅 + 骷髅族 | 老弓手 | same/gossip | 比箭法、比谁骨头响，互相拆穿昨晚射偏的事 |
| 骷髅 + 流浪者 | 冷脸同行 | same/casual | 一个嫌对方太冻，一个嫌普通骷髅太急 |
| 骷髅 + 凋灵骷髅 | 压力很大 | authority/conflict | 普通骷髅会嘴硬，但凋灵骷髅一句话就让气氛变冷 |
| 骷髅 + 蜘蛛 | 互相嫌弃又常搭伙 | collab/conflict | 一个爱高处，一个嫌网碍事，追人时却很会卡角度 |
| 骷髅 + 苦力怕 | 远近分工 | collab/conflict | 骷髅要视野，苦力怕要靠近，谁挡谁都容易急 |
| 骷髅 + 幻翼 | 天上地下互嘴 | gossip/casual | 一个说天上看得清，一个说地上才打得准 |
| 骷髅 + 袭击者 | 嘴硬竞争 | conflict/gossip | 都觉得自己更会当哨兵，谁先发现玩家就要炫耀 |
| 苦力怕 + 苦力怕 | 小声社交 | same/casual | 互相提醒别靠太近，又都不想承认自己紧张 |
| 苦力怕 + 蜘蛛 | 安静组合 | casual/conflict | 一个怕乱，一个怕亮；蜘蛛占门口，苦力怕就更焦虑 |
| 苦力怕 + 末影族 | 彼此别盯 | conflict/casual | 苦力怕怕被突然贴脸，末影族烦它一惊一乍 |
| 苦力怕 + 袭击者 | 被当成麻烦 | conflict/collab | 袭击者想把它往前推，苦力怕不想成为别人计划的一部分 |
| 蜘蛛 + 蜘蛛族 | 梁上邻居 | same/conflict | 谁占哪根梁，谁的网破了，谁把虫子吃光了 |
| 蜘蛛 + 末影族 | 空间互扰 | conflict/casual | 一个走墙，一个闪现，谁都觉得对方路线奇怪 |
| 蜘蛛 + 女巫 | 林中熟人 | collab/gossip | 女巫借网拦路，蜘蛛嫌药味重，但知道她有用 |
| 蜘蛛 + 幻翼 | 上方地盘争夺 | conflict/gossip | 一个占屋檐，一个抢风口，经常互相嫌翅膀和腿太多 |
| 末影族 + 末影族 | 冷淡同类 | same/casual | 交换方块、沉默站位、用很短的话评价附近动静 |
| 末影族 + 潜影贝 | 末地老规矩 | same/authority | 一个走来走去，一个待着不动，互相嫌对方麻烦 |
| 末影族 + 守卫者 | 天生不对盘 | conflict/gossip | 一个烦视线，一个烦距离；水和空间都让对方不舒服 |
| 末影族 + 幻翼 | 冷淡默契 | casual/gossip | 都习惯从上面看别人，说话少，但能一起盯玩家 |
| 末影族 + Jean | 末地上位 | authority/gossip | Jean 不需要多说，末影族会少一点动作，但不必卑微 |
| 女巫 + 女巫 | 锅边同行 | same/gossip | 互相闻药味，嘲笑对方锅里少了什么，偶尔交换配方 |
| 女巫 + 袭击者 | 交易伙伴 | collab/conflict | 谈条件，互损，照样合作；账、药、战利品都能吵起来 |
| 女巫 + 史莱姆 | 实验对象 | casual/conflict | 女巫想取一点黏液，史莱姆觉得她的瓶子不怀好意 |
| 女巫 + 烈焰 | 火候之争 | collab/conflict | 女巫要火候，烈焰嫌她锅太潮，合作时像在拌嘴做饭 |
| 袭击者 + 袭击者 | 营地同伙 | same/gossip | 偷酒、分账、谁站岗偷懒，嘴上互骂但知道规矩 |
| 袭击者 + 猪灵 | 两套生意经 | casual/conflict | 一个算账，一个数金；都觉得对方贪，但能谈条件 |
| 袭击者 + 野兽类 | 管不住的冲锋 | collab/conflict | 袭击者想指路，野兽只想撞过去，事后还要算损失 |
| 史莱姆 + 史莱姆族 | 弹来弹去 | same/casual | 谁碎了，谁黏在墙上，谁刚才弹得太响 |
| 史莱姆 + 烈焰 | 经常吵 | conflict/scuffle | 一个嫌干，一个嫌湿；靠太近就像水火脾气撞上 |
| 史莱姆 + 守卫者 | 水边怪邻居 | casual/conflict | 史莱姆喜欢湿冷，守卫者嫌它把水搅浑 |
| 史莱姆 + 猪灵 | 货物麻烦 | conflict/gossip | 猪灵怕它黏住金子，史莱姆觉得猪灵老是大惊小怪 |
| 烈焰 + 烈焰族 | 炉火同类 | same/casual | 比火色、比热度，谁灰了就会被笑一晚上 |
| 烈焰 + 恶魂 | 下界远亲 | casual/gossip | 一个贴地烧，一个远处叫，互相嫌对方动静太大 |
| 烈焰 + 猪灵 | 下界合作 | collab/conflict | 猪灵想谈价，烈焰懒得听；真有外人来又能一起动手 |
| 烈焰 + 野兽类 | 容易失控 | conflict/scuffle | 一个怕被撞散火，一个嫌火烫鼻子，冲突来得快 |
| 守卫者 + 守卫者族 | 水下哨友 | same/collab | 谁看哪条水道，谁刚才尾巴扫了谁，语气冷但熟 |
| 守卫者 + 溺尸 | 水里旧客 | casual/gossip | 一个守地盘，一个从水里晃过，互相知道对方脾气 |
| 守卫者 + 远古守卫 | 深水压场 | authority/same | 远古守卫一句话会让周围水声都像矮半截 |
| 幻翼 + 幻翼 | 熬夜同伴 | same/gossip | 谁撞屋顶，谁翅膀歪，谁盯上了没睡觉的玩家 |
| 幻翼 + Jean | 高空上下级 | authority/casual | Jean 嫌它们吵，幻翼会绕远一点，但还会小声抱怨 |
| 猪灵 + 猪灵族 | 金子亲戚 | same/conflict | 数金、藏箱子、谁拿多了，三句话里总有一句谈价 |
| 猪灵 + 野兽类 | 管不住但离不开 | collab/conflict | 一个管箱子，一个管冲锋；吵归吵，分工很固定 |
| 猪灵 + 凋灵骷髅 | 下界旧怨 | conflict/scuffle | 都不爱对方靠近，话里带刺，动手概率可以比普通组合高 |
| 野兽类 + 野兽类 | 粗暴同窝 | same/scuffle | 顶角、抢路、抢食，打一下又散开，很少讲道理 |
| 远古守卫 + 任意普通怪 | 地底压场 | authority/gossip | 它不需要吼，普通怪会少说废话，但仍可能小声顶一句 |
| 远古守卫 + Simmons | 两种沉重 | authority/conflict | 一个像地底深处，一个像凋亡王座，谁都不太愿意让步 |
| 远古守卫 + Jean | 深处和高空 | authority/gossip | 一个听心跳，一个看天幕；对彼此保持距离和戒备 |
| Simmons + 凋灵骷髅 | 凋亡家底 | authority/same | 凋灵骷髅会更像近卫，Simmons 可以嫌它们不够漂亮 |
| Simmons + 普通怪 | 家族上位 | authority/conflict | 会骂，会用，也会嫌它们不够体面 |
| Simmons + Jean | 家族兄妹 | casual/conflict | 都有上位感，一个沉重，一个高傲；互相顶嘴但承认对方分量 |
| Jean + 普通怪 | 高空上位 | authority/gossip | 懒得多说，但一句就让场面变冷 |
| Jean + 末地怪物 | 女王式熟人 | authority/same | 她不必解释，末地怪物知道她烦什么，也知道别靠太近 |

实现备注：

- 当前代码里的关系规则覆盖了表中一批高辨识度组合，优先让这些组合更容易走 `collab/conflict/gossip/casual/authority` 等对应场景。
- 特殊组合 key 已支持多句随机，后续可以继续按 `推荐场景` 补 `peer.to_<target>.<scene>.2/.3`。
- AI peer scene 已经能读取双方 root、scene 和关系提示；没写死特殊 key 的组合，也能通过 prompt 风格自然体现关系。

## 7. 通用中文台词稿

下面文本先作为文案稿，不直接等同最终 JSON。迁移到语言文件时保持 `%1$s` 占位符。

### Zombie

```json
"message.herobrine_companion.awakened_mob.zombie.peer.same.0": "%1$s，你昨晚跑哪儿去了？我在井边站了半天。",
"message.herobrine_companion.awakened_mob.zombie.peer.casual.0": "%1$s，这块地挺软，站着舒服。你别老踩来踩去。",
"message.herobrine_companion.awakened_mob.zombie.peer.collab.0": "%1$s，你把他往坡下赶。我腿慢，正好堵底下。",
"message.herobrine_companion.awakened_mob.zombie.peer.gossip.0": "%1$s，那个玩家又来了。闻着像昨晚没睡好。",
"message.herobrine_companion.awakened_mob.zombie.peer.conflict.0": "%1$s，别挤我。你胳膊掉了也别往我身上挂。",
"message.herobrine_companion.awakened_mob.zombie.peer.scuffle.0": "%1$s，行啊，那就来。谁先散架谁认输。",
"message.herobrine_companion.awakened_mob.zombie.peer.authority.0": "%1$s来了。少说两句，别在这时候犯傻。",
"message.herobrine_companion.awakened_mob.zombie.peer.reply.0": "知道了，%1$s。我过去看看。"
```

### Skeleton

```json
"message.herobrine_companion.awakened_mob.skeleton.peer.same.0": "%1$s，你箭袋呢？别又说是风偷的。",
"message.herobrine_companion.awakened_mob.skeleton.peer.casual.0": "%1$s，月亮挺正。今天手感应该不错。",
"message.herobrine_companion.awakened_mob.skeleton.peer.collab.0": "%1$s，把他逼出树后。剩下的不用你管。",
"message.herobrine_companion.awakened_mob.skeleton.peer.gossip.0": "%1$s，那个人走路还是那么响，隔老远都烦。",
"message.herobrine_companion.awakened_mob.skeleton.peer.conflict.0": "%1$s，离我远点。你挡着我瞄了。",
"message.herobrine_companion.awakened_mob.skeleton.peer.scuffle.0": "%1$s，嘴硬就算了，骨头别跟着硬。",
"message.herobrine_companion.awakened_mob.skeleton.peer.authority.0": "%1$s来了。先别犯贱。",
"message.herobrine_companion.awakened_mob.skeleton.peer.reply.0": "行，%1$s。我看着呢。"
```

### Creeper

```json
"message.herobrine_companion.awakened_mob.creeper.peer.same.0": "%1$s，你别总盯着我看，我会紧张。",
"message.herobrine_companion.awakened_mob.creeper.peer.casual.0": "%1$s，今晚风小。挺适合发呆的。",
"message.herobrine_companion.awakened_mob.creeper.peer.collab.0": "%1$s，你站左边。我离门远一点，省得大家一起后悔。",
"message.herobrine_companion.awakened_mob.creeper.peer.gossip.0": "%1$s，那个人一跑起来我就头皮发麻。",
"message.herobrine_companion.awakened_mob.creeper.peer.conflict.0": "%1$s，别推我。真炸了别怪我没说。",
"message.herobrine_companion.awakened_mob.creeper.peer.scuffle.0": "%1$s，你再靠一步试试。咱们谁都别想体面。",
"message.herobrine_companion.awakened_mob.creeper.peer.authority.0": "%1$s来了。你别抖，我也尽量。",
"message.herobrine_companion.awakened_mob.creeper.peer.reply.0": "知道了，%1$s。我先离你远一点。"
```

### Spider

```json
"message.herobrine_companion.awakened_mob.spider.peer.same.0": "%1$s，梁上那位置是我的。你换一根。",
"message.herobrine_companion.awakened_mob.spider.peer.casual.0": "%1$s，今晚虫子少。倒是人味挺重。",
"message.herobrine_companion.awakened_mob.spider.peer.collab.0": "%1$s，你从上面绕，我从墙角过去。",
"message.herobrine_companion.awakened_mob.spider.peer.gossip.0": "%1$s，那个人每次都先看地，不看头顶。",
"message.herobrine_companion.awakened_mob.spider.peer.conflict.0": "%1$s，别碰我的网。碰坏了你自己补。",
"message.herobrine_companion.awakened_mob.spider.peer.scuffle.0": "%1$s，还咬我？行，掉下去别喊。",
"message.herobrine_companion.awakened_mob.spider.peer.authority.0": "%1$s来了。贴好，别乱爬。",
"message.herobrine_companion.awakened_mob.spider.peer.reply.0": "看见了，%1$s。他还不知道我在这。"
```

### Enderman

```json
"message.herobrine_companion.awakened_mob.enderman.peer.same.0": "%1$s，你拿着那块石头转半天了，不晕吗。",
"message.herobrine_companion.awakened_mob.enderman.peer.casual.0": "%1$s，这地方太窄。我肩膀都不舒服。",
"message.herobrine_companion.awakened_mob.enderman.peer.collab.0": "%1$s，你堵门，我去他背后站着。",
"message.herobrine_companion.awakened_mob.enderman.peer.gossip.0": "%1$s，那个人又在偷看。胆子不大，眼神倒挺直。",
"message.herobrine_companion.awakened_mob.enderman.peer.conflict.0": "%1$s，别贴这么近。空间都让你挤皱了。",
"message.herobrine_companion.awakened_mob.enderman.peer.scuffle.0": "%1$s，再碰我一下，我把你扔到另一头去。",
"message.herobrine_companion.awakened_mob.enderman.peer.authority.0": "%1$s来了。别乱动，省得待会儿谁都难看。",
"message.herobrine_companion.awakened_mob.enderman.peer.reply.0": "行，%1$s。我换个地方盯着。"
```

### Witch

```json
"message.herobrine_companion.awakened_mob.witch.peer.same.0": "%1$s，那瓶不是喝的。你上次已经证明过一次了。",
"message.herobrine_companion.awakened_mob.witch.peer.casual.0": "%1$s，锅里还差点味道。可惜你闻不出来。",
"message.herobrine_companion.awakened_mob.witch.peer.collab.0": "%1$s，把门口那家伙拖住，我腾个手调药。",
"message.herobrine_companion.awakened_mob.witch.peer.gossip.0": "%1$s，那个人脸色差得像我刚熬坏的一锅。",
"message.herobrine_companion.awakened_mob.witch.peer.conflict.0": "%1$s，离我桌子远点。你那手一伸我就想骂人。",
"message.herobrine_companion.awakened_mob.witch.peer.scuffle.0": "%1$s，真要翻脸？那你先想好喝哪瓶。",
"message.herobrine_companion.awakened_mob.witch.peer.authority.0": "%1$s来了。把嘴闭上，今天不是你逞能的时候。",
"message.herobrine_companion.awakened_mob.witch.peer.reply.0": "好啊，%1$s。出了事别赖我。"
```

### Raider

```json
"message.herobrine_companion.awakened_mob.raider.peer.same.0": "%1$s，你昨晚偷喝营火边那瓶酒了吧。",
"message.herobrine_companion.awakened_mob.raider.peer.casual.0": "%1$s，这条路今天真冷清，连个能宰的都没有。",
"message.herobrine_companion.awakened_mob.raider.peer.collab.0": "%1$s，你站前头吓人，我在后面算账。",
"message.herobrine_companion.awakened_mob.raider.peer.gossip.0": "%1$s，那个人看着穷，包里东西未必少。",
"message.herobrine_companion.awakened_mob.raider.peer.conflict.0": "%1$s，少拿旗杆戳我。再戳我就折了它。",
"message.herobrine_companion.awakened_mob.raider.peer.scuffle.0": "%1$s，来，别磨叽。打完谁站着谁说了算。",
"message.herobrine_companion.awakened_mob.raider.peer.authority.0": "%1$s来了。都像样点，别丢脸。",
"message.herobrine_companion.awakened_mob.raider.peer.reply.0": "懂，%1$s。我先盯着那边。"
```

### Slime

```json
"message.herobrine_companion.awakened_mob.slime.peer.same.0": "%1$s，你今天怎么这么碎，路上摔了？",
"message.herobrine_companion.awakened_mob.slime.peer.casual.0": "%1$s，这墙凉凉的，我喜欢。",
"message.herobrine_companion.awakened_mob.slime.peer.collab.0": "%1$s，你从缝里过去，我从地上滚。",
"message.herobrine_companion.awakened_mob.slime.peer.gossip.0": "%1$s，那个人脚步真重，我隔着石头都嫌吵。",
"message.herobrine_companion.awakened_mob.slime.peer.conflict.0": "%1$s，别弹我脸上。黏死了。",
"message.herobrine_companion.awakened_mob.slime.peer.scuffle.0": "%1$s，来啊，谁先糊墙上谁输。",
"message.herobrine_companion.awakened_mob.slime.peer.authority.0": "%1$s来了。别闹，先看他想干嘛。",
"message.herobrine_companion.awakened_mob.slime.peer.reply.0": "知道，%1$s。我往那边挪挪。"
```

### Blaze

```json
"message.herobrine_companion.awakened_mob.blaze.peer.same.0": "%1$s，你今天火色发灰，昨晚没睡好？",
"message.herobrine_companion.awakened_mob.blaze.peer.casual.0": "%1$s，这里风一吹，火都没脾气了。",
"message.herobrine_companion.awakened_mob.blaze.peer.collab.0": "%1$s，你去吓他，我在后头补一把火。",
"message.herobrine_companion.awakened_mob.blaze.peer.gossip.0": "%1$s，那个人一身潮气，靠近就烦。",
"message.herobrine_companion.awakened_mob.blaze.peer.conflict.0": "%1$s，离远点。你把我火都蹭偏了。",
"message.herobrine_companion.awakened_mob.blaze.peer.scuffle.0": "%1$s，想打就早点说，别绕圈子。",
"message.herobrine_companion.awakened_mob.blaze.peer.authority.0": "%1$s来了。别乱喷，先听他说。",
"message.herobrine_companion.awakened_mob.blaze.peer.reply.0": "行，%1$s。我看着门口。"
```

### Guardian

```json
"message.herobrine_companion.awakened_mob.guardian.peer.same.0": "%1$s，你刚才游太快了，尾巴扫我一脸水。",
"message.herobrine_companion.awakened_mob.guardian.peer.casual.0": "%1$s，今天这片水倒是安静，像在憋事。",
"message.herobrine_companion.awakened_mob.guardian.peer.collab.0": "%1$s，你看下边，我盯上边那道影子。",
"message.herobrine_companion.awakened_mob.guardian.peer.gossip.0": "%1$s，那个人在水里越慌越显眼。",
"message.herobrine_companion.awakened_mob.guardian.peer.conflict.0": "%1$s，别拿光照我眼。我本来就看得见。",
"message.herobrine_companion.awakened_mob.guardian.peer.scuffle.0": "%1$s，再撞一次试试。今天就看谁先翻肚皮。",
"message.herobrine_companion.awakened_mob.guardian.peer.authority.0": "%1$s来了。先别争，省得一起挨看。",
"message.herobrine_companion.awakened_mob.guardian.peer.reply.0": "看见了，%1$s。我去右边那条水道。"
```

### Phantom

```json
"message.herobrine_companion.awakened_mob.phantom.peer.same.0": "%1$s，你昨晚是不是撞屋顶了，翅膀都歪了。",
"message.herobrine_companion.awakened_mob.phantom.peer.casual.0": "%1$s，今夜云挺薄，飞着省劲。",
"message.herobrine_companion.awakened_mob.phantom.peer.collab.0": "%1$s，你从前面晃他，我从后面俯下去。",
"message.herobrine_companion.awakened_mob.phantom.peer.gossip.0": "%1$s，那个人眼圈黑得跟请我吃饭似的。",
"message.herobrine_companion.awakened_mob.phantom.peer.conflict.0": "%1$s，别挤同一阵风。你翅膀太吵。",
"message.herobrine_companion.awakened_mob.phantom.peer.scuffle.0": "%1$s，再抢我这条线，我就先啄你。",
"message.herobrine_companion.awakened_mob.phantom.peer.authority.0": "%1$s来了。飞高点，别挡路。",
"message.herobrine_companion.awakened_mob.phantom.peer.reply.0": "知道了，%1$s。我绕一圈再回来。"
```

### Piglin

```json
"message.herobrine_companion.awakened_mob.piglin.peer.same.0": "%1$s，你把那块金子放下，我数过了。",
"message.herobrine_companion.awakened_mob.piglin.peer.casual.0": "%1$s，这地方除了热，倒也算宽敞。",
"message.herobrine_companion.awakened_mob.piglin.peer.collab.0": "%1$s，你去谈，我盯着他手里有没有别的。",
"message.herobrine_companion.awakened_mob.piglin.peer.gossip.0": "%1$s，那个人嘴上说没钱，脚步倒挺横。",
"message.herobrine_companion.awakened_mob.piglin.peer.conflict.0": "%1$s，别碰我的箱子。碰一下我就跟你翻脸。",
"message.herobrine_companion.awakened_mob.piglin.peer.scuffle.0": "%1$s，行，今天不谈价。直接动手。",
"message.herobrine_companion.awakened_mob.piglin.peer.authority.0": "%1$s来了。都老实点，别把场面弄脏。",
"message.herobrine_companion.awakened_mob.piglin.peer.reply.0": "听见了，%1$s。我先把东西挪开。"
```

### Beast

```json
"message.herobrine_companion.awakened_mob.beast.peer.same.0": "%1$s，你别老闻我，我又不是吃的。",
"message.herobrine_companion.awakened_mob.beast.peer.casual.0": "%1$s，这地上味道乱七八糟，我都饿了。",
"message.herobrine_companion.awakened_mob.beast.peer.collab.0": "%1$s，你把他往林子里赶，空地留给我跑。",
"message.herobrine_companion.awakened_mob.beast.peer.gossip.0": "%1$s，那个人腿在抖，还装得挺镇定。",
"message.herobrine_companion.awakened_mob.beast.peer.conflict.0": "%1$s，别踩我尾巴。真不是故意我也不信。",
"message.herobrine_companion.awakened_mob.beast.peer.scuffle.0": "%1$s，再顶我一下试试。今天谁也别装好脾气。",
"message.herobrine_companion.awakened_mob.beast.peer.authority.0": "%1$s来了。站好，别像刚放出来似的。",
"message.herobrine_companion.awakened_mob.beast.peer.reply.0": "知道，%1$s。我先绕过去。"
```

### Ancient

```json
"message.herobrine_companion.awakened_mob.ancient.peer.same.0": "%1$s，你每次开口都像山在咳嗽。",
"message.herobrine_companion.awakened_mob.ancient.peer.casual.0": "%1$s，今天底下挺安静。安静得有点假。",
"message.herobrine_companion.awakened_mob.ancient.peer.collab.0": "%1$s，你听左边，我听更深的地方。",
"message.herobrine_companion.awakened_mob.ancient.peer.gossip.0": "%1$s，那个人心跳乱成这样，还敢往里走。",
"message.herobrine_companion.awakened_mob.ancient.peer.conflict.0": "%1$s，离我远点。你震得我头疼。",
"message.herobrine_companion.awakened_mob.ancient.peer.scuffle.0": "%1$s，再碰这片地一下，我就不跟你客气。",
"message.herobrine_companion.awakened_mob.ancient.peer.authority.0": "%1$s来了。安静点，别惹他回头。",
"message.herobrine_companion.awakened_mob.ancient.peer.reply.0": "我知道，%1$s。我在听。"
```

### Simmons

```json
"message.herobrine_companion.awakened_mob.simmons.peer.same.0": "%1$s，左边那个头能不能别老插嘴。听着烦。",
"message.herobrine_companion.awakened_mob.simmons.peer.casual.0": "%1$s，今天这片天色不错。适合有人倒霉。",
"message.herobrine_companion.awakened_mob.simmons.peer.collab.0": "%1$s，你去前面闹，我在后面看他还能撑多久。",
"message.herobrine_companion.awakened_mob.simmons.peer.gossip.0": "%1$s，那个人嘴硬得很，骨头应该也不软。",
"message.herobrine_companion.awakened_mob.simmons.peer.conflict.0": "%1$s，别在我旁边摆威风。你撑不起这个场。",
"message.herobrine_companion.awakened_mob.simmons.peer.scuffle.0": "%1$s，不服就过来。我正好心情一般。",
"message.herobrine_companion.awakened_mob.simmons.peer.authority.0": "%1$s，站稳点。父亲在看。",
"message.herobrine_companion.awakened_mob.simmons.peer.reply.0": "行，%1$s。你先说完。"
```

### Jean

```json
"message.herobrine_companion.awakened_mob.jean.peer.same.0": "%1$s，你老在我眼前绕，我看着都烦。",
"message.herobrine_companion.awakened_mob.jean.peer.casual.0": "%1$s，今天风不错。难得没谁来送命。",
"message.herobrine_companion.awakened_mob.jean.peer.collab.0": "%1$s，你把地上那群东西赶散，我懒得一头撞进去。",
"message.herobrine_companion.awakened_mob.jean.peer.gossip.0": "%1$s，那个人抬头的时候居然还敢瞪我。挺有意思。",
"message.herobrine_companion.awakened_mob.jean.peer.conflict.0": "%1$s，别碰柱子。你碰一下，我就想把你丢下去。",
"message.herobrine_companion.awakened_mob.jean.peer.scuffle.0": "%1$s，来。别只会在下面吵。",
"message.herobrine_companion.awakened_mob.jean.peer.authority.0": "%1$s，闭嘴。父亲不想听你们乱叫。",
"message.herobrine_companion.awakened_mob.jean.peer.reply.0": "听见了，%1$s。让他们再跑两步。"
```

## 8. 特殊组合台词稿

这些用于增强辨识度。可以先不进首版实现，等通用 peer key 跑通后补。

### Zombie 与 Skeleton

```json
"message.herobrine_companion.awakened_mob.zombie.peer.to_skeleton.collab.0": "%1$s，箭别往我后背飞。我这边肉不多，经不起挑。",
"message.herobrine_companion.awakened_mob.skeleton.peer.to_zombie.casual.0": "%1$s，你慢归慢，挡路倒是一把好手。"
```

### Creeper 与 Zombie

```json
"message.herobrine_companion.awakened_mob.creeper.peer.to_zombie.conflict.0": "%1$s，别贴我这么近。我心里一慌，大家都不好看。",
"message.herobrine_companion.awakened_mob.zombie.peer.to_creeper.conflict.0": "%1$s，你站远点。我不想第二次死得这么亮。"
```

### Spider 与 Skeleton

```json
"message.herobrine_companion.awakened_mob.spider.peer.to_skeleton.collab.0": "%1$s，右边那根梁给你。我不跟骨头抢高处。",
"message.herobrine_companion.awakened_mob.skeleton.peer.to_spider.conflict.0": "%1$s，网挂归挂，别挂我脸上。"
```

### Witch 与 Raider

```json
"message.herobrine_companion.awakened_mob.witch.peer.to_raider.conflict.0": "%1$s，你的账本算不出我锅里这口会不会炸。",
"message.herobrine_companion.awakened_mob.raider.peer.to_witch.collab.0": "%1$s，你先配药，我帮你把门口那些蠢货赶远点。"
```

### Piglin 与 Beast

```json
"message.herobrine_companion.awakened_mob.piglin.peer.to_beast.conflict.0": "%1$s，獠牙朝外，别朝我的箱子。",
"message.herobrine_companion.awakened_mob.beast.peer.to_piglin.casual.0": "%1$s，金子不能吃。你守着它不累吗。"
```

### Blaze 与 Slime

```json
"message.herobrine_companion.awakened_mob.blaze.peer.to_slime.conflict.0": "%1$s，你一过来我就觉得四周都黏了。",
"message.herobrine_companion.awakened_mob.slime.peer.to_blaze.conflict.0": "%1$s，你离我远点。你一热，我就烦。"
```

### Guardian 与 Enderman

```json
"message.herobrine_companion.awakened_mob.guardian.peer.to_enderman.conflict.0": "%1$s，别老一闪一闪。水都让你弄烦了。",
"message.herobrine_companion.awakened_mob.enderman.peer.to_guardian.conflict.0": "%1$s，你那眼神太直了。看久了让人想走。"
```

### Simmons 与普通觉醒怪物

```json
"message.herobrine_companion.awakened_mob.simmons.peer.to_common.authority.0": "%1$s，别丢人。父亲看着呢。",
"message.herobrine_companion.awakened_mob.simmons.peer.to_common.authority.1": "%1$s，闹归闹，轮不到你把场面弄坏。"
```

### Jean 与普通觉醒怪物

```json
"message.herobrine_companion.awakened_mob.jean.peer.to_common.authority.0": "%1$s，地上那点吵闹，你们是真没完。",
"message.herobrine_companion.awakened_mob.jean.peer.to_common.authority.1": "%1$s，给我安分一点。别逼我下来。"
```

## 9. 双句互动样例

### 僵尸和骷髅随口聊天

```text
Zombie: Ashstring，你箭袋呢？昨晚不是还背着。
Skeleton: Gravewalker，被你踩断了。你倒是忘得快。
```

### 蜘蛛和苦力怕抢位置

```text
Spider: Hushspark，门口给我。你去墙外站着。
Creeper: 为什么又是我？你们是不是都嫌我近。
```

### 女巫和袭击者边做事边拌嘴

```text
Witch: Ash Ledger，你手脏，别碰我那锅。
Raider: 行，那你也别碰我的账。上次少了一页。
```

### 猪灵和野兽吵箱子

```text
Piglin: Bramblehide，你再撞一次箱子，我就先跟你算。
Beast: Gilt Snout，那你把肉给我分点。我就不撞。
```

### 烈焰和史莱姆真翻脸

```text
Blaze: Seep，你离我远点。
Slime: 不远。就站这。你能怎样？
Blaze: 那就试试。
```

### Jean 路过，场面降温

```text
Jean: Night Draft，别绕着柱子叫了。
Phantom: 听见了，jean。我只是看他不顺眼。
```

## 10. 首版交付建议

第一版只做：

- 通用 peer key：`same`、`casual`、`collab`、`gossip`、`conflict`、`scuffle`、`authority`、`reply`。
- 每种怪物先各做 1 条 `casual`、1 条 `conflict`、1 条 `collab`。
- `scuffle` 只给少数组合，避免整个生态到处内斗。
- 只在有玩家旁观时触发。
- 不接 AI，只用固定台词。
- 加 `nextPeerInteractionGameTime`，避免和 ambient、hero 互动抢气泡。

第二版再做：

- AI peer scene 概率调优和更多 prompt 场景细分。
- 更多特殊组合 key 变体，例如 `.1/.2`。
- Simmons/Jean 更细的高位特殊互动。

最终体验目标：

**玩家经过一片夜地时，听见的不是两只怪物在值班，而像是在偷听一群邻居说话。它们会聊天，会搭手，会呛声，会翻脸。玩家只是刚好路过。**
