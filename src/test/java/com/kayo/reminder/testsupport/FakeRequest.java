package com.kayo.reminder.testsupport;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.Value;

@Value
public class FakeRequest {

	String method;
	String path;
	Map<String, String> headers;
	byte[] body;

	public String bodyText() {
		return new String(body, StandardCharsets.ISO_8859_1);
	}

	public String header(String name) {
		return headers.get(name.toLowerCase());
	}
}
