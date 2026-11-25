package com.trapbuilder;

public final class Config {
	public enum Mode { ALL, SINGLE_LAYER, SMART }

	// RGB colors (0xRRGGBB)
	public static int digColor = 0x00FF55;
	public static int dropColor = 0xFF3333;
	public static int triggerColor = 0xFFD700;
	public static int redstoneColor = 0x8A5CFF;
	public static int markerColor = 0x00FFFF;

	// Flexible substitution groups
	public static boolean stone = true, dirt = true, metal = true, stairsSlabs = true;

	public static Mode mode = Mode.SMART;
	public static int layerY = 0;
	public static boolean overlayOn = true, markerOn = true;

	private Config() {}
}
