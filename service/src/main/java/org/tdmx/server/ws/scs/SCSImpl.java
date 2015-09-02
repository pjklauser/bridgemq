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
package org.tdmx.server.ws.scs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tdmx.client.crypto.certificate.PKIXCertificate;
import org.tdmx.core.api.v01.scs.Acknowledge;
import org.tdmx.core.api.v01.scs.Endpoint;
import org.tdmx.core.api.v01.scs.Error;
import org.tdmx.core.api.v01.scs.GetMDSSession;
import org.tdmx.core.api.v01.scs.GetMDSSessionResponse;
import org.tdmx.core.api.v01.scs.GetMOSSession;
import org.tdmx.core.api.v01.scs.GetMOSSessionResponse;
import org.tdmx.core.api.v01.scs.GetMRSSession;
import org.tdmx.core.api.v01.scs.GetMRSSessionResponse;
import org.tdmx.core.api.v01.scs.GetZASSession;
import org.tdmx.core.api.v01.scs.GetZASSessionResponse;
import org.tdmx.core.api.v01.scs.Session;
import org.tdmx.core.api.v01.scs.ws.SCS;
import org.tdmx.core.system.lang.StringUtils;
import org.tdmx.lib.control.datasource.ThreadLocalPartitionIdProvider;
import org.tdmx.lib.control.domain.AccountZone;
import org.tdmx.lib.control.service.AccountZoneService;
import org.tdmx.lib.zone.domain.AgentCredential;
import org.tdmx.lib.zone.domain.AgentCredentialStatus;
import org.tdmx.lib.zone.domain.Domain;
import org.tdmx.lib.zone.domain.Service;
import org.tdmx.lib.zone.service.AddressService;
import org.tdmx.lib.zone.service.AgentCredentialFactory;
import org.tdmx.lib.zone.service.AgentCredentialService;
import org.tdmx.lib.zone.service.AgentCredentialValidator;
import org.tdmx.lib.zone.service.ChannelService;
import org.tdmx.lib.zone.service.DestinationService;
import org.tdmx.lib.zone.service.DomainService;
import org.tdmx.lib.zone.service.ServiceService;
import org.tdmx.server.session.ServerSessionAllocationService;
import org.tdmx.server.session.WebServiceSessionEndpoint;
import org.tdmx.server.ws.ErrorCode;
import org.tdmx.server.ws.security.service.AuthenticatedClientLookupService;

public class SCSImpl implements SCS {

	// -------------------------------------------------------------------------
	// PUBLIC CONSTANTS
	// -------------------------------------------------------------------------

	// -------------------------------------------------------------------------
	// PROTECTED AND PRIVATE VARIABLES AND CONSTANTS
	// -------------------------------------------------------------------------
	private static final Logger log = LoggerFactory.getLogger(SCSImpl.class);

	private AuthenticatedClientLookupService authenticatedClientService;

	private AccountZoneService accountZoneService;
	private ThreadLocalPartitionIdProvider partitionIdProvider;

	private ServerSessionAllocationService sessionAllocationService;

	private DomainService domainService;
	private AddressService addressService;
	private ServiceService serviceService;
	private ChannelService channelService;
	private DestinationService destinationService;

	private AgentCredentialFactory credentialFactory;
	private AgentCredentialService credentialService;
	private AgentCredentialValidator credentialValidator;

	// -------------------------------------------------------------------------
	// CONSTRUCTORS
	// -------------------------------------------------------------------------

	// -------------------------------------------------------------------------
	// PUBLIC METHODS
	// -------------------------------------------------------------------------

	@Override
	public GetMRSSessionResponse getMRSSession(GetMRSSession parameters) {
		GetMRSSessionResponse response = new GetMRSSessionResponse();
		PKIXCertificate sp = checkNonTDMXClientAuthenticated(response);
		if (sp == null) {
			return response;
		}

		String serviceProviderName = sp.getCommonName();

		// TODO check DNS trust that the channel's origin or destination domain points to the serviceProvider

		// TODO SCS ssl trust - all TDMX usercerts and otherwise fallback to std. PKIX

		response.setSuccess(true);
		return response;
	}

	@Override
	public GetMDSSessionResponse getMDSSession(GetMDSSession parameters) {
		GetMDSSessionResponse response = new GetMDSSessionResponse();
		PKIXCertificate user = checkUserAuthorized(response);
		if (user == null) {
			return response;
		}

		String serviceName = parameters.getServicename();
		if (!StringUtils.hasText(serviceName)) {
			setError(ErrorCode.MissingServiceName, response);
			return response;
		}

		String zoneApex = user.getTdmxZoneInfo().getZoneRoot();

		AccountZone az = getAccountZone(zoneApex, response);
		if (az == null) {
			return response;
		}

		AgentCredential existingCred = getAgentCredential(az, user, response);
		if (existingCred == null) {
			return response;
		}

		Service service = getService(az, existingCred.getDomain(), serviceName, response);
		if (service == null) {
			return response;
		}

		WebServiceSessionEndpoint ep = sessionAllocationService.associateMDSSession(az, existingCred, service);
		if (ep == null) {
			setError(ErrorCode.NoSessionCapacity, response);
			return response;
		}

		Session session = new Session();
		session.setSessionId(ep.getSessionId());
		session.setZone(existingCred.getZone().getZoneApex());
		session.setAddress(existingCred.getAddress().getLocalName());
		session.setDomain(existingCred.getDomain().getDomainName());
		session.setService(service.getServiceName());
		response.setSession(session);

		Endpoint endpoint = new Endpoint();
		endpoint.setTlsCertificate(ep.getPublicCertificate().getX509Encoded());
		endpoint.setUrl(ep.getHttpsUrl());
		response.setEndpoint(endpoint);

		response.setSuccess(true);
		return response;
	}

	@Override
	public GetMOSSessionResponse getMOSSession(GetMOSSession parameters) {
		GetMOSSessionResponse response = new GetMOSSessionResponse();
		PKIXCertificate user = checkUserAuthorized(response);
		if (user == null) {
			return response;
		}

		String zoneApex = user.getTdmxZoneInfo().getZoneRoot();

		AccountZone az = getAccountZone(zoneApex, response);
		if (az == null) {
			return response;
		}

		AgentCredential existingCred = getAgentCredential(az, user, response);
		if (existingCred == null) {
			return response;
		}

		WebServiceSessionEndpoint ep = sessionAllocationService.associateMOSSession(az, existingCred);
		if (ep == null) {
			setError(ErrorCode.NoSessionCapacity, response);
			return response;
		}

		Session session = new Session();
		session.setSessionId(ep.getSessionId());
		session.setZone(existingCred.getZone().getZoneApex());
		session.setAddress(existingCred.getAddress().getLocalName());
		session.setDomain(existingCred.getDomain().getDomainName());
		session.setService(null);
		response.setSession(session);

		Endpoint endpoint = new Endpoint();
		endpoint.setTlsCertificate(ep.getPublicCertificate().getX509Encoded());
		endpoint.setUrl(ep.getHttpsUrl());
		response.setEndpoint(endpoint);

		response.setSuccess(true);
		return response;
	}

	@Override
	public GetZASSessionResponse getZASSession(GetZASSession parameters) {
		GetZASSessionResponse response = new GetZASSessionResponse();
		PKIXCertificate admin = checkZACorDACAuthorized(response);
		if (admin == null) {
			return response;
		}
		String zoneApex = admin.getTdmxZoneInfo().getZoneRoot();

		AccountZone az = getAccountZone(zoneApex, response);
		if (az == null) {
			return response;
		}

		AgentCredential existingCred = getAgentCredential(az, admin, response);
		if (existingCred == null) {
			return response;
		}

		WebServiceSessionEndpoint ep = sessionAllocationService.associateZASSession(az, existingCred);
		if (ep == null) {
			setError(ErrorCode.NoSessionCapacity, response);
			return response;
		}

		Session session = new Session();
		session.setSessionId(ep.getSessionId());
		session.setZone(existingCred.getZone().getZoneApex());
		session.setDomain(existingCred.getDomain().getDomainName());
		session.setAddress(null);
		session.setService(null);
		response.setSession(session);

		Endpoint endpoint = new Endpoint();
		endpoint.setTlsCertificate(ep.getPublicCertificate().getX509Encoded());
		endpoint.setUrl(ep.getHttpsUrl());
		response.setEndpoint(endpoint);

		response.setSuccess(true);
		return response;
	}

	// -------------------------------------------------------------------------
	// PROTECTED METHODS
	// -------------------------------------------------------------------------

	// -------------------------------------------------------------------------
	// PRIVATE METHODS
	// -------------------------------------------------------------------------

	private void setError(ErrorCode ec, Acknowledge ack) {
		Error error = new Error();
		error.setCode(ec.getErrorCode());
		error.setDescription(ec.getErrorDescription());
		ack.setError(error);
		ack.setSuccess(false);
	}

	private AccountZone getAccountZone(String zoneApex, Acknowledge ack) {
		AccountZone az = accountZoneService.findByZoneApex(zoneApex);
		if (az == null) {
			setError(ErrorCode.ZoneNotFound, ack);
			return null;
		}
		return az;
	}

	private PKIXCertificate checkUserAuthorized(Acknowledge ack) {
		PKIXCertificate user = authenticatedClientService.getAuthenticatedClient();
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

	private PKIXCertificate checkZACorDACAuthorized(Acknowledge ack) {
		PKIXCertificate user = authenticatedClientService.getAuthenticatedClient();
		if (user == null) {
			setError(ErrorCode.MissingCredentials, ack);
			return null;
		}
		if (!user.isTdmxZoneAdminCertificate() && !user.isTdmxDomainAdminCertificate()) {
			setError(ErrorCode.NonAdministratorAccess, ack);
			return null;
		}
		return user;
	}

	private PKIXCertificate checkNonTDMXClientAuthenticated(Acknowledge ack) {
		PKIXCertificate user = authenticatedClientService.getAuthenticatedClient();
		if (user == null) {
			setError(ErrorCode.MissingCredentials, ack);
			return null;
		}
		if (user.isTdmxUserCertificate() || user.isTdmxDomainAdminCertificate() || user.isTdmxZoneAdminCertificate()) {
			setError(ErrorCode.NonPKIXAccess, ack);
			return null;
		}
		return user;
	}

	private AgentCredential getAgentCredential(AccountZone az, PKIXCertificate cert, Acknowledge ack) {
		// check the credential used exists and is active.
		partitionIdProvider.setPartitionId(az.getZonePartitionId());
		try {
			AgentCredential existingUser = credentialService.findByFingerprint(cert.getFingerprint());
			if (existingUser == null) {
				setError(ErrorCode.UserCredentialNotFound, ack);
				return null;
			}
			if (AgentCredentialStatus.ACTIVE != existingUser.getCredentialStatus()) {
				setError(ErrorCode.SuspendedAccess, ack);
				return null;
			}
			// paranoia checks - in case the fingerprint matches some other cert by mistake
			if (!existingUser.getZone().getZoneApex().equals(cert.getTdmxZoneInfo().getZoneRoot())) {
				setError(ErrorCode.InvalidUserCredentials, ack);
				return null;
			}
			// paranoia checks - in case the fingerprint matches some other cert by mistake
			if (existingUser.getDomain() != null
					&& !existingUser.getDomain().getDomainName().equals(cert.getTdmxDomainName())) {
				setError(ErrorCode.InvalidUserCredentials, ack);
				return null;
			}
			// paranoia checks - in case the fingerprint matches some other cert by mistake
			if (existingUser.getAddress() != null
					&& !existingUser.getAddress().getLocalName().equals(cert.getTdmxUserName())) {
				setError(ErrorCode.InvalidUserCredentials, ack);
				return null;
			}
			return existingUser;
		} finally {
			partitionIdProvider.clearPartitionId();
		}
	}

	private Service getService(AccountZone az, Domain domain, String serviceName, Acknowledge ack) {
		// check the credential used exists and is active.
		partitionIdProvider.setPartitionId(az.getZonePartitionId());
		try {
			Service service = serviceService.findByName(domain, serviceName);
			if (service == null) {
				setError(ErrorCode.ServiceNotFound, ack);
				return null;
			}
			return service;
		} finally {
			partitionIdProvider.clearPartitionId();
		}
	}

	// -------------------------------------------------------------------------
	// PUBLIC ACCESSORS (GETTERS / SETTERS)
	// -------------------------------------------------------------------------

	public AuthenticatedClientLookupService getAuthenticatedClientService() {
		return authenticatedClientService;
	}

	public void setAuthenticatedClientService(AuthenticatedClientLookupService authenticatedClientService) {
		this.authenticatedClientService = authenticatedClientService;
	}

	public ServerSessionAllocationService getSessionAllocationService() {
		return sessionAllocationService;
	}

	public void setSessionAllocationService(ServerSessionAllocationService sessionAllocationService) {
		this.sessionAllocationService = sessionAllocationService;
	}

	public ThreadLocalPartitionIdProvider getPartitionIdProvider() {
		return partitionIdProvider;
	}

	public void setPartitionIdProvider(ThreadLocalPartitionIdProvider partitionIdProvider) {
		this.partitionIdProvider = partitionIdProvider;
	}

	public AccountZoneService getAccountZoneService() {
		return accountZoneService;
	}

	public void setAccountZoneService(AccountZoneService accountZoneService) {
		this.accountZoneService = accountZoneService;
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

}