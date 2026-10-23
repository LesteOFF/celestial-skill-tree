package dev.nostalgiacraft.skills.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.nostalgiacraft.skills.compat.OptionalEvents;

public final class NostalgiaSkillsPlugin implements KubeJSPlugin {
    public static final String MOD_ID = "kubejs";
    private static final String BINDING_NAME = "NostalgiaSkills";

    @Override
    public void init() {
        OptionalEvents.guard(MOD_ID, "event forwarding", SkillKubeEvents::forwardAll);
    }

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        OptionalEvents.guard(MOD_ID, "event registration", () -> registry.register(SkillKubeEvents.GROUP));
    }

    @Override
    public void registerBindings(BindingRegistry bindings) {
        OptionalEvents.guard(MOD_ID, "binding registration", () -> {
            if (bindings.type() == ScriptType.SERVER) {
                bindings.add(BINDING_NAME, SkillsBinding.class);
            }
        });
    }
}
