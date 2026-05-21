package com.jacob.pumasi.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.persistence.Transient;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "participants",
		uniqueConstraints = @javax.persistence.UniqueConstraint(
				name = "uq_participants_room_user",
				columnNames = { "room_id", "user_id" }))
@Getter
@Setter
@NoArgsConstructor
public class Participant {

	@Id
	@Column(length = 36)
	private String id;

	@Column(name = "user_id", nullable = false, length = 36)
	private String userId;

	@Column(name = "room_id", nullable = false, length = 36)
	private String roomId;

	/** reserved | active | completed */
	@Column(nullable = false, length = 16)
	private String status;

	@Column(nullable = false, length = 64)
	private String nickname;

	@Column(name = "avatar_url", columnDefinition = "text")
	private String avatarUrl;

	@Column(name = "joined_at", nullable = false, updatable = false)
	private LocalDateTime joinedAt;

	@PrePersist
	void onCreate() {
		if (joinedAt == null) {
			joinedAt = LocalDateTime.now();
		}
	}

	/** 응답 시 서비스가 채워주는 사진 목록 ({id, src}). 영속 필드 아님. */
	@Transient
	private List<PhotoView> photos = new ArrayList<>();

	/** 응답 시 서비스가 채워주는 평판 별명 (영속 필드 아님) */
	@Transient
	private String title;
}
