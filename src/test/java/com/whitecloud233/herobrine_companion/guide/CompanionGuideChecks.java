package com.whitecloud233.herobrine_companion.guide;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 同行手册的静态回归检查。
 *
 * <p>只读源码与资源，不启动游戏、不依赖 Patchouli：验证的是「书能被子类加载、
 * 组件写法和 1.21.1 对得上」这三件容易在升级时悄悄坏掉的事。</p>
 *
 * <ol>
 *   <li><b>禁止退回 1.20.1 的 NBT 写法</b>——{@code getOrCreateTag()} / {@code getTag()}
 *       在 1.20.5+ 已被删除，混进来就说明有人把旧代码搬了回来。</li>
 *   <li><b>必须走数据组件</b>——指南代码里要出现 {@code set(<patchouli:book>, ResourceLocation)}，
 *       且组件值不能是字符串。</li>
 *   <li><b>书本资源自洽</b>——book.json 必须在 {@code data/}，正文在 {@code assets/}；
 *       每个 entry 引用的 category 必须真实存在，每条 {@code pages[].recipe} 必须在配方目录里。</li>
 *   <li><b>软依赖成立</b>——主源码不得 import 任何 {@code vazkii.patchouli.*}，
 *       否则未安装 Patchouli 时会在类加载阶段炸掉。</li>
 * </ol>
 */
public final class CompanionGuideChecks {

    private static final String BOOK = "companion_guide";
    private static final String MOD = "herobrine_companion";

    private static int checks;

    public static void main(String[] args) throws IOException {
        Path root = Path.of(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        Path javaRoot = root.resolve("src/main/java/com/whitecloud233/herobrine_companion");
        Path resources = root.resolve("src/main/resources");
        Path generated = root.resolve("src/generated/resources");

        checkNoLegacyTagApi(javaRoot);
        checkDataComponentUsage(javaRoot);
        checkNoPatchouliImports(javaRoot);
        checkBookLayout(resources, generated);

        System.out.println("CompanionGuideChecks: " + checks + " checks passed");
    }

    // ---------------------------------------------------------------- 1. NBT 旧写法

    /** 1.20.1 的 {@code getOrCreateTag().putString("patchouli:book", ...)} 必须彻底消失。 */
    private static void checkNoLegacyTagApi(Path javaRoot) throws IOException {
        Pattern legacy = Pattern.compile("getOrCreateTag\\s*\\(|\\.getTag\\s*\\(\\s*\\)");
        List<String> offenders = new ArrayList<>();
        for (Path file : javaSources(javaRoot)) {
            // 注释里合法地写着「旧版这样写、1.21.1 已失效」这类说明，不能算违规；
            // 只有真正出现在代码里的调用才作数。
            String text = stripComments(Files.readString(file, StandardCharsets.UTF_8));
            Matcher matcher = legacy.matcher(text);
            while (matcher.find()) {
                offenders.add(javaRoot.relativize(file) + " -> " + matcher.group().trim());
            }
        }
        if (!offenders.isEmpty()) {
            throw new AssertionError("1.20.1 的 ItemStack NBT 写法在 1.21.1 上已不可用: " + offenders);
        }
        checks++;
    }

    // ---------------------------------------------------------------- 2. 数据组件写法

    private static void checkDataComponentUsage(Path javaRoot) throws IOException {
        Path service = javaRoot.resolve("guide/CompanionGuideService.java");
        if (!Files.isRegularFile(service)) {
            throw new AssertionError("CompanionGuideService is missing: " + service);
        }
        String text = Files.readString(service, StandardCharsets.UTF_8);

        // 组件按注册名取得，值必须按 ResourceLocation 写入。
        require(text.contains("DATA_COMPONENT_TYPE"), "guide 服务必须从 DATA_COMPONENT_TYPE 注册表取 patchouli:book");
        require(text.contains("\"patchouli\", \"book\""), "patchouli:book 组件 ID 必须为 patchouli:book");
        require(text.contains("guide.set("), "必须通过 ItemStack#set 写入数据组件");
        require(text.contains("ResourceLocation"), "组件值类型必须是 ResourceLocation");
        checks++;

        // 物品本体仍然是 patchouli:guide_book（1.21.x 未改名）。
        require(text.contains("\"patchouli\", \"guide_book\""), "手册物品必须仍是 patchouli:guide_book");
        checks++;
    }

    // ---------------------------------------------------------------- 3. 软依赖

    private static void checkNoPatchouliImports(Path javaRoot) throws IOException {
        for (Path file : javaSources(javaRoot)) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (text.contains("import vazkii.patchouli")) {
                throw new AssertionError("主源码不得 import Patchouli 类型（软依赖）: " + javaRoot.relativize(file));
            }
        }
        checks++;
    }

    // ---------------------------------------------------------------- 4. 书本资源

    private static void checkBookLayout(Path resources, Path generated) throws IOException {
        Path bookJson = resources.resolve("data/" + MOD + "/patchouli_books/" + BOOK + "/book.json");
        require(Files.isRegularFile(bookJson), "book.json 必须位于 data/" + MOD + "/patchouli_books/" + BOOK + "/");
        checks++;

        // 这个检查跑在只含 JDK 的 classpath 上（不拉起游戏与 Gson），因此用文本匹配。
        String book = Files.readString(bookJson, StandardCharsets.UTF_8);
        require(book.contains("\"name\""), "book.json 缺少 name");
        require(book.contains("\"use_resource_pack\"") && book.contains("\"use_resource_pack\": true"),
                "1.20+ 起 Patchouli 强制要求 use_resource_pack=true，否则加载时直接抛异常");
        checks++;

        // 1.21 的 Book.getSubtitle()：version 解析为 0 时用 subtitle 键，否则显示 "Nth Edition"。
        // 想让副标题文案生效，version 必须保持整数 0。
        if (book.contains("\"subtitle\"")) {
            Matcher version = Pattern.compile("\"version\"\\s*:\\s*(\\d+)").matcher(book);
            require(version.find() && version.group(1).equals("0"),
                    "book.json 配了 subtitle 时 version 必须为 0，否则副标题会被替换成版本号");
            checks++;
        }

        Matcher model = Pattern.compile("\"model\"\\s*:\\s*\"([^\"]+)\"").matcher(book);
        if (model.find()) {
            String value = model.group(1);
            String path = value.substring(value.indexOf(':') + 1);
            require(Files.isRegularFile(resources.resolve("assets/" + MOD + "/models/item/" + path + ".json")),
                    "book.json 引用的物品模型不存在: " + value);
            checks++;
        }

        Set<String> recipes = recipeIds(resources, generated);
        require(!recipes.isEmpty(), "未找到任何配方，配方页会全部显示为缺失");
        checks++;

        for (String lang : new String[]{"en_us", "zh_cn"}) {
            checks += checkLanguage(resources, lang, recipes);
        }
    }

    private static int checkLanguage(Path resources, String lang, Set<String> recipes) throws IOException {
        int local = 0;
        Path dir = resources.resolve("assets/" + MOD + "/patchouli_books/" + BOOK + "/" + lang);
        require(Files.isDirectory(dir), "缺少语言目录: " + lang);

        Set<String> categories = new LinkedHashSet<>();
        try (var paths = Files.list(dir.resolve("categories"))) {
            for (Path p : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                String text = Files.readString(p, StandardCharsets.UTF_8);
                require(text.contains("\"name\""), "分类缺少 name: " + p.getFileName());
                categories.add(MOD + ":" + stripJson(p.getFileName().toString()));
                local++;
            }
        }
        require(!categories.isEmpty(), lang + " 没有任何分类");
        local++;

        List<Path> entries = new ArrayList<>();
        try (var paths = Files.walk(dir.resolve("entries"))) {
            paths.filter(p -> p.toString().endsWith(".json")).forEach(entries::add);
        }
        require(!entries.isEmpty(), lang + " 没有任何词条");

        for (Path entry : entries) {
            String text = Files.readString(entry, StandardCharsets.UTF_8);
            String rel = dir.relativize(entry).toString();

            Matcher category = Pattern.compile("\"category\"\\s*:\\s*\"([^\"]+)\"").matcher(text);
            require(category.find(), lang + "/" + rel + " 缺少 category");
            require(categories.contains(category.group(1)),
                    lang + "/" + rel + " 引用了不存在的分类: " + category.group(1));

            // 配方页引用的 id 必须真的有对应配方，否则书里会显示成空槽。
            Matcher recipe = Pattern.compile("\"recipe\"\\s*:\\s*\"([^\"]+)\"").matcher(text);
            while (recipe.find()) {
                String id = recipe.group(1);
                String path = id.substring(id.indexOf(':') + 1);
                require(recipes.contains(path), lang + "/" + rel + " 引用了不存在的配方: " + id);
                local++;
            }

            // 每个词条至少要有 pages，内容页才是可读的。
            require(text.contains("\"pages\""), lang + "/" + rel + " 缺少 pages");
            local++;
        }
        return local;
    }

    private static Set<String> recipeIds(Path... roots) throws IOException {
        Set<String> ids = new LinkedHashSet<>();
        for (Path root : roots) {
            if (root == null || !Files.isDirectory(root)) {
                continue;
            }
            Path recipeDir = root.resolve("data/" + MOD + "/recipe");
            if (!Files.isDirectory(recipeDir)) {
                continue;
            }
            try (var paths = Files.list(recipeDir)) {
                paths.filter(p -> p.toString().endsWith(".json"))
                        .forEach(p -> ids.add(stripJson(p.getFileName().toString())));
            }
        }
        return ids;
    }

    // ---------------------------------------------------------------- helpers

    private static List<Path> javaSources(Path javaRoot) throws IOException {
        try (var paths = Files.walk(javaRoot)) {
            return paths.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    /**
     * 去掉行注释与块注释，避免把文档里的旧写法示例误判成真实调用。
     *
     * <p>不做完整词法分析，只需保证字符串字面量不被误伤：本项目用不到带双斜杠的
     * URL 常量出现在被检查的行上，因此按行简单状态机即可。</p>
     */
    private static String stripComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        boolean inBlock = false;
        boolean inLine = false;
        boolean inString = false;
        boolean inChar = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';

            if (inLine) {
                if (c == '\n') {
                    inLine = false;
                    out.append(c);
                }
                continue;
            }
            if (inBlock) {
                if (c == '*' && next == '/') {
                    inBlock = false;
                    i++;
                } else if (c == '\n') {
                    out.append(c);
                }
                continue;
            }
            if (inString || inChar) {
                out.append(c);
                if (c == '\\') {
                    if (next != '\0') {
                        out.append(next);
                        i++;
                    }
                } else if ((inString && c == '"') || (inChar && c == '\'')) {
                    inString = false;
                    inChar = false;
                }
                continue;
            }
            if (c == '/' && next == '/') {
                inLine = true;
                i++;
                continue;
            }
            if (c == '/' && next == '*') {
                inBlock = true;
                i++;
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '\'') {
                inChar = true;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String stripJson(String name) {
        return name.substring(0, name.length() - ".json".length());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
