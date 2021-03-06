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
package org.tdmx.client.crypto.certificate;

import java.util.Calendar;
import java.util.Date;

import org.tdmx.client.crypto.algorithm.PublicKeyAlgorithm;
import org.tdmx.client.crypto.algorithm.SignatureAlgorithm;

public class CertificateFacade {
	private CertificateFacade() {
	}

	public static Calendar getNow() {
		Calendar now = Calendar.getInstance();
		now.setTime(new Date());
		now.set(Calendar.MILLISECOND, 0);
		return now;
	}

	public static Calendar getNowPlusYears(int years) {
		Calendar later = getNow();
		later.add(Calendar.YEAR, years);
		return later;
	}

	public static TdmxZoneInfo createZI(String zoneRoot, int credentialType) {
		TdmxZoneInfo zi = new TdmxZoneInfo(1, zoneRoot, credentialType);
		return zi;
	}

	public static ServerIpCredentialSpecifier createServerIp(String ipAddress, Calendar validStart, Calendar validEnd) {
		ServerIpCredentialSpecifier req = new ServerIpCredentialSpecifier(ipAddress);
		req.setNotBefore(validStart);
		req.setNotAfter(validEnd);
		req.setKeyAlgorithm(PublicKeyAlgorithm.RSA2048);
		req.setSignatureAlgorithm(SignatureAlgorithm.SHA_256_RSA);
		return req;
	}

	public static ZoneAdministrationCredentialSpecifier createZACS(int version, String zoneRoot, Calendar validStart,
			Calendar validEnd) {
		ZoneAdministrationCredentialSpecifier req = new ZoneAdministrationCredentialSpecifier(version, zoneRoot);

		req.setCn("name");
		req.setTelephoneNumber("0417100000");
		req.setEmailAddress("pjk@gmail.com");
		req.setOrgUnit("IT");
		req.setOrg("mycompany");
		req.setLocation("Zug");
		req.setCountry("CH");
		req.setNotBefore(validStart);
		req.setNotAfter(validEnd);
		req.setKeyAlgorithm(PublicKeyAlgorithm.RSA2048);
		req.setSignatureAlgorithm(SignatureAlgorithm.SHA_256_RSA);
		return req;
	}

	public static PKIXCredential createServerIp(String ipAddress, int validForYears) throws CryptoCertificateException {
		ServerIpCredentialSpecifier req = createServerIp(ipAddress, getNow(), getNowPlusYears(validForYears));
		return CredentialUtils.createServerIpCredential(req);
	}

	public static PKIXCredential createZAC(String zoneApex, int validForYears) throws CryptoCertificateException {
		ZoneAdministrationCredentialSpecifier req = createZACS(1, zoneApex, getNow(), getNowPlusYears(validForYears));
		return CredentialUtils.createZoneAdministratorCredential(req);
	}

	public static DomainAdministrationCredentialSpecifier createDACS(PKIXCredential zac, Calendar from, Calendar to) {

		DomainAdministrationCredentialSpecifier req = new DomainAdministrationCredentialSpecifier("subdomain", zac);
		req.setNotBefore(from);
		req.setNotAfter(to);
		req.setKeyAlgorithm(PublicKeyAlgorithm.RSA2048);
		req.setSignatureAlgorithm(SignatureAlgorithm.SHA_256_RSA);
		return req;
	}

	public static PKIXCredential createDAC(PKIXCredential zac, int validForYears) throws CryptoCertificateException {
		DomainAdministrationCredentialSpecifier req = createDACS(zac, getNow(), getNowPlusYears(validForYears));

		return CredentialUtils.createDomainAdministratorCredential(req);
	}

	public static UserCredentialSpecifier createUCS(PKIXCredential dac, Calendar from, Calendar to) {
		UserCredentialSpecifier req = new UserCredentialSpecifier();
		req.setDomainAdministratorCredential(dac);
		req.setName("username123");
		req.setNotBefore(from);
		req.setNotAfter(to);
		req.setKeyAlgorithm(PublicKeyAlgorithm.RSA2048);
		req.setSignatureAlgorithm(SignatureAlgorithm.SHA_256_RSA);
		return req;
	}

	public static PKIXCredential createUC(PKIXCredential dac, int validForYears) throws CryptoCertificateException {
		UserCredentialSpecifier req = createUCS(dac, getNow(), getNowPlusYears(validForYears));
		PKIXCredential cred = CredentialUtils.createUserCredential(req);
		return cred;
	}

}
