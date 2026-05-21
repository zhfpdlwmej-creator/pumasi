package com.jacob.pumasi.model;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
public class Report {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "reporter_id", nullable = false, length = 36)
	private String reporterId;

	/** photo | user | room */
	@Column(name = "target_type", nullable = false, length = 16)
	private String targetType;

	@Column(name = "target_id", nullable = false, length = 64)
	private String targetId;

	@Column(columnDefinition = "text")
	private String reason;

	/** pending | resolved | dismissed */
	@Column(nullable = false, length = 16)
	private String status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
		if (status == null) {
			status = "pending";
		}
	}
}
