/*
 * Copyright (C) 2007-2022 Crafter Software Corporation. All Rights Reserved.
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

package org.craftercms.engine.service.context;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the {@link org.craftercms.engine.service.context.SiteContext} to be used for the specified request.
 *
 * @author avasquez
 */
public interface SiteContextResolver {

	SiteContext getContext(HttpServletRequest request);

	/**
	 * Site name resolved from the request, or empty when the request does not identify a site.
	 * Does not fall back to the default site and does not create a context.
	 *
	 * @param request the current request
	 * @return the site name, possibly empty
	 */
	String getSiteName(HttpServletRequest request);

	/**
	 * Destroys the preview context for the site when one is already registered.
	 * Does not create a context.
	 *
	 * @param siteName the site name
	 */
	void destroyContext(String siteName);

}
