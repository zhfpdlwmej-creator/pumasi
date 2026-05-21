package com.jacob.pumasi.model;

public enum Category {
	청소("🧹"),
	집중("🔥"),
	집밥("🍚"),
	운동("💪");

	private final String emoji;

	Category(String emoji) {
		this.emoji = emoji;
	}

	public String getEmoji() {
		return emoji;
	}

	public static Category of(String name) {
		for (Category c : values()) {
			if (c.name().equals(name)) {
				return c;
			}
		}
		return 청소;
	}
}
