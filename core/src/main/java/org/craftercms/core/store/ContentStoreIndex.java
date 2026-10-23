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
package org.craftercms.core.store;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

/**
 * Directory index built from a {@link ContentStoreManifest}.
 * <p>
 * Each file path is split into its parent directories. Those directories are recorded in the type map and in the
 * children map so existence and children lookups do not need to list the bucket.
 */
public class ContentStoreIndex {

	public static final String TYPE_DIRECTORY = "directory";

	public static final String ROOT_PATH = "/";

	private final Map<String, String> types;

	private final Map<String, List<String>> children;

	private ContentStoreIndex(Map<String, String> types, Map<String, List<String>> children) {
		this.types = types;
		this.children = children;
	}

	/**
	 * Splits the manifest's file paths into the children and type maps.
	 * An empty or directory type on an item is replaced with {@link ContentStoreManifest#typeFor(String)}.
	 *
	 * @param manifest the flat file list read from S3, or null
	 * @return the directory index
	 */
	public static ContentStoreIndex from(ContentStoreManifest manifest) {
		Map<String, String> types = new HashMap<>();
		Map<String, TreeSet<String>> childNames = new HashMap<>();

		if (manifest != null && manifest.getFiles() != null) {
			for (ContentStoreManifest.Item item : manifest.getFiles()) {
				if (item == null || isEmpty(item.getPath())) {
					continue;
				}
				String path = ContentStoreManifest.normalize(item.getPath());
				String type = item.getType();
				if (isEmpty(type) || TYPE_DIRECTORY.equals(type)) {
					type = ContentStoreManifest.typeFor(path);
				}
				addFile(types, childNames, path, type);
			}
		}

		Map<String, List<String>> children = new HashMap<>();
		childNames.forEach((directory, names) -> children.put(directory, List.copyOf(names)));
		return new ContentStoreIndex(types, children);
	}

	/**
	 * @param path a content-store path
	 * @return the entry type, or null when the path is not in the index
	 */
	public String typeOf(String path) {
		return types.get(ContentStoreManifest.normalize(path));
	}

	/**
	 * @param path a content-store path
	 * @return true when the path is a directory derived from a published file
	 */
	public boolean isDirectory(String path) {
		return TYPE_DIRECTORY.equals(typeOf(path));
	}

	/**
	 * @param path a directory path
	 * @return the direct child names, sorted. Empty when the directory is not in the index.
	 */
	public List<String> childrenOf(String path) {
		List<String> names = children.get(ContentStoreManifest.normalize(path));
		return names == null ? List.of() : names;
	}

	/**
	 * @return an unmodifiable view of path to type, including derived directories
	 */
	public Map<String, String> types() {
		return Collections.unmodifiableMap(types);
	}

	private static void addFile(Map<String, String> types, Map<String, TreeSet<String>> childNames,
				    String path, String type) {
		types.put(path, type);

		String parent = parentOf(path);
		String name = nameOf(path);
		while (isNotEmpty(name)) {
			childNames.computeIfAbsent(parent, key -> new TreeSet<>()).add(name);
			types.putIfAbsent(parent, TYPE_DIRECTORY);
			if (ROOT_PATH.equals(parent)) {
				break;
			}
			name = nameOf(parent);
			parent = parentOf(parent);
		}
	}

	private static String parentOf(String path) {
		if (ROOT_PATH.equals(path)) {
			return ROOT_PATH;
		}
		int slash = path.lastIndexOf('/');
		if (slash <= 0) {
			return ROOT_PATH;
		}
		return path.substring(0, slash);
	}

	private static String nameOf(String path) {
		if (ROOT_PATH.equals(path)) {
			return "";
		}
		int slash = path.lastIndexOf('/');
		return slash >= 0 ? path.substring(slash + 1) : path;
	}

}
