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
package org.tdmx.lib.control.dao;

import java.util.Date;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;

import org.tdmx.lib.control.domain.LockEntry;

public class LockDaoImpl implements LockDao {

	@PersistenceContext(unitName = "ControlDB")
	private EntityManager em;

	@Override
	public void persist(LockEntry value) {
		em.persist(value);
	}

	@Override
	public void delete(LockEntry value) {
		em.remove(value);
	}

	@Override
	public void lock(LockEntry value) {
		em.lock(value, LockModeType.WRITE);
	}

	@Override
	public LockEntry merge(LockEntry value) {
		return em.merge(value);
	}

	@Override
	public LockEntry loadById(String id) {
		Query query = em.createQuery("from LockEntry as l where l.lockId = :id");
		query.setParameter("id", id);
		try {
			return (LockEntry) query.getSingleResult();
		} catch (NoResultException e) {
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<LockEntry> loadAll() {
		Query query = em.createQuery("from LockEntry as l");
		return query.getResultList();
	}

	@Override
	public LockEntry conditionalLock(String lockId) {
		Date now = new Date();
		Query query = em
				.createQuery("from LockEntry as l where l.lockId = :id and l.lockedBy is null and ( l.lockedUntilTime is null or l.lockedUntilTime < :n )");
		query.setParameter("id", lockId);
		query.setParameter("n", now);
		query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
		try {
			return (LockEntry) query.getSingleResult();
		} catch (NoResultException e) {
			return null;
		}
	}

}
