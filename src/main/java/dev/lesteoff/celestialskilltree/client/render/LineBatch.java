package dev.lesteoff.celestialskilltree.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;


public final class LineBatch {
    private static final double MIN_LENGTH = 1e-4;

    private final Matrix4f pose;
    private final FloatArrayList positions = new FloatArrayList();
    private final IntArrayList colors = new IntArrayList();

    public LineBatch(GuiGraphics graphics) {
        graphics.flush();
        this.pose = new Matrix4f(graphics.pose().last().pose());
    }

    public void segment(double x0, double y0, double x1, double y1, float width, int argb) {
        double deltaX = x1 - x0;
        double deltaY = y1 - y0;
        double length = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        if (length < MIN_LENGTH) {
            return;
        }
        float halfWidth = width / 2;
        float alongX = (float) (deltaX / length * halfWidth);
        float alongY = (float) (deltaY / length * halfWidth);
        float startX = (float) x0 - alongX;
        float startY = (float) y0 - alongY;
        float endX = (float) x1 + alongX;
        float endY = (float) y1 + alongY;
        quad(startX - alongY, startY + alongX, endX - alongY, endY + alongX, endX + alongY, endY - alongX,
                startX + alongY, startY - alongX, argb);
    }

    public void rect(float x0, float y0, float x1, float y1, int argb) {
        quad(x0, y0, x0, y1, x1, y1, x1, y0, argb);
    }

    private void quad(float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy, int argb) {
        positions.addElements(positions.size(), new float[]{ax, ay, bx, by, cx, cy, dx, dy});
        colors.add(argb);
    }

    public void draw() {
        if (colors.isEmpty()) {
            return;
        }
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float[] xy = positions.elements();
        for (int quad = 0; quad < colors.size(); quad++) {
            int argb = colors.getInt(quad);
            for (int corner = 0; corner < 4; corner++) {
                int at = (quad * 4 + corner) * 2;
                buffer.addVertex(pose, xy[at], xy[at + 1], 0).setColor(argb);
            }
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}
