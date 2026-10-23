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

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.craftercms.core.store.ContentStoreManifest.TYPE_FILE;
import static org.craftercms.core.store.ContentStoreManifest.TYPE_LEVEL_DESCRIPTOR;
import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link ContentStoreManifest}.
 */
public class ContentStoreManifestTest {

	@Test
	public void testOfPathsStoresNormalizedFiles() {
		ContentStoreManifest manifest = ContentStoreManifest.ofPaths(List.of(
			"site/website/index.xml",
			"/site/website/crafter-level-descriptor.level.xml",
			"",
			"/site/website/index.xml"
		));

		assertEquals(List.of(
			new ContentStoreManifest.Item("/site/website/crafter-level-descriptor.level.xml", TYPE_LEVEL_DESCRIPTOR),
			new ContentStoreManifest.Item("/site/website/index.xml", TYPE_FILE)
		), manifest.getFiles());
	}

	@Test
	public void testGzipRoundTripKeepsTheFileList() throws Exception {
		ContentStoreManifest manifest = ContentStoreManifest.ofPaths(List.of(
			"/site/website/index.xml",
			"/site/website/crafter-level-descriptor.level.xml"
		));

		ContentStoreManifest restored = ContentStoreManifest.readGzipJson(new ByteArrayInputStream(manifest.toGzipJson()));

		assertEquals(manifest.getFiles(), restored.getFiles());
	}

	@Test
	public void testNormalizesSeparators() {
		ContentStoreManifest manifest = ContentStoreManifest.ofPaths(List.of("site\\website\\index.xml"));

		assertEquals(List.of(new ContentStoreManifest.Item("/site/website/index.xml", TYPE_FILE)), manifest.getFiles());
	}

}
