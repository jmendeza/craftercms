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

import static org.eclipse.jgit.lib.Constants.DOT_GIT;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.configuration2.Configuration;
import org.craftercms.commons.config.ConfigurationException;
import org.craftercms.core.store.ContentStoreManifest;
import org.craftercms.deployer.api.ChangeSet;
import org.craftercms.deployer.api.Deployment;
import org.craftercms.deployer.api.ProcessorExecution;
import org.craftercms.deployer.api.exceptions.DeployerException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.beans.ConstructorProperties;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import static java.lang.String.format;
import static org.apache.commons.lang3.Strings.CS;
import static org.craftercms.commons.config.ConfigUtils.getBooleanProperty;

/**
 * Writes a single content-store manifest to S3.
 * The manifest lists every file uploaded for the site so Engine can answer folder
 * existence and children lookups without listing the bucket.
 * <p>
 * A publish that only updates existing files does not change that list. When a manifest
 * is already in the bucket, the processor leaves it in place. Created files, deleted files,
 * or a missing manifest rebuild it from the local repository.
 * <p>
 * Runs after the S3 sync and before {@link S3DeploymentEventsProcessor}, so the
 * deployment-events signal is published only after the manifest is in place.
 * <p>
 * Can be configured with the following YAML properties:
 * <ul>
 *     <li><strong>ignoreBlobs:</strong> Do not include blob pointer files. Defaults to true,
 *     matching {@link S3SyncProcessor}.</li>
 * </ul>
 */
public class S3ContentStoreManifestProcessor extends AbstractS3Processor {

	public static final String CONFIG_KEY_IGNORE_BLOBS = "ignoreBlobs";

	private static final String GIT_ROOT = DOT_GIT + "/";

	private static final int NOT_FOUND = 404;

	protected String localRepoUrl;

	protected String blobExtension;

	protected boolean ignoreBlobs;

	@ConstructorProperties({"threadPoolTaskExecutor", "localRepoUrl", "blobExtension"})
	public S3ContentStoreManifestProcessor(ThreadPoolTaskExecutor threadPoolTaskExecutor, String localRepoUrl,
					       String blobExtension) {
		super(threadPoolTaskExecutor);
		this.localRepoUrl = localRepoUrl;
		this.blobExtension = blobExtension;

	}

	@Override
	protected void doInit(Configuration config) throws ConfigurationException {
		super.doInit(config);
		ignoreBlobs = getBooleanProperty(config, CONFIG_KEY_IGNORE_BLOBS, true);
	}

	@Override
	protected ChangeSet doMainProcess(Deployment deployment, ProcessorExecution execution, ChangeSet filteredChangeSet,
					  ChangeSet originalChangeSet) throws DeployerException {
		if (!manifestFileListChanged(filteredChangeSet) && currentManifestExists()) {
			logger.info("Skipping content store manifest rebuild for s3://{}/{} because this publish did not create or delete files",
				getBucket(), getS3Key(ContentStoreManifest.FILE_NAME));
			return null;
		}

		Path repoRoot = Path.of(localRepoUrl);
		if (!Files.isDirectory(repoRoot)) {
			throw new DeployerException("Local repository does not exist @ " + repoRoot);
		}

		List<String> paths;
		try {
			paths = listPublishedFiles(repoRoot);
		} catch (IOException e) {
			throw new DeployerException("Error reading local repository @ " + repoRoot, e);
		}

		ContentStoreManifest manifest = ContentStoreManifest.ofPaths(paths);
		byte[] body;
		try {
			body = manifest.toGzipJson();
		} catch (IOException e) {
			throw new DeployerException("Error writing content store manifest", e);
		}

		String key = getS3Key(ContentStoreManifest.FILE_NAME);
		logger.info("Uploading content store manifest with {} files to s3://{}/{}", paths.size(), getBucket(), key);

		try (S3Client client = buildClient()) {
			PutObjectRequest request = PutObjectRequest.builder()
				.bucket(getBucket())
				.key(key)
				.contentType("application/gzip")
				.build();
			client.putObject(request, RequestBody.fromBytes(body));
		} catch (Exception e) {
			throw new DeployerException(format("Error uploading content store manifest to s3://%s/%s", getBucket(), key), e);
		}

		return null;
	}

	/**
	 * Created and deleted files change the manifest. Updates do not. A missing change set is rebuilt
	 * because the file list cannot be confirmed.
	 */
	protected boolean manifestFileListChanged(ChangeSet changeSet) {
		if (changeSet == null) {
			return true;
		}
		return CollectionUtils.isNotEmpty(changeSet.getCreatedFiles())
			|| CollectionUtils.isNotEmpty(changeSet.getDeletedFiles());
	}

	/**
	 * @return true when the manifest object is already in the bucket. A missing object, or a check that
	 * fails, returns false so the caller rebuilds the manifest.
	 */
	protected boolean currentManifestExists() {
		String bucket = getBucket();
		String key = getS3Key(ContentStoreManifest.FILE_NAME);
		try (S3Client client = buildClient()) {
			client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
			return true;
		} catch (S3Exception e) {
			if (e.statusCode() == NOT_FOUND) {
				logger.info("Content store manifest s3://{}/{} was not found. Rebuilding it", bucket, key);
			} else {
				logger.warn("Unable to check content store manifest s3://{}/{}. Rebuilding it", bucket, key, e);
			}
			return false;
		} catch (Exception e) {
			logger.warn("Unable to check content store manifest s3://{}/{}. Rebuilding it", bucket, key, e);
			return false;
		}
	}

	/**
	 * Lists the files in the local repository that S3 sync uploads.
	 * {@code .git} directories are not entered. Blob pointer files are skipped when configured.
	 *
	 * @param repoRoot the local git working tree
	 * @return store paths beginning with {@code /}
	 * @throws IOException if the repository cannot be walked
	 */
	protected List<String> listPublishedFiles(Path repoRoot) throws IOException {
		List<String> paths = new ArrayList<>();
		Files.walkFileTree(repoRoot, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
				Path fileName = dir.getFileName();
				if (!dir.equals(repoRoot) && fileName != null && DOT_GIT.equals(fileName.toString())) {
					return FileVisitResult.SKIP_SUBTREE;
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				if (!attrs.isRegularFile()) {
					return FileVisitResult.CONTINUE;
				}
				String relative = repoRoot.relativize(file).toString().replace('\\', '/');
				if (DOT_GIT.equals(relative) || relative.startsWith(GIT_ROOT)) {
					return FileVisitResult.CONTINUE;
				}
				if (ignoreBlobs && CS.endsWith(relative, blobExtension)) {
					return FileVisitResult.CONTINUE;
				}
				String storePath = "/" + relative;
				if (shouldIncludeFile(storePath)) {
					paths.add(storePath);
				}
				return FileVisitResult.CONTINUE;
			}
		});
		return paths;
	}

}
