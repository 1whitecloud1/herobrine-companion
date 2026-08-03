package com.whitecloud233.modid.herobrine_companion.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Detects paired white eyes in the front face UV of a player skin. */
final class HeroSkinEyeDetector {
    private static final int MIN_ALPHA = 64;
    // Eye whites must be bright and nearly neutral. A loose saturation check makes
    // pale skin tones join the eyes into one large component on many HD skins.
    private static final int MIN_CHANNEL = 225;
    private static final int MIN_AVERAGE = 235;
    private static final int MAX_CHROMA = 18;

    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DY = {0, 0, 1, -1};

    private HeroSkinEyeDetector() {}

    @Nullable
    static Result detect(NativeImage skin) {
        int width = skin.getWidth();
        int height = skin.getHeight();
        if (width < 64 || height < 32) {
            return null;
        }

        float scale = width / 64.0F;
        if (scale < 1.0F || height + 0.01F < 32.0F * scale) {
            return null;
        }

        EyePair baseEyes = findBestPair(skin, scale, 8, 8);
        // The hat UV commonly contains white hair highlights and decorations. It
        // should only supply the eyes when the base face has no usable pair.
        EyePair eyes = baseEyes != null ? baseEyes : findBestPair(skin, scale, 40, 8);
        if (eyes == null) {
            return null;
        }

        NativeImage overlay = new NativeImage(width, height, true);
        int pixelCount = copyComponent(skin, overlay, eyes.left)
                + copyComponent(skin, overlay, eyes.right);
        if (pixelCount == 0) {
            overlay.close();
            return null;
        }
        return new Result(overlay, pixelCount, scale);
    }

    @Nullable
    private static EyePair findBestPair(NativeImage skin, float scale, int faceU, int faceV) {
        int faceX = uv(faceU, scale);
        int faceY = uv(faceV, scale);
        int faceEndX = uv(faceU + 8, scale);
        int eyeStartY = uv(faceV + 2, scale);
        int eyeEndY = Math.min(uv(faceV + 7, scale), skin.getHeight());
        int middleX = uv(faceU + 4, scale);

        if (faceX < 0 || faceY < 0 || faceEndX > skin.getWidth() || eyeStartY >= eyeEndY) {
            return null;
        }

        List<Component> left = components(skin, faceX, middleX, eyeStartY, eyeEndY, scale);
        List<Component> right = components(skin, middleX, faceEndX, eyeStartY, eyeEndY, scale);
        EyePair best = null;

        for (Component leftEye : left) {
            for (Component rightEye : right) {
                float verticalGap = Math.abs(leftEye.centerY() - rightEye.centerY());
                if (verticalGap > Math.max(1.0F, 1.35F * scale)) {
                    continue;
                }

                float sizeRatio = Math.min(leftEye.pixels.size(), rightEye.pixels.size())
                        / (float) Math.max(leftEye.pixels.size(), rightEye.pixels.size());
                if (sizeRatio < 0.28F) {
                    continue;
                }

                // Player skins commonly place eyes between rows 12 and 14. Using
                // the middle of that range supports both vanilla and lower anime eyes.
                float expectedY = uv(faceV + 5.0F, scale);
                float yDistance = Math.abs((leftEye.centerY() + rightEye.centerY()) * 0.5F - expectedY);
                float expectedLeftX = uv(faceU + 2.0F, scale);
                float expectedRightX = uv(faceU + 6.0F, scale);
                float xDistance = Math.abs(leftEye.centerX() - expectedLeftX)
                        + Math.abs(rightEye.centerX() - expectedRightX);
                float edgeContrast = boundaryContrast(skin, leftEye) + boundaryContrast(skin, rightEye);
                float score = (leftEye.averageBrightness() + rightEye.averageBrightness())
                        + sizeRatio * 180.0F
                        + edgeContrast * 0.35F
                        - verticalGap * 45.0F
                        - yDistance * 18.0F
                        - xDistance * 12.0F;
                EyePair candidate = new EyePair(leftEye, rightEye, score);
                if (best == null || candidate.score > best.score) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    private static List<Component> components(NativeImage skin, int x0, int x1, int y0, int y1, float scale) {
        int regionWidth = x1 - x0;
        int regionHeight = y1 - y0;
        boolean[] visited = new boolean[regionWidth * regionHeight];
        List<Component> result = new ArrayList<>();

        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int index = (y - y0) * regionWidth + x - x0;
                if (visited[index] || !isEyeWhite(skin.getPixelRGBA(x, y))) {
                    visited[index] = true;
                    continue;
                }

                Component component = floodFill(skin, x, y, x0, x1, y0, y1, visited);
                float maxWidth = Math.max(2.0F, 3.25F * scale);
                float maxHeight = Math.max(3.0F, 3.25F * scale);
                int minArea = Math.max(1, Math.round(scale * scale * 0.25F));
                if (component.pixels.size() >= minArea
                        && component.width() <= maxWidth
                        && component.height() <= maxHeight) {
                    result.add(component);
                }
            }
        }
        return result;
    }

    private static Component floodFill(NativeImage skin, int startX, int startY,
                                       int x0, int x1, int y0, int y1, boolean[] visited) {
        int regionWidth = x1 - x0;
        ArrayDeque<Pixel> queue = new ArrayDeque<>();
        Component component = new Component();
        queue.add(new Pixel(startX, startY));

        while (!queue.isEmpty()) {
            Pixel pixel = queue.removeFirst();
            int index = (pixel.y - y0) * regionWidth + pixel.x - x0;
            if (visited[index]) {
                continue;
            }
            visited[index] = true;

            int color = skin.getPixelRGBA(pixel.x, pixel.y);
            if (!isEyeWhite(color)) {
                continue;
            }
            component.add(pixel, brightness(color));

            for (int direction = 0; direction < DX.length; direction++) {
                int nextX = pixel.x + DX[direction];
                int nextY = pixel.y + DY[direction];
                if (nextX >= x0 && nextX < x1 && nextY >= y0 && nextY < y1) {
                    queue.addLast(new Pixel(nextX, nextY));
                }
            }
        }
        return component;
    }

    private static boolean isEyeWhite(int abgr) {
        int alpha = (abgr >>> 24) & 0xFF;
        int blue = (abgr >>> 16) & 0xFF;
        int green = (abgr >>> 8) & 0xFF;
        int red = abgr & 0xFF;
        int maximum = Math.max(red, Math.max(green, blue));
        int minimum = Math.min(red, Math.min(green, blue));
        return alpha >= MIN_ALPHA
                && minimum >= MIN_CHANNEL
                && (red + green + blue) / 3 >= MIN_AVERAGE
                && maximum - minimum <= MAX_CHROMA;
    }

    private static int brightness(int abgr) {
        int blue = (abgr >>> 16) & 0xFF;
        int green = (abgr >>> 8) & 0xFF;
        int red = abgr & 0xFF;
        return (red + green + blue) / 3;
    }

    private static float boundaryContrast(NativeImage skin, Component component) {
        int brightnessSum = 0;
        int sampleCount = 0;
        int x0 = Math.max(0, component.minX - 1);
        int x1 = Math.min(skin.getWidth() - 1, component.maxX + 1);
        int y0 = Math.max(0, component.minY - 1);
        int y1 = Math.min(skin.getHeight() - 1, component.maxY + 1);

        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                if (x >= component.minX && x <= component.maxX
                        && y >= component.minY && y <= component.maxY) {
                    continue;
                }
                int color = skin.getPixelRGBA(x, y);
                if (((color >>> 24) & 0xFF) > 0) {
                    brightnessSum += brightness(color);
                    sampleCount++;
                }
            }
        }
        if (sampleCount == 0) {
            return 0.0F;
        }
        return Math.max(0.0F, component.averageBrightness() - brightnessSum / (float) sampleCount);
    }

    private static int copyComponent(NativeImage skin, NativeImage overlay, Component component) {
        for (Pixel pixel : component.pixels) {
            overlay.setPixelRGBA(pixel.x, pixel.y, skin.getPixelRGBA(pixel.x, pixel.y) | 0xFF000000);
        }
        return component.pixels.size();
    }

    private static int uv(float coordinate, float scale) {
        return Math.round(coordinate * scale);
    }

    static final class Result {
        private final NativeImage overlay;
        private final int pixelCount;
        private final float scale;

        Result(NativeImage overlay, int pixelCount, float scale) {
            this.overlay = overlay;
            this.pixelCount = pixelCount;
            this.scale = scale;
        }

        NativeImage overlay() {
            return overlay;
        }

        int pixelCount() {
            return pixelCount;
        }

        float scale() {
            return scale;
        }
    }

    private static final class EyePair {
        private final Component left;
        private final Component right;
        private final float score;

        private EyePair(Component left, Component right, float score) {
            this.left = left;
            this.right = right;
            this.score = score;
        }
    }

    private static final class Component {
        private final List<Pixel> pixels = new ArrayList<>();
        private int minX = Integer.MAX_VALUE;
        private int minY = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE;
        private int maxY = Integer.MIN_VALUE;
        private int brightnessSum;

        private void add(Pixel pixel, int brightness) {
            pixels.add(pixel);
            minX = Math.min(minX, pixel.x);
            minY = Math.min(minY, pixel.y);
            maxX = Math.max(maxX, pixel.x);
            maxY = Math.max(maxY, pixel.y);
            brightnessSum += brightness;
        }

        private int width() {
            return maxX - minX + 1;
        }

        private int height() {
            return maxY - minY + 1;
        }

        private float centerY() {
            return (minY + maxY) * 0.5F;
        }

        private float centerX() {
            return (minX + maxX) * 0.5F;
        }

        private float averageBrightness() {
            return brightnessSum / (float) pixels.size();
        }
    }

    private static final class Pixel {
        private final int x;
        private final int y;

        private Pixel(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}
