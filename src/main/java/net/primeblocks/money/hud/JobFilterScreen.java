package net.primeblocks.money.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.primeblocks.money.PrimeMoneyConfig;
import net.primeblocks.money.job.JobDefinition;

/** Picks which jobs' earnings are counted - one toggle per job, laid out in a grid. */
public final class JobFilterScreen extends Screen {
	private static final int DIM = 0xC0101010;
	private static final int HINT = 0xFFBFBFBF;
	/** Plain RGB, not ARGB: text styles carry no alpha channel. */
	private static final int ON = 0x7ED37E;
	private static final int OFF = 0xE08A8A;

	private static final int COLUMNS = 3;
	private static final int BUTTON_WIDTH = 128;
	private static final int BUTTON_HEIGHT = 20;
	private static final int GAP = 4;

	private final Screen parent;
	private final PrimeMoneyConfig config;

	public JobFilterScreen(Screen parent, PrimeMoneyConfig config) {
		super(Component.literal("PrimeMoney — Jobs auswählen"));
		this.parent = parent;
		this.config = config;
	}

	@Override
	protected void init() {
		JobDefinition[] jobs = JobDefinition.values();
		int rows = (jobs.length + COLUMNS - 1) / COLUMNS;
		int gridWidth = COLUMNS * BUTTON_WIDTH + (COLUMNS - 1) * GAP;
		int startX = (width - gridWidth) / 2;
		int startY = (height - rows * (BUTTON_HEIGHT + GAP)) / 2 - 10;

		for (int i = 0; i < jobs.length; i++) {
			JobDefinition job = jobs[i];
			int x = startX + (i % COLUMNS) * (BUTTON_WIDTH + GAP);
			int y = startY + (i / COLUMNS) * (BUTTON_HEIGHT + GAP);

			addRenderableWidget(Button.builder(label(job), button -> {
				config.setJobEnabled(job.displayName(), !config.isJobEnabled(job.displayName()));
				button.setMessage(label(job));
			}).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
		}

		int footerY = startY + rows * (BUTTON_HEIGHT + GAP) + GAP;
		int footerWidth = (gridWidth - 2 * GAP) / 3;

		addRenderableWidget(Button.builder(Component.literal("Alle an"), button -> setAll(true))
				.bounds(startX, footerY, footerWidth, BUTTON_HEIGHT).build());
		addRenderableWidget(Button.builder(Component.literal("Alle aus"), button -> setAll(false))
				.bounds(startX + footerWidth + GAP, footerY, footerWidth, BUTTON_HEIGHT).build());
		addRenderableWidget(Button.builder(Component.literal("Fertig"), button -> onClose())
				.bounds(startX + 2 * (footerWidth + GAP), footerY, footerWidth, BUTTON_HEIGHT).build());
	}

	private void setAll(boolean enabled) {
		for (JobDefinition job : JobDefinition.values()) {
			config.setJobEnabled(job.displayName(), enabled);
		}

		// Every button's label is baked in at build time, so the whole grid is rebuilt rather than
		// tracked individually.
		rebuildWidgets();
	}

	private Component label(JobDefinition job) {
		boolean enabled = config.isJobEnabled(job.displayName());
		return Component.literal((enabled ? "☑ " : "☐ ") + job.displayName())
				.withColor(enabled ? ON : OFF);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		context.fill(0, 0, width, height, DIM);
		super.extractRenderState(context, mouseX, mouseY, delta);

		context.centeredText(font, Component.literal("Welche Jobs sollen mitgezählt werden?"),
				width / 2, 24, MoneyXpOverlayRenderer.COLOUR_TITLE);
		context.centeredText(font, Component.literal(
						"Ausgeschaltete Jobs werden weder gezählt noch hochgerechnet."),
				width / 2, height - 20, HINT);
	}

	@Override
	public void onClose() {
		config.save();
		minecraft.gui.setScreen(parent);
	}
}
