package net.primeblocks.money.job;

/**
 * One parsed {@code JOBS »} chat line.
 *
 * @param timestampMillis wall-clock time the message arrived
 * @param money           coins gained by this message, 0 if the message carried no money amount
 * @param xp              job XP gained by this message, 0 if the message carried no XP amount
 * @param jobLabel        job name captured from the message, or null if the message did not name one
 */
public record JobEvent(long timestampMillis, double money, double xp, String jobLabel) {
}
