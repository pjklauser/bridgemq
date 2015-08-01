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

package org.tdmx.lib.zone.service;

import java.math.BigInteger;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.tdmx.core.system.lang.StringUtils;
import org.tdmx.lib.common.domain.PageSpecifier;
import org.tdmx.lib.common.domain.ProcessingState;
import org.tdmx.lib.common.domain.ProcessingStatus;
import org.tdmx.lib.zone.dao.AgentCredentialDao;
import org.tdmx.lib.zone.dao.ChannelDao;
import org.tdmx.lib.zone.dao.DestinationDao;
import org.tdmx.lib.zone.dao.ServiceDao;
import org.tdmx.lib.zone.domain.Channel;
import org.tdmx.lib.zone.domain.ChannelAuthorization;
import org.tdmx.lib.zone.domain.ChannelAuthorizationSearchCriteria;
import org.tdmx.lib.zone.domain.ChannelDestination;
import org.tdmx.lib.zone.domain.ChannelFlowMessage;
import org.tdmx.lib.zone.domain.ChannelFlowMessageSearchCriteria;
import org.tdmx.lib.zone.domain.ChannelOrigin;
import org.tdmx.lib.zone.domain.Destination;
import org.tdmx.lib.zone.domain.DestinationSearchCriteria;
import org.tdmx.lib.zone.domain.DestinationSession;
import org.tdmx.lib.zone.domain.Domain;
import org.tdmx.lib.zone.domain.EndpointPermission;
import org.tdmx.lib.zone.domain.EndpointPermissionGrant;
import org.tdmx.lib.zone.domain.FlowControlStatus;
import org.tdmx.lib.zone.domain.FlowQuota;
import org.tdmx.lib.zone.domain.MessageDescriptor;
import org.tdmx.lib.zone.domain.Service;
import org.tdmx.lib.zone.domain.Zone;

/**
 * Transactional CRUD Services for Channel Entity.
 * 
 * @author Peter Klauser
 * 
 */
public class ChannelServiceRepositoryImpl implements ChannelService {

	// -------------------------------------------------------------------------
	// PUBLIC CONSTANTS
	// -------------------------------------------------------------------------

	// -------------------------------------------------------------------------
	// PROTECTED AND PRIVATE VARIABLES AND CONSTANTS
	// -------------------------------------------------------------------------
	private static final Logger log = LoggerFactory.getLogger(ChannelServiceRepositoryImpl.class);

	private ChannelDao channelDao;
	private DestinationDao destinationDao;
	private ServiceDao serviceDao;
	private AgentCredentialDao agentCredentialDao;

	// -------------------------------------------------------------------------
	// CONSTRUCTORS
	// -------------------------------------------------------------------------

	// -------------------------------------------------------------------------
	// PUBLIC METHODS
	// -------------------------------------------------------------------------

	@Override
	@Transactional(value = "ZoneDB")
	public SetAuthorizationResultHolder setAuthorization(Zone zone, Domain domain, ChannelOrigin origin,
			ChannelDestination dest, ChannelAuthorization auth) {
		SetAuthorizationResultHolder resultHolder = new SetAuthorizationResultHolder();

		// lookup any existing ChannelAuthorization in the domain given the provided channel(origin+destination).
		Channel existingChannel = null;
		boolean newChannel = false;
		ChannelAuthorization existingCA = findByChannel(zone, domain, origin, dest);
		if (existingCA == null) {
			// If no existing ca - then create one with empty data.
			existingChannel = new Channel(domain, origin, dest);
			newChannel = true;
			existingCA = new ChannelAuthorization(existingChannel);
			existingCA.setProcessingState(new ProcessingState(ProcessingStatus.NONE));
		} else {
			existingChannel = existingCA.getChannel();
		}

		if (existingChannel.isSend() && existingChannel.isRecv()) {
			// 1) setting send&recvAuth on same domain channel
			// - no requested send/recv allowed in existing ca.
			if (auth.getRecvAuthorization() == null) {
				resultHolder.status = SetAuthorizationOperationStatus.RECEIVER_AUTHORIZATION_CONFIRMATION_MISSING;
				return resultHolder;
			}
			if (auth.getSendAuthorization() == null) {
				resultHolder.status = SetAuthorizationOperationStatus.SENDER_AUTHORIZATION_CONFIRMATION_MISSING;
				return resultHolder;
			}
			if (auth.getRecvAuthorization().getGrant() == EndpointPermissionGrant.ALLOW) {
				// check that the Service exists
				Service service = serviceDao.loadByName(domain, existingChannel.getDestination().getServiceName());
				if (service == null) {
					resultHolder.status = SetAuthorizationOperationStatus.RECEIVER_SERVICE_NOT_FOUND;
					return resultHolder;
				}
			}
			existingCA.setReqRecvAuthorization(null);
			existingCA.setReqSendAuthorization(null);
			existingCA.setSendAuthorization(auth.getSendAuthorization());
			existingCA.setRecvAuthorization(auth.getRecvAuthorization());
			existingCA.setProcessingState(new ProcessingState(ProcessingStatus.SUCCESS)); // no need to relay

		} else if (existingChannel.isSend()) {
			// we must confirm any requested recvAuth if there is one, but not invent one
			if (existingCA.getReqRecvAuthorization() != null) {
				if (auth.getRecvAuthorization() == null) {
					resultHolder.status = SetAuthorizationOperationStatus.RECEIVER_AUTHORIZATION_CONFIRMATION_MISSING;
					return resultHolder;
				} else if (!auth.getRecvAuthorization().getSignature().getValue()
						.equals(existingCA.getReqRecvAuthorization().getSignature().getValue())) {
					resultHolder.status = SetAuthorizationOperationStatus.RECEIVER_AUTHORIZATION_CONFIRMATION_MISMATCH;
					return resultHolder;
				}
			} else if (auth.getRecvAuthorization() != null) {
				// and if there isn't a requestedRecvAuth we cannot provide one either
				resultHolder.status = SetAuthorizationOperationStatus.RECEIVER_AUTHORIZATION_CONFIRMATION_PROVIDED;
				return resultHolder;
			}
			// we are sender and there shall be no pending send authorization
			existingCA.setReqRecvAuthorization(null);
			existingCA.setReqSendAuthorization(null);
			existingCA.setRecvAuthorization(auth.getRecvAuthorization());

			// change of sendAuth vs existing sendAuth forces transfer
			if (existingCA.getSendAuthorization() == null
					|| !existingCA.getSendAuthorization().getSignature().getValue()
							.equals(auth.getSendAuthorization().getSignature().getValue())
					|| auth.getProcessingState().getStatus() != ProcessingStatus.SUCCESS) {
				existingCA.setProcessingState(new ProcessingState(ProcessingStatus.PENDING));
			}
			existingCA.setSendAuthorization(auth.getSendAuthorization());
		} else {
			// 3) recvAuth(+confirming requested sendAuth)
			// - no reqRecvAuth allowed in existing ca.
			// we are receiver and there shall be no pending recv authorization
			// we must confirm any requested sendAuth if there is one, but not invent one
			if (auth.getRecvAuthorization().getGrant() == EndpointPermissionGrant.ALLOW) {
				// check that the Service exists if we're opening up the receiving end.
				Service service = serviceDao.loadByName(domain, existingChannel.getDestination().getServiceName());
				if (service == null) {
					resultHolder.status = SetAuthorizationOperationStatus.RECEIVER_SERVICE_NOT_FOUND;
					return resultHolder;
				}
			}
			if (existingCA.getReqSendAuthorization() != null) {
				if (auth.getSendAuthorization() == null) {
					resultHolder.status = SetAuthorizationOperationStatus.SENDER_AUTHORIZATION_CONFIRMATION_MISSING;
					return resultHolder;
				} else if (!auth.getSendAuthorization().getSignature().getValue()
						.equals(existingCA.getReqSendAuthorization().getSignature().getValue())) {
					resultHolder.status = SetAuthorizationOperationStatus.SENDER_AUTHORIZATION_CONFIRMATION_MISMATCH;
					return resultHolder;
				}
			} else if (auth.getSendAuthorization() != null) {
				// and if there isn't a requestedRecvAuth we cannot provide one either
				resultHolder.status = SetAuthorizationOperationStatus.SENDER_AUTHORIZATION_CONFIRMATION_PROVIDED;
				return resultHolder;
			}
			existingCA.setReqSendAuthorization(null);
			existingCA.setReqRecvAuthorization(null);
			existingCA.setSendAuthorization(auth.getSendAuthorization());

			// change of recvAuth vs existing recvAuth forces transfer
			if (existingCA.getRecvAuthorization() == null
					|| !existingCA.getRecvAuthorization().getSignature().getValue()
							.equals(auth.getRecvAuthorization().getSignature().getValue())
					|| auth.getProcessingState().getStatus() != ProcessingStatus.SUCCESS) {
				existingCA.setProcessingState(new ProcessingState(ProcessingStatus.PENDING));
			}
			existingCA.setRecvAuthorization(auth.getRecvAuthorization());
		}

		// if the channel is "OPEN" and we are a receiving channel, then initialize any ChannelFlowTargets from the
		// existing FlowTargets of receivers.
		handleChannelOpenClose(zone, existingChannel);

		// persist the new ca
		if (newChannel) {
			getChannelDao().persist(existingChannel);
		}

		resultHolder.channelAuthorization = existingCA;
		return resultHolder;
	}

	@Override
	@Transactional(value = "ZoneDB")
	public void relayAuthorization(Zone zone, Long channelId, EndpointPermission otherPerm) {
		// lookup any existing ChannelAuthorization in the domain given the provided channel(origin+destination).
		Channel existingChannel = findById(channelId);
		// lookup or create a new ChannelAuthorization to hold the relayed in Permission
		ChannelAuthorization existingCA = existingChannel.getAuthorization();
		if (existingCA == null) {
			existingCA = new ChannelAuthorization(existingChannel);
			existingCA.setProcessingState(new ProcessingState(ProcessingStatus.NONE));
			existingChannel.setAuthorization(existingCA);
		}
		if (existingChannel.isSend()) {
			// we are sender and we got relayed in a requested recv authorization
			existingCA.setReqRecvAuthorization(otherPerm);
			existingCA.setReqSendAuthorization(null);
		} else {
			// we are receiver and we received a requested send authorization
			existingCA.setReqRecvAuthorization(null);
			existingCA.setReqSendAuthorization(otherPerm);
		}
	}

	@Override
	@Transactional(value = "ZoneDB")
	public void setChannelDestinationSession(Zone zone, Long channelId, DestinationSession destinationSession) {
		Channel channel = findById(channelId);
		setChannelDestinationSession(zone, channel, destinationSession);
		// persist should not be necessary
	}

	@Override
	@Transactional(value = "ZoneDB")
	public void relayChannelDestinationSession(Zone zone, Long channelId, DestinationSession destinationSession) {
		Channel channel = findById(channelId);
		setChannelDestinationSession(zone, channel, destinationSession);
		// persist should not be necessary
	}

	@Override
	@Transactional(value = "ZoneDB")
	public void createOrUpdate(Channel channel) {
		if (channel.getId() != null) {
			Channel storedAuth = getChannelDao().loadById(channel.getId());
			if (storedAuth != null) {
				getChannelDao().merge(channel);
			} else {
				log.warn("Unable to find Channel with id " + channel.getId());
			}
		} else {
			getChannelDao().persist(channel);
		}
	}

	@Override
	@Transactional(value = "ZoneDB")
	public void delete(Channel channel) {
		Channel storedAuth = getChannelDao().loadById(channel.getId());
		if (storedAuth != null) {
			getChannelDao().delete(storedAuth);
		} else {
			log.warn("Unable to find Channel to delete with id " + channel.getId());
		}
	}

	@Override
	@Transactional(value = "ZoneDB", readOnly = true)
	public List<Channel> search(Zone zone, ChannelAuthorizationSearchCriteria criteria) {
		return getChannelDao().search(zone, criteria);
	}

	@Override
	@Transactional(value = "ZoneDB", readOnly = true)
	public List<ChannelFlowMessage> search(Zone zone, ChannelFlowMessageSearchCriteria criteria) {
		return getChannelDao().search(zone, criteria);
	}

	@Override
	@Transactional(value = "ZoneDB")
	public SubmitMessageResultHolder submitMessage(Zone zone, Channel channel, MessageDescriptor md) {
		SubmitMessageResultHolder result = new SubmitMessageResultHolder();

		// TODO caller must have loaded Channel.ChannelAuthorization prior, or we move it to Channel.

		// get and lock quota and check we can send
		FlowQuota quota = getChannelDao().lock(channel.getQuota().getId());
		if (FlowControlStatus.CLOSED == quota.getSenderStatus()) {
			// we are already closed for sending - opening is only changed by the relaying out creating capacity.
			result.status = SubmitMessageOperationStatus.FLOW_CONTROL_CLOSED;
			return result;
		}
		// we can exceed the high mark but if we do then we set flow control to closed.
		quota.setUnsentBytes(quota.getUnsentBytes().add(BigInteger.valueOf(md.getPayloadLength())));
		if (quota.getUnsentBytes().subtract(channel.getAuthorization().getUnsentBuffer().getHighMarkBytes())
				.compareTo(BigInteger.ZERO) > 0) {
			// quota exceeded, close send
			quota.setSenderStatus(FlowControlStatus.CLOSED);
		}

		ChannelFlowMessage m = new ChannelFlowMessage(channel, md);
		getChannelDao().persist(m);

		result.message = m;
		return result;
	}

	@Override
	@Transactional(value = "ZoneDB")
	public SubmitMessageResultHolder relayMessage(Zone zone, Channel channel, MessageDescriptor md) {
		SubmitMessageResultHolder result = new SubmitMessageResultHolder();
		// get and lock quota and check we can send
		FlowQuota quota = getChannelDao().lock(channel.getQuota().getId());
		if (FlowControlStatus.CLOSED == quota.getReceiverStatus()) {
			// quota remains exceeded and closed to relaying in - receiving consuming messages creates capacity which
			// changes this status eventually
			result.status = SubmitMessageOperationStatus.FLOW_CONTROL_CLOSED;
			return result;
		}
		// we can exceed the high mark but if we do then we set flow control to closed.
		quota.setUndeliveredBytes(quota.getUndeliveredBytes().add(BigInteger.valueOf(md.getPayloadLength())));
		if (quota.getUndeliveredBytes().subtract(channel.getAuthorization().getUndeliveredBuffer().getHighMarkBytes())
				.compareTo(BigInteger.ZERO) > 0) {
			// quota exceeded, close relay
			quota.setReceiverStatus(FlowControlStatus.CLOSED);
		}

		ChannelFlowMessage m = new ChannelFlowMessage(channel, md);
		getChannelDao().persist(m);

		result.message = m;
		return result;
	}

	@Override
	@Transactional(value = "ZoneDB", readOnly = true)
	public ChannelAuthorization findByChannel(Zone zone, Domain domain, ChannelOrigin origin, ChannelDestination dest) {
		if (domain == null) {
			throw new IllegalArgumentException("missing domain");
		}
		if (origin == null) {
			throw new IllegalArgumentException("missing origin");
		}
		if (!StringUtils.hasText(origin.getLocalName())) {
			throw new IllegalArgumentException("missing origin localName");
		}
		if (!StringUtils.hasText(origin.getDomainName())) {
			throw new IllegalArgumentException("missing origin domainName");
		}
		if (dest == null) {
			throw new IllegalArgumentException("missing dest");
		}
		if (!StringUtils.hasText(dest.getLocalName())) {
			throw new IllegalArgumentException("missing dest localName");
		}
		if (!StringUtils.hasText(dest.getDomainName())) {
			throw new IllegalArgumentException("missing dest domainName");
		}
		if (!StringUtils.hasText(dest.getServiceName())) {
			throw new IllegalArgumentException("missing dest serviceName");
		}
		ChannelAuthorizationSearchCriteria criteria = new ChannelAuthorizationSearchCriteria(new PageSpecifier(0, 1));
		criteria.setDomain(domain);
		criteria.getOrigin().setLocalName(origin.getLocalName());
		criteria.getOrigin().setDomainName(origin.getDomainName());
		criteria.getDestination().setLocalName(dest.getLocalName());
		criteria.getDestination().setDomainName(dest.getDomainName());
		criteria.getDestination().setServiceName(dest.getServiceName());
		List<Channel> auths = getChannelDao().search(zone, criteria);

		return auths.isEmpty() ? null : auths.get(0).getAuthorization();
	}

	@Override
	@Transactional(value = "ZoneDB", readOnly = true)
	public Channel findById(Long id) {
		return getChannelDao().loadById(id);
	}

	@Override
	@Transactional(value = "ZoneDB", readOnly = true)
	public ChannelFlowMessage findByMessageId(Zone zone, String msgId) {
		return getChannelDao().loadChannelFlowMessageByMessageId(zone, msgId);
	}

	// -------------------------------------------------------------------------
	// PROTECTED METHODS
	// -------------------------------------------------------------------------

	// -------------------------------------------------------------------------
	// PRIVATE METHODS
	// -------------------------------------------------------------------------

	private void handleChannelOpenClose(Zone zone, Channel channel) {
		if (channel.isOpen() && channel.isRecv()) {
			DestinationSearchCriteria ftsc = new DestinationSearchCriteria(new PageSpecifier(0, 100)); // TODO global
																										// hard
																										// limit
			ftsc.getDestination().setLocalName(channel.getDestination().getLocalName());
			ftsc.getDestination().setDomainName(channel.getDestination().getDomainName());
			ftsc.getDestination().setServiceName(channel.getDestination().getServiceName());
			// suspended agents don't have FlowTargets, nor ChannelFlowTargets, so we don't need to restrict here on
			// "active" agents

			List<Destination> destinations = destinationDao.search(zone, ftsc);
			for (Destination d : destinations) {
				setChannelDestinationSession(zone, channel, d.getDestinationSession());
			}
		} else if (!channel.isOpen()) {
			// if the channel is "CLOSED" we don't allow any DestinationSession
			channel.setSession(null);
		}
	}

	private void setChannelDestinationSession(Zone zone, Channel channel, DestinationSession destinationSession) {
		channel.setSession(destinationSession);
		// on the receiving side, we need to relay new ChannelFlowTargets to the sending side, except if we are both
		// sender and receiver
		if (!channel.isSend() && channel.isRecv()) {
			channel.setProcessingState(new ProcessingState(ProcessingStatus.PENDING));
		} else {
			channel.setProcessingState(new ProcessingState(ProcessingStatus.SUCCESS));
		}
	}

	// -------------------------------------------------------------------------
	// PUBLIC ACCESSORS (GETTERS / SETTERS)
	// -------------------------------------------------------------------------

	public ChannelDao getChannelDao() {
		return channelDao;
	}

	public void setChannelDao(ChannelDao channelDao) {
		this.channelDao = channelDao;
	}

	public DestinationDao getDestinationDao() {
		return destinationDao;
	}

	public void setDestinationDao(DestinationDao destinationDao) {
		this.destinationDao = destinationDao;
	}

	public ServiceDao getServiceDao() {
		return serviceDao;
	}

	public void setServiceDao(ServiceDao serviceDao) {
		this.serviceDao = serviceDao;
	}

	public AgentCredentialDao getAgentCredentialDao() {
		return agentCredentialDao;
	}

	public void setAgentCredentialDao(AgentCredentialDao agentCredentialDao) {
		this.agentCredentialDao = agentCredentialDao;
	}

}