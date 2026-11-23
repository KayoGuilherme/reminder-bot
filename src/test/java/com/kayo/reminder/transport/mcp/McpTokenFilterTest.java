package com.kayo.reminder.transport.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class McpTokenFilterTest {

	private static final String TOKEN = "0123456789abcdef0123456789abcdef";

	private final McpTokenFilter filter = new McpTokenFilter(TOKEN);

	private MockHttpServletRequest request() {
		return new MockHttpServletRequest("POST", "/mcp");
	}

	private final MockFilterChain chain = new MockFilterChain();

	private MockHttpServletResponse call(MockHttpServletRequest request) throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, chain);
		return response;
	}

	private boolean chainCalled() {
		return chain.getRequest() != null;
	}

	@Test
	void aValidTokenPassesToTheChain() throws Exception {
		MockHttpServletRequest request = request();
		request.addHeader("Authorization", "Bearer " + TOKEN);

		MockHttpServletResponse response = call(request);

		assertThat(chainCalled()).isTrue();
		assertThat(response.getStatus()).isEqualTo(200);
	}

	@Test
	void theSchemeIsCaseInsensitive() throws Exception {
		MockHttpServletRequest request = request();
		request.addHeader("Authorization", "bearer " + TOKEN);

		call(request);

		assertThat(chainCalled()).isTrue();
	}

	@Test
	void aMissingHeaderIsRefused() throws Exception {
		MockHttpServletResponse response = call(request());

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
		assertThat(chainCalled()).isFalse();
	}

	@Test
	void anotherSchemeIsRefused() throws Exception {
		MockHttpServletRequest request = request();
		request.addHeader("Authorization", "Basic " + TOKEN);

		MockHttpServletResponse response = call(request);

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(chainCalled()).isFalse();
	}

	@Test
	void aWrongTokenIsRefused() throws Exception {
		MockHttpServletRequest request = request();
		request.addHeader("Authorization", "Bearer " + TOKEN + "x");

		MockHttpServletResponse response = call(request);

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(chainCalled()).isFalse();
	}

	@Test
	void theTokenInTheQueryStringOrACookieIsNotAccepted() throws Exception {
		MockHttpServletRequest request = request();
		request.setParameter("token", TOKEN);
		request.setQueryString("token=" + TOKEN);
		request.setCookies(new Cookie("token", TOKEN));

		MockHttpServletResponse response = call(request);

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(chainCalled()).isFalse();
	}
}
