package com.trapbuilder;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Overlay {
	private Overlay() {}

	public static void render(WorldRenderContext ctx) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (!Config.overlayOn || Plan.schem == null || mc.player == null) return;

		List<Plan.Entry> visible = select();
		if (visible.isEmpty()) return;

		MatrixStack m = ctx.matrices();
		VertexConsumerProvider vcp = ctx.consumers();
		if (m == null || vcp == null) return;
		Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
		MatrixStack.Entry e = m.peek();
		VertexConsumer fill = vcp.getBuffer(RenderLayers.debugQuads());
		VertexConsumer lines = vcp.getBuffer(RenderLayers.lines());

		ItemStack held = mc.player.getMainHandStack();
		Block heldBlock = held.getItem() instanceof BlockItem bi ? bi.getBlock() : null;

		int budget = 2500;
		for (Plan.Entry en : visible) {
			if (budget-- <= 0) break;
			BlockPos p = en.pos();
			int rgb;
			float fa;
			double inset = 0.0;
			switch (en.kind()) {
				case DIG -> { rgb = Config.digColor; fa = 0.35f; }
				case DROP -> { rgb = Config.dropColor; fa = 0.25f; }
				case TRIGGER -> { rgb = tint(heldBlock, en, Config.triggerColor); fa = 0.4f; inset = 0.05; }
				case REDSTONE -> { rgb = tint(heldBlock, en, Config.redstoneColor); fa = 0.4f; inset = 0.05; }
				default -> { rgb = tint(heldBlock, en, 0xDDDDDD); fa = 0.4f; inset = 0.08; }
			}
			float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
			box(e, fill, lines,
				(float) (p.getX() + inset - cam.x), (float) (p.getY() + inset - cam.y), (float) (p.getZ() + inset - cam.z),
				(float) (p.getX() + 1 - inset - cam.x), (float) (p.getY() + 1 - inset - cam.y), (float) (p.getZ() + 1 - inset - cam.z),
				r, g, b, fa, 0.9f);
		}

		if (Config.markerOn) {
			Plan.Entry t = target(visible, mc.player.getEntityPos());
			if (t != null) {
				int c = Config.markerColor;
				float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
				double pu = 0.04 + 0.03 * Math.sin(System.currentTimeMillis() / 180.0);
				BlockPos p = t.pos();
				box(e, null, lines,
					(float) (p.getX() - pu - cam.x), (float) (p.getY() - pu - cam.y), (float) (p.getZ() - pu - cam.z),
					(float) (p.getX() + 1 + pu - cam.x), (float) (p.getY() + 1 + pu - cam.y), (float) (p.getZ() + 1 + pu - cam.z),
					r, g, b, 0f, 1f);
			}
		}
	}

	// ---------------------------------------------------------------- drawing helpers
	private static void box(MatrixStack.Entry e, VertexConsumer fill, VertexConsumer lines,
							float x0, float y0, float z0, float x1, float y1, float z1,
							float r, float g, float b, float fa, float la) {
		if (fill != null && fa > 0) {
			quad(e, fill, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, fa); // down
			quad(e, fill, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, b, fa); // up
			quad(e, fill, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, fa); // north
			quad(e, fill, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, fa); // south
			quad(e, fill, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, fa); // west
			quad(e, fill, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, fa); // east
		}
		// 12 edges
		line(e, lines, x0, y0, z0, x1, y0, z0, r, g, b, la);
		line(e, lines, x0, y1, z0, x1, y1, z0, r, g, b, la);
		line(e, lines, x0, y0, z1, x1, y0, z1, r, g, b, la);
		line(e, lines, x0, y1, z1, x1, y1, z1, r, g, b, la);
		line(e, lines, x0, y0, z0, x0, y1, z0, r, g, b, la);
		line(e, lines, x1, y0, z0, x1, y1, z0, r, g, b, la);
		line(e, lines, x0, y0, z1, x0, y1, z1, r, g, b, la);
		line(e, lines, x1, y0, z1, x1, y1, z1, r, g, b, la);
		line(e, lines, x0, y0, z0, x0, y0, z1, r, g, b, la);
		line(e, lines, x1, y0, z0, x1, y0, z1, r, g, b, la);
		line(e, lines, x0, y1, z0, x0, y1, z1, r, g, b, la);
		line(e, lines, x1, y1, z0, x1, y1, z1, r, g, b, la);
	}

	/** Quad drawn twice (both windings) so it is visible from any side regardless of culling. */
	private static void quad(MatrixStack.Entry e, VertexConsumer vc,
							 float ax, float ay, float az, float bx, float by, float bz,
							 float cx, float cy, float cz, float dx, float dy, float dz,
							 float r, float g, float b, float a) {
		v(e, vc, ax, ay, az, r, g, b, a); v(e, vc, bx, by, bz, r, g, b, a);
		v(e, vc, cx, cy, cz, r, g, b, a); v(e, vc, dx, dy, dz, r, g, b, a);
		v(e, vc, dx, dy, dz, r, g, b, a); v(e, vc, cx, cy, cz, r, g, b, a);
		v(e, vc, bx, by, bz, r, g, b, a); v(e, vc, ax, ay, az, r, g, b, a);
	}

	private static void v(MatrixStack.Entry e, VertexConsumer vc, float x, float y, float z,
						  float r, float g, float b, float a) {
		vc.vertex(e, x, y, z).color(r, g, b, a);
	}

	private static void line(MatrixStack.Entry e, VertexConsumer vc,
							 float ax, float ay, float az, float bx, float by, float bz,
							 float r, float g, float b, float a) {
		float nx = bx - ax, ny = by - ay, nz = bz - az;
		float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (len == 0) return;
		nx /= len; ny /= len; nz /= len;
		vc.vertex(e, ax, ay, az).color(r, g, b, a).normal(e, nx, ny, nz).lineWidth(2f);
		vc.vertex(e, bx, by, bz).color(r, g, b, a).normal(e, nx, ny, nz).lineWidth(2f);
	}

	// ---------------------------------------------------------------- selection logic
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
				if (!Plan.dig.isEmpty()) { out.addAll(Plan.dig); out.addAll(Plan.drop); }
				else if (!Plan.mech.isEmpty()) out.addAll(Plan.mech);
				else out.addAll(Plan.struct);
			}
		}
		return out;
	}

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
