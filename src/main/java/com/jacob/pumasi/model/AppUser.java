package com.jacob.pumasi.model;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class AppUser {

	@Id
	@Column(length = 36)
	private String id;

	@Column(nullable = false, length = 64)
	private String nickname;

	@Column(name = "avatar_url", columnDefinition = "text")
	private String avatarUrl;

	@Column(name = "success_count", nullable = false)
	private int successCount;

	@Column(name = "fail_count", nullable = false)
	private int failCount;

	/** Kakao OAuth 식별자 — 시드/고스트는 null */
	@Column(name = "kakao_id", length = 64)
	private String kakaoId;

	/** false 면 첫 로그인 직후 → 닉네임 설정 화면(/welcome) 보내야 함 */
	@Column(name = "setup_complete", nullable = false)
	private boolean setupComplete = true;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	public AppUser(String id, String nickname, String avatarUrl, int successCount, int failCount) {
		this.id = id;
		this.nickname = nickname;
		this.avatarUrl = avatarUrl;
		this.successCount = successCount;
		this.failCount = failCount;
	}

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
	}

	/*
	 * ── 쉬어간 별명: 라이브 중 이탈이 완료보다 많을 때 ──
	 * 사람 자체를 비난하지 않고, 상황·유혹·자기비하 유머로만 가볍게 표현.
	 * 누적될수록 강해지는 게 아니라 다른 결의 귀여운 핑계가 된다.
	 */
	private static final String[] FAIL_1 = {
			"💤 오늘은 쉬어간 감자",
			"☕ 의욕 충전 중",
			"🛋️ 소파의 유혹에 패배",
			"🌙 내일부터 진짜 합니다",
			"🥱 잠깐 한숨 돌리는 중" };
	private static final String[] FAIL_2 = {
			"🐢 잠시 딴길로 샜어요",
			"📱 쇼츠에 잡아먹힘",
			"🍗 치킨에게 졌습니다",
			"🎮 게임만 살짝 한다더니",
			"🛌 이불 밖은 위험해" };
	private static final String[] FAIL_3 = {
			"🙈 조용히 사라진 사람",
			"🌫️ 안개 속으로 사라짐",
			"🚪 살짝 빠져나간 요정",
			"🎈 풍선처럼 떠올랐어요",
			"🐑 양 세다 진짜 잠듦" };
	private static final String[] FAIL_5 = {
			"🦥 단골 휴식러",
			"💭 마음만 와 있는 분",
			"🍂 바람 따라 흘러가는 분",
			"🌌 별 보러 떠난 사람",
			"🛸 잠깐 다른 차원에 다녀옴" };

	/* ── 명예 별명: 완료가 쌓일수록 점점 화려하게 ── */
	private static final String[] OK_0 = {
			"🌱 새내기", "🐣 갓 깨어난 품린이", "🔰 초보 운전(품앗이)",
			"🍼 품앗이 0일차", "👶 풋풋 입문자" };
	private static final String[] OK_1 = {
			"🌿 성실 새싹", "☘️ 꾸준 떡잎", "✨ 품앗이 성공",
			"🐥 삐약삐약 일꾼", "🌼 정 붙인 단골" };
	private static final String[] OK_3 = {
			"💪 꾸준함 장인", "🛠️ 우리 동네 일꾼", "🤝 약속 지키는 사람",
			"🧱 벽돌 한 장 한 장", "🔋 방전 없는 체력왕" };
	private static final String[] OK_6 = {
			"🏅 품앗이 장인", "🧹 오늘의 정리왕", "🎯 약속 명중률 100%",
			"⚙️ 소문난 일잘러", "🧗 끝까지 가는 사람" };
	private static final String[] OK_10 = {
			"✨ 품앗이 마스터", "🔥 집중의 신", "🍳 집밥 마스터",
			"🥷 약속 닌자", "🧙 약속 마법사" };
	private static final String[] OK_20 = {
			"👑 품앗이 레전드", "🐉 품앗이 드래곤", "🏆 명예의 전당 입성",
			"🌟 살아있는 전설", "🛡️ 약속 불멸자" };

	private String pick(String[] pool) {
		int h = (id == null ? 0 : id.hashCode()) & 0x7fffffff;
		return pool[h % pool.length];
	}

	@Transient
	@JsonProperty("title")
	public String getTitle() {
		if (failCount > 0 && failCount > successCount) {
			if (failCount >= 5) {
				return pick(FAIL_5);
			}
			if (failCount >= 3) {
				return pick(FAIL_3);
			}
			if (failCount >= 2) {
				return pick(FAIL_2);
			}
			return pick(FAIL_1);
		}
		if (successCount >= 20) {
			return pick(OK_20);
		}
		if (successCount >= 10) {
			return pick(OK_10);
		}
		if (successCount >= 6) {
			return pick(OK_6);
		}
		if (successCount >= 3) {
			return pick(OK_3);
		}
		if (successCount >= 1) {
			return pick(OK_1);
		}
		return pick(OK_0);
	}

	public static String[][] honorTiers() {
		return new String[][] { OK_0, OK_1, OK_3, OK_6, OK_10, OK_20 };
	}

	public static int[] honorThresholds() {
		return new int[] { 0, 1, 3, 6, 10, 20 };
	}

	public static String[][] shameTiers() {
		return new String[][] { FAIL_1, FAIL_2, FAIL_3, FAIL_5 };
	}

	public static int[] shameThresholds() {
		return new int[] { 1, 2, 3, 5 };
	}
}
