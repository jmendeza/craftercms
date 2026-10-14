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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.craftercms.engine.service.context.SiteContextResolver;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SiteContextDestroyFilterTest {

	private static final String TOKEN = "management-token";

	private static final String SITE_NAME = "editorial";

	@Mock
	private SiteContextResolver contextResolver;

	@Mock
	private FilterChain chain;

	private SiteContextDestroyFilter filter;

	@Before
	public void setUp() {
		MockitoAnnotations.openMocks(this);
		filter = new SiteContextDestroyFilter(contextResolver, TOKEN);
	}

	@Test
	public void rejectsARequestWithTheWrongToken() throws IOException, ServletException {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setParameter("token", "wrong");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, chain);

		assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
		verify(contextResolver, never()).destroyContext(any());
		verify(chain, never()).doFilter(any(), any());
	}

	@Test
	public void rejectsATokenlessRequestWhenNoTokenIsConfigured() throws IOException, ServletException {
		filter = new SiteContextDestroyFilter(contextResolver, null);
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, chain);

		assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
		verify(contextResolver, never()).destroyContext(any());
		verify(chain, never()).doFilter(any(), any());
	}

	@Test
	public void rejectsARequestWithNoSiteName() throws IOException, ServletException {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setParameter("token", TOKEN);
		MockHttpServletResponse response = new MockHttpServletResponse();
		when(contextResolver.getSiteName(request)).thenReturn("");

		filter.doFilter(request, response, chain);

		assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.getStatus());
		verify(contextResolver, never()).destroyContext(any());
		verify(chain, never()).doFilter(any(), any());
	}

	@Test
	public void destroysTheSiteContextAndCompletesTheResponse() throws IOException, ServletException {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setParameter("token", TOKEN);
		MockHttpServletResponse response = new MockHttpServletResponse();
		when(contextResolver.getSiteName(request)).thenReturn(SITE_NAME);

		filter.doFilter(request, response, chain);

		assertEquals(HttpServletResponse.SC_OK, response.getStatus());
		assertEquals("application/json", response.getContentType());
		assertEquals("{\"message\":\"Removed site context for 'editorial'\"}", response.getContentAsString());
		verify(contextResolver).destroyContext(SITE_NAME);
		verify(chain, never()).doFilter(any(), any());
	}

}
