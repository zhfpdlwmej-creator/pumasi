package com.jacob.pumasi.web;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jacob.pumasi.model.AppUser;
import com.jacob.pumasi.model.Participant;
import com.jacob.pumasi.model.Room;
import com.jacob.pumasi.model.RoomView;
import com.jacob.pumasi.socket.RealtimeHandler;
import com.jacob.pumasi.store.DataStore;

@RestController
public class ApiController {

	@Autowired
	private DataStore store;

	@Autowired
	private RealtimeHandler realtime;

	private AppUser user(HttpSession s) {
		String uid = (String) s.getAttribute("uid");
		if (uid == null) {
			return null;
		}
		return store.getUser(uid);
	}

	@GetMapping("/api/time")
	public Map<String, String> serverTime() {
		Map<String, String> m = new HashMap<>();
		m.put("now", LocalDateTime.now().format(DataStore.ISO));
		return m;
	}

	@GetMapping("/api/me")
	public AppUser me(HttpSession session) {
		return user(session);
	}

	@GetMapping("/api/rooms")
	public List<RoomView> rooms() {
		return store.listRooms();
	}

	@GetMapping("/api/rooms/{id}")
	public ResponseEntity<RoomView> room(@PathVariable String id) {
		RoomView v = store.roomView(id);
		return v == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(v);
	}

	@GetMapping("/api/rooms/{id}/participants")
	public List<Participant> participants(@PathVariable String id) {
		return store.listParticipants(id);
	}

	@PostMapping("/api/rooms")
	public ResponseEntity<RoomView> create(@RequestParam String title,
			@RequestParam String category,
			@RequestParam String startTime,
			@RequestParam int duration,
			@RequestParam(required = false) Integer capacity,
			@RequestParam(required = false, defaultValue = "false") boolean secret,
			@RequestParam(required = false) String password,
			HttpSession session) {
		AppUser u = user(session);
		if (u == null) {
			return ResponseEntity.status(401).build();
		}
		String t = title == null ? "" : title.trim();
		if (t.isEmpty() || t.length() > 40) {
			return ResponseEntity.badRequest().build();
		}
		// 서버측 방어 clamp
		int dur = Math.max(5, Math.min(600, duration));
		Integer cap = capacity == null ? null : Math.max(2, Math.min(1000, capacity));
		Room r = store.createRoom(t, category, startTime, dur, cap, u.getId(), secret, password);
		store.join(r.getId(), u, "reserved");
		// 생성자는 본인 방에 비번 없이 들어가도록 세션 인증 처리
		if (r.isLocked()) {
			session.setAttribute("authed_" + r.getId(), Boolean.TRUE);
		}
		realtime.broadcast("rooms", null);
		return ResponseEntity.ok(store.roomView(r.getId()));
	}

	@PostMapping("/api/rooms/{id}/unlock")
	public ResponseEntity<Void> unlock(@PathVariable String id,
			@RequestParam String password, HttpSession session) {
		if (store.checkRoomPassword(id, password)) {
			session.setAttribute("authed_" + id, Boolean.TRUE);
			return ResponseEntity.ok().build();
		}
		return ResponseEntity.status(403).build();
	}

	@PostMapping("/api/rooms/{id}/join")
	public ResponseEntity<Void> join(@PathVariable String id, HttpSession session) {
		AppUser u = user(session);
		if (u == null) {
			return ResponseEntity.status(401).build();
		}
		Room r = store.getRoom(id);
		if (r == null) {
			return ResponseEntity.notFound().build();
		}
		// 종료된 방엔 새로 참여 못 함
		if ("ended".equals(r.phase())) {
			return ResponseEntity.status(410).build();
		}
		// 비번 잠긴 방은 unlock 인증(또는 기존 참여자)만 입장
		if (r.isLocked() && session.getAttribute("authed_" + id) == null
				&& !store.isParticipant(id, u.getId())) {
			return ResponseEntity.status(403).build();
		}
		store.join(id, u, "reserved");
		realtime.broadcast("participants", id);
		realtime.broadcast("rooms", null);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/api/rooms/{id}/leave")
	public ResponseEntity<AppUser> leave(@PathVariable String id, HttpSession session) {
		AppUser u = user(session);
		if (u == null) {
			return ResponseEntity.status(401).build();
		}
		AppUser updated = store.leave(id, u.getId());
		realtime.broadcast("participants", id);
		realtime.broadcast("rooms", null);
		// 종료 후 탈출 = 완주 인정인 경우 갱신된 user 반환(클라이언트가 축하 모달 띄움).
		// 그 외 케이스는 null 본문.
		return ResponseEntity.ok(updated);
	}

	@PostMapping("/api/rooms/{id}/activate")
	public ResponseEntity<Void> activate(@PathVariable String id, HttpSession session) {
		AppUser u = user(session);
		if (u == null) {
			return ResponseEntity.status(401).build();
		}
		store.setStatus(id, u.getId(), "active");
		realtime.broadcast("participants", id);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/api/rooms/{id}/complete")
	public ResponseEntity<AppUser> complete(@PathVariable String id, HttpSession session) {
		AppUser u = user(session);
		if (u == null) {
			return ResponseEntity.status(401).build();
		}
		store.setStatus(id, u.getId(), "completed");
		realtime.broadcast("participants", id);
		return ResponseEntity.ok(store.getUser(u.getId()));
	}

	/** 사진 업로드 — 화이트리스트로 data:image/ 만 허용, 길이 상한 둠 */
	private static final int MAX_PHOTO_DATAURL_LEN = 3_000_000; // ~2.2MB 이미지(base64)
	/** 인증샷 업로드 시간 윈도우 — 종료 10분 전 ~ 종료 24시간 후 */
	private static final long WINDOW_OPEN_BEFORE_END_MIN = 10;
	private static final long WINDOW_CLOSE_AFTER_END_HOURS = 24;

	@PostMapping("/api/rooms/{id}/photo")
	public ResponseEntity<AppUser> photo(@PathVariable String id,
			@RequestParam String dataUrl, HttpSession session) {
		AppUser u = user(session);
		if (u == null) {
			return ResponseEntity.status(401).build();
		}
		if (dataUrl == null
				|| !dataUrl.startsWith("data:image/")
				|| dataUrl.length() > MAX_PHOTO_DATAURL_LEN) {
			return ResponseEntity.badRequest().build();
		}
		Room r = store.getRoom(id);
		if (r == null) {
			return ResponseEntity.notFound().build();
		}
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime end = r.getStartTime().plusMinutes(r.getDuration());
		LocalDateTime windowOpen = end.minusMinutes(WINDOW_OPEN_BEFORE_END_MIN);
		LocalDateTime windowClose = end.plusHours(WINDOW_CLOSE_AFTER_END_HOURS);
		if (now.isBefore(windowOpen) || now.isAfter(windowClose)) {
			// 423 Locked — 윈도우 밖
			return ResponseEntity.status(423).build();
		}
		AppUser updated = store.addPhoto(id, u.getId(), dataUrl);
		if (updated == null) {
			return ResponseEntity.status(403).build();
		}
		realtime.broadcast("participants", id);
		return ResponseEntity.ok(updated);
	}

	@org.springframework.web.bind.annotation.DeleteMapping("/api/photos/{photoId}")
	public ResponseEntity<Void> deletePhoto(
			@PathVariable Long photoId, HttpSession session) {
		AppUser u = user(session);
		if (u == null) {
			return ResponseEntity.status(401).build();
		}
		String roomId = store.deletePhoto(photoId, u.getId());
		if (roomId == null) {
			return ResponseEntity.status(403).build(); // 본인 사진 아니거나 미존재
		}
		realtime.broadcast("participants", roomId);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/api/reports")
	public ResponseEntity<Void> report(@RequestParam String targetType,
			@RequestParam String targetId,
			@RequestParam(required = false) String reason,
			HttpSession session) {
		AppUser u = user(session);
		if (u == null) {
			return ResponseEntity.status(401).build();
		}
		if (!"photo".equals(targetType) && !"user".equals(targetType) && !"room".equals(targetType)) {
			return ResponseEntity.badRequest().build();
		}
		if (targetId == null || targetId.isEmpty() || targetId.length() > 64) {
			return ResponseEntity.badRequest().build();
		}
		String r = reason == null ? null
				: (reason.length() > 500 ? reason.substring(0, 500) : reason);
		boolean ok = store.addReport(u.getId(), targetType, targetId, r);
		if (!ok) {
			return ResponseEntity.status(409).build(); // 이미 신고함
		}
		if ("photo".equals(targetType)) {
			// 신고된 사진은 즉시 갤러리에서 숨겨야 하므로 전체 방 새로고침 신호
			realtime.broadcast("participants", "");
		}
		return ResponseEntity.ok().build();
	}

	@GetMapping("/api/titles")
	public Map<String, Object> titles() {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("honor", buildTier(AppUser.honorTiers(), AppUser.honorThresholds(), "완료 ", "회 이상"));
		out.put("shame", buildTier(AppUser.shameTiers(), AppUser.shameThresholds(), "쉬어간 날 ", "회 이상"));
		return out;
	}

	private List<Map<String, Object>> buildTier(String[][] pools, int[] thresh, String pre, String suf) {
		List<Map<String, Object>> out = new ArrayList<>();
		for (int i = 0; i < pools.length; i++) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("min", thresh[i]);
			m.put("label", pre + thresh[i] + suf);
			m.put("titles", Arrays.asList(pools[i]));
			out.add(m);
		}
		return out;
	}

	/** welcome 에서 고를 수 있는 동물 이모지 화이트리스트 */
	private static final java.util.Set<String> ALLOWED_AVATARS = new java.util.HashSet<>(
			java.util.Arrays.asList(
					"🐶", "🐱", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐵"));

	@PostMapping("/api/welcome")
	public ResponseEntity<Void> welcomeSubmit(
			@RequestParam String nickname,
			@RequestParam(required = false) String avatar,
			HttpSession session) {
		AppUser u = user(session);
		if (u == null) {
			return ResponseEntity.status(401).build();
		}
		String t = nickname == null ? "" : nickname.trim();
		if (t.isEmpty() || t.length() > 14) {
			return ResponseEntity.badRequest().build();
		}
		// 미허용 값은 무시 (null 처리). 클라이언트 조작으로 임의 텍스트가 박히는 걸 차단
		String av = (avatar != null && ALLOWED_AVATARS.contains(avatar)) ? avatar : null;
		store.completeSetup(u.getId(), t, av);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/api/me/nickname")
	public ResponseEntity<AppUser> rename(@RequestParam String nickname, HttpSession session) {
		AppUser u = user(session);
		if (u == null) {
			return ResponseEntity.status(401).build();
		}
		String t = nickname == null ? "" : nickname.trim();
		if (t.isEmpty() || t.length() > 14) {
			return ResponseEntity.badRequest().build();
		}
		u.setNickname(t);
		store.upsertUser(u);
		store.renameParticipants(u.getId(), t);
		realtime.broadcast("rooms", null);
		return ResponseEntity.ok(u);
	}

	@GetMapping("/api/me/joined")
	public List<String> joined(HttpSession session) {
		AppUser u = user(session);
		if (u == null) {
			return Collections.emptyList();
		}
		return store.joinedRoomIds(u.getId());
	}

	@GetMapping("/api/profile/history")
	public ResponseEntity<List<RoomView>> history(HttpSession session) {
		AppUser u = user(session);
		if (u == null) {
			return ResponseEntity.status(401).build();
		}
		return ResponseEntity.ok(store.historyFor(u.getId()));
	}
}
