package com.whitecloud233.herobrine_companion.event;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.whitecloud233.herobrine_companion.HerobrineCompanion;
import com.whitecloud233.herobrine_companion.command.HeroCommandStates;
import com.whitecloud233.herobrine_companion.command.HeroRecoveryService;
import com.whitecloud233.herobrine_companion.command.HeroTrustService;
import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import com.whitecloud233.herobrine_companion.entity.ai.learning.SimpleNeuralNetwork.MindState;
import com.whitecloud233.herobrine_companion.entity.logic.data.HeroWorldData;
import com.whitecloud233.herobrine_companion.fight.ChallengeSession;
import com.whitecloud233.herobrine_companion.item.HeroSummonItem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Bedrock-style companion commands; all actions are scoped to the executing player. */
@EventBusSubscriber(modid = HerobrineCompanion.MODID)
public final class HeroCompanionCommands {
    private HeroCompanionCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) { register(event.getDispatcher()); }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var recovery = dispatcher.register(Commands.literal("hc_recover_hero")
                .executes(c -> recover(c.getSource())));
        aliases(dispatcher, recovery, "hc_restore_hero", "hero_recover", "hcr");
        var hero = Commands.literal("hc_hero");
        for (String action : List.of("recover", "restore", "fix")) {
            hero.then(Commands.literal(action).executes(c -> recover(c.getSource())));
        }
        dispatcher.register(hero);

        var addTrust = dispatcher.register(increment("hc_add_trust"));
        aliases(dispatcher, addTrust, "hc_trust_add", "hero_add_trust", "hcat");
        var trust = Commands.literal("hc_trust").executes(c -> queryTrust(c.getSource()));
        for (String query : List.of("query", "q", "show", "status")) {
            trust.then(Commands.literal(query).executes(c -> queryTrust(c.getSource())));
        }
        for (String add : List.of("add", "increase", "inc")) trust.then(increment(add));
        aliases(dispatcher, dispatcher.register(trust), "hero_trust");

        var state = Commands.literal("hc_hero_state").executes(c -> queryState(c.getSource()));
        for (String query : List.of("show", "status")) {
            state.then(Commands.literal(query).executes(c -> queryState(c.getSource())));
        }
        for (String change : List.of("set", "force", "switch")) {
            state.then(Commands.literal(change).requires(s -> s.hasPermission(2)).then(stateArgument()));
        }
        state.then(stateArgument());
        for (String owners : List.of("owners", "owner_map", "players", "map")) {
            state.then(Commands.literal(owners).requires(s -> s.hasPermission(2))
                    .executes(c -> ownerMap(c.getSource())));
        }
        aliases(dispatcher, dispatcher.register(state), "hero_state", "hchs");
        var owners = dispatcher.register(Commands.literal("hc_hero_owner_map").requires(s -> s.hasPermission(2))
                .executes(c -> ownerMap(c.getSource())));
        aliases(dispatcher, owners, "hero_owner_map", "hcom");
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> stateArgument() {
        return Commands.argument("state", StringArgumentType.greedyString()).requires(s -> s.hasPermission(2))
                .suggests((context, builder) -> {
                    List<String> values = new ArrayList<>(HeroCommandStates.chineseNames());
                    for (MindState state : MindState.values()) {
                        values.add(state.name().toLowerCase(Locale.ROOT));
                        values.add(Integer.toString(state.ordinal()));
                    }
                    return SharedSuggestionProvider.suggest(values, builder);
                }).executes(c -> changeState(c.getSource(), StringArgumentType.getString(c, "state")));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> increment(String literal) {
        return Commands.literal(literal).requires(s -> s.hasPermission(2))
                .executes(c -> addTrust(c.getSource(), 10))
                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                        .executes(c -> addTrust(c.getSource(), IntegerArgumentType.getInteger(c, "amount"))));
    }

    private static void aliases(CommandDispatcher<CommandSourceStack> dispatcher,
                                LiteralCommandNode<CommandSourceStack> target, String... names) {
        for (String name : names) dispatcher.register(Commands.literal(name).requires(target.getRequirement())
                .executes(target.getCommand()).redirect(target));
    }

    private static int recover(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var result = HeroRecoveryService.recover(player);
        if (result.hero() == null) {
            source.sendFailure(message("recover." + result.message()));
            return 0;
        }
        source.sendSuccess(() -> message("recover." + result.message(), result.hero().getTrustLevel(),
                HeroWorldData.get(player.serverLevel()).getClaimedRewards(player.getUUID()).size()), false);
        return 1;
    }

    private static int queryTrust(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HeroEntity hero = HeroSummonItem.findHeroInAnyDimension(player.server, player.getUUID());
        source.sendSuccess(() -> message("trust.query", player.getUUID().toString(),
                HeroWorldData.get(player.serverLevel()).getTrust(player.getUUID()),
                hero == null ? message("unloaded") : Component.literal(Integer.toString(hero.getTrustLevel()))), false);
        return 1;
    }

    private static int addTrust(CommandSourceStack source, int amount) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        int result = HeroTrustService.add(source.getPlayerOrException(), amount);
        source.sendSuccess(() -> message("trust.add", result), false);
        return 1;
    }

    private static int queryState(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HeroEntity hero = HeroSummonItem.findHeroInAnyDimension(player.server, player.getUUID());
        if (hero == null) { source.sendFailure(message("no_hero")); return 0; }
        source.sendSuccess(() -> message("state.query", stateName(hero.getHeroBrain().getState()),
                hero.getHeroBrain().getState().name().toLowerCase(Locale.ROOT), hero.getUUID().toString(),
                hero.level().dimension().location().toString()), false);
        return 1;
    }

    private static int changeState(CommandSourceStack source, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        MindState state = HeroCommandStates.parse(name);
        if (state == null) { source.sendFailure(message("state.invalid")); return 0; }
        ServerPlayer player = source.getPlayerOrException();
        HeroEntity hero = HeroSummonItem.findHeroInAnyDimension(player.server, player.getUUID());
        if (hero == null) { source.sendFailure(message("no_hero")); return 0; }
        if (ChallengeSession.isHeroInChallenge(hero) || ChallengeSession.isPlayerInChallenge(player)
                || ChallengeSession.isHeroFakeOutPhase(hero) || ChallengeSession.isPlayerFakeOutPhase(player)) {
            source.sendFailure(message("recover.blocked")); return 0;
        }
        hero.getHeroBrain().forceCommandState(state);
        source.sendSuccess(() -> message("state.changed", stateName(state)), false);
        return 1;
    }

    private static int ownerMap(CommandSourceStack source) {
        source.sendSuccess(() -> message("owners.header"), false);
        HeroWorldData data = HeroWorldData.get(source.getServer().overworld());
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            HeroEntity hero = HeroSummonItem.findHeroInAnyDimension(source.getServer(), player.getUUID());
            source.sendSuccess(() -> Component.literal(player.getGameProfile().getName() + " [" + player.getUUID()
                    + "] saved=" + data.getActiveHeroUUID(player.getUUID()) + " live="
                    + (hero == null ? "-" : hero.getUUID() + " owner=" + hero.getOwnerUUID() + " @"
                    + hero.level().dimension().location())), false);
        }
        return source.getServer().getPlayerList().getPlayerCount();
    }

    private static Component stateName(MindState state) {
        return Component.translatable("gui.herobrine_companion.mind_state." + state.name().toLowerCase(Locale.ROOT));
    }

    private static Component message(String key, Object... args) {
        return Component.translatableEscape("command.herobrine_companion." + key, args);
    }
}
