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

import org.apache.commons.io.FilenameUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

/**
 * Directory index built from a {@link ContentStoreManifest}.
 * <p>
 * Each file path is split into its parent directories. Each path is stored once, with its type and direct child
 * names, so existence and children lookups do not need to list the bucket. Every listed path is a file, and every
 * parent path is a directory. A published file is never the parent of another file.
 */
public class ContentStoreIndex {

	public static final String TYPE_FILE = "file";

	public static final String TYPE_DIRECTORY = "directory";

	public static final String ROOT_PATH = "/";

	private final Map<String, Entry> entries;

	private ContentStoreIndex(Map<String, Entry> entries) {
		this.entries = entries;
	}

	/**
	 * Splits the manifest's file paths into one path map.
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
		Map<String, MutableEntry> building = HashMap.newHashMap(capacity);

		for (ContentStoreManifest.Item item : files) {
			if (item == null || isEmpty(item.getPath())) {
				continue;
			}
			addFile(building, ContentStoreManifest.normalize(item.getPath()));
		}

		Map<String, Entry> entries = HashMap.newHashMap(building.size());
		building.forEach((path, node) -> entries.put(path, node.toEntry()));
		return new ContentStoreIndex(entries);
	}

	/**
	 * @param path a content-store path
	 * @return the entry type, or null when the path is not in the index
	 */
	public String typeOf(String path) {
		Entry entry = entryOf(path);
		if (entry == null) {
			return null;
		}
		return entry.directory ? TYPE_DIRECTORY : TYPE_FILE;
	}

	/**
	 * @param path a content-store path
	 * @return true when the path is a directory derived from a published file
	 */
	public boolean isDirectory(String path) {
		Entry entry = entryOf(path);
		return entry != null && entry.directory;
	}

	/**
	 * @param path a directory path
	 * @return the direct child names, sorted. Empty when the directory is not in the index.
	 */
	public List<String> childrenOf(String path) {
		Entry entry = entryOf(path);
		return entry == null ? List.of() : entry.children;
	}

	private Entry entryOf(String path) {
		return entries.get(ContentStoreManifest.normalize(path));
	}

	private static void addFile(Map<String, MutableEntry> entries, String path) {
		entries.computeIfAbsent(path, key -> MutableEntry.file());

		String parent = FilenameUtils.getFullPathNoEndSeparator(path);
		String name = FilenameUtils.getName(path);
		while (isNotEmpty(name)) {
			entries.computeIfAbsent(parent, key -> MutableEntry.directory()).addChild(name);
			if (ROOT_PATH.equals(parent)) {
				break;
			}
			name = FilenameUtils.getName(parent);
			parent = FilenameUtils.getFullPathNoEndSeparator(parent);
		}
	}

	/**
	 * One indexed path. A file has an empty child list.
	 */
	private static final class Entry {

		private final boolean directory;

		private final List<String> children;

		private Entry(boolean directory, List<String> children) {
			this.directory = directory;
			this.children = children;
		}

	}

	/**
	 * Path accumulated while the manifest is read.
	 */
	private static final class MutableEntry {

		private final boolean file;

		private TreeSet<String> childNames;

		private MutableEntry(boolean file) {
			this.file = file;
		}

		private static MutableEntry file() {
			return new MutableEntry(true);
		}

		private static MutableEntry directory() {
			return new MutableEntry(false);
		}

		private void addChild(String name) {
			if (childNames == null) {
				childNames = new TreeSet<>();
			}
			childNames.add(name);
		}

		private Entry toEntry() {
			List<String> names = childNames == null ? List.of() : List.copyOf(childNames);
			return new Entry(!file, names);
		}

	}

}
