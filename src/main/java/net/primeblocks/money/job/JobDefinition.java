package net.primeblocks.money.job;

/** The 12 jobs listed on the PrimeBlocks wiki (wiki.primeblocks.net/de/Wirtschafts-Systeme/job-system). */
public enum JobDefinition {
	MINENARBEITER("Minenarbeiter"),
	HOLZFAELLER("Holzfäller"),
	FARMER("Farmer"),
	JAEGER("Jäger"),
	FISCHER("Fischer"),
	BOTANIKER("Botaniker"),
	GRAEBER("Gräber"),
	TAUCHER("Taucher"),
	ENTDECKER("Entdecker"),
	GOURMET("Gourmet"),
	CRAFTER("Crafter"),
	ZAUBERER("Zauberer");

	private final String displayName;

	JobDefinition(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return displayName;
	}

	/** Matches a job label captured from a chat message onto one of the known jobs. */
	public static JobDefinition fromLabel(String label) {
		if (label == null) {
			return null;
		}

		String normalized = label.trim();

		for (JobDefinition job : values()) {
			if (job.displayName.equalsIgnoreCase(normalized)) {
				return job;
			}
		}

		return null;
	}
}
