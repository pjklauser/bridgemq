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
package org.tdmx.client.cli.service;

import java.util.List;

import org.tdmx.client.crypto.certificate.PKIXCertificate;

/**
 * HTTPS truststore service.
 * 
 * @author Peter
 * 
 */
public interface ClientUITruststoreService {

	public boolean existsTruststore(String truststoreFilename);

	public List<PKIXCertificate> getTrustedCertificates(String truststoreFilename, String truststoreType,
			String truststorePassword);

	public boolean contansTrustedCertificate(PKIXCertificate rootCertificate, String truststoreFilename,
			String truststoreType, String truststorePassword);

	public boolean addTrustedCertificate(PKIXCertificate rootCertificate, String truststoreFilename,
			String truststoreType, String truststorePassword);

	public boolean removeTrustedCertificate(PKIXCertificate rootCertificate, String truststoreFilename,
			String truststoreType, String truststorePassword);
}
