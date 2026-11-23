package com.kayo.reminder.infrastructure.http;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

public final class RestClients {

	private RestClients() {
	}

	/** A client for {@code baseUrl} that gives up connecting after {@code connect} and waiting after {@code read}. */
	public static RestClient create(String baseUrl, Duration connect, Duration read) {
		HttpClient httpClient = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(connect)
				.build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
		factory.setReadTimeout(read);
		return RestClient.builder().baseUrl(trimTrailingSlash(baseUrl)).requestFactory(factory).build();
	}

	private static String trimTrailingSlash(String url) {
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}
}
