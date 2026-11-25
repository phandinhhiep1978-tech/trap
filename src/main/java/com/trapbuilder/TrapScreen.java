package com.trapbuilder;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.stream.Stream;

public class TrapScreen extends Screen {
	private static final int PER_PAGE = 5;
	private static int page = 0;
	private static String status = "";

	private record Label(int x, int y, String text) {}
	private final List<Label> labels = new ArrayList<>();
	private List<Path> files = new ArrayList<>();

	public TrapScreen() { super(Text.literal("TrapBuilder Assistant")); }

	private static Path dir() {
		Path d = FabricLoader.getInstance().getGameDir().resolve("schematics");
		try { Files.createDirectories(d); } catch (IOException ignored) {}
		return d;
	}

	private void scanFiles() {
		files.clear();
		try (Stream<Path> s = Files.list(dir())) {
			s.filter(p -> {
				String n = p.getFileName().toString().toLowerCase();
				return n.endsWith(".litematic") || n.endsWith(".schem");
			}).sorted().forEach(files::add);
		} catch (IOException ignored) {}
	}

	private void btn(int x, int y, int w, String label, Runnable r) {
		addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> r.run()).dimensions(x, y, w, 18).build());
	}

	private void refresh() { clearAndInit(); }

	@Override
	protected void init() {
		labels.clear();
		scanFiles();
		int top = Math.max(8, height / 2 - 125);
		int lx = width / 2 - 205, rx = width / 2 + 5;

		// ---------------- left column: schematic loader
		labels.add(new Label(lx, top, "Schematics (.minecraft/schematics) - click to place at your feet"));
		int pages = Math.max(1, (files.size() + PER_PAGE - 1) / PER_PAGE);
		page = Math.min(page, pages - 1);
		for (int i = 0; i < PER_PAGE; i++) {
			int idx = page * PER_PAGE + i;
			if (idx >= files.size()) break;
			Path f = files.get(idx);
			btn(lx, top + 12 + i * 20, 200, f.getFileName().toString(), () -> {
				try {
					Schematic s = SchematicLoader.load(f);
					var p = client.player;
					Plan.place(s, p != null ? p.getBlockPos() : BlockPos.ORIGIN);
					status = "Loaded " + s.name + " (" + s.blocks.size() + " blocks)";
				} catch (Exception ex) {
					status = "Load failed: " + ex.getMessage();
				}
				refresh();
			});
		}
		btn(lx, top + 114, 40, "<", () -> { page = Math.max(0, page - 1); refresh(); });
		btn(lx + 160, top + 114, 40, ">", () -> { page = Math.min(pages - 1, page + 1); refresh(); });
		labels.add(new Label(lx + 76, top + 119, "Page " + (page + 1) + "/" + pages));
		btn(lx, top + 134, 98, "Clear", () -> { Plan.clear(); status = "Cleared"; refresh(); });
		btn(lx + 102, top + 134, 98, "Refresh list", this::refresh);

		// color fields (hex RRGGBB)
		labels.add(new Label(lx, top + 160, "Colors (hex RRGGBB)"));
		colorField(lx, top + 182, "Dig", () -> Config.digColor, v -> Config.digColor = v);
		colorField(lx + 66, top + 182, "Drop", () -> Config.dropColor, v -> Config.dropColor = v);
		colorField(lx + 132, top + 182, "Trigger", () -> Config.triggerColor, v -> Config.triggerColor = v);
		colorField(lx, top + 216, "Redstone", () -> Config.redstoneColor, v -> Config.redstoneColor = v);
		colorField(lx + 66, top + 216, "Marker", () -> Config.markerColor, v -> Config.markerColor = v);

		// ---------------- right column: offsets / rotation
		BlockPos a = Plan.anchor;
		offsetRow(rx, top, "X", a.getX(), 1, 0, 0);
		offsetRow(rx, top + 22, "Y", a.getY(), 0, 1, 0);
		offsetRow(rx, top + 44, "Z", a.getZ(), 0, 0, 1);
		btn(rx, top + 66, 200, "Rotate 90\u00B0  (now " + (Plan.rot * 90) + "\u00B0)", () -> {
			Plan.rot = (Plan.rot + 1) % 4; Plan.rebuild(); refresh();
		});

		// substitution toggles
		labels.add(new Label(rx, top + 90, "Flexible block substitution"));
		btn(rx, top + 102, 98, onOff("Stone/Cobble", Config.stone), () -> { Config.stone = !Config.stone; Plan.rebuild(); refresh(); });
		btn(rx + 102, top + 102, 98, onOff("Dirt/Grass", Config.dirt), () -> { Config.dirt = !Config.dirt; Plan.rebuild(); refresh(); });
		btn(rx, top + 122, 98, onOff("Iron/Copper", Config.metal), () -> { Config.metal = !Config.metal; Plan.rebuild(); refresh(); });
		btn(rx + 102, top + 122, 98, onOff("Stairs/Slabs", Config.stairsSlabs), () -> { Config.stairsSlabs = !Config.stairsSlabs; Plan.rebuild(); refresh(); });

		// render options
		labels.add(new Label(rx, top + 148, "Render"));
		btn(rx, top + 160, 98, onOff("Overlay", Config.overlayOn), () -> { Config.overlayOn = !Config.overlayOn; refresh(); });
		btn(rx + 102, top + 160, 98, onOff("Next marker", Config.markerOn), () -> { Config.markerOn = !Config.markerOn; refresh(); });
		btn(rx, top + 182, 200, "Mode: " + switch (Config.mode) {
			case ALL -> "All Layers";
			case SINGLE_LAYER -> "Single Layer (Y)";
			case SMART -> "Smart Auto-Step";
		}, () -> {
			Config.Mode[] v = Config.Mode.values();
			Config.mode = v[(Config.mode.ordinal() + 1) % v.length];
			refresh();
		});
		btn(rx, top + 204, 40, "-", () -> { Config.layerY--; refresh(); });
		btn(rx + 160, top + 204, 40, "+", () -> { Config.layerY++; refresh(); });
		labels.add(new Label(rx + 62, top + 209, "Layer Y = " + Config.layerY));

		btn(width / 2 - 50, top + 244, 100, "Close", this::close);
	}

	private static String onOff(String n, boolean v) { return n + ": " + (v ? "ON" : "OFF"); }

	private void offsetRow(int x, int y, String axis, int value, int dx, int dy, int dz) {
		btn(x, y, 40, "-1", () -> { shift(-dx, -dy, -dz); });
		btn(x + 160, y, 40, "+1", () -> { shift(dx, dy, dz); });
		labels.add(new Label(x + 62, y + 5, axis + " = " + value));
	}

	private void shift(int dx, int dy, int dz) {
		Plan.anchor = Plan.anchor.add(dx, dy, dz);
		Plan.rebuild();
		refresh();
	}

	private void colorField(int x, int y, String label, IntSupplier get, IntConsumer set) {
		TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, 62, 16, Text.literal(label));
		f.setMaxLength(6);
		f.setText(String.format("%06X", get.getAsInt()));
		f.setChangedListener(s -> { if (s.matches("[0-9a-fA-F]{6}")) set.accept(Integer.parseInt(s, 16)); });
		addDrawableChild(f);
		labels.add(new Label(x, y - 10, label));
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 4, 0xFFFFFFFF);
		for (Label l : labels) ctx.drawTextWithShadow(textRenderer, l.text(), l.x(), l.y(), 0xFFB0B0B0);
		if (!status.isEmpty()) ctx.drawCenteredTextWithShadow(textRenderer, status, width / 2, height - 12, 0xFFFFFF55);
	}
}
