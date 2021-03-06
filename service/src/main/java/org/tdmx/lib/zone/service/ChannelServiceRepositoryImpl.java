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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import org.tdmx.lib.zone.dao.MessageDao;
import org.tdmx.lib.zone.dao.ServiceDao;
import org.tdmx.lib.zone.domain.Channel;
import org.tdmx.lib.zone.domain.ChannelAuthorization;
import org.tdmx.lib.zone.domain.ChannelAuthorizationSearchCriteria;
import org.tdmx.lib.zone.domain.ChannelAuthorizationStatus;
import org.tdmx.lib.zone.domain.ChannelDestination;
import org.tdmx.lib.zone.domain.ChannelMessage;
import org.tdmx.lib.zone.domain.ChannelMessageSearchCriteria;
import org.tdmx.lib.zone.domain.ChannelOrigin;
import org.tdmx.lib.zone.domain.Destination;
import org.tdmx.lib.zone.domain.DestinationSession;
import org.tdmx.lib.zone.domain.Domain;
import org.tdmx.lib.zone.domain.EndpointPermission;
import org.tdmx.lib.zone.domain.EndpointPermissionGrant;
import org.tdmx.lib.zone.domain.FlowControlStatus;
import org.tdmx.lib.zone.domain.FlowQuota;
import org.tdmx.lib.zone.domain.MessageState;
import org.tdmx.lib.zone.domain.MessageStatus;
import org.tdmx.lib.zone.domain.MessageStatusSearchCriteria;
import org.tdmx.lib.zone.domain.Service;
import org.tdmx.lib.zone.domain.TemporaryChannel;
import org.tdmx.lib.zone.domain.TemporaryChannelSearchCriteria;
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
	private MessageDao messageDao;
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
			existingCA.setLimit(auth.getLimit());
			existingCA.setMaxRedeliveryCount(auth.getMaxRedeliveryCount());
			existingCA.setRedeliveryDelaySec(auth.getRedeliveryDelaySec());
			// no need to relay
			existingCA.setProcessingState(ProcessingState.none());

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
					|| auth.getProcessingState().getStatus() != ProcessingStatus.NONE) {
				existingCA.setProcessingState(ProcessingState.pending());
			}
			existingCA.setSendAuthorization(auth.getSendAuthorization());
			existingCA.setLimit(auth.getLimit());
			existingCA.setMaxRedeliveryCount(auth.getMaxRedeliveryCount());
			existingCA.setRedeliveryDelaySec(auth.getRedeliveryDelaySec());
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
					|| auth.getProcessingState().getStatus() != ProcessingStatus.NONE) {
				existingCA.setProcessingState(ProcessingState.pending());
			}
			existingCA.setRecvAuthorization(auth.getRecvAuthorization());
			existingCA.setLimit(auth.getLimit());
			existingCA.setMaxRedeliveryCount(auth.getMaxRedeliveryCount());
			existingCA.setRedeliveryDelaySec(auth.getRedeliveryDelaySec());
		}
		// set the signature of the CA
		existingCA.setSignature(auth.getSignature());

		// replicate the CA data into the flowQuota
		existingChannel.getQuota().updateAuthorizationInfo();

		// if the channel is "OPEN" and we are a receiving channel, then initialize any ChannelDestinationSessions from
		// any existing Destination.
		if (existingChannel.isOpen() && existingChannel.isRecv()) {
			Destination d = destinationDao.loadByChannelDestination(zone, existingChannel.getDestination());
			if (d != null) {
				setChannelDestinationSession(zone, existingChannel, d.getDestinationSession());
				// we don't relay this DS - because the ROS will automatically relay CA and then DS
			}
		} else if (!existingChannel.isOpen()) {
			// if the channel is "CLOSED" we don't allow any DestinationSession
			existingChannel.setSession(null);
		}

		// persist the new ca
		if (newChannel) {
			channelDao.persist(existingChannel);
		}

		resultHolder.channelAuthorization = existingCA;
		return resultHolder;
	}

	@Override
	@Transactional(value = "ZoneDB")
	public Channel relayAuthorization(Zone zone, Long channelId, EndpointPermission otherPerm) {
		// lookup any existing ChannelAuthorization in the domain given the provided channel(origin+destination).
		Channel existingChannel = findById(channelId, true, true);
		// lookup or create a new ChannelAuthorization to hold the relayed in Permission
		ChannelAuthorization existingCA = existingChannel.getAuthorization();
		if (existingChannel.isSend()) {
			// we are sender and we got relayed in a requested recv authorization
			existingCA.setReqRecvAuthorization(otherPerm);
			existingCA.setReqSendAuthorization(null);
		} else {
			// we are receiver and we received a requested send authorization
			existingCA.setReqRecvAuthorization(null);
			existingCA.setReqSendAuthorization(otherPerm);
		}
		return existingChannel;
	}

	@Override
	@Transactional(value = "ZoneDB")
	public Channel relayInitialAuthorization(Zone zone, Long tempChannelId, EndpointPermission otherPerm) {
		// lookup any existing ChannelAuthorization in the domain given the provided channel(origin+destination).
		TemporaryChannel tempChannel = findByTempChannelId(tempChannelId);

		Channel newChannel = new Channel(tempChannel.getDomain(), tempChannel.getOrigin(),
				tempChannel.getDestination());
		ChannelAuthorization newCA = new ChannelAuthorization(newChannel);

		// lookup or create a new ChannelAuthorization to hold the relayed in Permission
		if (newChannel.isSend()) {
			// we are sender and we got relayed in a requested recv authorization
			newCA.setReqRecvAuthorization(otherPerm);
			newCA.setReqSendAuthorization(null);
		} else {
			// we are receiver and we received a requested send authorization
			newCA.setReqRecvAuthorization(null);
			newCA.setReqSendAuthorization(otherPerm);
		}
		// update the denormalized CA data in flowquota
		newChannel.getQuota().updateAuthorizationInfo();

		channelDao.persist(newChannel);
		channelDao.delete(tempChannel);
		return newChannel;
	}

	@Override
	@Transactional(value = "ZoneDB")
	public Channel setChannelDestinationSession(Zone zone, Long channelId, DestinationSession destinationSession) {
		Channel channel = findById(channelId, false, false);
		setChannelDestinationSession(zone, channel, destinationSession);
		return channel;
	}

	@Override
	@Transactional(value = "ZoneDB")
	public void relayChannelDestinationSession(Zone zone, Long channelId, DestinationSession destinationSession) {
		Channel channel = findById(channelId, false, false);
		setChannelDestinationSession(zone, channel, destinationSession);
		// persist should not be necessary
	}

	@Override
	@Transactional(value = "ZoneDB")
	public void create(Channel channel) {
		if (channel.getId() != null) {
			log.warn("Unable to persist Channel with id " + channel.getId());
		} else {
			channelDao.persist(channel);
		}
	}

	@Override
	@Transactional(value = "ZoneDB")
	public void delete(Channel channel) {
		Channel storedChannel = channelDao.loadById(channel.getId(), false, false);
		if (storedChannel != null) {
			channelDao.delete(storedChannel);
		} else {
			log.warn("Unable to find Channel to delete with id " + channel.getId());
		}
	}

	@Override
	@Transactional(value = "ZoneDB")
	public void create(TemporaryChannel channel) {
		channelDao.persist(channel);
	}

	@Override
	@Transactional(value = "ZoneDB")
	public void delete(TemporaryChannel tempChannel) {
		TemporaryChannel storedTempChannel = channelDao.loadByTempId(tempChannel.getId());
		if (storedTempChannel != null) {
			channelDao.delete(storedTempChannel);
		} else {
			log.warn("Unable to find TemporaryChannel to delete with id " + tempChannel.getId());
		}
	}

	@Override
	@Transactional(value = "ZoneDB")
	public void create(ChannelMessage message) {
		if (message.getId() != null) {
			log.warn("Unable to persist ChannelMessage with id " + message.getId());
		} else {
			messageDao.persist(message);
		}
	}

	@Override
	@Transactional(value = "ZoneDB")
	public void delete(ChannelMessage message) {
		ChannelMessage storedMessage = messageDao.loadById(message.getId(), false);
		if (storedMessage != null) {
			messageDao.delete(storedMessage);
		} else {
			log.warn("Unable to find ChannelMessage to delete with id " + message.getId());
		}
	}

	@Override
	@Transactional(value = "ZoneDB", readOnly = true)
	public List<Channel> search(Zone zone, ChannelAuthorizationSearchCriteria criteria) {
		return channelDao.search(zone, criteria);
	}

	@Override
	@Transactional(value = "ZoneDB", readOnly = true)
	public List<ChannelMessage> search(Zone zone, ChannelMessageSearchCriteria criteria) {
		return messageDao.search(zone, criteria);
	}

	@Override
	@Transactional(value = "ZoneDB", readOnly = true)
	public List<ChannelMessage> search(Zone zone, MessageStatusSearchCriteria criteria) {
		List<MessageState> states = messageDao.search(zone, criteria, true);
		List<ChannelMessage> msgs = new ArrayList<>(states.size());
		for (MessageState state : states) {
			msgs.add(state.getMsg());
		}
		return msgs;
	}

	@Override
	@Transactional(value = "ZoneDB")
	public ReceiveMessageResultHolder receiveMessage(Long stateId, int txTimeoutSec) {
		MessageState state = messageDao.loadStateById(stateId, true, true);
		if (state == null) {
			return null;
		}
		if (MessageStatus.READY != state.getStatus()) {
			return null;
		}
		ChannelMessage existingMsg = state.getMsg();

		ReceiveMessageResultHolder result = new ReceiveMessageResultHolder();
		result.flowQuota = null;
		result.flowControlOpened = false;
		result.msg = existingMsg;

		if (existingMsg.getState().getDeliveryCount() > existingMsg.getChannel().getQuota().getMaxRedeliveryCount()) {
			// the ChannelMessage and it's MessageState are deleted if not delivered after x retries
			// freeing quota
			existingMsg.getState().setStatus(MessageStatus.DELETED);
			messageDao.delete(existingMsg);

			result.flowQuota = channelDao.lock(existingMsg.getChannel().getQuota().getId());
			result.flowControlOpened = result.flowQuota.reduceBufferOnReceive(existingMsg.getPayloadLength());
			// we can fall below the low mark but and if we do then the flow control is opened.
		} else {
			// transition state and delivery count
			existingMsg.getState().setStatus(MessageStatus.RECEIVING);
			existingMsg.getState().setDeliveryCount(state.getDeliveryCount() + 1);
			// we can recycle the message after the retry time - since MDS tx timeout just "forgets" the message in the
			// RECEIVING state.
			Calendar futureRetry = Calendar.getInstance();
			futureRetry.add(Calendar.SECOND, txTimeoutSec);
			existingMsg.getState().setRedeliverAfter(futureRetry.getTime());
		}

		return result;
	}

	@Override
	@Transactional(value = "ZoneDB", readOnly = true)
	public List<Long> getStatusReferences(Zone zone, MessageStatusSearchCriteria criteria, int maxResults) {
		return messageDao.getReferences(zone, criteria, maxResults);
	}

	@Override
	@Transactional(value = "ZoneDB", readOnly = true)
	public List<TemporaryChannel> search(Zone zone, TemporaryChannelSearchCriteria criteria) {
		return channelDao.search(zone, criteria);
	}

	@Override
	@Transactional(value = "ZoneDB", readOnly = true)
	public SubmitMessageResultHolder checkChannelQuota(Zone zone, Channel channel, long messageSize,
			long requiredQuota) {

		// get quota (no lock / read-only) and check we can send
		FlowQuota quota = channelDao.read(channel.getQuota().getId());

		SubmitMessageResultHolder result = new SubmitMessageResultHolder();
		result.flowQuota = quota;

		if (ChannelAuthorizationStatus.CLOSED == quota.getAuthorizationStatus()) {
			// we are not currently authorized to send out.
			result.status = SubmitMessageOperationStatus.CHANNEL_CLOSED;
		} else if (FlowControlStatus.CLOSED == quota.getFlowStatus()) {
			// we are already closed for sending - opening is only changed by the relaying out creating capacity.
			result.status = SubmitMessageOperationStatus.FLOW_CONTROL_CLOSED;
		} else if (messageSize > quota.getMaxPlaintextSizeBytes()) {
			// limit max size of message to authorized max
			// checked by MOS on submit of message and MRS on relay of message.
			result.status = SubmitMessageOperationStatus.MESSAGE_TOO_LARGE;
		} else if (!quota.hasAvailableQuotaFor(requiredQuota)) {
			// we can exceed the high mark but if we do then we set flow control to closed.
			result.status = SubmitMessageOperationStatus.NOT_ENOUGH_QUOTA_AVAILABLE;
		}
		return result;
	}

	@Override
	@Transactional(value = "ZoneDB")
	public void onePhaseCommitSend(Zone zone, Channel channel, List<ChannelMessage> messages) {
		// the messages have not been persisted yet, so persist each message in a state that they can be immediately
		// relayed afterwards.
		long totalPayloadSize = 0;
		for (ChannelMessage msg : messages) {
			if (msg.getState().isSameDomain()) {
				msg.getState().setStatus(MessageStatus.READY);
				// transfer to receiver.
				msg.getState().setProcessingState(ProcessingState.none());

			} else {
				msg.getState().setStatus(MessageStatus.SUBMITTED);
				// relay initiated immediately
				msg.getState().setProcessingState(ProcessingState.pending());

			}

			// persist the message
			create(msg);

			totalPayloadSize += msg.getPayloadLength();
		}
		// reduce the available quota for all the messages sent in the tx together.
		FlowQuota quota = channelDao.lock(channel.getQuota().getId());
		quota.incrementBufferOnSend(totalPayloadSize);
	}

	@Override
	@Transactional(value = "ZoneDB")
	public void twoPhasePrepareSend(Zone zone, Channel channel, List<ChannelMessage> messages, String xid) {
		// the messages have not been persisted yet, so persist each message in a state that relates to the TX which can
		// be committed or rolled back afterwards.
		long totalPayloadSize = 0;
		for (ChannelMessage msg : messages) {
			// prepared and waiting for commit before relaying
			msg.getState().setStatus(MessageStatus.UPLOADED);
			msg.getState().setProcessingState(ProcessingState.none());
			msg.getState().setTxId(xid);

			// persist the message
			create(msg);

			totalPayloadSize += msg.getPayloadLength();
		}
		// reduce the available quota for all the messages sent in the tx together.
		FlowQuota quota = channelDao.lock(channel.getQuota().getId());
		quota.incrementBufferOnSend(totalPayloadSize);
	}

	@Override
	@Transactional(value = "ZoneDB")
	public List<String> twoPhaseRecoverSend(Zone zone, ChannelOrigin origin, int originSerialNr) {
		return messageDao.getPreparedSendTransactions(zone, origin, originSerialNr);
	}

	@Override
	@Transactional(value = "ZoneDB")
	public List<MessageState> twoPhaseCommitSend(Zone zone, ChannelOrigin origin, int originSerialNr, String xid) {
		if (origin == null) {
			throw new IllegalArgumentException("missing origin");
		}
		List<MessageState> result = new ArrayList<>();
		List<MessageState> states = null;
		do {
			MessageStatusSearchCriteria sc = new MessageStatusSearchCriteria(
					new PageSpecifier(0, PageSpecifier.DEFAULT_PAGE_SIZE));
			sc.getOrigin().setLocalName(origin.getLocalName());
			sc.getOrigin().setDomainName(origin.getDomainName());
			sc.setOriginSerialNr(originSerialNr);
			sc.setXid(xid);
			sc.setMessageStatus(MessageStatus.UPLOADED);

			states = messageDao.search(zone, sc, false);
			// we don't want to fetch the entire message with the status because on prepare we flushed
			// the channel messages to the DB, we don't want to read them back again just to continue
			// with their relaying.

			for (MessageState state : states) {
				state.setTxId(null); // clear the XID
				if (state.isSameDomain()) {
					// same domain can be "received" immediately without relaying
					state.setStatus(MessageStatus.READY);
					state.setProcessingState(ProcessingState.none());

				} else {
					state.setStatus(MessageStatus.SUBMITTED);
					state.setProcessingState(ProcessingState.pending());
				}
				result.add(state);
			}

		} while (states.size() == PageSpecifier.DEFAULT_PAGE_SIZE);

		return result;
	}

	@Override
	@Transactional(value = "ZoneDB")
	public List<MessageState> twoPhaseRollbackSend(Zone zone, ChannelOrigin origin, int originSerialNr, String xid) {
		if (origin == null) {
			throw new IllegalArgumentException("missing origin");
		}
		List<MessageState> result = new ArrayList<>();

		Map<Channel, Long> quotaMap = new HashMap<>(); // undo the quota reduction done at prepare.

		List<MessageState> states = null;
		do {
			MessageStatusSearchCriteria sc = new MessageStatusSearchCriteria(
					new PageSpecifier(0, PageSpecifier.DEFAULT_PAGE_SIZE));
			sc.getOrigin().setLocalName(origin.getLocalName());
			sc.getOrigin().setDomainName(origin.getDomainName());
			sc.setOriginSerialNr(originSerialNr);
			sc.setXid(xid);
			sc.setMessageStatus(MessageStatus.UPLOADED);

			states = messageDao.search(zone, sc, true);
			// we need to fetch the entire message with the status because on we need to undo it's quota per channel

			for (MessageState state : states) {
				ChannelMessage msg = state.getMsg();

				// accumulate the quota used per channel
				Channel ch = msg.getChannel();
				Long channelQuota = quotaMap.get(ch);
				if (channelQuota == null) {
					channelQuota = msg.getPayloadLength();
				} else {
					channelQuota += msg.getPayloadLength();
				}
				quotaMap.put(ch, channelQuota);

				// delete the message - chunks are handled separate.
				messageDao.delete(msg);

				// provide feedback of what was rolled-back
				result.add(state);

			}
		} while (states.size() == PageSpecifier.DEFAULT_PAGE_SIZE);

		// reduce the available quota for all the messages sent in the tx together.
		for (Map.Entry<Channel, Long> channelQuota : quotaMap.entrySet()) {
			FlowQuota quota = channelDao.lock(channelQuota.getKey().getQuota().getId());
			quota.reduceBuffer(channelQuota.getValue());
		}
		return result;
	}

	@Override
	@Transactional(value = "ZoneDB")
	public void updateMessageProcessingState(Long stateId, ProcessingState newState) {
		MessageState cms = messageDao.loadStateById(stateId, false, false);

		// update the processing state.
		cms.setProcessingState(newState);
	}

	@Override
	@Transactional(value = "ZoneDB")
	public void updateMessageProcessingState(Long stateId, MessageStatus status, String xid, ProcessingState newState) {
		MessageState cms = messageDao.loadStateById(stateId, false, false);

		// update the processing state.
		cms.setStatus(status);
		cms.setTxId(xid);
		cms.setProcessingState(newState);
	}

	@Override
	@Transactional(value = "ZoneDB")
	public FlowQuota relayMessage(Zone zone, ChannelMessage msg) {

		// persist the message, READY state, no ProcessingState
		create(msg);

		// get and lock quota and check we can recv
		FlowQuota quota = channelDao.lock(msg.getChannel().getQuota().getId());
		// we can exceed the high mark but if we do then we set flow control to closed.
		quota.incrementBufferOnRelay(msg.getPayloadLength());

		return quota;
	}

	@Override
	@Transactional(value = "ZoneDB")
	public void onePhaseCommitRelaySend(Zone zone, ChannelMessage msg, FlowControlStatus relayStatus) {
		// we delete the channel message and it's state.
		delete(msg);

		// get and lock quota, reduce unsent buffer on origin side
		FlowQuota quota = channelDao.lock(msg.getChannel().getQuota().getId());
		quota.reduceBuffer(msg.getPayloadLength());
		// update other side's relay status too
		quota.setRelayStatus(relayStatus);
	}

	@Override
	@Transactional(value = "ZoneDB")
	public ReceiveMessageResultHolder onePhaseCommitReceive(Zone zone, ChannelMessage msg) {
		ChannelMessage existingMsg = messageDao.loadById(msg.getId(), true);
		if (existingMsg == null) {
			throw new IllegalStateException("Message " + msg.getMsgId() + " not found.");
		}
		// the ChannelMessage and it's MessageState are deleted.
		existingMsg.getState().setStatus(MessageStatus.DELETED);
		messageDao.delete(existingMsg);

		FlowQuota quota = channelDao.lock(msg.getChannel().getQuota().getId());

		// lock quota, increase available quota, set flow control status opened if crossing low limit
		boolean openedRelayFC = quota.reduceBufferOnReceive(msg.getPayloadLength());

		ReceiveMessageResultHolder result = new ReceiveMessageResultHolder();
		result.flowQuota = quota;
		result.flowControlOpened = openedRelayFC;
		result.msg = existingMsg;
		return result;
	}

	@Override
	@Transactional(value = "ZoneDB")
	public ReceiveMessageResultHolder onePhaseRollbackReceive(Zone zone, ChannelMessage msg) {
		// message failed delivery is either recycled (up to a point) or deleted

		ChannelMessage existingMsg = messageDao.loadById(msg.getId(), true);
		if (existingMsg == null) {
			throw new IllegalStateException("Message " + msg.getMsgId() + " not found.");
		}

		ReceiveMessageResultHolder result = new ReceiveMessageResultHolder();
		result.flowQuota = channelDao.lock(msg.getChannel().getQuota().getId());
		result.flowControlOpened = false;
		result.msg = existingMsg;

		if (existingMsg.getState().getDeliveryCount() >= result.flowQuota.getMaxRedeliveryCount()) {
			// the ChannelMessage and it's MessageState are deleted if not delivered after x retries
			// freeing quota

			existingMsg.getState().setStatus(MessageStatus.DELETED);
			messageDao.delete(existingMsg);
			result.flowControlOpened = result.flowQuota.reduceBufferOnReceive(msg.getPayloadLength());
			// we can fall below the low mark but and if we do then the flow control is opened.
		} else {
			existingMsg.getState().setStatus(MessageStatus.REDELIVER);
			Calendar redeliverTime = Calendar.getInstance();
			redeliverTime.add(Calendar.SECOND, result.flowQuota.getRedeliveryDelaySec());
			existingMsg.getState().setRedeliverAfter(redeliverTime.getTime());
		}

		return result;
	}

	@Override
	@Transactional(value = "ZoneDB")
	public ReceiveMessageResultHolder twoPhasePrepareReceive(Zone zone, ChannelMessage msg, String xid) {
		ChannelMessage existingMsg = messageDao.loadById(msg.getId(), true);
		if (existingMsg == null) {
			throw new IllegalStateException("Message " + msg.getMsgId() + " not found.");
		}
		ReceiveMessageResultHolder result = new ReceiveMessageResultHolder();
		result.flowQuota = channelDao.lock(msg.getChannel().getQuota().getId());
		result.flowControlOpened = false;
		result.msg = existingMsg;

		existingMsg.getState().setStatus(MessageStatus.DOWNLOADED);
		existingMsg.getState().setTxId(xid);
		result.flowControlOpened = result.flowQuota.reduceBufferOnReceive(existingMsg.getPayloadLength());
		// we can fall below the low mark but and if we do then the flow control is opened.
		return result;
	}

	@Override
	@Transactional(value = "ZoneDB")
	public List<MessageState> twoPhaseCommitReceive(Zone zone, ChannelDestination destination, int destinationSerialNr,
			String xid) {
		if (destination == null) {
			throw new IllegalArgumentException("missing destination");
		}
		List<MessageState> result = new ArrayList<>();
		List<MessageState> states = null;
		do {
			MessageStatusSearchCriteria sc = new MessageStatusSearchCriteria(
					new PageSpecifier(0, PageSpecifier.DEFAULT_PAGE_SIZE));
			sc.getDestination().setLocalName(destination.getLocalName());
			sc.getDestination().setDomainName(destination.getDomainName());
			sc.getDestination().setServiceName(destination.getServiceName());
			sc.setDestinationSerialNr(destinationSerialNr);
			sc.setXid(xid);
			sc.setMessageStatus(MessageStatus.DOWNLOADED);

			states = messageDao.search(zone, sc, true);

			for (MessageState state : states) {
				state.setStatus(MessageStatus.DELETED);
				messageDao.delete(state.getMsg());
				result.add(state);
			}

		} while (states.size() == PageSpecifier.DEFAULT_PAGE_SIZE);

		return result;
	}

	@Override
	@Transactional(value = "ZoneDB")
	public List<String> twoPhaseRecoverReceive(Zone zone, ChannelDestination destination, int destinationSerialNr) {
		return messageDao.getPreparedReceiveTransactions(zone, destination, destinationSerialNr);
	}

	@Override
	@Transactional(value = "ZoneDB")
	public List<MessageState> twoPhaseRollbackReceive(Zone zone, ChannelDestination destination,
			int destinationSerialNr, String xid) {
		if (destination == null) {
			throw new IllegalArgumentException("missing destination");
		}
		List<MessageState> result = new ArrayList<>();
		List<MessageState> states = null;
		do {
			MessageStatusSearchCriteria sc = new MessageStatusSearchCriteria(
					new PageSpecifier(0, PageSpecifier.DEFAULT_PAGE_SIZE));
			sc.getDestination().setLocalName(destination.getLocalName());
			sc.getDestination().setDomainName(destination.getDomainName());
			sc.getDestination().setServiceName(destination.getServiceName());
			sc.setDestinationSerialNr(destinationSerialNr);
			sc.setXid(xid);
			sc.setMessageStatus(MessageStatus.DOWNLOADED);

			states = messageDao.search(zone, sc, true);

			for (MessageState state : states) {

				if (state.getDeliveryCount() >= state.getMsg().getChannel().getQuota().getMaxRedeliveryCount()) {
					// the message is deleted, quota used up before.

					state.setStatus(MessageStatus.DELETED);
					messageDao.delete(state.getMsg());
				} else {
					state.setStatus(MessageStatus.REDELIVER);
					Calendar redeliverTime = Calendar.getInstance();
					redeliverTime.add(Calendar.SECOND, state.getMsg().getChannel().getQuota().getRedeliveryDelaySec());
					state.setRedeliverAfter(redeliverTime.getTime());

					// put back the used quota
					FlowQuota quota = channelDao.lock(state.getMsg().getChannel().getQuota().getId());
					// we can exceed the high mark but if we do then we set flow control to closed.
					quota.incrementBufferOnRelay(state.getMsg().getPayloadLength());
				}
			}

		} while (states.size() == PageSpecifier.DEFAULT_PAGE_SIZE);

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
		List<Channel> auths = channelDao.search(zone, criteria);

		return auths.isEmpty() ? null : auths.get(0).getAuthorization();
	}

	@Override
	@Transactional(value = "ZoneDB", readOnly = true)
	public TemporaryChannel findByTemporaryChannel(Zone zone, Domain domain, ChannelOrigin origin,
			ChannelDestination dest) {
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
		TemporaryChannelSearchCriteria criteria = new TemporaryChannelSearchCriteria(new PageSpecifier(0, 1));
		criteria.setDomain(domain);
		criteria.getOrigin().setLocalName(origin.getLocalName());
		criteria.getOrigin().setDomainName(origin.getDomainName());
		criteria.getDestination().setLocalName(dest.getLocalName());
		criteria.getDestination().setDomainName(dest.getDomainName());
		criteria.getDestination().setServiceName(dest.getServiceName());
		List<TemporaryChannel> tempChannels = channelDao.search(zone, criteria);

		return tempChannels.isEmpty() ? null : tempChannels.get(0);
	}

	@Override
	@Transactional(value = "ZoneDB", readOnly = true)
	public Channel findById(Long id, boolean includeFlowQuota, boolean includeAuth) {
		return channelDao.loadById(id, includeFlowQuota, includeAuth);
	}

	@Override
	@Transactional(value = "ZoneDB", readOnly = true)
	public TemporaryChannel findByTempChannelId(Long tempChannelId) {
		return channelDao.loadByTempId(tempChannelId);
	}

	@Override
	@Transactional(value = "ZoneDB", readOnly = true)
	public ChannelMessage findByMessageId(Long msgId) {
		return messageDao.loadById(msgId, true);
	}

	@Override
	@Transactional(value = "ZoneDB", readOnly = true)
	public ChannelMessage findByStateId(Long stateId, boolean fetchChannel) {
		MessageState state = messageDao.loadStateById(stateId, true, fetchChannel);
		return state != null ? state.getMsg() : null;
	}

	@Override
	@Transactional(value = "ZoneDB")
	public void updateStatusDestinationSession(Long channelId, ProcessingState newState) {
		channelDao.updateChannelDestinationSessionProcessingState(channelId, newState);
	}

	@Override
	@Transactional(value = "ZoneDB")
	public void updateStatusChannelAuthorization(Long channelId, ProcessingState newState) {
		channelDao.updateChannelAuthorizationProcessingState(channelId, newState);
	}

	@Override
	@Transactional(value = "ZoneDB")
	public FlowQuota updateStatusFlowQuota(Long quotaId, ProcessingState newState) {
		FlowQuota fc = channelDao.lock(quotaId);
		fc.setProcessingState(newState);
		return fc;
	}

	@Override
	@Transactional(value = "ZoneDB")
	public FlowQuota relayFlowControlOpen(Zone zone, Long quotaId) {
		FlowQuota fc = channelDao.lock(quotaId);
		fc.setRelayStatus(FlowControlStatus.OPEN);
		fc.setProcessingState(ProcessingState.pending());
		return fc;
	}

	// -------------------------------------------------------------------------
	// PROTECTED METHODS
	// -------------------------------------------------------------------------

	// -------------------------------------------------------------------------
	// PRIVATE METHODS
	// -------------------------------------------------------------------------

	private void setChannelDestinationSession(Zone zone, Channel channel, DestinationSession destinationSession) {
		channel.setSession(destinationSession);
		// on the receiving side, we need to relay new Channel DestinationSessions to the sending side, except if we are
		// both
		// sender and receiver
		if (!channel.isSend() && channel.isRecv()) {
			channel.setProcessingState(ProcessingState.pending());
		} else {
			channel.setProcessingState(ProcessingState.none());
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

	public MessageDao getMessageDao() {
		return messageDao;
	}

	public void setMessageDao(MessageDao messageDao) {
		this.messageDao = messageDao;
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
