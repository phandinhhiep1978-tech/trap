package com.trapbuilder;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * ALL version-sensitive rendering code lives here. If 1.21.11's render API differs slightly,
 * only this file needs adjusting (matrices / consumers / camera accessors, VertexRendering helpers).
 */
public final class Overlay {
	private Overlay() {}

	public static void render(WorldRenderContext ctx) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!Config.overlayOn || Plan.schem == null || mc.player == null) return;

		List<Plan.Entry> visible = select();
		if (visible.isEmpty()) return;

		MatrixStack m = ctx.matrices();
		VertexConsumerProvider vcp = ctx.consumers();
		Vec3d cam = ctx.camera().getPos();
		VertexConsumer fill = vcp.getBuffer(RenderLayer.getDebugFilledBox());
		VertexConsumer lines = vcp.getBuffer(RenderLayer.getLines());

		ItemStack held = mc.player.getMainHandStack();
		Block heldBlock = held.getItem() instanceof BlockItem bi ? bi.getBlock() : null;

		m.push();
		m.translate(-cam.x, -cam.y, -cam.z);

		int budget = 4000; // hard cap on boxes per frame
		for (Plan.Entry e : visible) {
			if (budget-- <= 0) break;
			BlockPos p = e.pos();
			int rgb;
			float fa;
			double inset = 0.0;
			switch (e.kind()) {
				case DIG -> { rgb = Config.digColor; fa = 0.35f; }
				case DROP -> { rgb = Config.dropColor; fa = 0.25f; }
				case TRIGGER -> { rgb = tint(heldBlock, e, Config.triggerColor); fa = 0.4f; inset = 0.05; }
				case REDSTONE -> { rgb = tint(heldBlock, e, Config.redstoneColor); fa = 0.4f; inset = 0.05; }
				default -> { rgb = tint(heldBlock, e, 0xDDDDDD); fa = 0.4f; inset = 0.08; } // ghost block
			}
			float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
			double x0 = p.getX() + inset, y0 = p.getY() + inset, z0 = p.getZ() + inset;
			double x1 = p.getX() + 1 - inset, y1 = p.getY() + 1 - inset, z1 = p.getZ() + 1 - inset;
			VertexRendering.drawFilledBox(m, fill, x0, y0, z0, x1, y1, z1, r, g, b, fa);
			VertexRendering.drawBox(m, lines, x0, y0, z0, x1, y1, z1, r, g, b, 0.9f);
		}

		if (Config.markerOn) {
			Plan.Entry t = target(visible, mc.player.getPos());
			if (t != null) {
				int c = Config.markerColor;
				float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
				double pulse = 0.04 + 0.03 * Math.sin(System.currentTimeMillis() / 180.0);
				BlockPos p = t.pos();
				VertexRendering.drawBox(m, lines, p.getX() - pulse, p.getY() - pulse, p.getZ() - pulse,
					p.getX() + 1 + pulse, p.getY() + 1 + pulse, p.getZ() + 1 + pulse, r, g, b, 1f);
			}
		}
		m.pop();
	}

	/** Cyan if the held block is a valid substitute, red if it is wrong, category color if hand is empty. */
	private static int tint(Block held, Plan.Entry e, int base) {
		if (held == null) return base;
		return Classifier.heldOk(held, e.want(), e.kind()) ? 0x66FFFF : 0xFF4444;
	}

	private static List<Plan.Entry> select() {
		List<Plan.Entry> out = new ArrayList<>();
		switch (Config.mode) {
			case ALL -> { out.addAll(Plan.dig); out.addAll(Plan.drop); out.addAll(Plan.mech); out.addAll(Plan.struct); }
			case SINGLE_LAYER -> {
				for (List<Plan.Entry> l : List.of(Plan.dig, Plan.drop, Plan.mech, Plan.struct))
					for (Plan.Entry e : l) if (e.pos().getY() == Config.layerY) out.add(e);
			}
			case SMART -> {
				if (!Plan.dig.isEmpty()) { out.addAll(Plan.dig); out.addAll(Plan.drop); }   // Phase 1: excavate
				else if (!Plan.mech.isEmpty()) out.addAll(Plan.mech);                       // Phase 2: redstone + triggers
				else out.addAll(Plan.struct);                                               // Phase 3: camouflage
			}
		}
		return out;
	}

	/** Digging goes top-down; building goes bottom-up; ties broken by distance. */
	private static Plan.Entry target(List<Plan.Entry> list, Vec3d player) {
		return list.stream()
			.filter(e -> e.kind() != Classifier.Kind.DROP)
			.min(Comparator
				.comparingInt((Plan.Entry e) -> e.kind() == Classifier.Kind.DIG ? 0 : 1)
				.thenComparingInt(e -> e.kind() == Classifier.Kind.DIG ? -e.pos().getY() : e.pos().getY())
				.thenComparingDouble(e -> e.pos().getSquaredDistance(player)))
			.orElse(null);
	}
}
