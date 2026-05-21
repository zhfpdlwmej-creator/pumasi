package com.jacob.pumasi.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jacob.pumasi.model.Participant;

public interface ParticipantRepository extends JpaRepository<Participant, String> {

	List<Participant> findByRoomId(String roomId);

	List<Participant> findByUserId(String userId);

	Optional<Participant> findByRoomIdAndUserId(String roomId, String userId);

	boolean existsByRoomIdAndUserId(String roomId, String userId);

	/** 참여 이력이 있는 모든 방 id (status 무관) — 종료 후 사진 다시 보기 위해 입장 허용 */
	@Query("select p.roomId from Participant p where p.userId = :uid")
	List<String> findJoinedRoomIds(@Param("uid") String userId);

	@Modifying
	@Query("delete from Participant p where p.roomId = :rid and p.userId = :uid")
	int deleteByRoomAndUser(@Param("rid") String roomId, @Param("uid") String userId);

	/** 방별 활성(=completed 아님) 참여자 수 — 한 번에 집계 */
	@Query("select p.roomId, count(p) from Participant p where p.status <> 'completed' group by p.roomId")
	List<Object[]> countActiveByRoom();

	@Modifying
	@Query("update Participant p set p.nickname = :nick where p.userId = :uid")
	int renameByUserId(@Param("uid") String userId, @Param("nick") String nickname);
}
