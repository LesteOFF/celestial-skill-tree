package dev.nostalgiacraft.skills.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptType;

public final class NostalgiaSkillsPlugin implements KubeJSPlugin {
    private static final String BINDING_NAME = "NostalgiaSkills";

    @Override
    public void init() {
        KubeJSGuard.run("event forwarding", SkillKubeEvents::forwardAll);
    }

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        KubeJSGuard.run("event registration", () -> registry.register(SkillKubeEvents.GROUP));
    }

    @Override
    public void registerBindings(BindingRegistry bindings) {
        KubeJSGuard.run("binding registration", () -> {
            if (bindings.type() == ScriptType.SERVER) {
                bindings.add(BINDING_NAME, SkillsBinding.class);
            }
        });
    }
}
