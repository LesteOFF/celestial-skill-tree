package dev.lesteoff.celestialskilltree.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class TreeKeys {
    private static final String CATEGORY = "key.categories.celestialskilltree";

    public static final KeyMapping UNDO = inTree("undo", KeyModifier.CONTROL, GLFW.GLFW_KEY_Z);
    public static final KeyMapping REDO = inTree("redo", KeyModifier.CONTROL, GLFW.GLFW_KEY_Y);
    public static final KeyMapping SEARCH = inTree("search", KeyModifier.CONTROL, GLFW.GLFW_KEY_F);
    public static final KeyMapping CONFIRM = inTree("confirm", KeyModifier.NONE, GLFW.GLFW_KEY_ENTER);
    public static final KeyMapping MARK = inTree("mark", KeyModifier.NONE, GLFW.GLFW_KEY_SPACE);
    public static final KeyMapping TOGGLE = inTree("toggle", KeyModifier.NONE, GLFW.GLFW_KEY_T);
    public static final KeyMapping HOME = inTree("home", KeyModifier.NONE, GLFW.GLFW_KEY_HOME);
    public static final KeyMapping FIT = inTree("fit", KeyModifier.NONE, GLFW.GLFW_KEY_F);
    public static final KeyMapping NEXT_HIT = inTree("next_hit", KeyModifier.NONE, GLFW.GLFW_KEY_TAB);
    public static final KeyMapping HELP = inTree("help", KeyModifier.NONE, GLFW.GLFW_KEY_H);
    public static final KeyMapping ZOOM_IN = inTree("zoom_in", KeyModifier.NONE, GLFW.GLFW_KEY_EQUAL);
    public static final KeyMapping ZOOM_OUT = inTree("zoom_out", KeyModifier.NONE, GLFW.GLFW_KEY_MINUS);
    public static final KeyMapping PAN_UP = inTree("pan_up", KeyModifier.NONE, GLFW.GLFW_KEY_W);
    public static final KeyMapping PAN_LEFT = inTree("pan_left", KeyModifier.NONE, GLFW.GLFW_KEY_A);
    public static final KeyMapping PAN_DOWN = inTree("pan_down", KeyModifier.NONE, GLFW.GLFW_KEY_S);
    public static final KeyMapping PAN_RIGHT = inTree("pan_right", KeyModifier.NONE, GLFW.GLFW_KEY_D);
    public static final KeyMapping FOCUS_UP = inTree("focus_up", KeyModifier.NONE, GLFW.GLFW_KEY_UP);
    public static final KeyMapping FOCUS_LEFT = inTree("focus_left", KeyModifier.NONE, GLFW.GLFW_KEY_LEFT);
    public static final KeyMapping FOCUS_DOWN = inTree("focus_down", KeyModifier.NONE, GLFW.GLFW_KEY_DOWN);
    public static final KeyMapping FOCUS_RIGHT = inTree("focus_right", KeyModifier.NONE, GLFW.GLFW_KEY_RIGHT);

    public static final List<KeyMapping> ALL = List.of(UNDO, REDO, SEARCH, CONFIRM, MARK, TOGGLE, HOME, FIT, NEXT_HIT, HELP,
            ZOOM_IN, ZOOM_OUT, PAN_UP, PAN_LEFT, PAN_DOWN, PAN_RIGHT, FOCUS_UP, FOCUS_LEFT, FOCUS_DOWN, FOCUS_RIGHT);

    private TreeKeys() {
    }

    private static KeyMapping inTree(String name, KeyModifier modifier, int key) {
        return new KeyMapping("key.celestialskilltree." + name, KeyConflictContext.GUI, modifier, InputConstants.Type.KEYSYM, key, CATEGORY);
    }

    public static boolean matches(KeyMapping mapping, int key, int scanCode) {
        boolean modifierHeld = mapping.getKeyModifier() == KeyModifier.NONE || mapping.getKeyModifier().isActive(null);
        return mapping.getKey().equals(InputConstants.getKey(key, scanCode)) && modifierHeld;
    }

    public static boolean held(KeyMapping mapping, long window) {
        InputConstants.Key key = mapping.getKey();
        return key.getType() == InputConstants.Type.KEYSYM && key.getValue() != InputConstants.UNKNOWN.getValue()
                && InputConstants.isKeyDown(window, key.getValue());
    }
}
