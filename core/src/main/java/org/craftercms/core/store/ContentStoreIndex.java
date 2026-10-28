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
 * children map so existence and children lookups do not need to list the bucket. Every listed path is a file,
 * and every parent path is a directory.
 */
public class ContentStoreIndex {

	public static final String TYPE_FILE = "file";

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
	 * Every path is a file. Parent directories are derived from those paths.
	 *
	 * @param manifest the flat file list read from S3, or null
	 * @return the directory index
	 */
	public static ContentStoreIndex from(ContentStoreManifest manifest) {
		List<ContentStoreManifest.Item> files = manifest == null || manifest.getFiles() == null
			? List.of() : manifest.getFiles();
		// Each file also records its parent directories. Four entries per file covers a typical
		// /site/website/... path, including shared parents, without resizing at large manifests.
		int capacity = files.size() > Integer.MAX_VALUE / 4 ? Integer.MAX_VALUE : files.size() * 4;
		Map<String, String> types = HashMap.newHashMap(capacity);
		Map<String, TreeSet<String>> childNames = HashMap.newHashMap(capacity);

		for (ContentStoreManifest.Item item : files) {
			if (item == null || isEmpty(item.getPath())) {
				continue;
			}
			addFile(types, childNames, ContentStoreManifest.normalize(item.getPath()));
		}

		Map<String, List<String>> children = HashMap.newHashMap(childNames.size());
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

	private static void addFile(Map<String, String> types, Map<String, TreeSet<String>> childNames, String path) {
		types.put(path, TYPE_FILE);

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
