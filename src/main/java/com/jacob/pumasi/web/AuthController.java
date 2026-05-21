package com.jacob.pumasi.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.jacob.pumasi.model.AppUser;
import com.jacob.pumasi.store.DataStore;

/**
 * 카카오 OAuth 흐름. rest-api-key 가 비어 있으면 dev mock 폴백.
 */
@Controller
public class AuthController {

	private static final Logger log = LoggerFactory.getLogger(AuthController.class);

	private static final String AUTHORIZE_URL = "https://kauth.kakao.com/oauth/authorize";
	private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
	private static final String USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";

	private final DataStore store;
	private final RestTemplate http = new RestTemplate();

	@Value("${app.kakao.rest-api-key:}")
	private String restKey;

	@Value("${app.kakao.redirect-uri:}")
	private String redirectUri;

	/** Kakao Developers → 보안 → Client Secret 이 "사용함" 일 때만 채움 */
	@Value("${app.kakao.client-secret:}")
	private String clientSecret;

	public AuthController(DataStore store) {
		this.store = store;
	}

	@GetMapping("/auth/kakao")
	public String start(
			@RequestParam(required = false, defaultValue = "/") String next,
			HttpSession session) {
		if (session.getAttribute("uid") != null) {
			return "redirect:/";
		}
		// dev 폴백: 키 미설정 시 즉시 가짜 유저 만들고 로그인 (verification 환경용)
		if (restKey == null || restKey.isEmpty()) {
			return mockLogin(next, session);
		}
		if (next != null && next.startsWith("/")) {
			session.setAttribute("auth_next", next);
		}
		String url = AUTHORIZE_URL
				+ "?response_type=code"
				+ "&client_id=" + enc(restKey)
				+ "&redirect_uri=" + enc(redirectUri);
		return "redirect:" + url;
	}

	@GetMapping("/auth/kakao/callback")
	public String callback(
			@RequestParam(required = false) String code,
			@RequestParam(required = false) String error,
			HttpSession session) {
		if (error != null) {
			log.warn("Kakao OAuth error from authorize: {}", error);
			return "redirect:/login?err=" + enc(error);
		}
		if (code == null || code.isEmpty()) {
			return "redirect:/login?err=no_code";
		}
		try {
			// 1) 인가 코드 → 액세스 토큰
			MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
			form.add("grant_type", "authorization_code");
			form.add("client_id", restKey);
			form.add("redirect_uri", redirectUri);
			form.add("code", code);
			if (clientSecret != null && !clientSecret.isEmpty()) {
				form.add("client_secret", clientSecret);
			}
			// 진단용 — 키는 앞4/뒤4만 마스킹해서 노출
			String maskedKey = restKey == null ? "null"
					: restKey.length() < 8 ? "***short***"
					: restKey.substring(0, 4) + "..." + restKey.substring(restKey.length() - 4);
			log.info("Kakao token request: client_id={} (len={}), redirect_uri={}, code_len={}, has_secret={}",
					maskedKey,
					restKey == null ? 0 : restKey.length(),
					redirectUri,
					code.length(),
					clientSecret != null && !clientSecret.isEmpty());
			HttpHeaders tokenHeaders = new HttpHeaders();
			tokenHeaders.setContentType(new MediaType("application", "x-www-form-urlencoded", java.nio.charset.StandardCharsets.UTF_8));
			tokenHeaders.setAccept(java.util.Arrays.asList(MediaType.APPLICATION_JSON));
			// 일부 서버가 Java 기본 UA 를 봇으로 보고 빈 응답 주는 케이스 방어
			tokenHeaders.add("User-Agent", "pumasi/1.0 (java)");
			@SuppressWarnings("rawtypes")
			ResponseEntity<Map> tokenRes = http.postForEntity(
					TOKEN_URL,
					new HttpEntity<>(form, tokenHeaders),
					Map.class);
			Object accessTokenObj = tokenRes.getBody() == null ? null : tokenRes.getBody().get("access_token");
			if (accessTokenObj == null) {
				log.warn("Kakao token response missing access_token");
				return "redirect:/login?err=token_failed";
			}
			String accessToken = String.valueOf(accessTokenObj);

			// 2) 액세스 토큰 → 사용자 프로필
			HttpHeaders profileHeaders = new HttpHeaders();
			profileHeaders.setBearerAuth(accessToken);
			@SuppressWarnings("rawtypes")
			ResponseEntity<Map> meRes = http.exchange(
					USER_INFO_URL,
					HttpMethod.GET,
					new HttpEntity<>(profileHeaders),
					Map.class);
			@SuppressWarnings("rawtypes")
			Map body = meRes.getBody();
			if (body == null || body.get("id") == null) {
				log.warn("Kakao user info missing id");
				return "redirect:/login?err=profile_failed";
			}
			String kakaoId = String.valueOf(body.get("id"));
			String kakaoNick = "사용자";
			Object accountObj = body.get("kakao_account");
			if (accountObj instanceof Map) {
				Object profileObj = ((Map<?, ?>) accountObj).get("profile");
				if (profileObj instanceof Map) {
					Object n = ((Map<?, ?>) profileObj).get("nickname");
					if (n != null) {
						String s = String.valueOf(n).trim();
						if (!s.isEmpty()) {
							// 14자 제한에 맞춰 자르기 (DB 컬럼 64 이지만 닉네임 제약 유지)
							kakaoNick = s.length() > 14 ? s.substring(0, 14) : s;
						}
					}
					// 프로필 이미지는 가져오지 않음 — welcome 단계에서 동물 이모지로 선택
				}
			}

			// 3) 유저 매칭/생성
			AppUser user = store.findByKakaoId(kakaoId).orElse(null);
			if (user == null) {
				// avatar 는 welcome 에서 정함. 일단 null
				user = new AppUser(DataStore.id("u"), kakaoNick, null, 0, 0);
				user.setKakaoId(kakaoId);
				user.setSetupComplete(false); // → /welcome 으로 보냄
				user = store.upsertUser(user);
			}
			// 기존 유저는 닉네임/아바타 모두 본인 설정값을 존중 — 카카오에서 덮어쓰지 않음
			session.setAttribute("uid", user.getId());

			if (!user.isSetupComplete()) {
				return "redirect:/welcome";
			}
			String next = (String) session.getAttribute("auth_next");
			session.removeAttribute("auth_next");
			return "redirect:" + (next != null ? next : "/");

		} catch (HttpStatusCodeException e) {
			// 카카오가 명시적 오류 응답을 줬을 때 — body + 헤더까지 같이 로그
			log.error("Kakao OAuth HTTP error: status={}, headers={}, body=[{}]",
					e.getStatusCode(), e.getResponseHeaders(), e.getResponseBodyAsString());
			return "redirect:/login?err=oauth_failed";
		} catch (Exception e) {
			log.error("Kakao OAuth callback failed", e);
			return "redirect:/login?err=oauth_failed";
		}
	}

	/** rest-api-key 미설정 시 dev 폴백 — 자동으로 가짜 유저 생성, welcome 단계도 그대로 거침 */
	private String mockLogin(String next, HttpSession session) {
		String[] names = { "하늘", "바다", "별이", "구름", "노을", "단비", "햇살" };
		String name = names[(int) (Math.random() * names.length)] + "님";
		AppUser u = new AppUser(DataStore.id("u"), name, null, 0, 0);
		u.setSetupComplete(false); // dev 환경에서도 /welcome 흐름 테스트 가능
		store.upsertUser(u);
		session.setAttribute("uid", u.getId());
		return "redirect:" + (next != null && next.startsWith("/") ? next : "/welcome");
	}

	private static String enc(String s) {
		try {
			return URLEncoder.encode(s, StandardCharsets.UTF_8.name());
		} catch (Exception e) {
			return s;
		}
	}
}
