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
package org.craftercms.deployer.impl.processors.aws;

import org.craftercms.core.store.ContentStoreManifest;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link S3ContentStoreManifestProcessor#listPublishedFiles(Path)}.
 */
public class S3ContentStoreManifestProcessorTest {

	@Test
	public void testListPublishedFilesSkipsGitAndBlobs() throws Exception {
		Path repo = Files.createTempDirectory("manifest-repo");
		Files.createDirectories(repo.resolve(".git"));
		Files.writeString(repo.resolve(".git/config"), "git");
		Files.createDirectories(repo.resolve("site/website"));
		Files.writeString(repo.resolve("site/website/index.xml"), "<page/>");
		Files.writeString(repo.resolve("site/website/crafter-level-descriptor.level.xml"), "<level/>");
		Files.writeString(repo.resolve("site/website/hero.png.blob"), "blob");

		S3ContentStoreManifestProcessor processor =
			new S3ContentStoreManifestProcessor(null, repo.toString(), ".blob");
		processor.ignoreBlobs = true;

		List<String> paths = processor.listPublishedFiles(repo);

		assertTrue(paths.contains("/site/website/index.xml"));
		assertTrue(paths.contains("/site/website/crafter-level-descriptor.level.xml"));
		assertFalse(paths.contains("/.git/config"));
		assertFalse(paths.stream().anyMatch(path -> path.endsWith(".blob")));
		assertEquals(ContentStoreManifest.TYPE_LEVEL_DESCRIPTOR,
			ContentStoreManifest.typeFor("/site/website/crafter-level-descriptor.level.xml"));
	}

}
