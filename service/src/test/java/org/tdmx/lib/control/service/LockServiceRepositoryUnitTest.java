/*
 * TDMX - Trusted Domain Messaging eXchange
 * 
 * Enterprise B2B messaging between separate corporations via interoperable cloud service providers.
 * 
 * Copyright (C) 2014 Peter Klauser (http://tdmx.org)
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see
 * http://www.gnu.org/licenses/.
 */
package org.tdmx.lib.control.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.tdmx.lib.control.domain.LockEntry;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
// @TransactionConfiguration(transactionManager="tdmx.lib.control.TransactionManager")
// @Transactional("ControlDB")
public class LockServiceRepositoryUnitTest {

	@Autowired
	private LockService service;

	// @Autowired
	// private AuthorizedAgentDao dao;

	private String lockId;

	@Before
	public void doSetup() throws Exception {
		lockId = UUID.randomUUID().toString();

		LockEntry l = new LockEntry();
		l.setLockId(lockId);

		service.createOrUpdate(l);
	}

	@After
	public void doTeardown() {
		LockEntry l = service.findById(lockId);
		if (l != null) {
			service.delete(l);
		}
	}

	@Test
	public void testAutoWire() throws Exception {
		assertNotNull(service);
	}

	@Test
	public void testLookup() throws Exception {
		LockEntry l = service.findById(lockId);
		assertNotNull(l);
		assertNotNull(l.getLockId());
	}

	@Test
	public void testLookup_NotFound() throws Exception {
		LockEntry l = service.findById("gugus");
		assertNull(l);
	}

	@Test
	public void testModify() throws Exception {
		Date d = new Date();
		LockEntry l = service.findById(lockId);
		l.setLockedBy("me");
		l.setLockedUntilTime(d);
		service.createOrUpdate(l);

		LockEntry l2 = service.findById(lockId);

		assertEquals(d, l2.getLockedUntilTime());
		assertEquals(l.getLockId(), l2.getLockId());
		assertEquals(l.getLockedBy(), l2.getLockedBy());
	}

	@Test
	public void testAquireLock() throws Exception {
		String holderIdentitifier = UUID.randomUUID().toString();

		assertTrue(service.acquireLock(lockId, holderIdentitifier));

		String holderIdentitifier2 = UUID.randomUUID().toString();

		assertFalse(service.acquireLock(lockId, holderIdentitifier2));

		service.releaseLock(lockId, holderIdentitifier, null);

		assertTrue(service.acquireLock(lockId, holderIdentitifier2));

		Calendar futureDate = Calendar.getInstance();
		futureDate.add(Calendar.DATE, 1);

		service.releaseLock(lockId, holderIdentitifier2, futureDate.getTime());

		// cannot get lock because of time locking
		assertFalse(service.acquireLock(lockId, holderIdentitifier));

		// release for some time in the past
		Calendar pastDate = Calendar.getInstance();
		pastDate.add(Calendar.DATE, -1);
		service.releaseLock(lockId, holderIdentitifier2, pastDate.getTime());

		assertTrue(service.acquireLock(lockId, holderIdentitifier));
	}

}