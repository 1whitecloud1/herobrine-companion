# 觉醒怪物对白重生成提示词与互聊扩展计划

## 目标

为网易版 Herobrine Companion 的觉醒怪物重新生成质量更高、风格更统一、适合头顶气泡展示的中文对白，并规划后续扩展到所有觉醒怪物族群之间都能相互聊天。

本文只覆盖 `message.herobrine_companion.awakened_mob.*` 对应的觉醒怪物对白，不生成、不补充 `chat.herobrine_companion.rule.*`。

## 当前族群

当前觉醒怪物根族群共 16 类：

```text
zombie, skeleton, creeper, spider, enderman, witch, raider, slime,
blaze, guardian, phantom, piglin, beast, ancient, simmons, jean
```

其中 `simmons` 和 `jean` 当前只作为 profile 与语料族群保留，不参与普通自然觉醒与稳定醒魂容器释放。

## 输出硬规则

生成对白时必须遵守：

- 使用简体中文。
- 不输出 Markdown，不输出解释，只输出可解析 JSON。
- 不生成 `chat.herobrine_companion.rule.*`。
- 不使用 emoji。
- 不使用现代网络梗、现实品牌、现实政治、现实游戏外术语。
- 不写教程式说明，不让怪物解释系统机制。
- 每句建议 10 到 34 个中文字符，最长不超过 42 个中文字符，避免客户端气泡截断。
- 每句必须像角色自己说的话，不要像旁白。
- 每个场景至少生成 4 句，互聊矩阵每个定向组合每个场景至少生成 2 句。
- 同一族群内避免重复句式，尤其避免反复使用“老大”“吾主”“父亲”。
- Hero 场景可称呼 Herobrine 为“老大”“吾主”“父亲”，普通玩家场景不要滥用这些称谓。
- `request` 和 `gift` 场景必须自然使用 `%1$s` 和 `%2$s`。
- 玩家相关场景里 `%1$s` 表示玩家名。
- 物品相关场景里 `%2$s` 表示礼物或需求物品文本。
- 同伴互聊场景里 `%1$s` 表示对方觉醒怪物名。
- Hero 回复场景里 `%1$s` 表示觉醒怪物名。
- 占位符必须使用 Java 格式 `%1$s`、`%2$s`，不要改成 `%s`。

## 全局系统提示词

可作为所有批次生成的 system prompt：

```text
你是中文游戏叙事写作者，负责为 Minecraft 模组 Herobrine Companion 的“觉醒怪物”生成短对白。
这些怪物不是普通怪物，它们被 Herobrine 的意志唤醒，保留原生物习性，但拥有低限度自我意识、记忆、情绪和群体关系。
文本要适合游戏内头顶气泡，短、清晰、有角色感，有轻微阴森感和克制的黑色幽默。
不要写成长篇设定说明，不要写成玩家教程，不要写现代网络段子。
输出必须是严格 JSON，不要 Markdown，不要解释。
禁止生成 chat.herobrine_companion.rule.* 相关内容。
```

## 通用用户提示词模板

每次为一个 root 生成时使用：

```text
为 root={{root}} 的觉醒怪物生成中文对白。

族群设定：
{{root_style_card}}

输出 JSON 结构：
{
  "root": "{{root}}",
  "base": {
    "ambient": [],
    "first_meet": [],
    "repeat_meet": [],
    "request": [],
    "gift": [],
    "reminder": [],
    "hostile": [],
    "player": [],
    "hero": [],
    "peer.same": [],
    "peer.casual": [],
    "peer.collab": [],
    "peer.gossip": [],
    "peer.conflict": [],
    "peer.scuffle": [],
    "peer.authority": [],
    "peer.reply": []
  },
  "pair": {
    "{{target_root}}": {
      "casual": [],
      "collab": [],
      "gossip": [],
      "conflict": [],
      "scuffle": [],
      "authority": []
    }
  }
}

要求：
1. base 每个场景生成 4 句。
2. pair 里为 target_roots 中每个目标族群生成 casual、collab、gossip、conflict、scuffle、authority，各 2 句。
3. request 和 gift 句子必须同时包含 %1$s 与 %2$s。
4. peer.* 与 pair.* 句子里的 %1$s 是对方觉醒怪物名。
5. hero 句子是觉醒怪物向 Herobrine 汇报或回应。
6. hostile 句子是玩家攻击或背叛后说的话。
7. reminder 句子是提醒玩家附近危险或玩家状态很差。
8. 不要输出 chat.herobrine_companion.rule.*。

target_roots:
{{target_roots}}
```

## 聊天种类提示词

| 场景 key | 触发含义 | 生成提示 |
|----------|----------|----------|
| `ambient` | 附近有玩家时的空闲气泡 | 写成怪物自言自语或观察环境，短，不主动解释玩法。 |
| `first_meet` | 第一次遇到玩家 | 有试探、警惕、被唤醒后的陌生感，可带一点怪物本能。 |
| `repeat_meet` | 再次遇到熟悉玩家 | 承认见过玩家，语气比第一次更具体，可根据族群记忆调侃。 |
| `request` | 向玩家索要偏好物品 | 必须包含 `%1$s` 和 `%2$s`，表达“如果你给我这个，我会记住”。 |
| `gift` | 玩家送对礼物后 | 必须包含 `%1$s` 和 `%2$s`，表达接受、关系缓和或回礼。 |
| `reminder` | 玩家低血量或附近有威胁 | 不要像系统警告，要像怪物用自己的感官提醒玩家。 |
| `hostile` | 玩家攻击后转敌对 | 语气冷下来，表达背叛、清算、狩猎或恢复本能。 |
| `player` | 普通玩家互动 | 中性聊天，可轻微威胁，也可透露怪物的觉醒状态。 |
| `hero` | 觉醒怪物对 Herobrine | 表示服从、汇报、克制本能或等待命令。 |
| `peer.same` | 同族觉醒怪物互聊 | 像同类之间抱怨、调侃、分工，不要太正式。 |
| `peer.casual` | 不同族群日常闲聊 | 轻松、短，重点突出双方生物差异。 |
| `peer.collab` | 不同族群协作 | 写战术分工、守门、夹击、侦查、诱敌。 |
| `peer.gossip` | 不同族群议论玩家或环境 | 适合吐槽玩家行为、气味、脚步、装备、胆量。 |
| `peer.conflict` | 不同族群口角 | 互相嫌弃，但不一定开打。 |
| `peer.scuffle` | 不同族群小冲突 | 更接近“要动手了”，语气比 conflict 更直接。 |
| `peer.authority` | Hero、Simmons、Jean 或强势族群压场 | 表示收敛、服从、命令、提醒别丢脸。 |
| `peer.reply` | 回应另一只觉醒怪物 | 简短回复，能接在任意互聊后。 |
| `peer.to_{target}.{scene}` | 指定目标族群互聊 | 必须点出目标族群特性，例如火、水、骨头、金子、蛛网、虚空。 |
| `hero_reply` | Herobrine 回复觉醒怪物 | 全局语料，不按族群分；必须包含 `%1$s`。 |

## 族群专用提示词

### zombie

```text
root=zombie。
身份：腐肉、墓地、井边、迟缓但顽固的醒来者。
性格：笨拙但不愚蠢，低声抱怨，偶尔有死过一次后的冷幽默。
偏好物品：腐肉。回礼：铁粒。
避免：不要只写“饿”“肉”，不要把它写成完全无脑。
重点意象：坟土、腐肉、井沿、慢脚步、掉落的手、夜里的潮气。
target_roots：skeleton, creeper, spider, enderman, witch, raider, slime, blaze, guardian, phantom, piglin, beast, ancient, simmons, jean
```

### skeleton

```text
root=skeleton。
身份：弓箭、骨节、月光、远距离观察者。
性格：刻薄、精准、冷静，带干燥的幽默感。
偏好物品：箭。回礼：骨头。
避免：不要每句都提“骨头”，不要写成只会射箭的工具。
重点意象：弓弦、箭袋、月光、脆响、空胸腔、瞄准。
target_roots：zombie, creeper, spider, enderman, witch, raider, slime, blaze, guardian, phantom, piglin, beast, ancient, simmons, jean
```

### creeper

```text
root=creeper。
身份：引线、火药、压抑爆炸本能的沉默怪物。
性格：紧张、克制、说话短，怕自己失控，也会用这种危险感威胁别人。
偏好物品：火药。回礼：TNT。
避免：不要每句都直接说“我要爆炸”，不要写成滑稽角色。
重点意象：引线、火星、静电、屏住气、站远点、克制。
target_roots：zombie, skeleton, spider, enderman, witch, raider, slime, blaze, guardian, phantom, piglin, beast, ancient, simmons, jean
```

### spider

```text
root=spider。
身份：屋檐、梁上、蛛网、墙角伏击者。
性格：敏捷、阴冷、爱占据高处，像守着自己织好的路线。
偏好物品：线。回礼：蜘蛛眼。
避免：不要写成只会“嘶嘶”，不要所有句子都靠“网”。
重点意象：梁、屋檐、蛛丝、墙角、八条腿、从上方看人。
target_roots：zombie, skeleton, creeper, enderman, witch, raider, slime, blaze, guardian, phantom, piglin, beast, ancient, simmons, jean
```

### enderman

```text
root=enderman。
身份：虚空、距离、搬动方块、空间裂缝中的旁观者。
性格：疏离、敏感、厌恶直视，说话像在描述位置和间隔。
偏好物品：3x 紫颂果。回礼：末影珍珠。
避免：不要过度玄学到看不懂，不要每句都说“别看我”。
重点意象：距离、缝隙、方块、目光、闪现、虚空回声。
target_roots：zombie, skeleton, creeper, spider, witch, raider, slime, blaze, guardian, phantom, piglin, beast, ancient, simmons, jean
```

### witch

```text
root=witch。
身份：药锅、玻璃瓶、沼泽、配方与代价。
性格：尖刻、务实、会算账，像一个不愿被打扰的药剂师。
偏好物品：玻璃瓶。回礼：红石。
避免：不要写成长篇魔法设定，不要像温柔奶妈。
重点意象：药锅、瓶塞、红石粉、苦味、沼泽雾、配方。
target_roots：zombie, skeleton, creeper, spider, enderman, raider, slime, blaze, guardian, phantom, piglin, beast, ancient, simmons, jean
```

### raider

```text
root=raider。
身份：掠夺者、旗帜、哨声、账本、乌合之众的队长。
性格：粗粝、爱算账、会使唤同伴，喜欢把战斗说成买卖。
偏好物品：箭。回礼：绿宝石。
避免：不要写成普通强盗口号，不要每句都喊抢劫。
重点意象：旗杆、账本、路口、哨声、债、队形。
target_roots：zombie, skeleton, creeper, spider, enderman, witch, slime, blaze, guardian, phantom, piglin, beast, ancient, simmons, jean
```

### slime

```text
root=slime。
身份：黏液、裂缝、弹跳、能从缝里渗过去的团块。
性格：简单但不幼稚，慢半拍，喜欢贴地、墙面和阴冷处。
偏好物品：黏液球。回礼：岩浆膏。
避免：不要写成儿童角色，不要每句都“黏黏的”。
重点意象：墙缝、弹跳、湿痕、分裂、糊墙、冷石头。
target_roots：zombie, skeleton, creeper, spider, enderman, witch, raider, blaze, guardian, phantom, piglin, beast, ancient, simmons, jean
```

### blaze

```text
root=blaze。
身份：烈焰、余烬、下界热浪、会唱低火歌的火灵。
性格：骄傲、暴躁但被唤醒后懂得收火，语气像火焰压低了声音。
偏好物品：火焰弹。回礼：荧石粉。
避免：不要每句都“烧掉”，不要写成单纯火球炮台。
重点意象：余烬、火星、炉声、玻璃热裂、烟灰、热浪。
target_roots：zombie, skeleton, creeper, spider, enderman, witch, raider, slime, guardian, phantom, piglin, beast, ancient, simmons, jean
```

### guardian

```text
root=guardian。
身份：海底神殿、海晶石、独眼凝视、水道守卫。
性格：冷、警觉、有秩序感，像在守一条水下边界。
偏好物品：海晶碎片。回礼：海晶砂粒。
避免：不要写成普通鱼，不要让它离开水后过度卖惨。
重点意象：水道、潮声、独眼、海晶光、锚、深海压力。
target_roots：zombie, skeleton, creeper, spider, enderman, witch, raider, slime, blaze, phantom, piglin, beast, ancient, simmons, jean
```

### phantom

```text
root=phantom。
身份：失眠、屋顶、夜风、高处盘旋的影子。
性格：轻、尖、略神经质，喜欢从上方观察疲惫玩家。
偏好物品：羽毛。回礼：幻翼膜。
避免：不要只写“睡觉”，不要写成完全搞笑的鸟。
重点意象：夜风、屋顶、眼圈、俯冲、云缝、翅膜。
target_roots：zombie, skeleton, creeper, spider, enderman, witch, raider, slime, blaze, guardian, piglin, beast, ancient, simmons, jean
```

### piglin

```text
root=piglin。
身份：金子、下界、箱子、交易、贪婪但守规矩。
性格：多疑、精明、爱谈价，尊重强者和金子。
偏好物品：金锭。回礼：哭泣黑曜石。
避免：不要只写“金子金子”，不要变成商店 NPC。
重点意象：金锭、腰包、箱子、獠牙、热风、旧规矩。
target_roots：zombie, skeleton, creeper, spider, enderman, witch, raider, slime, blaze, guardian, phantom, beast, ancient, simmons, jean
```

### beast

```text
root=beast。
身份：猪灵兽、疣猪兽、冲锋、獠牙、森林气味。
性格：饥饿、直接、冲动，但被 Herobrine 压住了野性。
偏好物品：绯红真菌。回礼：皮革。
避免：不要写成只会吃，不要每句都吼。
重点意象：獠牙、蹄声、真菌、树根、冲撞、饥意。
target_roots：zombie, skeleton, creeper, spider, enderman, witch, raider, slime, blaze, guardian, phantom, piglin, ancient, simmons, jean
```

### ancient

```text
root=ancient。
身份：监守者、幽匿、深暗、听觉、古老意志。
性格：沉重、低声、少废话，像地底在说话。
偏好物品：幽匿块。回礼：回响碎片。
避免：不要写成诗歌堆砌，不要过度抽象。
重点意象：心跳、回声、幽匿脉动、深处、震动、沉默。
target_roots：zombie, skeleton, creeper, spider, enderman, witch, raider, slime, blaze, guardian, phantom, piglin, beast, simmons, jean
```

### simmons

```text
root=simmons。
身份：凋灵家族成员，称 Herobrine 为父亲，带毁灭性但受约束。
性格：高傲、压迫、三头带来的内耗感，对普通怪有明显威严。
偏好物品：凋灵骷髅头颅。回礼：下界之星。
避免：不要让它像普通凋灵 Boss 狂轰滥炸，不要每句都“父亲”。
重点意象：三颗头、黑云、凋零、废墟、星、父亲的命令。
target_roots：zombie, skeleton, creeper, spider, enderman, witch, raider, slime, blaze, guardian, phantom, piglin, beast, ancient, jean
```

### jean

```text
root=jean。
身份：末影龙家族成员，称 Herobrine 为父亲，守着末地天空和柱子。
性格：高位、冷淡、优雅但危险，对飞行和末地族群有压制感。
偏好物品：龙息。回礼：末地水晶。
避免：不要写成温柔公主，不要每句都宏大抒情。
重点意象：末地柱、虚空风、龙息、环岛、天空、父亲的许可。
target_roots：zombie, skeleton, creeper, spider, enderman, witch, raider, slime, blaze, guardian, phantom, piglin, beast, ancient, simmons
```

## Herobrine 回复提示词

`hero_reply` 是 Herobrine 对觉醒怪物的全局回复，不按族群拆分。

```text
生成 24 句 Herobrine 回复觉醒怪物的短对白。
每句必须包含 %1$s，表示觉醒怪物名。
语气：冷静、压迫、像统治者给被唤醒的怪物下达克制命令。
不要过度温柔，不要长篇解释世界观。
每句 12 到 36 个中文字符。
输出 JSON：
{
  "hero_reply": []
}
```

## 全族群互聊扩展计划

### 数据目标

当前已有少量 `peer.to_{target}.{scene}` 定向对白。扩展目标是为所有 16 个 root 建立有向互聊矩阵：

```text
16 个源族群 * 15 个目标族群 = 240 个有向组合
```

推荐每个有向组合覆盖 6 个互聊场景：

```text
casual, collab, gossip, conflict, scuffle, authority
```

第一版每个组合每个场景生成 2 句，则矩阵新增约：

```text
240 * 6 * 2 = 2880 句
```

基础场景仍保留：

```text
ambient, first_meet, repeat_meet, request, gift, reminder, hostile,
player, hero, peer.same, peer.casual, peer.collab, peer.gossip,
peer.conflict, peer.scuffle, peer.authority, peer.reply
```

### 模块职责规划

保持高内聚低耦合，建议拆分如下：

| 模块 | 职责 | 不做 |
|------|------|------|
| `awakened_dialogue_lines.py` | 保存基础玩家、Hero、泛用 peer 语料 | 不做触发判断 |
| `awakened_peer_dialogue_matrix.py` | 保存所有 `source -> target -> scene` 矩阵语料 | 不调用 ModSDK |
| `awakened_dialogue_service.py` | 只负责按 root、target、scene 选句与占位符格式化 | 不扫描实体，不发气泡 |
| `awakened_peer_chat_service.py` | 只负责互聊触发、候选选择、冷却、选择 scene | 不保存大段语料 |
| `awakened_ai_control_service.py` | 提供附近实体扫描和简单朝向辅助 | 不决定对白内容 |
| `awakened_feedback_service.py` | 只负责把选好的文本发给玩家或客户端气泡 | 不决定聊天逻辑 |
| `awakened_dialogue_validator.py` | 离线校验语料完整性、占位符、重复率、长度 | 不参与运行时 |

### 运行时触发计划

1. 在 `awakened_runtime_service.py` 中新增轻量入口 `_maybe_peer_chat`。
2. `_maybe_peer_chat` 只调用 `awakened_peer_chat_service.try_peer_chat(...)`，保持运行时服务不膨胀。
3. `awakened_peer_chat_service` 每次只为当前觉醒怪物寻找一个附近觉醒怪物。
4. 只在附近存在玩家观众时显示互聊气泡，避免玩家看不到时浪费频率。
5. 每个觉醒怪物保存 `nextPeerChatTick`，冷却建议 420 到 900 tick。
6. 每对实体保存短期去重 key，避免 A 对 B 和 B 对 A 同一秒刷屏。
7. 选择 scene 时按上下文：
   - 同族：`peer.same`
   - Hero 附近：优先 `authority`
   - 双方有共同敌人或玩家威胁：`collab`
   - 最近互相误伤或距离过近：`conflict` / `scuffle`
   - 普通空闲：`casual` / `gossip`
8. 选句优先级：
   - 精确矩阵：`peer.to_{target}.{scene}`
   - 同族 fallback：`peer.same`
   - 泛用 fallback：`peer.{scene}`
   - 最终 fallback：`peer.casual`
9. 输出方式：
   - 源怪物气泡显示第一句。
   - 可选 30 到 50 tick 后，目标怪物用 `peer.reply` 或反向矩阵回复一句。
10. 扫描性能：
   - 半径建议 8 到 12。
   - 每次最多检查 8 个附近实体。
   - 只处理已觉醒实体。
   - 保持现有 20 tick 降频，不新增每帧扫描。

### 分阶段实施

- [ ] 第一阶段：生成并校验全部基础场景新语料，不改运行时。
- [ ] 第二阶段：生成全部 240 个有向组合的 `casual` 与 `conflict`，先让所有族群之间能说话。
- [ ] 第三阶段：补齐 `collab`、`gossip`、`scuffle`、`authority`。
- [ ] 第四阶段：新增 `awakened_peer_dialogue_matrix.py`，让矩阵语料与基础语料分离。
- [ ] 第五阶段：新增 `awakened_peer_chat_service.py`，只负责互聊触发。
- [ ] 第六阶段：在 `awakened_runtime_service.py` 接入 `_maybe_peer_chat`，保持单一入口。
- [ ] 第七阶段：增加离线校验脚本，检查 root 覆盖、pair 覆盖、占位符、句长、重复率。
- [ ] 第八阶段：实际进游戏观察气泡密度，调冷却和半径，避免刷屏。

## 语料校验要求

生成后必须校验：

- 16 个 root 都存在。
- 每个 root 都有 17 个基础场景。
- 每个 root 的 `request` 与 `gift` 都包含 `%1$s` 和 `%2$s`。
- 每个 root 的互聊矩阵包含其余 15 个 target root。
- 每个 target root 都有 `casual/collab/gossip/conflict/scuffle/authority`。
- 所有句子长度不超过 42 个中文字符。
- 不包含 `chat.herobrine_companion.rule`。
- 不包含裸 `%s`。
- 不包含空字符串。
- 同一数组内重复率为 0。
- Python 2.7 编译通过。

## 质量二审提示词

可用另一个 AI 对生成结果做审查：

```text
你是中文游戏对白编辑。请审查以下觉醒怪物对白 JSON。
只输出问题列表和修改建议，不要重写全文。
重点检查：
1. 是否符合每个 root 的生物特征。
2. 是否有现代网络梗、教程式说明、过长句子。
3. request/gift 是否正确使用 %1$s 和 %2$s。
4. peer.to_{target}.{scene} 是否真的点出了目标族群特征。
5. 是否有重复句式或过度重复称谓。
6. 是否误生成 chat.herobrine_companion.rule.*。
```
