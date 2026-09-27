package net.primeblocks.money.hud;

import java.util.Locale;

/**
 * Formats amounts the short way - {@code 100k}, {@code 6,5M} - because at a glance on a HUD the
 * magnitude is what matters, and a fully written out {@code 6.543.219} takes longer to read than
 * it is worth.
 */
public final class AmountFormat {
	private AmountFormat() {
	}

	public static String compact(double value) {
		double abs = Math.abs(value);

		if (abs >= 1_000_000_000d) {
			return unit(value / 1_000_000_000d, "Mrd");
		}

		if (abs >= 1_000_000d) {
			return unit(value / 1_000_000d, "M");
		}

		if (abs >= 1_000d) {
			return unit(value / 1_000d, "k");
		}

		return String.format(Locale.GERMANY, "%,.0f", value);
	}

	/**
	 * Always three significant digits, so the shown figure never hides more than a tenth of a
	 * percent of itself.
	 *
	 * <p>Two digits are not enough once the millions start: {@code 1,1M} covers everything from
	 * 1.050.000 to 1.149.999, a spread of 100.000 - wide enough that comparing the display against
	 * a real figure looks like the tracking is wrong when it is only the rounding.
	 *
	 * <p>Trailing zeros are dropped, so {@code 6,50M} still reads as {@code 6,5M}.
	 */
	private static String unit(double mantissa, String suffix) {
		double abs = Math.abs(mantissa);
		String number;

		if (abs < 10) {
			number = String.format(Locale.GERMANY, "%.2f", mantissa);
		} else if (abs < 100) {
			number = String.format(Locale.GERMANY, "%.1f", mantissa);
		} else {
			number = String.format(Locale.GERMANY, "%.0f", mantissa);
		}

		return trimTrailingZeros(number) + suffix;
	}

	private static String trimTrailingZeros(String number) {
		if (number.indexOf(',') < 0) {
			return number;
		}

		int end = number.length();

		while (end > 0 && number.charAt(end - 1) == '0') {
			end--;
		}

		if (end > 0 && number.charAt(end - 1) == ',') {
			end--;
		}

		return number.substring(0, end);
	}

	/**
	 * A rough "how much longer" reading: seconds while it is close, then minutes, then hours. One
	 * decimal only, since the number it is derived from is itself an estimate - "1,3 min" claims
	 * exactly as much precision as there really is.
	 */
	public static String eta(long millis) {
		if (millis < 0) {
			return "?";
		}

		double seconds = millis / 1000.0;

		if (seconds < 90) {
			return String.format(Locale.GERMANY, "%.0f s", seconds);
		}

		double minutes = seconds / 60.0;

		if (minutes < 90) {
			return String.format(Locale.GERMANY, "%.1f min", minutes);
		}

		double hours = minutes / 60.0;

		if (hours < 48) {
			return String.format(Locale.GERMANY, "%.1f Std", hours);
		}

		return String.format(Locale.GERMANY, "%.0f Tage", hours / 24.0);
	}

	public static String duration(long millis) {
		long totalSeconds = millis / 1000;
		long hours = totalSeconds / 3600;
		long minutes = (totalSeconds % 3600) / 60;
		long seconds = totalSeconds % 60;

		if (hours > 0) {
			return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
		}

		return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
	}
}
