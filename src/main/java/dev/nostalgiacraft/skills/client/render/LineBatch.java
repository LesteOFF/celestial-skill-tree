package dev.nostalgiacraft.skills.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/** Batches thick line segments (as quads) into a single draw call. */
public final class LineBatch {
    private final Matrix4f pose;
    private final BufferBuilder buf;
    private boolean any;

    public LineBatch(GuiGraphics g) {
        g.flush();
        this.pose = new Matrix4f(g.pose().last().pose());
        this.buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
    }

    public void segment(double x0, double y0, double x1, double y1, float width, int argb) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-4) {
            return;
        }
        float nx = (float) (-dy / len * width / 2);
        float ny = (float) (dx / len * width / 2);
        // extend half a width at both ends so polyline joints have no gaps
        float ex = (float) (dx / len * width / 2);
        float ey = (float) (dy / len * width / 2);
        float ax = (float) x0 - ex, ay = (float) y0 - ey, bx = (float) x1 + ex, by = (float) y1 + ey;
        buf.addVertex(pose, ax + nx, ay + ny, 0).setColor(argb);
        buf.addVertex(pose, bx + nx, by + ny, 0).setColor(argb);
        buf.addVertex(pose, bx - nx, by - ny, 0).setColor(argb);
        buf.addVertex(pose, ax - nx, ay - ny, 0).setColor(argb);
        any = true;
    }

    public void rect(float x0, float y0, float x1, float y1, int argb) {
        buf.addVertex(pose, x0, y0, 0).setColor(argb);
        buf.addVertex(pose, x0, y1, 0).setColor(argb);
        buf.addVertex(pose, x1, y1, 0).setColor(argb);
        buf.addVertex(pose, x1, y0, 0).setColor(argb);
        any = true;
    }

    public void draw() {
        MeshData mesh = buf.build();
        if (mesh == null || !any) {
            if (mesh != null) {
                mesh.close();
            }
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(mesh);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}
