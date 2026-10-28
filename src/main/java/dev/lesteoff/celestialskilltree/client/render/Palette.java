package dev.lesteoff.celestialskilltree.client.render;

import dev.lesteoff.celestialskilltree.config.ClientConfig;

public final class Palette {
    public static final int DEEP_SPACE = 0xFF06060A;
    public static final int HIDDEN_VEIL = 0x160828;
    public static final int EDGE_INACTIVE = 0xFF35291D;
    public static final int EDGE_INACTIVE_HC = 0xFF6A5338;
    public static final int BRONZE = 0xFF88642C;
    public static final int ALLOCATED = 0xFFD28C2E;
    public static final int GOLD = 0xFFF3D15C;
    public static final int TEXT = 0xFFE9E3D8;
    public static final int TEXT_DIM = 0xFF9A9288;
    public static final int TEXT_DIM_HC = 0xFFCFC7BB;
    public static final int PENDING = 0xFF7FD6E8;

    private Palette() {
    }

    public static int textDim() {
        return ClientConfig.HIGH_CONTRAST.get() ? TEXT_DIM_HC : TEXT_DIM;
    }
}
