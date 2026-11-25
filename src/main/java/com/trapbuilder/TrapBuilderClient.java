package com.trapbuilder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class TrapBuilderClient implements ClientModInitializer {
	public static KeyBinding openKey;

	@Override
	public void onInitializeClient() {
		openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.trapbuilder.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R,
			KeyBinding.Category.create(Identifier.of("trapbuilder", "main"))));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (openKey.wasPressed()) {
				if (mc.currentScreen == null) mc.setScreen(new TrapScreen());
			}
			Plan.tick(mc);
		});

		WorldRenderEvents.AFTER_TRANSLUCENT.register(Overlay::render);
	}
}
