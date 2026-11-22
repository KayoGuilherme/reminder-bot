package com.kayo.reminder.infrastructure.telegram;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.TelegramUrl;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/** A local HTTP server standing in for api.telegram.org: replies with queued answers and records requests. */
final class FakeTelegramApi implements AutoCloseable {

	static final String TOKEN = "SECRET-TOKEN-123";

	record Reply(int status, String body, boolean repeat) {
	}

	record Request(String path, String body) {
	}

	private final HttpServer server;
	private final Deque<Reply> replies = new ArrayDeque<>();
	private final List<Request> requests = new ArrayList<>();

	FakeTelegramApi() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			Reply reply;
			synchronized (this) {
				requests.add(new Request(exchange.getRequestURI().getPath(), body));
				reply = replies.isEmpty() ? new Reply(500, "no reply queued", false)
						: replies.peekFirst().repeat() ? replies.peekFirst() : replies.pollFirst();
			}
			byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(reply.status(), bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		server.start();
	}

	TelegramClient client() {
		TelegramUrl url = TelegramUrl.builder().schema("http").host("127.0.0.1")
				.port(server.getAddress().getPort()).build();
		return new OkHttpTelegramClient(TOKEN, url);
	}

	synchronized FakeTelegramApi reply(int status, String body) {
		replies.addLast(new Reply(status, body, false));
		return this;
	}

	synchronized FakeTelegramApi replyAlways(int status, String body) {
		replies.addLast(new Reply(status, body, true));
		return this;
	}

	FakeTelegramApi sentMessage(int messageId) {
		return reply(200, "{\"ok\":true,\"result\":{\"message_id\":" + messageId
				+ ",\"date\":1,\"chat\":{\"id\":5,\"type\":\"private\"}}}");
	}

	FakeTelegramApi ok() {
		return reply(200, "{\"ok\":true,\"result\":true}");
	}

	FakeTelegramApi error(int code, String description) {
		return reply(code, errorBody(code, description, null));
	}

	FakeTelegramApi rateLimited(int retryAfter) {
		return reply(429, errorBody(429, "Too Many Requests: retry after " + retryAfter, retryAfter));
	}

	FakeTelegramApi errorAlways(int code, String description) {
		return replyAlways(code, errorBody(code, description, null));
	}

	private static String errorBody(int code, String description, Integer retryAfter) {
		return "{\"ok\":false,\"error_code\":" + code + ",\"description\":\"" + description + "\""
				+ (retryAfter != null ? ",\"parameters\":{\"retry_after\":" + retryAfter + "}" : "") + "}";
	}

	synchronized List<Request> requests() {
		return List.copyOf(requests);
	}

	void stop() {
		server.stop(0);
	}

	@Override
	public void close() {
		stop();
	}
}
