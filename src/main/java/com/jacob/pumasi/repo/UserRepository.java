package com.jacob.pumasi.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jacob.pumasi.model.AppUser;

public interface UserRepository extends JpaRepository<AppUser, String> {

	Optional<AppUser> findByKakaoId(String kakaoId);
}
