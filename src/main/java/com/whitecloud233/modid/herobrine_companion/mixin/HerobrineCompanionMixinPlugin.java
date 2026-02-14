package com.whitecloud233.modid.herobrine_companion.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

public class HerobrineCompanionMixinPlugin implements IMixinConfigPlugin {

    private boolean isHeroKingAuraEnabled = true;

    @Override
    public void onLoad(String mixinPackage) {
        // 尝试手动读取配置文件
        // 注意：此时 Forge/NeoForge 的 Config 系统尚未初始化，必须手动解析文件
        // 优先检查 run/config (开发环境)，然后是 config (生产环境)
        Path configPath = Paths.get("run/config/herobrine_companion-common.toml");
        if (!Files.exists(configPath)) {
            configPath = Paths.get("config/herobrine_companion-common.toml");
        }

        if (Files.exists(configPath)) {
            try {
                List<String> lines = Files.readAllLines(configPath);
                for (String line : lines) {
                    // 简单的字符串匹配，寻找配置项
                    if (line.trim().startsWith("heroKingAuraEnabled")) {
                        String[] parts = line.split("=");
                        if (parts.length == 2) {
                            this.isHeroKingAuraEnabled = Boolean.parseBoolean(parts[1].trim());
                            System.out.println("[HerobrineCompanion] Found config: heroKingAuraEnabled = " + this.isHeroKingAuraEnabled);
                        }
                        break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // 判断是否是我们需要控制的 Mixin
        if (mixinClassName.endsWith("DragonSittingScanningPhaseMixin") || 
            mixinClassName.endsWith("EnderDragonMixin")) {
            
            // 如果配置禁用了，就不加载这些 Mixin
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