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

	private record Style(float r, float g, float b, float fa, double inset) {}

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

		ItemStack held = mc.player.getMainHandStack();
		Block heldBlock = held.getItem() instanceof BlockItem bi ? bi.getBlock() : null;

		List<Plan.Entry> draw = visible.size() > 1500 ? visible.subList(0, 1500) : visible;

		// ---- PASS 1: all filled quads (one buffer only)
		VertexConsumer fill = vcp.getBuffer(RenderLayers.debugQuads());
		for (Plan.Entry en : draw) {
			Style s = style(en, heldBlock);
			float[] c = bounds(en.pos(), s.inset(), cam);
			fillBox(e, fill, c[0], c[1], c[2], c[3], c[4], c[5], s.r(), s.g(), s.b(), s.fa());
		}

		// ---- PASS 2: all lines (second buffer, never touch fill again)
		VertexConsumer lines = vcp.getBuffer(RenderLayers.lines());
		for (Plan.Entry en : draw) {
			Style s = style(en, heldBlock);
			float[] c = bounds(en.pos(), s.inset(), cam);
			lineBox(e, lines, c[0], c[1], c[2], c[3], c[4], c[5], s.r(), s.g(), s.b(), 0.9f);
		}

		if (Config.markerOn) {
			Plan.Entry t = target(visible, mc.player.getEntityPos());
			if (t != null) {
				int col = Config.markerColor;
				float r = ((col >> 16) & 255) / 255f, g = ((col >> 8) & 255) / 255f, b = (col & 255) / 255f;
				double pu = 0.04 + 0.03 * Math.sin(System.currentTimeMillis() / 180.0);
				float[] c = bounds(t.pos(), -pu, cam);
				lineBox(e, lines, c[0], c[1], c[2], c[3], c[4], c[5], r, g, b, 1f);
			}
		}
	}

	private static Style style(Plan.Entry en, Block heldBlock) {
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
		return new Style(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, fa, inset);
	}

	private static float[] bounds(BlockPos p, double inset, Vec3d cam) {
		return new float[] {
			(float) (p.getX() + inset - cam.x), (float) (p.getY() + inset - cam.y), (float) (p.getZ() + inset - cam.z),
			(float) (p.getX() + 1 - inset - cam.x), (float) (p.getY() + 1 - inset - cam.y), (float) (p.getZ() + 1 - inset - cam.z)
		};
	}

	// ---------------------------------------------------------------- drawing helpers
	private static void fillBox(MatrixStack.Entry e, VertexConsumer fill,
								float x0, float y0, float z0, float x1, float y1, float z1,
								float r, float g, float b, float a) {
		quad(e, fill, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, a);
		quad(e, fill, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, b, a);
		quad(e, fill, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, a);
		quad(e, fill, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, a);
		quad(e, fill, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, a);
		quad(e, fill, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, a);
	}

	private static void lineBox(MatrixStack.Entry e, VertexConsumer lines,
								float x0, float y0, float z0, float x1, float y1, float z1,
								float r, float g, float b, float a) {
		line(e, lines, x0, y0, z0, x1, y0, z0, r, g, b, a);
		line(e, lines, x0, y1, z0, x1, y1, z0, r, g, b, a);
		line(e, lines, x0, y0, z1, x1, y0, z1, r, g, b, a);
		line(e, lines, x0, y1, z1, x1, y1, z1, r, g, b, a);
		line(e, lines, x0, y0, z0, x0, y1, z0, r, g, b, a);
		line(e, lines, x1, y0, z0, x1, y1, z0, r, g, b, a);
		line(e, lines, x0, y0, z1, x0, y1, z1, r, g, b, a);
		line(e, lines, x1, y0, z1, x1, y1, z1, r, g, b, a);
		line(e, lines, x0, y0, z0, x0, y0, z1, r, g, b, a);
		line(e, lines, x1, y0, z0, x1, y0, z1, r, g, b, a);
		line(e, lines, x0, y1, z0, x0, y1, z1, r, g, b, a);
		line(e, lines, x1, y1, z0, x1, y1, z1, r, g, b, a);
	}

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
