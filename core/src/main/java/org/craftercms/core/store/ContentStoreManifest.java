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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.craftercms.core.util.ContentStoreUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.apache.commons.lang3.StringUtils.isEmpty;

/**
 * Flat list of files published to S3.
 * <p>
 * Deployer writes this document and Engine reads the same list back. The on-disk form is gzip-compressed JSON:
 * <pre>
 * { "files": [ { "path": "/site/website/index.xml", "type": "file" }, { "path": "/site/website/crafter-level-descriptor.level.xml", "type": "levelDescriptor" } ] }
 * </pre>
 * Directories are not stored. {@link ContentStoreIndex} splits these paths into the children and type maps.
 * Extra JSON properties are ignored so a newer document still loads on an older Engine.
 */
public class ContentStoreManifest {

	public static final String FILE_NAME = "content-store-manifest.json.gz";

	public static final String TYPE_FILE = "file";

	public static final String TYPE_LEVEL_DESCRIPTOR = "levelDescriptor";

	public static final String LEVEL_DESCRIPTOR_FILE_NAME = "crafter-level-descriptor.level.xml";

	private static final ObjectMapper MAPPER = new ObjectMapper()
		.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

	private List<Item> files = List.of();

	/**
	 * Used by Jackson when reading the manifest document.
	 */
	public ContentStoreManifest() {
	}

	private ContentStoreManifest(List<Item> files) {
		this.files = files;
	}

	/**
	 * One published file.
	 */
	public static class Item {

		private String path;

		private String type;

		/**
		 * Used by Jackson when reading a file entry.
		 */
		public Item() {
		}

		/**
		 * @param path store path, with a leading slash and no trailing slash
		 * @param type {@link ContentStoreManifest#TYPE_FILE} or {@link ContentStoreManifest#TYPE_LEVEL_DESCRIPTOR}
		 */
		public Item(String path, String type) {
			this.path = path;
			this.type = type;
		}

		/**
		 * @return the store path
		 */
		public String getPath() {
			return path;
		}

		/**
		 * @param path the store path
		 */
		public void setPath(String path) {
			this.path = path;
		}

		/**
		 * @return {@link ContentStoreManifest#TYPE_FILE} or {@link ContentStoreManifest#TYPE_LEVEL_DESCRIPTOR}
		 */
		public String getType() {
			return type;
		}

		/**
		 * @param type the file type
		 */
		public void setType(String type) {
			this.type = type;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (!(obj instanceof Item other)) {
				return false;
			}
			return Objects.equals(path, other.path) && Objects.equals(type, other.type);
		}

		@Override
		public int hashCode() {
			return Objects.hash(path, type);
		}

	}

	/**
	 * @param path a content-store path
	 * @return {@link #TYPE_LEVEL_DESCRIPTOR} when the file is a level descriptor, otherwise {@link #TYPE_FILE}
	 */
	public static String typeFor(String path) {
		String normalized = normalize(path);
		int slash = normalized.lastIndexOf('/');
		String name = slash >= 0 ? normalized.substring(slash + 1) : normalized;
		if (LEVEL_DESCRIPTOR_FILE_NAME.equals(name)) {
			return TYPE_LEVEL_DESCRIPTOR;
		}
		return TYPE_FILE;
	}

	/**
	 * Builds the file list Deployer uploads. Each path is stored with {@link #typeFor(String)}.
	 * Duplicate paths keep the last occurrence.
	 *
	 * @param paths repo paths of the files uploaded to S3
	 * @return the manifest document
	 */
	public static ContentStoreManifest ofPaths(Collection<String> paths) {
		TreeMap<String, Item> byPath = new TreeMap<>();
		if (paths != null) {
			for (String path : paths) {
				if (isEmpty(path)) {
					continue;
				}
				String normalized = normalize(path);
				byPath.put(normalized, new Item(normalized, typeFor(normalized)));
			}
		}
		return new ContentStoreManifest(List.copyOf(byPath.values()));
	}

	/**
	 * Builds a manifest from an existing file list. Empty paths are skipped.
	 *
	 * @param items published files
	 * @return the manifest document
	 */
	public static ContentStoreManifest ofItems(Collection<Item> items) {
		List<Item> files = new ArrayList<>();
		if (items != null) {
			for (Item item : items) {
				if (item == null || isEmpty(item.getPath())) {
					continue;
				}
				files.add(new Item(normalize(item.getPath()), item.getType()));
			}
		}
		return new ContentStoreManifest(List.copyOf(files));
	}

	/**
	 * Reads a gzip-compressed manifest document.
	 *
	 * @param in the gzip payload
	 * @return the file list stored in the document
	 * @throws IOException if the payload cannot be read
	 */
	public static ContentStoreManifest readGzipJson(InputStream in) throws IOException {
		try (GZIPInputStream gzip = new GZIPInputStream(in)) {
			return MAPPER.readValue(gzip, ContentStoreManifest.class);
		}
	}

	/**
	 * Writes this manifest as gzip-compressed JSON.
	 *
	 * @return the gzip payload
	 * @throws IOException if the payload cannot be written
	 */
	public byte[] toGzipJson() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
			MAPPER.writeValue(gzip, this);
		}
		return out.toByteArray();
	}

	/**
	 * @return the published files, in document order
	 */
	public List<Item> getFiles() {
		return files;
	}

	/**
	 * @param files the published files
	 */
	public void setFiles(List<Item> files) {
		this.files = files == null ? List.of() : files;
	}

	/**
	 * Normalizes a repo or store path to a leading-slash path without a trailing slash.
	 */
	public static String normalize(String path) {
		String normalized = path.replace('\\', '/').replaceAll("/+", "/");
		return ContentStoreUtils.normalizePath(normalized);
	}

}
