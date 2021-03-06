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
package org.tdmx.server.tos.client;

import org.tdmx.lib.control.domain.AccountZone;
import org.tdmx.lib.zone.domain.ChannelDestination;

/**
 * The client's interface to the TOS.
 * 
 * @author Peter
 *
 */
public interface TransferClientService {

	/**
	 * Initiate transfer of a Channel Message from MRS to the TOS of the MDS handling the destination session.
	 * 
	 * @param tosTcpAddress
	 *            the RPC endpoint address of the MDS-TOS handling the session ( null if not known ).
	 * @param sessionId
	 *            the sessionId which is actively handling the channel's destination ( null if not known ).
	 * @param accountzone
	 *            the detached accountzone
	 * @param dest
	 *            the channel destination
	 * @param stateId
	 *            the message's state identifier
	 * @return the relay status
	 */
	public TransferStatus transferMDS(String tosTcpAddress, String sessionId, AccountZone az, ChannelDestination dest,
			Long stateId);
}
