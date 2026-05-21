package com.jacob.pumasi.socket;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArraySet;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 실시간 브로드캐스트. 데이터 변경 시 연결된 모든 클라이언트에 알림을 보내고,
 * 클라이언트는 REST로 최신 상태를 다시 가져온다. (채팅 아님 — 신호만 전달)
 */
@Component
public class RealtimeHandler extends TextWebSocketHandler {

	private final CopyOnWriteArraySet<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		sessions.add(session);
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		sessions.remove(session);
	}

	/** scope: "rooms" | "participants", roomId optional */
	public void broadcast(String scope, String roomId) {
		String payload = "{\"scope\":\"" + scope + "\",\"roomId\":\""
				+ (roomId == null ? "" : roomId) + "\"}";
		TextMessage msg = new TextMessage(payload);
		for (WebSocketSession s : sessions) {
			try {
				if (s.isOpen()) {
					s.sendMessage(msg);
				}
			} catch (IOException ignored) {
				sessions.remove(s);
			}
		}
	}
}
