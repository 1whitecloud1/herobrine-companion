package com.whitecloud233.herobrine_companion.util;

import com.whitecloud233.herobrine_companion.entity.HeroEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * 英雄交互菜单（衣柜 / 交易）的"有效性"判定工具。
 *
 * <p>规则：只要 Herobrine 仍然存活、与玩家处于同一维度，并且它所在的区块仍然
 * 处于加载状态（即玩家周边的已加载区块区域），对应的菜单界面就保持打开；
 * 只有当 Herobrine 死亡、被移除或所在区块卸载后，界面才允许被强制关闭。
 *
 * <p>背景：原实现按「玩家与 Herobrine 的直线距离」（衣柜 8 格 / 交易沿用原版
 * 交易者校验）判定菜单有效性，导致玩家稍微走远一点——但 Herobrine 明明还在
 * 自己周边的加载区块里——页面就被强制关闭。这里把判定从「距离」改为
 * 「是否仍在同一已加载区块区域」，与需求一致：
 * 只要 herobrine 在玩家处于的加载区块里，就不要强制关闭页面。
 */
public final class HeroMenuValidity {

    private HeroMenuValidity() {
    }

    /**
     * Herobrine 是否仍处于玩家所在维度的已加载区块内。
     *
     * @param hero   打开菜单时绑定的 Herobrine 实体
     * @param player 打开菜单的玩家
     * @return true 表示菜单应保持打开；false 表示应允许关闭（hero 死亡 / 移除 / 区块卸载）
     */
    public static boolean isHeroInPlayerLoadedArea(HeroEntity hero, Player player) {
        if (hero == null || player == null) {
            return false;
        }
        if (!hero.isAlive()) {
            return false;
        }
        if (hero.level() != player.level()) {
            return false;
        }
        Level level = player.level();
        LevelChunk chunk = level.getChunkSource().getChunkNow(hero.chunkPosition().x, hero.chunkPosition().z);
        return chunk != null;
    }
}
