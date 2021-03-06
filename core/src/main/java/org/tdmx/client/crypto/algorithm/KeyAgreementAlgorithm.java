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
package org.tdmx.client.crypto.algorithm;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.KeyAgreement;

import org.tdmx.client.crypto.entropy.EntropySource;
import org.tdmx.client.crypto.scheme.CryptoException;
import org.tdmx.client.crypto.scheme.CryptoResultCode;

public enum KeyAgreementAlgorithm {

	ECDH256(256, "EC", "ECDH", "secp256r1", "X.509", "PKCS#8"),
	ECDH384(384, "EC", "ECDH", "secp384r1", "X.509", "PKCS#8");

	private int keyLength;
	private String keyAlgorithm;
	private String agreementAlgorithm;
	private String parameter;
	private String publicKeyFormat;
	private String privateKeyFormat;

	private KeyAgreementAlgorithm(int keyLength, String keyAlgorithm, String agreementAlgorithm, String parameter,
			String publicKeyFormat, String privateKeyFormat) {
		this.keyLength = keyLength;
		this.keyAlgorithm = keyAlgorithm;
		this.agreementAlgorithm = agreementAlgorithm;
		this.parameter = parameter;
		this.publicKeyFormat = publicKeyFormat;
		this.privateKeyFormat = privateKeyFormat;
	}

	public KeyPair generateNewKeyPair() throws CryptoException {
		try {
			KeyPairGenerator keyGen = KeyPairGenerator.getInstance(keyAlgorithm);
			ECGenParameterSpec ecSpec = new ECGenParameterSpec(parameter);
			keyGen.initialize(ecSpec, EntropySource.getSecureRandom()); // set up // KeyAgreement
			KeyPair kp = keyGen.generateKeyPair();
			return kp;
		} catch (NoSuchAlgorithmException e) {
			throw new CryptoException(CryptoResultCode.ERROR_PK_ALGORITHM_MISSING, e);
		} catch (InvalidAlgorithmParameterException e) {
			throw new CryptoException(CryptoResultCode.ERROR_KA_ALGORITHM_PARAMETER_INVALID, e);
		}
	}

	public byte[] agreeKey(KeyPair ownKeyPair, PublicKey otherKey) throws CryptoException {
		try {
			KeyAgreement keyAgree = KeyAgreement.getInstance(agreementAlgorithm);
			try {
				keyAgree.init(ownKeyPair.getPrivate());
			} catch (InvalidKeyException e) {
				throw new CryptoException(CryptoResultCode.ERROR_KA_PRIVATE_KEY_SPEC_INVALID, e);
			}
			try {
				keyAgree.doPhase(otherKey, true);
			} catch (InvalidKeyException e) {
				throw new CryptoException(CryptoResultCode.ERROR_KA_PUBLIC_KEY_SPEC_INVALID, e);
			}
			byte[] sk = keyAgree.generateSecret();
			if (sk.length != keyLength / 8) {
				throw new CryptoException(CryptoResultCode.ERROR_KA_SHARED_SECRET_KEY_INVALID);
			}
			return sk;
		} catch (NoSuchAlgorithmException e) {
			throw new CryptoException(CryptoResultCode.ERROR_KA_ALGORITHM_MISSING, e);
		}
	}

	public PublicKey decodeX509EncodedPublicKey(byte[] publicKeyBytes) throws CryptoException {
		try {
			KeyFactory kf = KeyFactory.getInstance(keyAlgorithm);
			EncodedKeySpec eks = new X509EncodedKeySpec(publicKeyBytes);
			PublicKey publicKey = kf.generatePublic(eks);
			return publicKey;
		} catch (NoSuchAlgorithmException e) {
			throw new CryptoException(CryptoResultCode.ERROR_PK_ALGORITHM_MISSING, e);
		} catch (InvalidKeySpecException e) {
			throw new CryptoException(CryptoResultCode.ERROR_KA_PUBLIC_KEY_SPEC_INVALID, e);
		}
	}

	public PrivateKey decodeX509EncodedPrivateKey(byte[] privateKeyBytes) throws CryptoException {
		try {
			KeyFactory kf = KeyFactory.getInstance(keyAlgorithm);
			EncodedKeySpec eks = new PKCS8EncodedKeySpec(privateKeyBytes);
			PrivateKey privateKey = kf.generatePrivate(eks);
			return privateKey;
		} catch (NoSuchAlgorithmException e) {
			throw new CryptoException(CryptoResultCode.ERROR_PK_ALGORITHM_MISSING, e);
		} catch (InvalidKeySpecException e) {
			throw new CryptoException(CryptoResultCode.ERROR_KA_PUBLIC_KEY_SPEC_INVALID, e);
		}
	}

	public byte[] encodeX509PublicKey(PublicKey publicKey) throws CryptoException {
		if (!publicKeyFormat.equals(publicKey.getFormat())) {
			throw new CryptoException(CryptoResultCode.ERROR_ENCODED_KEY_FORMAT_INVALID);
		}
		return publicKey.getEncoded();
	}

	public byte[] encodeX509PrivateKey(PrivateKey privateKey) throws CryptoException {
		if (!privateKeyFormat.equals(privateKey.getFormat())) {
			throw new CryptoException(CryptoResultCode.ERROR_ENCODED_KEY_FORMAT_INVALID);
		}
		return privateKey.getEncoded();
	}

	public int getKeyLength() {
		return keyLength;
	}

	public String getParameter() {
		return parameter;
	}

	public String getKeyAlgorithm() {
		return keyAlgorithm;
	}

	public String getAgreementAlgorithm() {
		return agreementAlgorithm;
	}

}
