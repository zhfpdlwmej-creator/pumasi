package com.jacob.pumasi.store;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jacob.pumasi.model.AppUser;
import com.jacob.pumasi.model.Category;
import com.jacob.pumasi.model.Participant;
import com.jacob.pumasi.model.ParticipantPhoto;
import com.jacob.pumasi.model.PhotoView;
import com.jacob.pumasi.model.Report;
import com.jacob.pumasi.model.Room;
import com.jacob.pumasi.model.RoomView;
import com.jacob.pumasi.repo.ParticipantPhotoRepository;
import com.jacob.pumasi.repo.ParticipantRepository;
import com.jacob.pumasi.repo.ReportRepository;
import com.jacob.pumasi.repo.RoomRepository;
import com.jacob.pumasi.repo.UserRepository;

/**
 * Repository 기반 영속 저장소. (Supabase Postgres + JPA + Flyway)
 * 기존 인메모리 저장소를 대체하지만 외부 API(컨트롤러가 호출하는 메서드)는 그대로 유지.
 */
@Service
public class DataStore {

	public static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

	private final UserRepository users;
	private final RoomRepository rooms;
	private final ParticipantRepository participants;
	private final ParticipantPhotoRepository photos;
	private final ReportRepository reports;

	public DataStore(UserRepository users, RoomRepository rooms,
			ParticipantRepository participants, ParticipantPhotoRepository photos,
			ReportRepository reports) {
		this.users = users;
		this.rooms = rooms;
		this.participants = participants;
		this.photos = photos;
		this.reports = reports;
	}

	/** 호환을 위해 시그니처 유지 — prefix 인자는 무시되고 UUID 본문만 반환 */
	public static String id(String prefix) {
		return UUID.randomUUID().toString();
	}

	/* ───────── rooms ───────── */

	@Transactional(readOnly = true)
	public List<RoomView> listRooms() {
		List<Room> all = rooms.findAllByOrderByStartTimeAsc();
		Map<String, Integer> counts = activeCountsByRoom();
		List<RoomView> out = new ArrayList<>(all.size());
		for (Room r : all) {
			out.add(toView(r, counts.getOrDefault(r.getId(), 0)));
		}
		return out;
	}

	@Transactional(readOnly = true)
	public Room getRoom(String id) {
		return rooms.findById(id).orElse(null);
	}

	@Transactional(readOnly = true)
	public RoomView roomView(String id) {
		Room r = rooms.findById(id).orElse(null);
		if (r == null) {
			return null;
		}
		int count = (int) participants.findByRoomId(id).stream()
				.filter(p -> !"completed".equals(p.getStatus())).count();
		return toView(r, count);
	}

	@Transactional
	public Room createRoom(String title, String category, String startIso,
			int duration, Integer capacity, String createdBy) {
		Room r = new Room();
		r.setId(id("r"));
		r.setTitle(title);
		r.setCategory(Category.of(category));
		r.setStartTime(LocalDateTime.parse(startIso, ISO));
		r.setDuration(duration);
		r.setCapacity(capacity);
		r.setCreatedBy(createdBy);
		return rooms.save(r);
	}

	private RoomView toView(Room r, int count) {
		return new RoomView(r.getId(), r.getTitle(), r.getCategory().name(),
				r.getCategory().getEmoji(), r.getStartTime().format(ISO),
				r.getDuration(), r.getCapacity(), r.getCreatedBy(),
				count, r.phase());
	}

	private Map<String, Integer> activeCountsByRoom() {
		Map<String, Integer> m = new HashMap<>();
		for (Object[] row : participants.countActiveByRoom()) {
			m.put((String) row[0], ((Number) row[1]).intValue());
		}
		return m;
	}

	/* ───────── participants ───────── */

	@Transactional(readOnly = true)
	public List<Participant> listParticipants(String roomId) {
		List<Participant> list = participants.findByRoomId(roomId);
		if (list.isEmpty()) {
			return list;
		}
		// 신고된(pending) 사진 id 화이트아웃 — 갤러리에서 자동 숨김
		java.util.Set<String> hidden = new java.util.HashSet<>(reports.findPendingReportedPhotoIds());

		// 사진 일괄 조회 — N+1 회피
		List<String> pids = list.stream().map(Participant::getId).collect(Collectors.toList());
		Map<String, List<PhotoView>> byPid = new HashMap<>();
		for (ParticipantPhoto ph : photos.findByParticipantIdInOrderByIdAsc(pids)) {
			if (hidden.contains(String.valueOf(ph.getId()))) {
				continue;
			}
			byPid.computeIfAbsent(ph.getParticipantId(), k -> new ArrayList<>())
					.add(new PhotoView(ph.getId(), ph.getDataUrl()));
		}
		// 별명 계산을 위해 유저 일괄 조회
		List<String> uids = list.stream().map(Participant::getUserId).distinct().collect(Collectors.toList());
		Map<String, AppUser> userById = new HashMap<>();
		for (AppUser u : users.findAllById(uids)) {
			userById.put(u.getId(), u);
		}
		for (Participant p : list) {
			p.setPhotos(byPid.getOrDefault(p.getId(), Collections.emptyList()));
			AppUser u = userById.get(p.getUserId());
			p.setTitle(u == null ? null : u.getTitle());
		}
		return list;
	}

	@Transactional
	public boolean join(String roomId, AppUser user, String status) {
		upsertUser(user);
		if (participants.existsByRoomIdAndUserId(roomId, user.getId())) {
			return false;
		}
		Participant p = new Participant();
		p.setId(id("p"));
		p.setRoomId(roomId);
		p.setUserId(user.getId());
		p.setStatus(status);
		p.setNickname(user.getNickname());
		p.setAvatarUrl(user.getAvatarUrl());
		try {
			participants.save(p);
			return true;
		} catch (DataIntegrityViolationException dup) {
			return false;
		}
	}

	@Transactional
	public void leave(String roomId, String userId) {
		Optional<Participant> opt = participants.findByRoomIdAndUserId(roomId, userId);
		if (!opt.isPresent()) {
			return;
		}
		Participant p = opt.get();
		Room r = rooms.findById(roomId).orElse(null);
		if (r != null && "live".equals(r.phase()) && !"completed".equals(p.getStatus())) {
			users.findById(userId).ifPresent(u -> {
				u.setFailCount(u.getFailCount() + 1);
				users.save(u);
			});
		}
		participants.delete(p);
	}

	@Transactional
	public void setStatus(String roomId, String userId, String status) {
		Optional<Participant> opt = participants.findByRoomIdAndUserId(roomId, userId);
		if (!opt.isPresent()) {
			return;
		}
		Participant p = opt.get();
		p.setStatus(status);
		participants.save(p);
		if ("completed".equals(status)) {
			users.findById(userId).ifPresent(u -> {
				u.setSuccessCount(u.getSuccessCount() + 1);
				// 완료 1회로 '쉬어간 날' 1회 상쇄 (0 미만 안 내려감)
				if (u.getFailCount() > 0) {
					u.setFailCount(u.getFailCount() - 1);
				}
				users.save(u);
			});
		}
	}

	@Transactional(readOnly = true)
	public boolean isParticipant(String roomId, String userId) {
		return participants.existsByRoomIdAndUserId(roomId, userId);
	}

	@Transactional(readOnly = true)
	public Participant participant(String roomId, String userId) {
		return participants.findByRoomIdAndUserId(roomId, userId).orElse(null);
	}

	/* ───────── users ───────── */

	@Transactional
	public AppUser upsertUser(AppUser user) {
		Optional<AppUser> existing = users.findById(user.getId());
		if (existing.isPresent()) {
			AppUser u = existing.get();
			u.setNickname(user.getNickname());
			u.setAvatarUrl(user.getAvatarUrl());
			return users.save(u);
		}
		return users.save(user);
	}

	@Transactional(readOnly = true)
	public AppUser getUser(String userId) {
		return users.findById(userId).orElse(null);
	}

	@Transactional(readOnly = true)
	public List<RoomView> historyFor(String userId) {
		List<Participant> myParts = participants.findByUserId(userId);
		if (myParts.isEmpty()) {
			return Collections.emptyList();
		}
		List<String> roomIds = myParts.stream().map(Participant::getRoomId).distinct().collect(Collectors.toList());
		Map<String, Room> roomById = new HashMap<>();
		for (Room r : rooms.findAllById(roomIds)) {
			roomById.put(r.getId(), r);
		}
		Map<String, Integer> counts = activeCountsByRoom();
		List<RoomView> out = new ArrayList<>();
		for (Participant p : myParts) {
			Room r = roomById.get(p.getRoomId());
			if (r != null) {
				RoomView v = toView(r, counts.getOrDefault(r.getId(), 0));
				v.setPhase(p.getStatus()); // phase 슬롯에 내 참여 상태 담아 반환 (기존 동작 유지)
				out.add(v);
			}
		}
		return out;
	}

	@Transactional
	public void renameParticipants(String userId, String newNickname) {
		participants.renameByUserId(userId, newNickname);
	}

	@Transactional(readOnly = true)
	public List<String> joinedRoomIds(String userId) {
		return participants.findJoinedRoomIds(userId);
	}

	/**
	 * 인증샷 업로드 — 사진 1장 저장 + 참여자 미완료 시 자동 완료 처리(successCount++).
	 * 컨트롤러가 시간 윈도우/MIME 검증 통과 후 호출한다.
	 * @return 갱신된 사용자 (참여자가 없거나 사용자가 없으면 null)
	 */
	@Transactional
	public AppUser addPhoto(String roomId, String userId, String dataUrl) {
		Optional<Participant> opt = participants.findByRoomIdAndUserId(roomId, userId);
		if (!opt.isPresent()) {
			return null;
		}
		Participant p = opt.get();
		photos.save(new ParticipantPhoto(p.getId(), dataUrl));
		if (!"completed".equals(p.getStatus())) {
			p.setStatus("completed");
			participants.save(p);
			AppUser u = users.findById(userId).orElse(null);
			if (u != null) {
				u.setSuccessCount(u.getSuccessCount() + 1);
				// 완료 1회로 '쉬어간 날' 1회 상쇄 (0 미만 안 내려감)
				if (u.getFailCount() > 0) {
					u.setFailCount(u.getFailCount() - 1);
				}
				return users.save(u);
			}
		}
		return users.findById(userId).orElse(null);
	}

	/**
	 * 본인 업로드 사진 삭제.
	 * 성공 시 해당 사진이 속했던 방 id 반환 (broadcast 용), 권한 없거나 미존재면 null.
	 * 완료 상태와 successCount 는 건드리지 않음 — 사진은 사라져도 인증 사실은 유지.
	 */
	@Transactional
	public String deletePhoto(Long photoId, String userId) {
		ParticipantPhoto ph = photos.findById(photoId).orElse(null);
		if (ph == null) {
			return null;
		}
		Participant p = participants.findById(ph.getParticipantId()).orElse(null);
		if (p == null || !p.getUserId().equals(userId)) {
			return null; // 본인 사진 아님
		}
		photos.delete(ph);
		return p.getRoomId();
	}

	/** 신고 등록 — 같은 사용자가 같은 대상을 중복 신고하면 false 반환 */
	@Transactional
	public boolean addReport(String reporterId, String targetType, String targetId, String reason) {
		if (reports.existsByReporterIdAndTargetTypeAndTargetId(reporterId, targetType, targetId)) {
			return false;
		}
		Report r = new Report();
		r.setReporterId(reporterId);
		r.setTargetType(targetType);
		r.setTargetId(targetId);
		r.setReason(reason);
		try {
			reports.save(r);
			return true;
		} catch (DataIntegrityViolationException dup) {
			return false;
		}
	}

	/* ───────── reports ───────── */

	/** 신고 저장. 같은 신고자가 같은 대상을 중복 신고하면 false */
	@Transactional
	public boolean report(String reporterId, String targetType, String targetId, String reason) {
		if (reports.existsByReporterIdAndTargetTypeAndTargetId(reporterId, targetType, targetId)) {
			return false;
		}
		Report r = new Report();
		r.setReporterId(reporterId);
		r.setTargetType(targetType);
		r.setTargetId(targetId);
		r.setReason(reason);
		try {
			reports.save(r);
			return true;
		} catch (DataIntegrityViolationException dup) {
			return false;
		}
	}

	/* ───────── 카카오 OAuth 지원 ───────── */

	@Transactional(readOnly = true)
	public Optional<AppUser> findByKakaoId(String kakaoId) {
		return users.findByKakaoId(kakaoId);
	}

	/** /welcome 단계 — 닉네임 + 아바타 확정 + setupComplete=true 로 전환 */
	@Transactional
	public void completeSetup(String userId, String nickname, String avatar) {
		users.findById(userId).ifPresent(u -> {
			u.setNickname(nickname);
			if (avatar != null) {
				u.setAvatarUrl(avatar);
			}
			u.setSetupComplete(true);
			users.save(u);
			// 참여한 방의 participant 닉네임도 동기화 (보통 없겠지만 안전 처리)
			renameParticipants(userId, nickname);
		});
	}

	/* ───────── 카운트 노출 (Seeder 용) ───────── */

	@Transactional(readOnly = true)
	public long userCount() {
		return users.count();
	}
}
