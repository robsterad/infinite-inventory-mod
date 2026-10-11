package com.robsterad.infiniteinv.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.shedaniel.clothconfig2.api.Modifier;
import me.shedaniel.clothconfig2.api.ModifierKeyCode;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Global (per-installation, not per-world) client settings: the mouse/key bindings
 * used to interact with the infinite inventory panel. Configurable via the
 * Mod Menu screen provided by {@link InfiniteInvModMenuIntegration}.
 */
public class InfiniteInvConfig {

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("infinite-inventory.json");

    public static final ModifierKeyCode DEFAULT_TAKE_ONE = ModifierKeyCode.of(
            InputUtil.Type.MOUSE.createFromCode(GLFW.GLFW_MOUSE_BUTTON_LEFT), Modifier.none());
    public static final ModifierKeyCode DEFAULT_TAKE_STACK = ModifierKeyCode.of(
            InputUtil.Type.MOUSE.createFromCode(GLFW.GLFW_MOUSE_BUTTON_LEFT), Modifier.of(false, false, true));
    public static final ModifierKeyCode DEFAULT_SEND_ONE = ModifierKeyCode.unknown();
    public static final ModifierKeyCode DEFAULT_SEND_STACK = ModifierKeyCode.of(
            InputUtil.Type.MOUSE.createFromCode(GLFW.GLFW_MOUSE_BUTTON_LEFT), Modifier.of(false, false, true));

    // Must come after the DEFAULT_* fields above: the constructor's instance-field
    // initializers (below) read them, and static fields init in declaration order.
    public static final InfiniteInvConfig INSTANCE = new InfiniteInvConfig();

    public ModifierKeyCode takeOne = ModifierKeyCode.copyOf(DEFAULT_TAKE_ONE);
    public ModifierKeyCode takeStack = ModifierKeyCode.copyOf(DEFAULT_TAKE_STACK);
    public ModifierKeyCode sendOne = ModifierKeyCode.copyOf(DEFAULT_SEND_ONE);
    public ModifierKeyCode sendStack = ModifierKeyCode.copyOf(DEFAULT_SEND_STACK);

    public static void load() {
        if (!Files.exists(FILE)) return;
        try {
            JsonObject root = new JsonParser().parse(new String(Files.readAllBytes(FILE), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            INSTANCE.takeOne = readBind(root, "takeOne", DEFAULT_TAKE_ONE);
            INSTANCE.takeStack = readBind(root, "takeStack", DEFAULT_TAKE_STACK);
            INSTANCE.sendOne = readBind(root, "sendOne", DEFAULT_SEND_ONE);
            INSTANCE.sendStack = readBind(root, "sendStack", DEFAULT_SEND_STACK);
        } catch (Exception e) {
            System.err.println("Failed to load infinite-inventory.json, using defaults: " + e.getMessage());
        }
    }

    public static void save() {
        try {
            JsonObject root = new JsonObject();
            root.add("takeOne", writeBind(INSTANCE.takeOne));
            root.add("takeStack", writeBind(INSTANCE.takeStack));
            root.add("sendOne", writeBind(INSTANCE.sendOne));
            root.add("sendStack", writeBind(INSTANCE.sendStack));
            Files.createDirectories(FILE.getParent());
            Files.write(FILE, root.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("Failed to save infinite-inventory.json: " + e.getMessage());
        }
    }

    private static JsonObject writeBind(ModifierKeyCode code) {
        JsonObject obj = new JsonObject();
        obj.addProperty("key", code.getKeyCode().getTranslationKey());
        Modifier mod = code.getModifier();
        obj.addProperty("alt", mod.hasAlt());
        obj.addProperty("control", mod.hasControl());
        obj.addProperty("shift", mod.hasShift());
        return obj;
    }

    private static ModifierKeyCode readBind(JsonObject root, String field, ModifierKeyCode fallback) {
        if (!root.has(field)) return ModifierKeyCode.copyOf(fallback);
        JsonObject obj = root.getAsJsonObject(field);
        InputUtil.Key key = InputUtil.fromTranslationKey(obj.get("key").getAsString());
        Modifier modifier = Modifier.of(
                obj.get("alt").getAsBoolean(),
                obj.get("control").getAsBoolean(),
                obj.get("shift").getAsBoolean());
        return ModifierKeyCode.of(key, modifier);
    }
}
