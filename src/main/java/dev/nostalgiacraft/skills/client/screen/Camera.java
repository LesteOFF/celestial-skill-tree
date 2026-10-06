package dev.nostalgiacraft.skills.client.screen;

public final class Camera {
    public static final double FIT_MIN_ZOOM = 0.03;

    double cx;
    double cy;
    double zoom = 1.0;
    double vx0, vy0, vx1, vy1;

    public void setViewport(double x0, double y0, double x1, double y1) {
        vx0 = x0;
        vy0 = y0;
        vx1 = x1;
        vy1 = y1;
    }

    public double zoomValue() {
        return zoom;
    }

    public double centerX() {
        return cx;
    }

    public double centerY() {
        return cy;
    }

    public double left() {
        return vx0;
    }

    public double right() {
        return vx1;
    }

    public double top() {
        return vy0;
    }

    public double bottom() {
        return vy1;
    }

    public double vcx() {
        return (vx0 + vx1) / 2;
    }

    public double vcy() {
        return (vy0 + vy1) / 2;
    }

    public double toScreenX(double tx) {
        return vcx() + (tx - cx) * zoom;
    }

    public double toScreenY(double ty) {
        return vcy() + (ty - cy) * zoom;
    }

    public double toTreeX(double sx) {
        return cx + (sx - vcx()) / zoom;
    }

    public double toTreeY(double sy) {
        return cy + (sy - vcy()) / zoom;
    }

    public boolean inViewport(double sx, double sy) {
        return sx >= vx0 && sx <= vx1 && sy >= vy0 && sy <= vy1;
    }

    public void zoomAt(double sx, double sy, double factor, double min, double max) {
        double tx = toTreeX(sx);
        double ty = toTreeY(sy);
        zoom = Math.max(min, Math.min(max, zoom * factor));
        cx = tx - (sx - vcx()) / zoom;
        cy = ty - (sy - vcy()) / zoom;
    }

    public void pan(double dxScreen, double dyScreen) {
        cx -= dxScreen / zoom;
        cy -= dyScreen / zoom;
    }

    public void centerOn(double tx, double ty) {
        cx = tx;
        cy = ty;
    }

    public void fit(double minX, double minY, double maxX, double maxY, double margin) {
        double w = Math.max(1, maxX - minX + 2 * margin);
        double h = Math.max(1, maxY - minY + 2 * margin);
        zoom = Math.max(FIT_MIN_ZOOM, Math.min((vx1 - vx0) / w, (vy1 - vy0) / h));
        cx = (minX + maxX) / 2;
        cy = (minY + maxY) / 2;
    }
}
