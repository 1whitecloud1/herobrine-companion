package com.whitecloud233.herobrine_companion.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader; // 【新增】1.21.1 核心渲染上传器
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.whitecloud233.herobrine_companion.client.fight.event.ClientCollapseHandler;
import com.whitecloud233.herobrine_companion.fight.network.CPacketCollapseFinished;
import com.whitecloud233.herobrine_companion.network.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class FakeCrashScreen extends Screen {

    private int ticksOpen = 0;

    private String crashText = "";
    private String meta1 = "";
    private String meta2 = "";
    private String meta3 = "";

    private int typeIndex1 = 0;
    private int typeIndex2 = 0;
    private int typeIndex3 = 0;
    private float finalFadeOut = 0.0f;

    private final List<CrackSegment> cracks = new ArrayList<>();
    private final List<GlassShard> shards = new ArrayList<>();

    private static class CrackSegment {
        float x1, y1, x2, y2, thickness;
        int revealTick;

        CrackSegment(float x1, float y1, float x2, float y2, float thickness, int revealTick) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
            this.thickness = thickness; this.revealTick = revealTick;
        }
    }

    private static class GlassShard {
        float x, y, vx, vy;
        float angle, angularVel;
        float[] ptsX = new float[3];
        float[] ptsY = new float[3];
        int color;

        GlassShard(float x, float y, float vx, float vy, float size, int color) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.color = color;
            this.angle = (float) (Math.random() * Math.PI * 2);
            this.angularVel = (float) ((Math.random() - 0.5) * 0.8);

            ptsX[0] = 0;
            ptsY[0] = -size;
            ptsX[1] = -size * (0.2f + (float)Math.random() * 0.8f);
            ptsY[1] = size * (0.2f + (float)Math.random() * 0.8f);
            ptsX[2] = size * (0.2f + (float)Math.random() * 0.8f);
            ptsY[2] = size * (0.2f + (float)Math.random() * 0.8f);
        }

        void update() {
            this.x += this.vx;
            this.y += this.vy;
            this.vy += 1.8f;
            this.vx *= 0.95f;
            this.vy *= 0.98f;
            this.angle += this.angularVel;
        }
    }

    public FakeCrashScreen() {
        super(Component.literal("Crash Report"));
    }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().stop();
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.ENDERMAN_STARE, 1.0F, 0.5F);
            mc.player.playSound(SoundEvents.MINECART_RIDING, 1.0F, 2.0F);
        }
        mc.setScreen(new FakeCrashScreen());
    }

    @Override
    protected void init() {
        super.init();

        crashText = Component.translatable("gui.herobrine_companion.fake_crash.text").getString();
        meta1 = Component.translatable("gui.herobrine_companion.fake_crash.meta1").getString();
        meta2 = Component.translatable("gui.herobrine_companion.fake_crash.meta2").getString();
        meta3 = Component.translatable("gui.herobrine_companion.fake_crash.meta3", Minecraft.getInstance().getUser().getName()).getString();

        cracks.clear();
        shards.clear();
    }

    private void generateProceduralCracks(int w, int h) {
        cracks.clear();
        Random rand = new Random();
        int cx = w / 2;
        int cy = h / 2;
        float maxR = Math.max(w, h) * 1.2f;

        generateImpactCracks(rand, cx, cy, 6, 430, 3.5f, maxR);
        generateImpactCracks(rand, cx + 25, cy - 15, 10, 460, 2.0f, maxR);
        generateImpactCracks(rand, cx - 35, cy + 20, 12, 465, 1.5f, maxR);
    }

    private void generateImpactCracks(Random rand, float startX, float startY, int numMainCracks, int startTick, float baseThickness, float maxRadius) {
        class CrackNode {
            float x, y, angle, thickness, lengthLeft;
            int tick;
            CrackNode(float x, float y, float angle, float thickness, float lengthLeft, int tick) {
                this.x = x; this.y = y; this.angle = angle; this.thickness = thickness; this.lengthLeft = lengthLeft; this.tick = tick;
            }
        }
        List<CrackNode> queue = new ArrayList<>();

        for(int i = 0; i < numMainCracks; i++) {
            float angle = (float) (i * 2 * Math.PI / numMainCracks + (rand.nextFloat() - 0.5) * 0.8);
            queue.add(new CrackNode(startX, startY, angle, baseThickness, maxRadius, startTick));
        }

        for(int i = 0; i < numMainCracks * 4; i++) {
            float r = rand.nextFloat() * 40;
            float ang = rand.nextFloat() * (float)Math.PI * 2;
            float fx = startX + (float)Math.cos(ang) * r;
            float fy = startY + (float)Math.sin(ang) * r;
            float flen = 5 + rand.nextFloat() * 12;
            float fang = ang + (float)Math.PI/2 + (rand.nextFloat() - 0.5f);
            cracks.add(new CrackSegment(fx, fy, fx + (float)Math.cos(fang)*flen, fy + (float)Math.sin(fang)*flen, baseThickness * 0.4f, startTick + rand.nextInt(4)));
        }

        while(!queue.isEmpty()) {
            CrackNode node = queue.remove(0);
            if (node.lengthLeft <= 0 || node.thickness < 0.15f) continue;
            if (node.x < -200 || node.x > this.width + 200 || node.y < -200 || node.y > this.height + 200) continue;

            float segmentLen = 10 + rand.nextFloat() * 40;
            if (segmentLen > node.lengthLeft) segmentLen = node.lengthLeft;

            float nextX = node.x + (float)Math.cos(node.angle) * segmentLen;
            float nextY = node.y + (float)Math.sin(node.angle) * segmentLen;

            cracks.add(new CrackSegment(node.x, node.y, nextX, nextY, node.thickness, node.tick));
            float newAngle = node.angle + (rand.nextFloat() - 0.5f) * 0.5f;

            if (rand.nextFloat() < 0.15f) newAngle += (rand.nextBoolean() ? 1 : -1) * (0.8f + rand.nextFloat() * 0.5f);
            if (rand.nextFloat() < 0.35f) {
                float branchAngle = node.angle + (rand.nextBoolean() ? 1 : -1) * (0.4f + rand.nextFloat() * 0.6f);
                queue.add(new CrackNode(nextX, nextY, branchAngle, node.thickness * 0.6f, node.lengthLeft - segmentLen, node.tick + 1 + rand.nextInt(2)));
            }

            queue.add(new CrackNode(nextX, nextY, newAngle, node.thickness * 0.85f, node.lengthLeft - segmentLen, node.tick + 1 + rand.nextInt(3)));
        }
    }

    private void generateExplosionShards() {
        shards.clear();
        Random rand = new Random();
        int cx = this.width / 2;
        int cy = this.height / 2;

        for (int i = 0; i < 150; i++) {
            float angle = rand.nextFloat() * (float) Math.PI * 2;
            float speed = 15 + rand.nextFloat() * 45;
            float vx = (float) Math.cos(angle) * speed;
            float vy = (float) Math.sin(angle) * speed;

            float size = 10 + rand.nextFloat() * 35;

            int alpha = 100 + rand.nextInt(155);
            int color = (alpha << 24) | (rand.nextBoolean() ? 0xFFFFFF : 0x99AABB);

            shards.add(new GlassShard(cx, cy, vx, vy, size, color));
        }
    }

    @Override
    public void tick() {
        ticksOpen++;
        Minecraft mc = Minecraft.getInstance();

        // 【修复】移除了 .get()
        if (ticksOpen == 200 && mc.player != null) mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.5F);

        if (ticksOpen > 220 && ticksOpen <= 220 + meta1.length() * 4) if (ticksOpen % 4 == 0) { typeIndex1++; playTypeSound(mc); }
        if (ticksOpen > 280 && ticksOpen <= 280 + meta2.length() * 4) if (ticksOpen % 4 == 0) { typeIndex2++; playTypeSound(mc); }
        if (ticksOpen > 360 && ticksOpen <= 360 + meta3.length() * 6) if (ticksOpen % 6 == 0) { typeIndex3++; playTypeSound(mc); }

        if (ticksOpen == 430 && mc.player != null) {
            mc.player.playSound(SoundEvents.ANVIL_USE, 1.0F, 1.5F);
            mc.player.playSound(SoundEvents.MINECART_RIDING, 0.5F, 0.8F);
        }
        if (ticksOpen == 460 && mc.player != null) {
            mc.player.playSound(SoundEvents.GLASS_BREAK, 1.5F, 0.8F);
            mc.player.playSound(SoundEvents.BEACON_ACTIVATE, 1.0F, 1.0F);
        }

        if (ticksOpen == 490 && mc.player != null) {
            mc.player.playSound(SoundEvents.GLASS_BREAK, 2.0F, 0.8F);
            mc.player.playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 0.5F);
            mc.player.playSound(SoundEvents.WARDEN_ROAR, 2.0F, 0.5F);
            generateExplosionShards();
        }

        if (ticksOpen >= 490) {
            for (GlassShard shard : shards) shard.update();
        }

        if (ticksOpen >= 510) finalFadeOut = Math.min(1.0f, finalFadeOut + 0.05f);
        if (ticksOpen > 560) this.onClose();
    }

    private void playTypeSound(Minecraft mc) {
        // 【修复】移除了 .get()
        if (mc.player != null) mc.player.playSound(SoundEvents.NOTE_BLOCK_HAT.value(), 1.0f, 1.5f);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (cracks.isEmpty() && this.width > 0) generateProceduralCracks(this.width, this.height);

        int bgAlphaVal = (int)((1.0f - finalFadeOut) * 255);
        int textColorAlpha = (int)((1.0f - finalFadeOut) * 255) << 24;

        int bgColor = (bgAlphaVal << 24) | 0x000000;
        graphics.fill(0, 0, this.width, this.height, bgColor);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        if (ticksOpen < 200) {
            graphics.drawString(this.font, crashText, centerX - this.font.width(crashText) / 2, centerY, textColorAlpha | 0xFFFFFF, false);
        } else if (ticksOpen < 560) {
            if (typeIndex1 > 0) {
                String t1 = meta1.substring(0, Math.min(typeIndex1, meta1.length()));
                graphics.drawString(this.font, t1, centerX - this.font.width(meta1) / 2, centerY - 20, textColorAlpha | 0xFF3333, false);
            }
            if (typeIndex2 > 0) {
                String t2 = meta2.substring(0, Math.min(typeIndex2, meta2.length()));
                graphics.drawString(this.font, t2, centerX - this.font.width(meta2) / 2, centerY, textColorAlpha | 0xFF3333, false);
            }
            if (typeIndex3 > 0) {
                String t3 = meta3.substring(0, Math.min(typeIndex3, meta3.length()));
                graphics.drawString(this.font, t3, centerX - this.font.width(meta3) / 2, centerY + 20, textColorAlpha | 0xAA0000, false);
            }
        }

        if (ticksOpen >= 490 && ticksOpen < 510) {
            float flashFade = 1.0f - ((ticksOpen - 490) / 20.0f);
            int flashColor = ((int)(0.8f * (1.0f - finalFadeOut) * flashFade * 255) << 24) | 0xFFFFFF;
            graphics.fill(0, 0, this.width, this.height, flashColor);
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        float currentFadeAlpha = (1.0f - finalFadeOut);

        for (CrackSegment crack : cracks) {
            if (ticksOpen >= crack.revealTick) {
                float age = Math.min(30, ticksOpen - crack.revealTick) / 30.0f;
                float lineAlpha = (0.5f - age * 0.2f) * currentFadeAlpha;

                int alphaBase = (int)(lineAlpha * 255);
                int shadowAlpha = (int)(alphaBase * 0.4f) << 24;
                drawThickLine(graphics, crack.x1 + 1.5f, crack.y1 + 1.5f, crack.x2 + 1.5f, crack.y2 + 1.5f,
                        crack.thickness * 1.5f, shadowAlpha | 0x112222);

                int midAlpha = (int)(alphaBase * 0.7f) << 24;
                drawThickLine(graphics, crack.x1, crack.y1, crack.x2, crack.y2,
                        crack.thickness * 1.0f, midAlpha | 0x99AABB);

                int highLightAlpha = alphaBase << 24;
                drawThickLine(graphics, crack.x1, crack.y1, crack.x2, crack.y2,
                        crack.thickness * 0.3f, highLightAlpha | 0xFFFFFF);
            }
        }

        if (ticksOpen >= 490) {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            for (GlassShard shard : shards) {
                graphics.pose().pushPose();

                graphics.pose().translate(shard.x, shard.y, 0);
                graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotation(shard.angle));

                int originalAlpha = (shard.color >> 24) & 0xFF;
                int currentAlpha = (int)(originalAlpha * currentFadeAlpha);
                int renderColor = (shard.color & 0x00FFFFFF) | (currentAlpha << 24);

                drawShardTriangle(graphics, shard.ptsX, shard.ptsY, renderColor);

                graphics.pose().popPose();
            }
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private void drawShardTriangle(GuiGraphics graphics, float[] ptsX, float[] ptsY, int color) {
        Matrix4f pose = graphics.pose().last().pose();
        float a = (color >> 24 & 255) / 255.0F;
        float r = (color >> 16 & 255) / 255.0F;
        float g = (color >> 8 & 255) / 255.0F;
        float b = (color & 255) / 255.0F;

        Tesselator tesselator = Tesselator.getInstance();

        // 【核心修改 1】：Tesselator 现在直接调用 begin() 返回 BufferBuilder
        BufferBuilder bufferbuilder = tesselator.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i < 3; i++) {
            // 【核心修改 2】：使用 addVertex 和 setColor 方法压入顶点数据
            bufferbuilder.addVertex(pose, ptsX[i], ptsY[i], 0).setColor(r, g, b, a);
        }

        // 【核心修改 3】：彻底弃用 tesselator.end()，改用全新的 BufferUploader
        BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
    }

    private void drawThickLine(GuiGraphics graphics, float x1, float y1, float x2, float y2, float thickness, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length == 0 || thickness < 0.1f) return;

        float midX = (x1 + x2) / 2f;
        float midY = (y1 + y2) / 2f;
        float angle = (float) Math.atan2(dy, dx);

        graphics.pose().pushPose();
        graphics.pose().translate(midX, midY, 0);
        graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotation(angle));

        float scale = 100.0f;
        graphics.pose().scale(1.0f / scale, 1.0f / scale, 1.0f);

        int fillMinX = (int) (-length / 2.0f * scale);
        int fillMaxX = (int) (length / 2.0f * scale);
        int fillMinY = (int) (-thickness / 2.0f * scale);
        int fillMaxY = (int) (thickness / 2.0f * scale);

        graphics.fill(fillMinX, fillMinY, fillMaxX, fillMaxY, color);

        graphics.pose().popPose();
    }

    @Override
    public void onClose() {
        super.onClose();
        ClientCollapseHandler.stopCollapse();
        PacketHandler.sendToServer(new CPacketCollapseFinished());
    }
}