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

import org.tdmx.lib.zone.domain.Channel;
import org.tdmx.lib.zone.domain.ChannelAuthorization;
import org.tdmx.lib.zone.domain.ChannelAuthorizationSearchCriteria;
import org.tdmx.lib.zone.domain.ChannelFlowMessage;
import org.tdmx.lib.zone.domain.ChannelFlowOrigin;
import org.tdmx.lib.zone.domain.ChannelFlowSearchCriteria;
import org.tdmx.lib.zone.domain.ChannelFlowTarget;
import org.tdmx.lib.zone.domain.ChannelFlowTargetSearchCriteria;
import org.tdmx.lib.zone.domain.ChannelSearchCriteria;
import org.tdmx.lib.zone.domain.FlowQuota;
import org.tdmx.lib.zone.domain.Zone;

/**
 * DAO for the Channel Entity.
 * 
 * @author Peter
 * 
 */
public interface ChannelDao {

	public void persist(Channel value);

	public Channel merge(Channel value);

	public void persist(ChannelFlowMessage value);

	public void delete(Channel value);

	public void delete(ChannelFlowTarget value);

	public void delete(ChannelFlowOrigin value);

	public void delete(ChannelFlowMessage value);

	public FlowQuota lock(Long quotaId);

	public Channel loadById(Long id);

	public ChannelFlowTarget loadChannelFlowTargetById(Long id);

	public ChannelFlowOrigin loadChannelFlowOriginById(Long id);

	/**
	 * Search for ChannelAuthorizations. FetchPlan includes Channel and Domain.
	 * 
	 * @param zone
	 * @param criteria
	 * @return
	 */
	public List<ChannelAuthorization> search(Zone zone, ChannelAuthorizationSearchCriteria criteria);

	/**
	 * Search for Channels. No FetchPlan.
	 * 
	 * @param zone
	 * @param criteria
	 * @return
	 */
	public List<Channel> search(Zone zone, ChannelSearchCriteria criteria);

	/**
	 * Search for ChannelFlowTargets. FetchPlan includes Channel, Domain.
	 * 
	 * @param zone
	 * @param criteria
	 * @return
	 */
	public List<ChannelFlowTarget> search(Zone zone, ChannelFlowTargetSearchCriteria criteria);

	/**
	 * Search for ChannelFlowOrigins. FetchPlan includes ChannelFlowTarget, Channel, Domain.
	 * 
	 * @param zone
	 * @param criteria
	 * @return
	 */
	public List<ChannelFlowOrigin> search(Zone zone, ChannelFlowSearchCriteria criteria);

}
