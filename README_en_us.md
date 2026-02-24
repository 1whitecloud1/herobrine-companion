
# Herobrine Companion

## Getting Started

Herobrine spawns randomly in the Overworld, particularly favoring dark environments with a light level of ≤ 7, and often appears within 8-16 blocks of the player. Alternatively, you can summon him instantly by typing the `hb` command in the chat.

You can interact with him primarily by right-clicking to open an interface where you can trade, check your Trust level, and accept quests. You can also right-click him while holding a specific item (**Creator's Covenant**) to issue commands such as "Check Location," "Rest," or "Guard." To increase Trust, you can trade with him, complete commissions posted on his interface, or collect Lore Fragments.

**Note: Only ONE Herobrine can exist per world.**

Herobrine possesses a dynamic mentality system, switching between multiple mindsets based on your actions: **Observer, Guardian, Judge, Trickster, Maintainer, Nostalgic, and King of Monsters**. Each mindset profoundly affects his dialogue and behavior:

* **Observer:** His default state. He does nothing extra, just watches you.
* **Guardian:** If you love building, farming, and actively invite him, he gradually becomes a gentle Guardian, granting you Regeneration and Resistance buffs.
* **Judge:** If you actively attack him, he turns into the Judge and will ignore you when you right-click him.
* **Trickster:** Increases the probability of him playing pranks on you.
* **Maintainer:** Triggered by too many dropped items or explosions. He will clear excess drops and restore blocks destroyed by explosions.
* **Nostalgic:** Triggered by using multiple Lore Fragments at once. He will stand still and sigh in reminiscence.
* **King of Monsters:** Triggered by killing too many monsters. He will grant buffs to hostile mobs.

All mindsets adjust dynamically according to the player's behavior.

His idle animations are full of life: sometimes he levitates in meditation, surrounded by mysterious particles; sometimes he holds his scythe horizontally, gently stroking the blade as if reminiscing; occasionally, he points into the air with his right hand, as if debugging the world's underlying rules; he might even blink and teleport instantly, showcasing his instincts as the "God of Code."
He is also aware of his environment—he knows which block you are looking at, what item you are holding, and reacts subtly. You can also switch Herobrine's skin within the interaction interface.

## Rich Interactions

Your relationship with Herobrine is built on Trust. You can gradually increase your Trust level by trading rare materials, fighting alongside him, and completing his commissions. Each Trust level unlocks new dialogues and rewards:

* **Level 2:** The Legend Manual.
* **Level 10:** Totem of Undying.
* **Level 20:** "Abyssal Vision" (Permanent Night Vision).
* **Level 50:** "Soul Covenant" (Keep inventory and experience on death) and unlocks **Companion Mode**.

**In Companion Mode**, Herobrine will follow you: he automatically switches between hovering and walking depending on the terrain, teleports instantly to your side if you get too far, and interacts with you using unique dialogues.
As Trust deepens, more features unlock:

* **Level 50:** Clears nearby tall grass and trees (preserves original terrain).
* **Level 70:** Flattens surrounding terrain (preserves original block features).

When you travel to the **End Ring**, you can unlock **Peaceful Mode**, causing all monsters to ignore you. However, if you attack any creature, the covenant breaks, and you will face a furious horde of monsters. Even normally, his Sovereign Aura stops nearby monsters from attacking, and can even force the Ender Dragon to land and submit.

You can interact with him via an invitation mechanism: hold the **Creator's Covenant**, aim at a bed, boat, minecart, or any modded seat, and `Shift + Right-click`. Herobrine will walk over and sit down indefinitely. `Shift + Right-click` the seated entity again, and he will stand up.

## Intelligent Dialogue System

Herobrine can engage in real conversations with the player. If you configure cloud services (supports DeepSeek, etc., API compatible), he will connect to a natural Large Language Model (LLM), enabling free-flowing chat while remembering your previous conversation history.

If you are offline, the built-in local dialogue library will respond to your messages via keyword matching, with thousands of preset dialogues covering various scenarios. He will also actively comment on your activities: while mining, in combat, or when your health is critically low, he might strike up a conversation.

*Cloud services are configured in `.minecraft/run/config/herobrine_companion/herobrine_companion_ai.json`.*

## World Anomalies & Exploration

While exploring the Overworld, you might stumble upon **Unstable Zones**—terrain replaced by exotic blocks like Netherrack and Soul Soil, haunted by Phantom creatures. These Phantom Zombies, Phantom Creepers, and Phantom Skeletons drop special items upon death, which can be used to trade with Herobrine.

By using the **Eternal Key** (found in Ancient Cities, End Cities, and Stronghold Libraries) and right-clicking End Bedrock, you can open a portal to his exclusive dimension: the **End Ring**.
This dimension consists of three massive bedrock rings and is Herobrine's domain. Entering for the first time grants an achievement, and right-clicking Herobrine here triggers special dialogue and awards you a Boundary Note.

Additionally, two special entities wander the world:

* **Phantom Steve:** Spawns on the Overworld surface with a very low probability of 0.5%. Drops a "Source Code Fragment" when killed.
* **Glitch Villager "N.":** Sells Barrier blocks for 64 Emeralds. When you exit the trading UI, he triggers a spooky sound effect, leaves behind a Lore Fragment, and vanishes.

## Important Items

* **End Poem:** Herobrine's scythe, functioning as an axe, shovel, pickaxe, and hoe. You can switch between three forms via `Shift + Right-click`. All damage scales with your Trust level:
* *Shatter Form:* Right-click to fire a lightning spear dealing AoE damage (terrain destruction can be toggled in configs).
* *Thunder Form:* Right-click to summon bolts of lightning that automatically target hostile mobs.
* *Void Form:* Extremely fast attack speed. Attacks create void rifts dealing continuous damage.


* **Soul Covenant:** Prevents item and experience loss upon death.
* **Promise of Transcendence:** Grants creative flight in Survival mode.
* **Abyssal Vision:** Grants permanent Night Vision.
* **Stone of Reversion:** Teleports you to your last death location.
* **Memory Fragment:** Place in a Jukebox to summon a Glitch Echo, which guides you to rare structures containing the Eternal Key.
* **Creator's Covenant:** The core item for summoning Herobrine. Right-click to summon after signing.

## Background Lore Collection

The mod features a complete narrative system. Through the **Legend Manual** (obtainable at Trust Level 2) and **11 Knowledge Fragments** scattered across the world, you can piece together Herobrine's origins.

Fragments are obtained in various ways: claimed as Trust rewards, hidden in Stronghold Libraries or Mineshafts, dropped by vanilla monsters or Phantom creatures, found in Village chests, End Cities, or Ancient Cities. Some even require mysterious actions like triggering special sleeping events or standing completely still for several minutes.

When you merge a fragment with the manual, not only can you read the lore, but Herobrine will also "learn" this knowledge, thereby changing his attitude towards you.

## Mod Compatibility

Herobrine Companion deeply integrates with **JEI** and **KubeJS**.

* In JEI, you can directly view all of Herobrine's trades and Trust level reward previews.
* Through KubeJS scripts, modpack authors can dynamically modify rewards and trades for high customizability.

### KubeJS Example:

```javascript
HerobrineCompanionEvents.registerTrades(event => {
    console.info('HerobrineCompanion: Registering custom trades via KubeJS...')
    // Remove all default trades (Optional)
    // event.removeAll()

    // Add a simple trade: 5 Apples for 1 Diamond
    event.add('5x minecraft:apple', 'minecraft:diamond')

    // Add a dual-input trade: 5 Apples + 1 Gold Ingot for 1 Diamond
    event.add('5x minecraft:apple', 'minecraft:gold_ingot', 'minecraft:diamond')

    // Add a trade based on Trust Level
    // Or call the method directly event.getTrustLevel()
    if (event.getTrustLevel() >= 50) {
        event.add('minecraft:dirt', 'minecraft:nether_star')
    }
})

HerobrineCompanionEvents.registerRewards(event => {
    // 1. Modify an existing reward (ID 2 is the diamond reward)
    event.modify(2, reward => {
        // Modify the required trust
        reward.requiredTrust = 5

        // Add new items
        reward.add('5x minecraft:emerald')
        reward.add(Item.of('minecraft:enchanted_book').enchant('sharpness', 5))

        // Note: Do not use reward.items.add() directly, please use reward.add()
    })

    // 2. Remove the original Totem reward
    event.remove(0)

    // 3. Add a completely new reward
    event.add(100, 10, 'minecraft:nether_star', '3x minecraft:diamond_block')
})

```

## Loot Distribution & Acquisition Guide

### 🗝️ Eternal Key — Dimension Pass

The Eternal Key is the only credential to access the "End Ring" dimension. Right-click bedrock in the End to open the portal.
**Obtained from:**

* **Ancient City:** In the chests of the Deep Dark.
* **End City Treasure:** In the chests of End Ships or at the top of towers.
* **Stronghold Library:** In the chests containing ancient knowledge.
  *(Due to its high generation probability, players do not need to search every structure; usually exploring just one complete structure listed above is enough.)*

### 📜 Knowledge Fragments Map

To piece together Herobrine's complete origin, players must collect Knowledge Fragments scattered worldwide. Different fragments correspond to different layers of "truth" within the worldview.
Once Herobrine's Trust Level reaches 2, you can claim the **Legend Manual**. Right-click a Knowledge Fragment to add it to the manual.

* **Fragment 1:** Claimed from the rewards page when Herobrine's Trust Level reaches 2.
* **Fragment 2:** Best found in Stronghold Libraries (symbolizing remnants of old era knowledge). Occasionally found in Abandoned Mineshaft minecarts with chests.
* **Fragment 3:** Obtained by killing vanilla monsters (Zombies, Skeletons, Creepers, Spiders, Witches, Endermen). Requires extensive combat.
* **Fragment 4:** Automatically added to inventory upon successfully entering the End Ring dimension and returning to the Overworld.
* **Fragment 5:** Records of the "Error" itself. Only obtained by killing entities that shouldn't exist: Phantom Zombies, Phantom Skeletons, and Phantom Creepers. (High difficulty as they only spawn in Unstable Zones).
* **Fragment 6:** Automatically obtained when the player stands completely still for a few minutes.
* **Fragment 7:** Found in all types of Village chests (Plains, Savanna, Snowy, Taiga, Desert). Pay attention to inconspicuous chests while looting.
* **Fragment 8:** Found in End City Treasures. Likely encountered while searching for Elytra.
* **Fragment 9:** Chance to obtain by triggering a special event while sleeping.
* **Fragment 10:** Found in Ancient Cities. You have a chance to find it in loot chests while evading the Warden.
* **Fragment 11:** Automatically obtained after encountering the Glitch Villager, opening the trade UI, and exiting it.

## License

This project is licensed under an open-source license. Please see the `LICENSE` file for details.

## Contributing

Thanks to Gemini for providing code support. Vibe coding!

## Support

If you encounter any issues while using the mod, please submit feedback in GitHub Issues.