package com.jacob.pumasi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * 인증은 우리 세션(uid) 기반으로 직접 관리하고, Spring Security 는
 * 모든 경로 permitAll + CSRF 보호 + 보안 헤더만 담당한다.
 *
 * CSRF: XSRF-TOKEN 쿠키(HttpOnly 아님 — JS가 읽어서 X-XSRF-TOKEN 헤더로 보냄).
 * WebSocket 핸드셰이크는 CSRF 검사에서 제외.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				.authorizeRequests(a -> a.anyRequest().permitAll())
				.csrf(c -> c
						.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
						.ignoringAntMatchers("/ws/**"))
				.formLogin(f -> f.disable())
				.httpBasic(h -> h.disable())
				.logout(l -> l.disable())
				.headers(h -> h
						.frameOptions(f -> f.sameOrigin()));
		return http.build();
	}

	/** 방 입장 비밀번호 해싱용 */
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
