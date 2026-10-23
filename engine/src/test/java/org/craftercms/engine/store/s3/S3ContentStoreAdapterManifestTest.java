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
package org.craftercms.engine.store.s3;

import org.craftercms.core.exception.StoreException;
import org.craftercms.core.store.ContentStoreIndex;
import org.craftercms.core.store.ContentStoreManifest;
import org.craftercms.core.store.impl.File;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Uri;
import software.amazon.awssdk.services.s3.S3Utilities;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests that a loaded content store manifest answers folder lookups without listing S3.
 */
@RunWith(MockitoJUnitRunner.class)
public class S3ContentStoreAdapterManifestTest {

	@Mock
	private S3Client client;

	private S3ContentStoreAdapter adapter;
	private S3Context context;

	@Before
	public void setUp() throws Exception {
		adapter = new S3ContentStoreAdapter(null, ".xml", ".meta.xml", null, null, 1024, new String[0]);
		adapter.client = client;

		S3Utilities utilities = S3Utilities.builder().region(Region.US_EAST_1).build();
		S3Uri uri = utilities.parseUri(URI.create("s3://test-bucket/sites/acme"));
		context = new S3Context("id", adapter, "s3://test-bucket/sites/acme", false, true, 1000,
			true, uri, null);
	}

	@Test
	public void testManifestAnswersFolderAndChildrenWithoutListing() {
		context.setContentStoreIndex(ContentStoreIndex.from(ContentStoreManifest.ofPaths(List.of(
			"/site/website/index.xml",
			"/site/website/crafter-level-descriptor.level.xml",
			"/site/website/products/index.xml",
			"/site/website/.hidden.txt"
		))));

		File folder = adapter.doFindFile(context, "/site/website");
		File missing = adapter.doFindFile(context, "/site/missing");
		List<File> children = adapter.doGetChildren(context, folder);

		assertNotNull(folder);
		assertTrue(folder.isDirectory());
		assertNull(missing);
		assertEquals(List.of("crafter-level-descriptor.level.xml", "index.xml", "products"),
			children.stream().map(File::getName).toList());
		assertTrue(adapter.validate(context));
		verify(client, never()).listObjectsV2(any(ListObjectsV2Request.class));
		verify(client, never()).listObjectsV2Paginator(any(ListObjectsV2Request.class));
	}

	@Test
	public void testManifestMissDoesNotList() {
		context.setContentStoreIndex(ContentStoreIndex.from(ContentStoreManifest.ofPaths(List.of("/site/website/index.xml"))));
		when(client.getObject(any(GetObjectRequest.class))).thenThrow(NoSuchKeyException.builder().message("missing").build());

		assertNull(adapter.doFindFile(context, "/site/website/index.xml"));
		verify(client, never()).listObjectsV2(any(ListObjectsV2Request.class));
	}

	@Test
	public void testDeploymentEventsFileIsReadFromS3WhenMissingFromIndex() {
		context.setContentStoreIndex(ContentStoreIndex.from(ContentStoreManifest.ofPaths(List.of("/site/website/index.xml"))));
		when(client.getObject(any(GetObjectRequest.class))).thenThrow(NoSuchKeyException.builder().message("missing").build());

		assertNull(adapter.doFindFile(context, "/deployment-events.properties"));

		verify(client).getObject(argThat((GetObjectRequest request) ->
			"test-bucket".equals(request.bucket()) && "sites/acme/deployment-events.properties".equals(request.key())));
		verify(client, never()).listObjectsV2(any(ListObjectsV2Request.class));
	}

	@Test
	public void testConfiguredDeploymentEventsFileIsReadFromS3() {
		adapter.setDeploymentEventsFileUrl("signals/events.properties");
		context.setContentStoreIndex(ContentStoreIndex.from(ContentStoreManifest.ofPaths(List.of("/site/website/index.xml"))));
		when(client.getObject(any(GetObjectRequest.class))).thenThrow(NoSuchKeyException.builder().message("missing").build());

		assertNull(adapter.doFindFile(context, "/other.properties"));
		verify(client, never()).getObject(any(GetObjectRequest.class));

		assertNull(adapter.doFindFile(context, "/signals/events.properties"));
		verify(client).getObject(argThat((GetObjectRequest request) ->
			"sites/acme/signals/events.properties".equals(request.key())));
	}

	@Test
	public void testMissingManifestFallsBackToList() {
		when(client.getObject(any(GetObjectRequest.class))).thenThrow(NoSuchKeyException.builder().message("missing").build());
		when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
			.contents(software.amazon.awssdk.services.s3.model.S3Object.builder().key("sites/acme/site/").build())
			.build());

		adapter.reloadContentStoreManifest(context);
		File folder = adapter.doFindFile(context, "/site");

		assertNotNull(folder);
		assertTrue(folder.isDirectory());
		verify(client).listObjectsV2(any(ListObjectsV2Request.class));
	}

	@Test
	public void testManifestLoadFailureClearsIndexAndRethrows() {
		context.setContentStoreIndex(ContentStoreIndex.from(ContentStoreManifest.ofPaths(List.of("/site/website/index.xml"))));
		when(client.getObject(any(GetObjectRequest.class)))
			.thenThrow(S3Exception.builder().statusCode(500).message("unavailable").build());
		when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
			.contents(software.amazon.awssdk.services.s3.model.S3Object.builder().key("sites/acme/site/").build())
			.build());

		try {
			adapter.reloadContentStoreManifest(context);
			fail("Expected StoreException");
		} catch (StoreException expected) {
			assertNull(context.getContentStoreIndex());
			assertTrue(context.isContentStoreIndexResolved());
			assertTrue(context.isContentStoreManifestLoadFailed());
			assertTrue(S3ContentStoreAdapter.isContentStoreManifestLoadFailed(context));
		}

		File folder = adapter.doFindFile(context, "/site");
		assertNotNull(folder);
		assertTrue(folder.isDirectory());
		verify(client).listObjectsV2(any(ListObjectsV2Request.class));

		when(client.getObject(any(GetObjectRequest.class))).thenThrow(NoSuchKeyException.builder().message("missing").build());
		adapter.reloadContentStoreManifest(context);
		assertNull(context.getContentStoreIndex());
		assertFalse(context.isContentStoreManifestLoadFailed());
	}

}
