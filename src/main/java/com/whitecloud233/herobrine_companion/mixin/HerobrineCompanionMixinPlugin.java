package com.whitecloud233.herobrine_companion.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

public class HerobrineCompanionMixinPlugin implements IMixinConfigPlugin {

    /** mixin 插件在配置系统加载前运行，只能用 slf4j；debug 级默认不输出。 */
    private static final Logger LOGGER = LoggerFactory.getLogger("HerobrineCompanion/Mixin");

    private boolean isHeroKingAuraEnabled = true;

    @Override
    public void onLoad(String mixinPackage) {
        Path configPath = Paths.get("run/config/herobrine_companion-common.toml");
        if (!Files.exists(configPath)) {
            configPath = Paths.get("config/herobrine_companion-common.toml");
        }

        if (Files.exists(configPath)) {
            try {
                List<String> lines = Files.readAllLines(configPath);
                for (String line : lines) {
                    if (line.trim().startsWith("heroKingAuraEnabled")) {
                        String[] parts = line.split("=");
                        if (parts.length == 2) {
                            this.isHeroKingAuraEnabled = Boolean.parseBoolean(parts[1].trim());
                            LOGGER.debug("Found config: heroKingAuraEnabled = {}", this.isHeroKingAuraEnabled);
                        }
                        break;
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("读取配置文件失败，heroKingAuraEnabled 回落到默认值 true", e);
            }
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // Check if the mixin is one of the ones controlled by the config
        String simpleName = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        if (simpleName.equals("DragonSittingScanningPhaseMixin") ||
            simpleName.equals("EnderDragonMixin") ||
            simpleName.equals("MobMixin") ||
            simpleName.equals("LookAtGoalMixin") ||
            simpleName.equals("RandomLookAroundGoalMixin")) {

            // If the config is disabled, do not apply these mixins
            return this.isHeroKingAuraEnabled;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
