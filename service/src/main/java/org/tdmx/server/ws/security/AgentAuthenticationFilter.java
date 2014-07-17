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
package org.tdmx.server.ws.security;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tdmx.server.ws.security.service.AgentCredentialAuthorizationService;
import org.tdmx.server.ws.security.service.AgentCredentialAuthorizationService.AuthorizationFailureCode;
import org.tdmx.server.ws.security.service.AgentCredentialAuthorizationService.AuthorizationResult;
import org.tdmx.server.ws.security.service.AuthenticatedAgentService;

public class AgentAuthenticationFilter implements Filter {

	// -------------------------------------------------------------------------
	// PUBLIC CONSTANTS
	// -------------------------------------------------------------------------

	// -------------------------------------------------------------------------
	// PROTECTED AND PRIVATE VARIABLES AND CONSTANTS
	// -------------------------------------------------------------------------
	private static final Logger log = LoggerFactory.getLogger(AgentAuthenticationFilter.class);

	private static String CLIENT_CERTIFICATE = "javax.servlet.request.X509Certificate";
	private static Map<AuthorizationFailureCode, String> errorMessageMap = new HashMap<>();
	static {
		errorMessageMap.put(AuthorizationFailureCode.BAD_CERTIFICATE, "Bad Certificate.");
		errorMessageMap.put(AuthorizationFailureCode.NON_TDMX, "Certificate is non TDMX.");
		errorMessageMap.put(AuthorizationFailureCode.MISSING_CERT, "Missing Certificate.");
		errorMessageMap.put(AuthorizationFailureCode.UNKNOWN_ZONE, "Unknown Zone.");
		errorMessageMap.put(AuthorizationFailureCode.UNKNOWN_AGENT, "Unknown Certificate.");
		errorMessageMap.put(AuthorizationFailureCode.AGENT_BLOCKED, "Blocked.");
	}

	private AgentCredentialAuthorizationService authorizationService;
	private AuthenticatedAgentService authenticatedAgentService;

	// -------------------------------------------------------------------------
	// CONSTRUCTORS
	// -------------------------------------------------------------------------

	// -------------------------------------------------------------------------
	// PUBLIC METHODS
	// -------------------------------------------------------------------------

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		log.debug("init");
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException,
			ServletException {
		if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
			throw new ServletException("AgentAuthenticationFilter just supports HTTP requests");
		}
		HttpServletRequest httpRequest = (HttpServletRequest) request;
		HttpServletResponse httpResponse = (HttpServletResponse) response;

		try {
			log.debug("doFilter");

			X509Certificate[] certs = (X509Certificate[]) httpRequest.getAttribute(CLIENT_CERTIFICATE);
			AuthorizationResult authorization = getAuthorizationService().isAuthorized(certs);
			if (authorization.getFailureCode() == null) {
				getAuthenticatedAgentService().setAuthenticatedAgent(authorization);

				// the AuthorizationLookupService will give the agent further down the chain.
				chain.doFilter(request, response);
			} else {
				// 401 - access denied, message mapped from errorMessageMap
				httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED,
						errorMessageMap.get(authorization.getFailureCode()));
			}
		} finally {
			getAuthenticatedAgentService().clearAuthenticatedAgent();
		}
	}

	@Override
	public void destroy() {
		log.debug("destroy");
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

	public AgentCredentialAuthorizationService getAuthorizationService() {
		return authorizationService;
	}

	public void setAuthorizationService(AgentCredentialAuthorizationService authorizationService) {
		this.authorizationService = authorizationService;
	}

	public AuthenticatedAgentService getAuthenticatedAgentService() {
		return authenticatedAgentService;
	}

	public void setAuthenticatedAgentService(AuthenticatedAgentService authenticatedAgentService) {
		this.authenticatedAgentService = authenticatedAgentService;
	}

}
