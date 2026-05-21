package com.jacob.pumasi.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.jacob.pumasi.model.Report;

public interface ReportRepository extends JpaRepository<Report, Long> {

	boolean existsByReporterIdAndTargetTypeAndTargetId(
			String reporterId, String targetType, String targetId);

	/** 갤러리에서 숨겨야 할 사진 ID들 (target_id는 photo id의 문자열 표현) */
	@Query("select r.targetId from Report r where r.targetType = 'photo' and r.status = 'pending'")
	List<String> findPendingReportedPhotoIds();
}

