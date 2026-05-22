package com.jacob.pumasi.web;

import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.ModelAndView;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacob.pumasi.model.AppUser;
import com.jacob.pumasi.store.DataStore;

@Controller
public class MainController {

	@Autowired
	private DataStore store;

	@Autowired
	private ObjectMapper mapper;

	@Value("${app.kakao.javascript-key:}")
	private String kakaoJsKey;

	private AppUser currentUser(HttpSession session) {
		String uid = (String) session.getAttribute("uid");
		if (uid == null) {
			return null;
		}
		return store.getUser(uid);
	}

	/** 템플릿이 <script type="application/json"> 안에 박을 안전한 JSON 문자열.
	 * </script 가 값에 들어 있어도 스크립트 종료를 막기 위해 </ 를 이스케이프한다. */
	private String meJson(AppUser u) {
		try {
			String json = mapper.writeValueAsString(u);
			return json.replace("</", "<\\/");
		} catch (JsonProcessingException e) {
			return "null";
		}
	}

	private ModelAndView render(String view, HttpSession session) {
		AppUser u = currentUser(session);
		ModelAndView mv = new ModelAndView(view);
		mv.addObject("user", u);
		mv.addObject("meJson", meJson(u));
		mv.addObject("kakaoJsKey", kakaoJsKey == null ? "" : kakaoJsKey);
		return mv;
	}

	/** 로그인 + setup 완료까지 통과시키는 게이트. 통과 못 하면 리다이렉트 뷰명 반환, 통과면 null. */
	private String authGate(HttpSession session) {
		AppUser u = currentUser(session);
		if (u == null) {
			return "redirect:/login";
		}
		if (!u.isSetupComplete()) {
			return "redirect:/welcome";
		}
		return null;
	}

	@GetMapping("/login")
	public ModelAndView login(HttpSession session) {
		if (session.getAttribute("uid") != null) {
			return new ModelAndView("redirect:/");
		}
		return new ModelAndView("login");
	}

	@GetMapping("/welcome")
	public ModelAndView welcome(HttpSession session) {
		AppUser u = currentUser(session);
		if (u == null) {
			return new ModelAndView("redirect:/login");
		}
		if (u.isSetupComplete()) {
			return new ModelAndView("redirect:/");
		}
		ModelAndView mv = new ModelAndView("welcome");
		mv.addObject("user", u);
		mv.addObject("meJson", meJson(u));
		mv.addObject("kakaoJsKey", kakaoJsKey == null ? "" : kakaoJsKey);
		return mv;
	}

	@GetMapping("/")
	public ModelAndView index(HttpSession session) {
		String redir = authGate(session);
		if (redir != null) {
			return new ModelAndView(redir);
		}
		return render("index", session);
	}

	@GetMapping("/create")
	public ModelAndView create(HttpSession session) {
		String redir = authGate(session);
		if (redir != null) {
			return new ModelAndView(redir);
		}
		return render("create", session);
	}

	@GetMapping("/room/{id}")
	public ModelAndView room(@PathVariable String id, HttpSession session) {
		com.jacob.pumasi.model.Room r = store.getRoom(id);
		if (r == null) {
			return new ModelAndView(session.getAttribute("uid") != null ? "redirect:/" : "redirect:/login");
		}
		AppUser u = currentUser(session);
		// 로그인은 했는데 setup 미완료면 welcome 으로
		if (u != null && !u.isSetupComplete()) {
			return new ModelAndView("redirect:/welcome");
		}
		// 종료된 방은 참여했던 사람만 다시 진입 가능
		if ("ended".equals(r.phase())) {
			if (u == null || !store.isParticipant(id, u.getId())) {
				return new ModelAndView("redirect:/");
			}
		}
		// 비밀번호 잠긴 방 — 참여자도 아니고 세션 인증도 안 됐으면 비번 게이트
		if (r.isLocked()) {
			boolean isParticipant = u != null && store.isParticipant(id, u.getId());
			boolean authed = session.getAttribute("authed_" + id) != null;
			if (!isParticipant && !authed) {
				ModelAndView gate = new ModelAndView("roomlock");
				gate.addObject("roomId", id);
				gate.addObject("roomTitle", r.getTitle());
				return gate;
			}
		}
		ModelAndView mv = render("room", session);
		mv.addObject("roomId", id);
		return mv;
	}

	@GetMapping("/profile")
	public ModelAndView profile(HttpSession session) {
		String redir = authGate(session);
		if (redir != null) {
			return new ModelAndView(redir);
		}
		AppUser u = currentUser(session);
		ModelAndView mv = new ModelAndView("profile");
		mv.addObject("user", u);
		mv.addObject("meJson", meJson(u));
		mv.addObject("kakaoJsKey", kakaoJsKey == null ? "" : kakaoJsKey);
		return mv;
	}

	@GetMapping("/logout")
	public String logout(HttpSession session) {
		session.invalidate();
		return "redirect:/login";
	}

	@GetMapping("/privacy")
	public ModelAndView privacy(HttpSession session) {
		return render("privacy", session);
	}
}
