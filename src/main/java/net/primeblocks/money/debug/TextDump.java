package net.primeblocks.money.debug;

/**
 * Unicode-escapes text for debug logging, so an exotic glyph shows up as a code point.
 *
 * <p>Written out by hand rather than with {@code String.format}, which costs on the order of a
 * microsecond per call - parses its pattern, allocates a formatter, boxes its argument. That is
 * nothing once, but this runs per character of every chat line the server sends, and PrimeBlocks
 * chat is full of custom glyphs. On the client thread that adds up to a stutter.
 */
public final class TextDump {
	private static final char[] HEX = "0123456789ABCDEF".toCharArray();

	private TextDump() {
	}

	public static String escapeNonAscii(String text) {
		if (isPlainAscii(text)) {
			// The common case by far, and it needs no new string at all.
			return text;
		}

		StringBuilder out = new StringBuilder(text.length() + 16);

		text.codePoints().forEach(codePoint -> {
			if (codePoint < 0x80) {
				out.append((char) codePoint);
			} else {
				appendEscape(out, codePoint);
			}
		});

		return out.toString();
	}

	private static boolean isPlainAscii(String text) {
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) >= 0x80) {
				return false;
			}
		}

		return true;
	}

	private static void appendEscape(StringBuilder out, int codePoint) {
		out.append("\\u");

		// Four hex digits for anything that fits, more for the rare astral code point.
		int digits = codePoint > 0xFFFF ? 8 : 4;

		for (int shift = (digits - 1) * 4; shift >= 0; shift -= 4) {
			out.append(HEX[(codePoint >> shift) & 0xF]);
		}
	}
}
