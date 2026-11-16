package dev.onyx.visual.gui;

import java.util.ArrayList;
import java.util.List;

/** ClickGUI preferences, stored inside config/onyx-visual.json under "gui". */
public final class GuiPrefs {
	public static final int[] ACCENTS = {0xFFFF8FB9, 0xFFB79CFF, 0xFF6FE3D0, 0xFFFFC861, 0xFFFF6B6B, 0xFFF7F4EC};

	public int accent = 0xFFFF8FB9;
	public boolean waves = true;
	public boolean title = true;
	public boolean dim = true;
	/** Cheap drawing: flat backdrop, no title, fewer shapes. */
	public boolean lite;
	/** Menu colour theme, see {@link Theme}. */
	public int theme;
	/** Name of the profile that was loaded or saved last. */
	public String profile = "";
	public float scale = 100;
	public boolean compact;
	public boolean activeFirst;
	public int category;
	public List<String> favorites = new ArrayList<>();

	public void sanitize() {
		if (favorites == null) favorites = new ArrayList<>();
		if (profile == null) profile = "";
		if (theme < 0 || theme >= Theme.COUNT) theme = 0;
		if (scale < 70 || scale > 130) scale = 100;
		if ((accent >>> 24) == 0) accent |= 0xFF000000;
	}
}
