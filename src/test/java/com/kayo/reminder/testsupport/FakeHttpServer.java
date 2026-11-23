package com.kayo.reminder.testsupport;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FakeHttpServer implements AutoCloseable {

	private final HttpServer server;
	private final Deque<FakeReply> replies = new ArrayDeque<>();
	private final List<FakeRequest> requests = new ArrayList<>();

	public FakeHttpServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			byte[] body = exchange.getRequestBody().readAllBytes();
			Map<String, String> headers = new LinkedHashMap<>();
			exchange.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), String.join(",", v)));
			FakeReply reply;
			synchronized (this) {
				requests.add(new FakeRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), headers,
						body));
				reply = replies.isEmpty() ? new FakeReply(500, "no reply queued", 0) : replies.pollFirst();
			}
			try {
				if (reply.delayMillis() > 0) {
					Thread.sleep(reply.delayMillis());
				}
				byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().add("Content-Type", "application/json");
				exchange.sendResponseHeaders(reply.status(), bytes.length);
				exchange.getResponseBody().write(bytes);
			}
			catch (InterruptedException | IOException ignored) {
			}
			finally {
				exchange.close();
			}
		});
		server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool(r -> {
			Thread t = new Thread(r);
			t.setDaemon(true);
			return t;
		}));
		server.start();
	}

	public String baseUrl() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	public synchronized FakeHttpServer reply(int status, String body) {
		replies.addLast(new FakeReply(status, body, 0));
		return this;
	}

	public synchronized FakeHttpServer replySlowly(int status, String body, long delayMillis) {
		replies.addLast(new FakeReply(status, body, delayMillis));
		return this;
	}

	public synchronized List<FakeRequest> requests() {
		return List.copyOf(requests);
	}

	@Override
	public void close() {
		server.stop(0);
	}
}
