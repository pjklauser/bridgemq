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
package org.tdmx.server.ws.mds;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tdmx.client.crypto.certificate.PKIXCertificate;
import org.tdmx.core.api.SignatureUtils;
import org.tdmx.core.api.v01.common.Acknowledge;
import org.tdmx.core.api.v01.common.Error;
import org.tdmx.core.api.v01.mds.Download;
import org.tdmx.core.api.v01.mds.DownloadResponse;
import org.tdmx.core.api.v01.mds.GetDestinationSession;
import org.tdmx.core.api.v01.mds.GetDestinationSessionResponse;
import org.tdmx.core.api.v01.mds.ListChannel;
import org.tdmx.core.api.v01.mds.ListChannelResponse;
import org.tdmx.core.api.v01.mds.Receive;
import org.tdmx.core.api.v01.mds.ReceiveResponse;
import org.tdmx.core.api.v01.mds.SetDestinationSession;
import org.tdmx.core.api.v01.mds.SetDestinationSessionResponse;
import org.tdmx.core.api.v01.mds.ws.MDS;
import org.tdmx.core.api.v01.msg.Destinationinfo;
import org.tdmx.core.api.v01.msg.Destinationsession;
import org.tdmx.core.api.v01.msg.UserIdentity;
import org.tdmx.core.api.v01.tx.Commit;
import org.tdmx.core.api.v01.tx.CommitResponse;
import org.tdmx.core.api.v01.tx.Forget;
import org.tdmx.core.api.v01.tx.ForgetResponse;
import org.tdmx.core.api.v01.tx.Prepare;
import org.tdmx.core.api.v01.tx.PrepareResponse;
import org.tdmx.core.api.v01.tx.Recover;
import org.tdmx.core.api.v01.tx.RecoverResponse;
import org.tdmx.core.api.v01.tx.Rollback;
import org.tdmx.core.api.v01.tx.RollbackResponse;
import org.tdmx.lib.common.domain.PageSpecifier;
import org.tdmx.lib.zone.domain.Address;
import org.tdmx.lib.zone.domain.AgentCredential;
import org.tdmx.lib.zone.domain.Channel;
import org.tdmx.lib.zone.domain.ChannelAuthorizationSearchCriteria;
import org.tdmx.lib.zone.domain.Destination;
import org.tdmx.lib.zone.domain.Service;
import org.tdmx.lib.zone.domain.Zone;
import org.tdmx.lib.zone.service.AddressService;
import org.tdmx.lib.zone.service.AgentCredentialFactory;
import org.tdmx.lib.zone.service.AgentCredentialService;
import org.tdmx.lib.zone.service.AgentCredentialValidator;
import org.tdmx.lib.zone.service.ChannelService;
import org.tdmx.lib.zone.service.DestinationService;
import org.tdmx.lib.zone.service.DomainService;
import org.tdmx.lib.zone.service.ServiceService;
import org.tdmx.server.ws.ApiToDomainMapper;
import org.tdmx.server.ws.ApiValidator;
import org.tdmx.server.ws.DomainToApiMapper;
import org.tdmx.server.ws.ErrorCode;
import org.tdmx.server.ws.security.ServerSecurityManager;

public class MDSImpl implements MDS {

	// -------------------------------------------------------------------------
	// PUBLIC CONSTANTS
	// -------------------------------------------------------------------------

	// -------------------------------------------------------------------------
	// PROTECTED AND PRIVATE VARIABLES AND CONSTANTS
	// -------------------------------------------------------------------------
	private static final Logger log = LoggerFactory.getLogger(MDSImpl.class);

	private ServerSecurityManager<MDSServerSession> securityManager;

	private DomainService domainService;
	private AddressService addressService;
	private ServiceService serviceService;
	private ChannelService channelService;
	private DestinationService destinationService;

	private AgentCredentialFactory credentialFactory;
	private AgentCredentialService credentialService;
	private AgentCredentialValidator credentialValidator;

	private final DomainToApiMapper d2a = new DomainToApiMapper();
	private final ApiToDomainMapper a2d = new ApiToDomainMapper();
	private final ApiValidator validator = new ApiValidator();

	private int batchSize = 100;

	// -------------------------------------------------------------------------
	// CONSTRUCTORS
	// -------------------------------------------------------------------------

	// -------------------------------------------------------------------------
	// PUBLIC METHODS
	// -------------------------------------------------------------------------

	@Override
	public GetDestinationSessionResponse getDestinationSession(GetDestinationSession parameters) {

		GetDestinationSessionResponse response = new GetDestinationSessionResponse();
		PKIXCertificate authorizedUser = checkUserAuthorized(response);
		if (authorizedUser == null) {
			return response;
		}

		Destination d = null; // FIXME new session context
		Service service = null; // FIXME
		Address address = null; // FIXME

		Destinationinfo info = new Destinationinfo();
		info.setDomain(address.getDomain().getDomainName());
		info.setLocalname(address.getLocalName());
		info.setServicename(service.getServiceName());
		if (d != null) {
			Destination existingDestination = destinationService.findById(d.getId());
			info.setDestinationsession(d2a.mapDestinationSession(existingDestination.getDestinationSession()));
		}

		response.setSuccess(true);
		return response;
	}

	@Override
	public SetDestinationSessionResponse setDestinationSession(SetDestinationSession parameters) {
		SetDestinationSessionResponse response = new SetDestinationSessionResponse();
		PKIXCertificate authorizedUser = checkUserAuthorized(response);
		if (authorizedUser == null) {
			return response;
		}
		// validate all DestinationSession fields are specified.
		Destinationsession ds = validator.checkDestinationsession(parameters.getDestinationsession(), response);
		if (ds == null) {
			return response;
		}

		AgentCredential existingCred = null;
		Destination d = null; // FIXME new session context
		Service service = null; // FIXME
		Address address = null; // FIXME

		UserIdentity id = d2a.mapUserIdentity(existingCred.getCertificateChain());
		// check that the FTS signature is ok for the targetagent.
		if (!SignatureUtils.checkFlowTargetSessionSignature(service.getServiceName(), id, ds)) {
			setError(ErrorCode.InvalidSignatureDestinationSession, response);
			return response;
		}

		// lookup existing service exists
		Destination dest = a2d.mapDestination(address, service, ds);
		destinationService.setSession(dest);

		Zone zone = getAgentService().getZone();

		boolean more = true;
		// fetch ALL Channels which have this Destination as Destination.
		for (int pageNo = 0; more; pageNo++) {
			ChannelAuthorizationSearchCriteria sc = new ChannelAuthorizationSearchCriteria(new PageSpecifier(pageNo,
					getBatchSize()));
			sc.setDomain(existingCred.getDomain());
			sc.getDestination().setLocalName(address.getLocalName());
			sc.getDestination().setDomainName(address.getDomain().getDomainName());
			sc.getDestination().setServiceName(service.getServiceName());

			List<Channel> channels = channelService.search(zone, sc);
			for (Channel channel : channels) {
				channelService.setChannelDestinationSession(zone, channel.getId(), d.getDestinationSession());
			}
			if (channels.isEmpty()) {
				more = false;
			}
		}

		response.setSuccess(true);
		return response;
	}

	@Override
	public DownloadResponse download(Download parameters) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ReceiveResponse receive(Receive parameters) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ListChannelResponse listChannel(ListChannel parameters) {
		ListChannelResponse response = new ListChannelResponse();
		PKIXCertificate authorizedUser = checkUserAuthorized(response);
		if (authorizedUser == null) {
			return response;
		}

		Service service = null; // FIXME session context
		Zone zone = getAgentService().getZone();

		ChannelAuthorizationSearchCriteria sc = new ChannelAuthorizationSearchCriteria(
				a2d.mapPage(parameters.getPage()));
		sc.setDomainName(authorizedUser.getTdmxDomainName());
		sc.getDestination().setLocalName(authorizedUser.getTdmxUserName());
		sc.getDestination().setDomainName(authorizedUser.getTdmxDomainName());
		sc.getDestination().setServiceName(service.getServiceName());
		if (parameters.getOrigin() != null) {
			sc.getOrigin().setDomainName(parameters.getOrigin().getDomain());
			sc.getOrigin().setLocalName(parameters.getOrigin().getLocalname());
		}
		List<Channel> channels = channelService.search(zone, sc);
		for (Channel c : channels) {
			response.getChannelinfos().add(d2a.mapChannelInfo(c));
		}

		response.setSuccess(true);
		return response;
	}

	@Override
	public PrepareResponse prepare(Prepare parameters) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ForgetResponse forget(Forget parameters) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public RollbackResponse rollback(Rollback parameters) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CommitResponse commit(Commit parameters) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public RecoverResponse recover(Recover parameters) {
		// TODO Auto-generated method stub
		return null;
	}

	// -------------------------------------------------------------------------
	// PROTECTED METHODS
	// -------------------------------------------------------------------------

	// -------------------------------------------------------------------------
	// PRIVATE METHODS
	// -------------------------------------------------------------------------

	private PKIXCertificate checkUserAuthorized(Acknowledge ack) {
		PKIXCertificate user = getAgentService().getAuthenticatedAgent();
		if (user == null) {
			setError(ErrorCode.MissingCredentials, ack);
			return null;
		}
		if (!user.isTdmxUserCertificate()) {
			setError(ErrorCode.NonUserAccess, ack);
			return null;
		}
		return user;
	}

	private void setError(ErrorCode ec, Acknowledge ack) {
		Error error = new Error();
		error.setCode(ec.getErrorCode());
		error.setDescription(ec.getErrorDescription());
		ack.setError(error);
		ack.setSuccess(false);
	}

	// -------------------------------------------------------------------------
	// PUBLIC ACCESSORS (GETTERS / SETTERS)
	// -------------------------------------------------------------------------

	public ServerSecurityManager<MDSServerSession> getSecurityManager() {
		return securityManager;
	}

	public void setSecurityManager(ServerSecurityManager<MDSServerSession> securityManager) {
		this.securityManager = securityManager;
	}

	public DomainService getDomainService() {
		return domainService;
	}

	public void setDomainService(DomainService domainService) {
		this.domainService = domainService;
	}

	public AddressService getAddressService() {
		return addressService;
	}

	public void setAddressService(AddressService addressService) {
		this.addressService = addressService;
	}

	public ServiceService getServiceService() {
		return serviceService;
	}

	public void setServiceService(ServiceService serviceService) {
		this.serviceService = serviceService;
	}

	public ChannelService getChannelService() {
		return channelService;
	}

	public void setChannelService(ChannelService channelService) {
		this.channelService = channelService;
	}

	public DestinationService getDestinationService() {
		return destinationService;
	}

	public void setDestinationService(DestinationService destinationService) {
		this.destinationService = destinationService;
	}

	public AgentCredentialFactory getCredentialFactory() {
		return credentialFactory;
	}

	public void setCredentialFactory(AgentCredentialFactory credentialFactory) {
		this.credentialFactory = credentialFactory;
	}

	public AgentCredentialService getCredentialService() {
		return credentialService;
	}

	public void setCredentialService(AgentCredentialService credentialService) {
		this.credentialService = credentialService;
	}

	public AgentCredentialValidator getCredentialValidator() {
		return credentialValidator;
	}

	public void setCredentialValidator(AgentCredentialValidator credentialValidator) {
		this.credentialValidator = credentialValidator;
	}

	public int getBatchSize() {
		return batchSize;
	}

	public void setBatchSize(int batchSize) {
		this.batchSize = batchSize;
	}

}
