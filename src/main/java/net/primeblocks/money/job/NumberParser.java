package net.primeblocks.money.job;

/**
 * Parses a number out of a chat message fragment without assuming a locale.
 *
 * <p>PrimeBlocks messages might use German grouping ({@code 24.135,04}) or English grouping
 * ({@code 24,135.04}) - both have been seen in different parts of the game. Whichever of
 * {@code ,} or {@code .} appears last in the string is treated as the decimal separator; any
 * earlier occurrences of either character are treated as grouping separators and dropped.
 */
public final class NumberParser {
	private NumberParser() {
	}

	public static Double parse(String raw) {
		if (raw == null) {
			return null;
		}

		String cleaned = raw.trim().replaceAll("[^0-9.,-]", "");

		if (cleaned.isEmpty()) {
			return null;
		}

		int lastComma = cleaned.lastIndexOf(',');
		int lastDot = cleaned.lastIndexOf('.');
		char decimalSeparator = '\0';

		if (lastComma >= 0 && lastDot >= 0) {
			decimalSeparator = lastComma > lastDot ? ',' : '.';
		} else if (lastComma >= 0 && looksLikeDecimal(cleaned, lastComma)) {
			decimalSeparator = ',';
		} else if (lastDot >= 0 && looksLikeDecimal(cleaned, lastDot)) {
			decimalSeparator = '.';
		}

		StringBuilder normalized = new StringBuilder(cleaned.length());

		for (int i = 0; i < cleaned.length(); i++) {
			char c = cleaned.charAt(i);

			if (c == decimalSeparator) {
				normalized.append('.');
			} else if (c == ',' || c == '.') {
				// grouping separator, drop it
			} else {
				normalized.append(c);
			}
		}

		try {
			return Double.parseDouble(normalized.toString());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** A lone separator only counts as a decimal point if it is followed by exactly 1-2 digits. */
	private static boolean looksLikeDecimal(String s, int index) {
		int digitsAfter = s.length() - index - 1;
		return digitsAfter == 1 || digitsAfter == 2;
	}
}
