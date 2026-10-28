package dev.lesteoff.celestialskilltree.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SpriteBatch {
    public enum Layer { BACK, GLOW, FRAME, ICON, FRONT }

    private static final int FLOATS_PER_SPRITE = 10;
    private static final float FRAME_INSET = 1 / 256f;

    private final Map<Layer, Map<ResourceLocation, FloatArrayList>> layers = new EnumMap<>(Layer.class);

    public SpriteBatch() {
        for (Layer layer : Layer.values()) {
            layers.put(layer, new LinkedHashMap<>());
        }
    }

    public void add(Layer layer, ResourceLocation texture, double centerX, double centerY, double radius, double angle,
                    float red, float green, float blue, float alpha) {
        add(layer, texture, 0, 1, centerX, centerY, radius, angle, red, green, blue, alpha);
    }

    public void add(Layer layer, ResourceLocation texture, int frame, int frames, double centerX, double centerY, double radius,
                    double angle, float red, float green, float blue, float alpha) {
        FloatArrayList sprites = layers.get(layer).computeIfAbsent(texture, unused -> new FloatArrayList());
        sprites.add((float) centerX);
        sprites.add((float) centerY);
        sprites.add((float) radius);
        sprites.add((float) angle);
        sprites.add(red);
        sprites.add(green);
        sprites.add(blue);
        sprites.add(alpha);
        float inset = frames > 1 ? FRAME_INSET / frames : 0;
        sprites.add(frame / (float) frames + inset);
        sprites.add((frame + 1) / (float) frames - inset);
    }

    public void add(Layer layer, ResourceLocation texture, double centerX, double centerY, double radius) {
        add(layer, texture, centerX, centerY, radius, 0, 1f, 1f, 1f, 1f);
    }

    public void draw(GuiGraphics graphics) {
        graphics.flush();
        Matrix4f pose = graphics.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        for (Map<ResourceLocation, FloatArrayList> textures : layers.values()) {
            textures.forEach((texture, sprites) -> drawTexture(pose, texture, sprites));
            textures.values().forEach(FloatArrayList::clear);
        }
        RenderSystem.disableBlend();
    }

    private static void drawTexture(Matrix4f pose, ResourceLocation texture, FloatArrayList sprites) {
        if (sprites.isEmpty()) {
            return;
        }
        RenderSystem.setShaderTexture(0, texture);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float[] data = sprites.elements();
        for (int start = 0; start < sprites.size(); start += FLOATS_PER_SPRITE) {
            quad(buffer, pose, data, start);
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private static void quad(BufferBuilder buffer, Matrix4f pose, float[] data, int start) {
        float x = data[start];
        float y = data[start + 1];
        float radius = data[start + 2];
        float cos = (float) Math.cos(data[start + 3]) * radius;
        float sin = (float) Math.sin(data[start + 3]) * radius;
        float red = data[start + 4];
        float green = data[start + 5];
        float blue = data[start + 6];
        float alpha = data[start + 7];
        float left = data[start + 8];
        float right = data[start + 9];
        corner(buffer, pose, x - cos + sin, y - sin - cos, left, 0, red, green, blue, alpha);
        corner(buffer, pose, x - cos - sin, y - sin + cos, left, 1, red, green, blue, alpha);
        corner(buffer, pose, x + cos - sin, y + sin + cos, right, 1, red, green, blue, alpha);
        corner(buffer, pose, x + cos + sin, y + sin - cos, right, 0, red, green, blue, alpha);
    }

    private static void corner(BufferBuilder buffer, Matrix4f pose, float x, float y, float u, float v,
                               float red, float green, float blue, float alpha) {
        buffer.addVertex(pose, x, y, 0).setUv(u, v).setColor(red, green, blue, alpha);
    }
}
