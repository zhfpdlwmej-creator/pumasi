package com.jacob.pumasi.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RoomView {
	private String id;
	private String title;
	private String category;
	private String categoryEmoji;
	private String startTime;   // ISO
	private int duration;
	private Integer capacity;   // null = unlimited
	private String createdBy;
	private int participantCount;
	private String phase;       // before | live | ended
}
