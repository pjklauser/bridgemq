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

import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.tdmx.lib.control.dao.LockDao;
import org.tdmx.lib.control.domain.LockEntry;

/**
 * A transactional service managing Locks.
 * 
 * @author Peter Klauser
 * 
 */
public class LockServiceRepositoryImpl implements LockService {

	// -------------------------------------------------------------------------
	// PUBLIC CONSTANTS
	// -------------------------------------------------------------------------

	// -------------------------------------------------------------------------
	// PROTECTED AND PRIVATE VARIABLES AND CONSTANTS
	// -------------------------------------------------------------------------
	private static final Logger log = LoggerFactory.getLogger(LockServiceRepositoryImpl.class);

	private LockDao lockDao;

	// -------------------------------------------------------------------------
	// CONSTRUCTORS
	// -------------------------------------------------------------------------

	// -------------------------------------------------------------------------
	// PUBLIC METHODS
	// -------------------------------------------------------------------------
	@Override
	@Transactional(value = "ControlDB")
	public void createOrUpdate(LockEntry lock) {
		LockEntry storedLock = getLockDao().loadById(lock.getLockId());
		if (storedLock == null) {
			getLockDao().persist(lock);
		} else {
			getLockDao().merge(lock);
		}
	}

	@Override
	@Transactional(value = "ControlDB")
	public void delete(LockEntry lock) {
		LockEntry storedLock = getLockDao().loadById(lock.getLockId());
		if (storedLock != null) {
			getLockDao().delete(storedLock);
		} else {
			log.warn("Unable to find Lock to delete with id " + lock.getLockId());
		}
	}

	@Override
	@Transactional(value = "ControlDB", readOnly = true)
	public LockEntry findById(String lockId) {
		return getLockDao().loadById(lockId);
	}

	@Override
	@Transactional(value = "ControlDB", readOnly = true)
	public List<LockEntry> findAll() {
		return getLockDao().loadAll();
	}

	@Override
	@Transactional(value = "ControlDB")
	public boolean acquireLock(String lockId, String holderIdentitifier) {
		LockEntry l = getLockDao().conditionalLock(lockId);
		if (l != null) {
			// all right: we have the lock
			l.setLockedBy(holderIdentitifier);
			l.setLockedUntilTime(null);
			return true;
		}
		return false;
	}

	@Override
	@Transactional(value = "ControlDB")
	public void releaseLock(String lockId, String holderIdentitifier, Date reserveUntil) {
		LockEntry l = findById(lockId);
		if (l != null) {
			if (holderIdentitifier != null && !holderIdentitifier.equals(l.getLockedBy())) {
				log.warn("Lock released by non holder " + holderIdentitifier + " held by " + l.getLockedBy());
			}
			l.setLockedBy(null);
			l.setLockedUntilTime(reserveUntil);
		}
	}

	// -------------------------------------------------------------------------
	// PROTECTED METHODS
	// -------------------------------------------------------------------------

	// -------------------------------------------------------------------------
	// PRIVATE METHODS
	// -------------------------------------------------------------------------

	// -------------------------------------------------------------------------
	// PUBLIC ACCESSORS (GETTERS / SETTERS)
	// -------------------------------------------------------------------------

	public LockDao getLockDao() {
		return lockDao;
	}

	public void setLockDao(LockDao lockDao) {
		this.lockDao = lockDao;
	}

}
