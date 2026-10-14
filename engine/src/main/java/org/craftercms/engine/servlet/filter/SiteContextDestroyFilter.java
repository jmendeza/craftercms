/*
 * Copyright (C) 2007-2026 Crafter Software Corporation. All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as published by
 * the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.craftercms.engine.servlet.filter;

import java.beans.ConstructorProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.craftercms.engine.service.context.SiteContextResolver;

/**
 * Handles the preview site-context destroy URL before site-context resolution.
 * Mapped in Engine ahead of {@link SiteContextResolvingFilter} for {@code /api/1/site/context/destroy}
 * and the {@code .json} form the container sees before suffix stripping. It does not
 * call {@link FilterChain#doFilter}. Resolving the context for this URL would create a preview
 * context and hold its access lock, which blocks shutdown.
 */
public class SiteContextDestroyFilter implements Filter {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	protected SiteContextResolver contextResolver;

	protected String configuredToken;

	@ConstructorProperties({"contextResolver", "configuredToken"})
	public SiteContextDestroyFilter(SiteContextResolver contextResolver, String configuredToken) {
		this.contextResolver = contextResolver;
		this.configuredToken = configuredToken;
	}

	@Override
	public void init(FilterConfig filterConfig) {
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		HttpServletRequest httpRequest = (HttpServletRequest) request;
		HttpServletResponse httpResponse = (HttpServletResponse) response;
		String token = httpRequest.getParameter("token");
		if (StringUtils.isBlank(configuredToken) || token == null ||
				!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
						configuredToken.getBytes(StandardCharsets.UTF_8))) {
			httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}
		String siteName = contextResolver.getSiteName(httpRequest);
		if (StringUtils.isEmpty(siteName)) {
			httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		contextResolver.destroyContext(siteName);
		httpResponse.setStatus(HttpServletResponse.SC_OK);
		httpResponse.setContentType("application/json");
		OBJECT_MAPPER.writeValue(httpResponse.getWriter(),
				Collections.singletonMap("message", "Removed site context for '" + siteName + "'"));
	}

	@Override
	public void destroy() {
	}

}
