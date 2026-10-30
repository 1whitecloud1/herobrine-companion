package com.whitecloud233.modid.herobrine_companion.client.service;

import com.google.gson.JsonParser;
import com.sun.source.tree.MethodTree;
import com.whitecloud233.modid.herobrine_companion.client.llm.format.OpenAiChatAdapter;
import com.whitecloud233.modid.herobrine_companion.regression.SourceFixturesChecks;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/** Exercises request-time environment injection plus task dispatch/receipts through an in-memory transport. */
public final class AgentFeedbackChecks {
    public static void main(String[] args) throws Exception {
        var response = new OpenAiChatAdapter().parseResponse(JsonParser.parseString("""
                {"choices":[{"finish_reason":"tool_calls","message":{"content":"",
                "reasoning_content":"inspect the surroundings",
                "tool_calls":[{"function":{"name":"agent_task","arguments":"{\\"scene\\":\\"inspect\\"}"}}]}}]}
                """).getAsJsonObject());
        if (!response.successful() || !response.text().isEmpty() || response.tool() == null
                || !response.tool().name().equals("agent_task")
                || !response.tool().arguments().get("scene").getAsString().equals("inspect")) {
            throw new AssertionError("A GLM tool-only response must be dispatched, not treated as an empty answer");
        }
        String root = "src/main/java/com/whitecloud233/modid/herobrine_companion/";
        String methods = select(root + "client/service/AIService.java", "executeNamedToolAction",
                "executeAgentToolAction", "buildToolResultSynthesisPrompt")
                + select(root + "network/AgentRequestPacket.java", "route");
        int checks;
        try (var fixture = SourceFixturesChecks.compile("AgentFeedbackFixture", IMPORTS
                + "public class AgentFeedbackFixture {" + HARNESS + methods + "}")) {
            checks = (int) fixture.type().getMethod("run").invoke(null);
        }
        // Substitute only game type names; run the actual environment collection loop with controlled entities.
        String environment = select(root + "client/service/AIGameContextSupport.java", "getDynamicGameData")
                .replace("net.minecraft.world.entity.Entity", "Entity")
                .replace("net.minecraft.core.registries.BuiltInRegistries", "BuiltInRegistries");
        try (var fixture = SourceFixturesChecks.compile("EnvironmentContextFixture", IMPORTS
                + "public class EnvironmentContextFixture {" + ENVIRONMENT + environment + "}")) {
            checks += (int) fixture.type().getMethod("run").invoke(null);
        }
        System.out.println("AgentFeedbackChecks: " + (checks + 1) + " checks passed");
    }

    private static String select(String file, String... names) throws Exception {
        Set<String> selected = Set.of(names);
        return SourceFixturesChecks.members(Files.readString(Path.of(file)),
                member -> member instanceof MethodTree method && selected.contains(method.getName().toString()));
    }

    private static final String IMPORTS = """
            import java.util.*;
            import java.util.concurrent.*;
            import java.util.function.Predicate;
            import com.google.gson.*;
            import com.whitecloud233.modid.herobrine_companion.client.service.AIChatAttempt;
            import com.whitecloud233.modid.herobrine_companion.client.service.AIRequestContext;
            import com.whitecloud233.modid.herobrine_companion.client.llm.*;
            import com.whitecloud233.modid.herobrine_companion.client.agent.ClientAgentToolRequestStore;
            """;

    private static final String HARNESS = """
        static final int TOOL_RESULT_TIMEOUT_SECONDS = 0;
        static final UUID PLAYER = UUID.randomUUID();
        static final UUID REQUEST = UUID.randomUUID();
        static final String KIND_TOOL = "tool", KIND_TASK = "task";
        static Level level;
        static HeroEntity hero;
        static ServerPlayer player;
        static AIChatAttempt synthesized;
        static AgentRequestPacket sent;
        static AgentToolResultPacket receipt;
        static int checks, submissions, syntheses;
        static boolean drop, emptyPlan;

        static void reset() {
            ClientAgentToolRequestStore.clear();
            Config.agentToolResultFeedback = true;
            level = new Level(); hero = new HeroEntity(); player = new ServerPlayer();
            synthesized = null; sent = null; receipt = null;
            submissions = 0; syntheses = 0; drop = false; emptyPlan = false;
        }
        static AIChatAttempt attempt() {
            var context = new AIRequestContext("所以附近到底有什么", PLAYER, PLAYER, "zh_cn", null,
                    AIRequestContext.ConversationPolicy.PERSISTENT, false);
            var settings = new LlmSettings("https://test.invalid", "test-only", "glm", null, null);
            return AIChatAttempt.initial(context, new ResolvedTask(settings, null), context.originalMessage(),
                    false, AIChatAttempt.ToolPolicy.WORLD);
        }
        static CompletableFuture<String> invoke(String scene) {
            JsonObject args = new JsonObject(); args.addProperty("scene", scene);
            return executeNamedToolAction("agent_task", args, attempt());
        }
        static void check(boolean value, String message) {
            if (!value) throw new AssertionError(message); checks++;
        }
        public static int run() {
            reset();
            check(!invoke("inspect").join().isBlank(), "task must produce a final answer");
            check(sent.kind().equals(KIND_TASK) && sent.name().equals("inspect"), "preserve task routing");
            check(submissions == 1 && syntheses == 1, "submit once and synthesize once");
            check(receipt.ok() && receipt.message().contains("尚未执行完成"), "queue acceptance is not completion");
            check(receipt.message().contains("Current Environment") && !receipt.message().contains("minecraft:"),
                    "reuse injected observation instead of adding another entity scan");
            check(synthesized.prompt().contains("所以附近到底有什么"), "retain original question for final answer");
            check(synthesized.tools() == AIChatAttempt.ToolPolicy.NONE, "synthesis must not call inspect again");
            check(synthesized.prompt().contains("不能说成任务已完成"), "prevent false completion claims");
            reset(); invoke("inspect").join();
            check(receipt.message().contains("任务已提交") && syntheses == 1,
                    "explicit task must receive a reply without relying on anomaly report messages");
            reset(); invoke("repair").join();
            check(submissions == 1 && receipt.ok() && !receipt.message().contains("Current Environment"), "repair still queues normally");
            reset(); invoke("not-a-scene").join();
            check(submissions == 0 && !receipt.ok() && syntheses == 1, "invalid scene cannot silently trigger repair");
            reset(); emptyPlan = true; invoke("inspect").join();
            check(!receipt.ok() && syntheses == 1, "empty plan gets an explicit response");
            reset(); hero = null; invoke("inspect").join();
            check(!receipt.ok() && syntheses == 1, "missing Hero must not leave a pending task reply");
            reset(); level.dimension = ModStructures.END_RING_DIMENSION_KEY; invoke("inspect").join();
            check(submissions == 0 && !receipt.ok() && syntheses == 1, "frozen trial brain must reject tasks immediately");
            reset(); Config.agentToolResultFeedback = false;
            check(invoke("inspect").join().contains("无法确认") && syntheses == 0 && submissions == 1,
                    "feedback escape hatch returns an honest nonempty acknowledgement");
            reset(); drop = true; invoke("inspect").join();
            check(synthesized.prompt().contains("超时") && syntheses == 1, "timeout must not claim success");
            reset(); JsonObject args = new JsonObject(); args.addProperty("field", "mind");
            executeNamedToolAction("hero_inspect", args, attempt()).join();
            check(sent.kind().equals(KIND_TOOL) && syntheses == 1 && submissions == 0, "ordinary tools retain feedback");
            check(receipt.message().length() <= 512, "receipt must fit existing packet limit");
            ClientAgentToolRequestStore.clear();
            return checks;
        }
        static CompletableFuture<String> chatWithRetry(AIChatAttempt a) {
            synthesized = a; syntheses++; return CompletableFuture.completedFuture("根据观测回答玩家");
        }
        static String completeReply(String text, AIRequestContext context) { return text; }
        static class Config { static boolean agentToolResultFeedback = true; }
        static class AgentToolJsonSupport {
            static final String TOOL_AGENT_TASK = "agent_task";
            static boolean isAgentTool(String name) { return Set.of("agent_task", "hero_inspect").contains(name); }
            static String buildArgsJson(JsonObject args) { return args.toString(); }
            static String taskRouteName(JsonObject args) {
                return args.has("scene") ? args.get("scene").getAsString() : "start";
            }
        }
        static class JsonInputs {
            static String optionalString(JsonObject args, String key, String fallback) {
                return args.has(key) ? args.get(key).getAsString() : fallback;
            }
        }
        record AgentRequestPacket(String kind, String name, String argsJson, UUID requestId) {
            static final String KIND_TASK="task", KIND_TOOL="tool";
        }
        record AgentToolResultPacket(UUID requestId, String toolId, boolean ok, String message) {}
        static class PacketHandler {
            static void sendToServer(AgentRequestPacket packet) {
                sent = packet; route(player, packet.kind(), packet.name(), packet.argsJson(), packet.requestId());
            }
            static void sendToPlayer(AgentToolResultPacket packet, ServerPlayer ignored) {
                receipt = packet;
                if (!drop) ClientAgentToolRequestStore.complete(packet.requestId(), packet.toolId(), packet.ok(), packet.message());
            }
        }
        static class Level {
            Object dimension = new Object();
            Object dimension() { return dimension; }
        }
        static class HeroEntity {
            boolean isAlive() { return true; }
            Level level() { return level; }
            HeroAgent getHeroAgent() { return new HeroAgent(); }
        }
        static class ServerPlayer {
            Object getServer() { return level; } UUID getUUID() { return PLAYER; }
            Level serverLevel() { return level; } Object position() { return new Object(); }
            void sendSystemMessage(Object message) {}
        }
        static class HeroSummonItem {
            static HeroEntity findHeroInAnyDimension(Object server, UUID id) { return hero; }
            static boolean performSummonOrTeleport(Level world, ServerPlayer player, Object pos) { return true; }
        }
        static class HeroSummonTool { static final String ID="hero_summon_to_player"; }
        static class Component { static Object translatable(String key) { return key; } }
        static class ModStructures { static final Object END_RING_DIMENSION_KEY = new Object(); }
        enum AgentIntention { REMEDIATE, INVESTIGATE }
        record Classification(AgentIntention intention) {
            static Classification world(AgentIntention intention, String reason) { return new Classification(intention); }
        }
        static class AgentFrame { static AgentFrame capture(HeroEntity hero) { return new AgentFrame(); } }
        static class PlannedTask {}
        static class Planner {
            List<PlannedTask> plan(AgentFrame frame, Classification classification) {
                return emptyPlan ? List.of() : List.of(new PlannedTask());
            }
        }
        record AgentToolArgs(String json) { static AgentToolArgs fromJson(String json) { return new AgentToolArgs(json); } }
        static class HeroAgent {
            Planner taskPlanner() { return new Planner(); }
            void requestTasks(List<PlannedTask> tasks) { submissions += tasks.size(); }
            AgentToolInbox.Offer submitToolRequest(HeroEntity hero, String name, AgentToolArgs args,
                                                   UUID owner, boolean approved, UUID id) {
                PacketHandler.sendToPlayer(new AgentToolResultPacket(id, name, true, "心智=calm"), player);
                return AgentToolInbox.Offer.ACCEPTED;
            }
        }
        static class AgentToolInbox {
            enum Offer { ACCEPTED, DUPLICATE, FULL }
        }
        static class AgentTaskRequestHandler {
            record Outcome(boolean ok, String message) {}
            static Outcome handle(HeroEntity hero, AgentToolArgs args, String scene) {
                if ("repair".equals(scene) || "inspect".equals(scene)) {
                    if (emptyPlan) return new Outcome(false, "没有可执行的任务（场景 " + scene + " 未找到目标）");
                    submissions++;
                    return new Outcome(true, "任务已提交队列（3 步），尚未执行完成");
                }
                return new Outcome(false, "没有可执行的任务（场景 " + scene + " 未找到目标）");
            }
        }
        static class AIComputerControlSupport { static boolean isSupportedToolName(String name) { return false; } }
        static class AICommandSkillSupport {
            static final String TOOL_MINECRAFT_COMMAND_SKILL="minecraft_command_skill";
            static String buildMinecraftSkillCommand(JsonObject args, String... constants) { throw new AssertionError(); }
        }
        static class AIGameCommandExecutor {
            static final String ACTION_TELEPORT_TO_HERO="teleport", ACTION_MASSIVE_LIGHTNING="lightning", ACTION_SUMMON_HERO_TO_PLAYER="summon";
        }
        static class AIJvmCodeSkillSupport { static boolean isSupportedToolName(String name) { return false; } }
        static class RegistryLookupSupport { static boolean isSupportedToolName(String name) { return false; } }
        static class WebLookupSupport { static boolean isSupportedToolName(String name) { return false; } }
        static CompletableFuture<String> executeComputerControlAction(JsonObject args, AIChatAttempt a) { throw new AssertionError(); }
        static CompletableFuture<String> executeJvmCodeAction(JsonObject args, AIChatAttempt a) { throw new AssertionError(); }
        static CompletableFuture<String> executeRegistryLookupAction(JsonObject args, AIChatAttempt a) { throw new AssertionError(); }
        static CompletableFuture<String> executeWebLookupAction(JsonObject args, AIChatAttempt a) { throw new AssertionError(); }
        static CompletableFuture<String> executeToolAction(String command, String dialogue, AIChatAttempt a) { throw new AssertionError(); }
        """;

    private static final String ENVIRONMENT = """
        static int checks;
        public static int run() {
            Minecraft mc = Minecraft.instance;
            Entity slime = new Entity("minecraft:slime", 5);
            mc.level.entities.addAll(List.of(mc.player, slime, new Entity("minecraft:cow", 21)));
            String first = getDynamicGameData();
            check(first.contains("minecraft:slime"), "ordinary chat already includes nearby slime without an Agent or Hero");
            check(!first.contains("minecraft:cow") && !first.contains("test:player"), "exclude distant entities and player");
            check(first.contains("Current Environment") && first.contains("Entities within 20 blocks"), "preserve observation labels");
            slime.x = 25;
            String second = getDynamicGameData();
            check(!second.contains("minecraft:slime"), "next request must recollect moved entities, not reuse stale history");
            check(second.contains("no entities"), "empty observation is explicit");
            mc.level.entities.add(new Entity("minecraft:pig", 3));
            check(getDynamicGameData().contains("minecraft:pig"), "new entities appear on next request");
            mc.player.cheats = false;
            check(getDynamicGameData().contains("minecraft:pig"), "perception remains available without command permission");
            mc.player.connection = null;
            check(getDynamicGameData().isEmpty(), "disconnected client must not reuse last world's facts");
            return checks;
        }
        static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
        static class Entity {
            String type; double x;
            Entity(String type, double x) { this.type=type; this.x=x; }
            double distanceTo(Entity other) { return Math.abs(x-other.x); }
            String getType() { return type; }
        }
        static class Player extends Entity {
            Connection connection = new Connection(); boolean cheats = true;
            Player() { super("test:player", 0); }
            boolean hasPermissions(int permission) { return cheats; }
        }
        static class Level {
            List<Entity> entities = new ArrayList<>();
            List<Entity> entitiesForRendering() { return entities; }
        }
        static class Minecraft {
            static final Minecraft instance = new Minecraft();
            Player player = new Player(); Level level = new Level();
            static Minecraft getInstance() { return instance; }
        }
        static class Connection {
            List<ResourceKey<Level>> levels() { return List.of(); }
            RegistryAccess registryAccess() { return new RegistryAccess(); }
        }
        static class RegistryAccess { Registry registryOrThrow(Object key) { return new Registry(); } }
        static class Registry { Set<ResourceLocation> keySet() { return Set.of(); } }
        static class ResourceKey<T> { String location() { return "test:level"; } }
        static class ResourceLocation { String getNamespace() { return "test"; } String getPath() { return "test"; } }
        static class Registries { static final Object STRUCTURE = new Object(), BIOME = new Object(); }
        static class BuiltInRegistries {
            static final EntityRegistry ENTITY_TYPE = new EntityRegistry();
        }
        static class EntityRegistry { String getKey(String type) { return type; } }
        static class HerobrineCompanion { static final String MODID="herobrine_companion"; }
        static void appendBuildingTemplates(StringBuilder data) {}
        static void appendOtherPlayerAwareness(StringBuilder data, Minecraft mc) {}
        static void appendPlayerEquipment(StringBuilder data, Player player) {}
        static void appendHeroOutfit(StringBuilder data, Minecraft mc) {}
        """;
}
