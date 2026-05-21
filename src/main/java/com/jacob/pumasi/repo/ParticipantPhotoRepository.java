package com.jacob.pumasi.repo;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jacob.pumasi.model.ParticipantPhoto;

public interface ParticipantPhotoRepository extends JpaRepository<ParticipantPhoto, Long> {

	List<ParticipantPhoto> findByParticipantIdOrderByIdAsc(String participantId);

	/** 여러 참여자의 사진을 한 번에 — N+1 방지 */
	List<ParticipantPhoto> findByParticipantIdInOrderByIdAsc(Collection<String> participantIds);
}
