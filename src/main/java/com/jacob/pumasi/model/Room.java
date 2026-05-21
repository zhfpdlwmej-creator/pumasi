package com.jacob.pumasi.model;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor
public class Room {

	@Id
	@Column(length = 36)
	private String id;

	@Column(nullable = false, length = 120)
	private String title;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Category category;

	@Column(name = "start_time", nullable = false)
	private LocalDateTime startTime;

	@Column(nullable = false)
	private int duration;

	/** null = 무제한 */
	@Column
	private Integer capacity;

	@Column(name = "created_by", nullable = false, length = 36)
	private String createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
	}

	/** before | live | ended — startTime/duration 으로 계산 */
	public String phase() {
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime end = startTime.plusMinutes(duration);
		if (now.isBefore(startTime)) {
			return "before";
		}
		if (now.isBefore(end)) {
			return "live";
		}
		return "ended";
	}
}
