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
package org.craftercms.engine.service.context;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.craftercms.commons.concurrency.locks.LockByKey;
import org.junit.After;
import org.junit.Test;
import org.springframework.context.ApplicationContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SiteContextManagerRemovalTest {

	private static final String SITE_NAME = "editorial";

	private LockByKey<String> sitesLock;

	private ExecutorService executor;

	@After
	public void tearDown() {
		if (executor != null) {
			executor.shutdownNow();
		}
		if (sitesLock != null) {
			sitesLock.destroy();
		}
	}

	@Test(timeout = 10000)
	public void removalShutsDownARegisteredContext() {
		SiteContextManager manager = managerWith(context("registered"));

		SiteContext created = manager.createContext(SITE_NAME, false);

		assertSame(created, manager.findContext(SITE_NAME));

		manager.removeSiteContext(SITE_NAME);

		assertNull(manager.findContext(SITE_NAME));
		assertEquals(SiteContext.State.DESTROYED, created.getState());
		assertTrue(manager.creatingContexts.isEmpty());
	}

	@Test(timeout = 10000)
	public void removalCancelsInFlightInitBeforeWaitingForTheSiteLock() throws Exception {
		CountDownLatch initStarted = new CountDownLatch(1);
		CountDownLatch shutdownDuringInit = new CountDownLatch(1);
		AtomicInteger shutdownsBeforeInitReturns = new AtomicInteger();
		AtomicReference<SiteContextManager> managerRef = new AtomicReference<>();

		SiteContext registered = new SiteContext() {
			@Override
			public void init(boolean waitTillFinished) {
			}

			@Override
			public void requestShutdown() {
				shutdownsBeforeInitReturns.incrementAndGet();
				super.requestShutdown();
			}
		};
		registered.setSiteName(SITE_NAME);

		SiteContext inFlight = new SiteContext() {
			@Override
			public void init(boolean waitTillFinished) {
				initStarted.countDown();
				try {
					assertTrue(shutdownDuringInit.await(5, TimeUnit.SECONDS));
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException(e);
				}
				assertEquals(2, shutdownsBeforeInitReturns.get());
				assertSame(registered, managerRef.get().findContext(SITE_NAME));
			}

			@Override
			public void requestShutdown() {
				shutdownsBeforeInitReturns.incrementAndGet();
				super.requestShutdown();
				shutdownDuringInit.countDown();
			}
		};
		inFlight.setSiteName(SITE_NAME);

		SiteContextManager manager = managerWith(registered, inFlight);
		managerRef.set(manager);

		assertSame(registered, manager.createContext(SITE_NAME, false));

		executor = Executors.newSingleThreadExecutor();
		Future<SiteContext> rebuild = executor.submit(() -> manager.rebuildContext(SITE_NAME, false));

		assertTrue(initStarted.await(5, TimeUnit.SECONDS));
		manager.removeSiteContext(SITE_NAME);

		assertNull(rebuild.get(5, TimeUnit.SECONDS));
		assertNull(manager.findContext(SITE_NAME));
		assertEquals(SiteContext.State.DESTROYED, registered.getState());
		assertEquals(SiteContext.State.DESTROYED, inFlight.getState());
		assertTrue(manager.creatingContexts.isEmpty());
	}

	private SiteContext context(String siteName) {
		SiteContext siteContext = new SiteContext() {
			@Override
			public void init(boolean waitTillFinished) {
			}
		};
		siteContext.setSiteName(siteName);
		return siteContext;
	}

	private SiteContextManager managerWith(SiteContext first, SiteContext... rest) {
		SiteContextFactory contextFactory = mock(SiteContextFactory.class);
		if (rest.length == 0) {
			when(contextFactory.createContext(SITE_NAME)).thenReturn(first);
		} else {
			when(contextFactory.createContext(SITE_NAME)).thenReturn(first, rest);
		}
		sitesLock = new LockByKey<>();
		SiteContextManager manager = new SiteContextManager(contextFactory, contextFactory, null, true,
			Runnable::run, "default", 1, 1L, 1, false, new String[0], new String[0], 1, 1, sitesLock);
		manager.setApplicationContext(mock(ApplicationContext.class));
		return manager;
	}

}
