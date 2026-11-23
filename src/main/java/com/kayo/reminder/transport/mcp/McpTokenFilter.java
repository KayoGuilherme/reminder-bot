package com.kayo.reminder.transport.mcp;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

public class McpTokenFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";
	private static final String DIGEST_ALGORITHM = "SHA-256";

	private static final Logger log = LoggerFactory.getLogger(McpTokenFilter.class);

	private final byte[] expectedDigest;
	private final Map<Boolean, Step> stepByAuthorization = Map.of(true, this::proceed, false, this::refuse);

	public McpTokenFilter(String token) {
		this.expectedDigest = digest(token);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		stepByAuthorization.get(authorized(request)).run(request, response, chain);
	}

	private boolean authorized(HttpServletRequest request) {
		return Optional.ofNullable(request.getHeader(HttpHeaders.AUTHORIZATION))
				.filter(header -> header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length()))
				.map(header -> header.substring(BEARER_PREFIX.length()))
				.map(McpTokenFilter::digest)
				.filter(candidate -> MessageDigest.isEqual(candidate, expectedDigest))
				.isPresent();
	}

	private void proceed(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		chain.doFilter(request, response);
	}

	private void refuse(HttpServletRequest request, HttpServletResponse response, FilterChain chain) {
		log.warn("Refused an MCP request without a valid token");
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
	}

	private static byte[] digest(String value) {
		try {
			return MessageDigest.getInstance(DIGEST_ALGORITHM).digest(value.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException unavailable) {
			throw new IllegalStateException(DIGEST_ALGORITHM + " is not available", unavailable);
		}
	}

	@FunctionalInterface
	private interface Step {

		void run(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
				throws ServletException, IOException;
	}
}
