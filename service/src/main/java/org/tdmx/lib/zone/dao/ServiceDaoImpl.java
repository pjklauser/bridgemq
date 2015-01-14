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
package org.tdmx.lib.zone.dao;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;

import org.tdmx.core.system.lang.StringUtils;
import org.tdmx.lib.zone.domain.Service;
import org.tdmx.lib.zone.domain.ServiceID;
import org.tdmx.lib.zone.domain.ServiceSearchCriteria;

public class ServiceDaoImpl implements ServiceDao {
	// -------------------------------------------------------------------------
	// PUBLIC CONSTANTS
	// -------------------------------------------------------------------------

	// -------------------------------------------------------------------------
	// PROTECTED AND PRIVATE VARIABLES AND CONSTANTS
	// -------------------------------------------------------------------------

	@PersistenceContext(unitName = "ZoneDB")
	private EntityManager em;

	// -------------------------------------------------------------------------
	// CONSTRUCTORS
	// -------------------------------------------------------------------------

	// -------------------------------------------------------------------------
	// PUBLIC METHODS
	// -------------------------------------------------------------------------

	@Override
	public void persist(Service value) {
		em.persist(value);
	}

	@Override
	public void delete(Service value) {
		em.remove(value);
	}

	@Override
	public void lock(Service value) {
		em.lock(value, LockModeType.WRITE);
	}

	@Override
	public Service merge(Service value) {
		return em.merge(value);
	}

	@Override
	public Service loadById(ServiceID id) {
		Query query = em.createQuery("from Service as a where a.id = :d");
		query.setParameter("d", id);
		try {
			return (Service) query.getSingleResult();
		} catch (NoResultException e) {
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<Service> search(String zoneApex, ServiceSearchCriteria criteria) {
		Query query = null;
		if (StringUtils.hasText(criteria.getDomainName())) {
			if (StringUtils.hasText(criteria.getServiceName())) {
				query = em
						.createQuery("from Service as s where s.id.serviceName = :l and s.id.domainName = :d and s.id.zoneApex = :z");
				query.setParameter("d", criteria.getDomainName());
				query.setParameter("l", criteria.getServiceName());
			} else {
				query = em.createQuery("from Service as s where s.id.domainName = :d and s.id.zoneApex = :z");
				query.setParameter("d", criteria.getDomainName());
			}
		} else if (StringUtils.hasText(criteria.getServiceName())) {
			query = em.createQuery("from Service as s where s.id.serviceName = :l and s.id.zoneApex = :z");
			query.setParameter("l", criteria.getServiceName());

		} else {
			query = em.createQuery("from Service as s where s.id.zoneApex = :z");
		}
		query.setParameter("z", zoneApex);
		query.setFirstResult(criteria.getPageSpecifier().getFirstResult());
		query.setMaxResults(criteria.getPageSpecifier().getMaxResults());
		return query.getResultList();
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

}
