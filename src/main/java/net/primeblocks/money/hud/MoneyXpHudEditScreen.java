package net.primeblocks.money.hud;

import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import net.primeblocks.money.PrimeMoneyConfig;
import net.primeblocks.money.TrackerControls;

/**
 * Drag-to-position editor for the overlay.
 *
 * <p>Grab the box with the mouse and drop it wherever. Dropping it near the horizontal center
 * snaps it back to centered mode, so the overlay keeps re-centering itself as the window/GUI
 * scale changes instead of drifting off; dropping it left or right of center anchors it to that
 * edge instead, same idea.
 */
public final class MoneyXpHudEditScreen extends Screen {
	private static final int DIM = 0xC0101010;
	private static final int OUTLINE = 0xFFFFD966;
	private static final int OUTLINE_DRAGGING = 0xFF7ED37E;
	private static final int HINT = 0xFFBFBFBF;
	private static final int CENTER_SNAP_PIXELS = 24;

	private static final int DEFAULT_Y = 20;

	private static final int[] OPACITY_STEPS = {0, 64, 144, 216};
	private static final String[] OPACITY_LABELS = {"aus", "leicht", "normal", "kräftig"};

	private static final int BUTTON_WIDTH = 108;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_GAP = 4;

	private final MoneyXpOverlayRenderer renderer;
	private final PrimeMoneyConfig config;
	private final TrackerControls controls;

	private int boxX;
	private int boxY;
	private int boxWidth;
	private int boxHeight;

	private boolean dragging;
	private int grabOffsetX;
	private int grabOffsetY;
	private Button scaleButton;

	public MoneyXpHudEditScreen(MoneyXpOverlayRenderer renderer, PrimeMoneyConfig config,
			TrackerControls controls) {
		super(Component.literal("PrimeMoney — HUD einstellen"));
		this.renderer = renderer;
		this.config = config;
		this.controls = controls;
	}

	@Override
	protected void init() {
		measure();
		clampIntoView();

		int columns = 4;
		int rowWidth = columns * BUTTON_WIDTH + (columns - 1) * BUTTON_GAP;
		int startX = (width - rowWidth) / 2;
		int step = BUTTON_WIDTH + BUTTON_GAP;
		int rowStep = BUTTON_HEIGHT + BUTTON_GAP;
		int row3 = height - rowStep - 46;
		int row2 = row3 - rowStep;
		int row1 = row2 - rowStep;

		addToggle(startX, row1, "Hintergrund", this::opacityLabel, this::cycleOpacity);
		scaleButton = addToggle(startX + step, row1, "Größe",
				this::scaleLabel, () -> changeScale(PrimeMoneyConfig.SCALE_STEP));
		addToggle(startX + 2 * step, row1, "Layout", () -> "Standard", this::resetAll);
		addAction(startX + 3 * step, row1, "Jobs wählen",
				() -> minecraft.gui.setScreen(new JobFilterScreen(this, config)));

		addToggle(startX, row2, "Gesamt", () -> onOff(config.showCombined),
				() -> config.showCombined = !config.showCombined);
		addToggle(startX + step, row2, "Pro Job", () -> onOff(config.showPerJob),
				() -> config.showPerJob = !config.showPerJob);
		addToggle(startX + 2 * step, row2, "Level-Zeit", () -> onOff(config.showLevelEta),
				() -> config.showLevelEta = !config.showLevelEta);
		addToggle(startX + 3 * step, row2, "Schnitt", () -> onOff(config.showRateReference),
				() -> config.showRateReference = !config.showRateReference);

		addAction(startX, row3, "Reset Sitzung", controls::resetSession);
		addAction(startX + step, row3, "Reset 60 Min", controls::resetHourWindow);
		addAction(startX + 2 * step, row3, "Reset 24 Std", controls::resetDayWindow);
	}

	/** A plain do-something button, as opposed to one whose label reflects a setting. */
	private void addAction(int x, int y, String name, Runnable action) {
		addRenderableWidget(Button.builder(Component.literal(name), pressed -> {
			action.run();
			renderer.invalidate();
			measure();
			clampIntoView();
		}).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
	}

	private Button addToggle(int x, int y, String name, LabelSupplier label, Runnable action) {
		Button button = Button.builder(Component.literal(name + ": " + label.get()), pressed -> {
			action.run();
			renderer.invalidate();
			pressed.setMessage(Component.literal(name + ": " + label.get()));
			measure();
			clampIntoView();
		}).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build();

		return addRenderableWidget(button);
	}

	private String onOff(boolean value) {
		return value ? "an" : "aus";
	}

	private void refreshScaleLabel() {
		if (scaleButton != null) {
			scaleButton.setMessage(Component.literal("Größe: " + scaleLabel()));
		}
	}

	private String opacityLabel() {
		int current = Math.clamp(config.backgroundOpacity, 0, 255);
		int best = 0;

		for (int i = 1; i < OPACITY_STEPS.length; i++) {
			if (Math.abs(OPACITY_STEPS[i] - current) < Math.abs(OPACITY_STEPS[best] - current)) {
				best = i;
			}
		}

		return OPACITY_LABELS[best];
	}

	private void cycleOpacity() {
		int current = Math.clamp(config.backgroundOpacity, 0, 255);
		int index = 0;

		for (int i = 1; i < OPACITY_STEPS.length; i++) {
			if (Math.abs(OPACITY_STEPS[i] - current) < Math.abs(OPACITY_STEPS[index] - current)) {
				index = i;
			}
		}

		config.backgroundOpacity = OPACITY_STEPS[(index + 1) % OPACITY_STEPS.length];
	}

	private String scaleLabel() {
		return String.format(Locale.ROOT, "%.0f%%", config.scale() * 100);
	}

	private void changeScale(double delta) {
		double next = Math.round((config.scale() + delta) / PrimeMoneyConfig.SCALE_STEP)
				* PrimeMoneyConfig.SCALE_STEP;
		config.hudScale = Math.clamp(next, PrimeMoneyConfig.MIN_SCALE, PrimeMoneyConfig.MAX_SCALE);
		refreshScaleLabel();
	}

	private void resetAll() {
		config.hudScale = 1.0;
		refreshScaleLabel();
		config.backgroundOpacity = 144;
		config.hudX = PrimeMoneyConfig.CENTERED;
		config.hudY = DEFAULT_Y;
		measure();
	}

	/**
	 * Recomputes the box's size from the current content, then re-derives its position from the
	 * saved anchor and that size - unless the box is being dragged, where the mouse is in charge.
	 *
	 * <p>This is what makes the anchor actually stick. Resetting a counter or toggling a line
	 * changes the content, hence the box's width or height; leaving the old {@code boxX}/{@code
	 * boxY} in place after that would let the anchored edge silently drift, and closing the screen
	 * would then bake that drift into the config as a new, wrong anchor. Recomputing from
	 * {@link MoneyXpOverlayRenderer#resolveX} every time keeps whichever edge is actually anchored
	 * pinned, exactly as the live HUD renders it - only the un-anchored edge moves with the content.
	 */
	private void measure() {
		List<HudLine> lines = renderer.buildLines();
		float scale = config.scale();
		boxWidth = MoneyXpOverlayRenderer.scaledWidth(font, lines, scale);
		boxHeight = MoneyXpOverlayRenderer.scaledHeight(font, lines, scale);

		if (!dragging) {
			boxX = MoneyXpOverlayRenderer.resolveX(config, width, boxWidth);
			boxY = MoneyXpOverlayRenderer.resolveY(config, height, boxHeight);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, width, height, DIM);

		List<HudLine> lines = renderer.buildLines();
		measure();
		clampIntoView();

		renderer.draw(context, font, lines, boxX, boxY, config.scale(), config.backgroundColour());
		drawOutline(context, dragging ? OUTLINE_DRAGGING : OUTLINE);

		super.extractRenderState(context, mouseX, mouseY, delta);

		context.centeredText(font, Component.literal("Ziehen zum Verschieben"),
				width / 2, height - 30, MoneyXpOverlayRenderer.COLOUR_TITLE);
		context.centeredText(font, Component.literal("Esc speichern  ·  " + describeAnchor()),
				width / 2, height - 16, HINT);
	}

	private void drawOutline(GuiGraphicsExtractor context, int colour) {
		int right = boxX + boxWidth;
		int bottom = boxY + boxHeight;

		context.fill(boxX - 1, boxY - 1, right + 1, boxY, colour);
		context.fill(boxX - 1, bottom, right + 1, bottom + 1, colour);
		context.fill(boxX - 1, boxY, boxX, bottom, colour);
		context.fill(right, boxY, right + 1, bottom, colour);
	}

	private String describeAnchor() {
		String horizontal = isNearCenter() ? "mittig" : (anchorsRight() ? "rechts" : "links");
		return horizontal + "  ·  Größe " + scaleLabel();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && isOverScaleButton(event.x(), event.y())) {
			changeScale(-PrimeMoneyConfig.SCALE_STEP);
			measure();
			clampIntoView();
			return true;
		}

		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}

		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && isInsideBox(event.x(), event.y())) {
			dragging = true;
			grabOffsetX = (int) event.x() - boxX;
			grabOffsetY = (int) event.y() - boxY;
			return true;
		}

		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (!dragging) {
			return super.mouseDragged(event, dragX, dragY);
		}

		boxX = (int) event.x() - grabOffsetX;
		boxY = (int) event.y() - grabOffsetY;
		clampIntoView();
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			dragging = false;
			return true;
		}

		return super.mouseReleased(event);
	}

	private boolean isOverScaleButton(double x, double y) {
		return scaleButton != null
				&& x >= scaleButton.getX() && x < scaleButton.getX() + scaleButton.getWidth()
				&& y >= scaleButton.getY() && y < scaleButton.getY() + scaleButton.getHeight();
	}

	private boolean isInsideBox(double x, double y) {
		return x >= boxX && x < boxX + boxWidth && y >= boxY && y < boxY + boxHeight;
	}

	private void clampIntoView() {
		boxX = Math.max(0, Math.min(boxX, Math.max(0, width - boxWidth)));
		boxY = Math.max(0, Math.min(boxY, Math.max(0, height - boxHeight)));
	}

	private boolean isNearCenter() {
		return Math.abs((boxX + boxWidth / 2) - width / 2) <= CENTER_SNAP_PIXELS;
	}

	private boolean anchorsRight() {
		return boxX + boxWidth / 2 > width / 2;
	}

	/** {@link PrimeMoneyConfig#CENTERED} when dropped near the middle, else edge-anchored like Slots' HUD. */
	private int storedX() {
		if (isNearCenter()) {
			return PrimeMoneyConfig.CENTERED;
		}

		return anchorsRight() ? boxX + boxWidth - width : boxX;
	}

	@Override
	public void onClose() {
		config.hudX = storedX();
		config.hudY = boxY;
		config.save();
		super.onClose();
	}

	@FunctionalInterface
	private interface LabelSupplier {
		String get();
	}
}
