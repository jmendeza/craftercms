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
package org.craftercms.studio.api.v2.security;

import static org.craftercms.studio.impl.v2.utils.security.SecurityUtils.getCurrentUsername;
import static org.craftercms.studio.permissions.StudioPermissionsConstants.PATH_LIST_RESOURCE_ID;
import static org.craftercms.studio.permissions.StudioPermissionsConstants.SITE_ID_RESOURCE_ID;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import org.craftercms.commons.security.exception.ActionDeniedException;
import org.craftercms.commons.security.permissions.PermissionEvaluator;

/**
 * Utility class for checking permissions.
 */
public class PermissionCheckingUtils {

	/**
	 * Checks if the current user has all the given permissions for the given
	 * secured resource.
	 *
	 * @param permissionEvaluator the permission evaluator
	 * @param securedResource     the secured resource
	 * @param actions             the actions
	 * @return true if the current user has all the given permissions for the given
	 *         secured resource, false otherwise
	 */
	public static boolean hasAllPermissions(PermissionEvaluator permissionEvaluator, Object securedResource,
			String... actions) {
		return Arrays.stream(actions).allMatch(action -> permissionEvaluator.isAllowed(getCurrentUsername(),securedResource, action));
	}

	/**
	 * Checks if the current user has all the given permissions for the given
	 * secured resource. If the current user does not have all the given
	 * permissions,
	 * it throws an ActionDeniedException.
	 *
	 * @param permissionEvaluator the permission evaluator
	 * @param securedResource     the secured resource
	 * @param actions             the actions
	 * @throws ActionDeniedException if the current user does not have all the given
	 *                               permissions
	 */
	public static void checkPermissions(PermissionEvaluator permissionEvaluator, Object securedResource,
			Collection<String> actions) {
		for (String action : actions) {
			if (!permissionEvaluator.isAllowed(getCurrentUsername(), securedResource, action)) {
				throw new ActionDeniedException(action, securedResource);
			}
		}
	}

	/**
	 * Returns the secured resource for the given site id.
	 *
	 * @param siteId the site id
	 * @return the secured resource
	 */
	public static Object getSecuredResource(String siteId) {
		return Map.of(SITE_ID_RESOURCE_ID, siteId);
	}

	/**
	 * Returns the secured resource for the given site id and paths.
	 *
	 * @param siteId the site id
	 * @param paths  the paths
	 * @return the secured resource
	 */
	public static Object getSecuredResource(String siteId, Collection<String> paths) {
		return Map.of(SITE_ID_RESOURCE_ID, siteId, PATH_LIST_RESOURCE_ID, paths);
	}
}
