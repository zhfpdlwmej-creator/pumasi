package com.jacob.pumasi.store;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.jacob.pumasi.model.AppUser;
import com.jacob.pumasi.model.Category;
import com.jacob.pumasi.model.Room;

/**
 * users 테이블이 비어 있을 때만 데모 데이터를 채워 넣는다.
 * 이미 데이터가 있으면 (예: 개발 중 재기동) 손대지 않음 — 진짜 사용자 데이터를 덮어쓰지 않기 위함.
 */
@Component
public class Seeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(Seeder.class);

	private final DataStore store;

	/** application-local.yml 에 app.seed.enabled: false 로 두면 시드 안 함 (완전 빈 DB) */
	@Value("${app.seed.enabled:true}")
	private boolean seedEnabled;

	public Seeder(DataStore store) {
		this.store = store;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!seedEnabled) {
			log.info("Seed disabled by app.seed.enabled=false — leaving DB empty");
			return;
		}
		if (store.userCount() > 0) {
			log.info("Seed skipped: users table already populated ({} rows)", store.userCount());
			return;
		}
		log.info("Seeding demo data…");

		LocalDateTime now = LocalDateTime.now();

		AppUser u1 = new AppUser("u_seed_1", "민지", null, 14, 0);
		AppUser u2 = new AppUser("u_seed_2", "도윤", null, 7, 0);
		AppUser u3 = new AppUser("u_seed_3", "서아", null, 21, 0);
		store.upsertUser(u1);
		store.upsertUser(u2);
		store.upsertUser(u3);

		Room r1 = newRoom("주말 이불 빨래 벼락치기", Category.청소, now.plusMinutes(3), 60, 5, u1.getId());
		Room r2 = newRoom("폰 잠그고 자소서 쓰기 1시간", Category.집중, now.plusMinutes(8), 60, 20, u3.getId());
		Room r3 = newRoom("같이 저녁 차려 먹기", Category.집밥, now.plusMinutes(40), 30, 10, u2.getId());
		Room r4 = newRoom("홈트 30분 같이 버티기", Category.운동, now.minusMinutes(5), 30, null, u3.getId());

		store.join(r1.getId(), u1, "reserved");
		store.join(r1.getId(), u2, "reserved");
		store.join(r1.getId(), u3, "reserved");
		store.join(r2.getId(), u3, "reserved");
		for (int i = 0; i < 11; i++) {
			AppUser ghost = new AppUser("u_ghost_" + i, "함께하는 " + (i + 1), null, 0, 0);
			store.upsertUser(ghost);
			store.join(r2.getId(), ghost, "reserved");
		}
		store.join(r4.getId(), u3, "active");
		store.join(r4.getId(), u1, "active");

		// r3 는 의도적으로 참여자 0 — 빈 방 표시 확인용
		if (r3 == null) {
			log.warn("r3 was null"); // 사용 안 함; 컴파일 경고 회피용 — 실제로는 r1~r4 모두 보존
		}

		log.info("Seed complete.");
	}

	private Room newRoom(String title, Category cat, LocalDateTime start, int dur,
			Integer cap, String createdBy) {
		return store.createRoom(title, cat.name(), start.format(DataStore.ISO), dur, cap, createdBy);
	}
}
