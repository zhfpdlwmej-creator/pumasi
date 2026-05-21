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
@Table(name = "participant_photos")
@Getter
@Setter
@NoArgsConstructor
public class ParticipantPhoto {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "participant_id", nullable = false, length = 36)
	private String participantId;

	@Column(name = "data_url", nullable = false, columnDefinition = "text")
	private String dataUrl;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	public ParticipantPhoto(String participantId, String dataUrl) {
		this.participantId = participantId;
		this.dataUrl = dataUrl;
	}

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
	}
}
