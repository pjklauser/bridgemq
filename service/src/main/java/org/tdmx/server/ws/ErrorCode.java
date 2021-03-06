package org.tdmx.server.ws;

import org.tdmx.core.api.v01.common.Acknowledge;
import org.tdmx.core.api.v01.common.Error;

public enum ErrorCode {
	// authorization errors
	MissingCredentials(403, "Missing Credentials."),
	NonAdministratorAccess(403, "Non Administrator access."),
	NonUserAccess(403, "Non User access."),
	NonPKIXAccess(403, "Non PKIX access."),
	NonDnsAuthorizedPKIXAccess(403, "PKIX access of client not authorized by TDMX info in DNS."),
	RelayNotAllowedOnSameSCS(403, "Relay access not allowed when SCS URL is same for origin and destination."),
	SuspendedAccess(403, "Suspended access."),
	OutOfZoneAccess(403, "ZAC only authorized on own subdomains."),
	OutOfDomainAccess(403, "DAC only authorized on own domain."),
	// business logic errors
	NoSessionCapacity(500, "No capacity for new sessions."),
	ZoneNotFound(500, "Zone not found."),
	DomainNotSpecified(500, "Domain not supplied."),
	NotNormalizedDomain(500, "Domain not normalized to uppercase."),
	DomainExists(500, "Domain exists."),
	DomainNotFound(500, "Domain not found."),
	AddressExists(500, "Address exists."),
	AddressNotFound(500, "Address not found."),
	ServiceExists(500, "Service exists."),
	ServiceNotFound(500, "Service not found."),
	ChannelAuthorizationNotFound(500, "ChannelAuthorization not found."),
	ChannelAuthorizationExist(500, "ChannelAuthorization exists."),
	ChannelNotFound(500, "Channel not found."),

	InvalidOriginPermissionAdministratorCredentials(500, "Invalid DAC credentials in origin permission."),
	InvalidDestinationPermissionAdministratorCredentials(500, "Invalid DAC credentials in destination permission."),
	InvalidDomainAdministratorCredentials(500, "Invalid DAC credentials."),
	DomainAdministratorCredentialsExist(500, "DACs exists."),
	DomainAdministratorCredentialNotFound(500, "DAC not found."),
	DomainAdministratorCredentialSerialIncreasing(500, "DAC serial number must be > %d."),
	InvalidUserCredentials(500, "Invalid User credentials."),
	UserCredentialsExist(500, "UCs exists."),
	UserCredentialNotFound(500, "UC not found."),
	UserCredentialSerialIncreasing(500, "UC serial number must be > %d."),
	CredentialsExist(500, "Credentials exists."),
	AddressesExist(500, "Addresses exists."),
	ServicesExist(500, "Services exists."),
	MessageNotFound(500, "Message not found."),

	MissingAddress(500, "Address missing."),
	MissingDomain(500, "Domain missing."),
	MissingLocalname(500, "Localname missing."),
	MissingChannel(500, "Channel missing."),
	MissingAuthorization(500, "Authorization missing."),
	MissingChannelEndpoint(500, "ChannelEndpoint missing."),
	MissingChannelAuthorization(500, "ChannelAuthorization missing."),
	MissingFlowControlLimit(500, "FlowControlLimit missing."),
	MissingMaxRedeliveryCount(500, "Max redelivery count missing."),
	MissingMinRedeliveryDelay(500, "Min redelivery delay missing."),
	MissingDestinationSession(500, "DestinationSession missing."),
	MissingSegment(500, "Segment missing."),
	MissingService(500, "Service missing."),
	MissingServiceName(500, "Servicename missing."),
	MissingMessage(500, "Message missing."),
	MissingHeader(500, "Header missing."),
	MissingHeaderMsgId(500, "Header msgId missing."),
	MissingHeaderTTL(500, "Header TTL missing."),
	MissingHeaderTo(500, "Header To missing."),
	MissingHeaderEncryptionContextId(500, "Header EncryptionContextId missing."),
	MissingHeaderScheme(500, "Header Scheme missing."),
	MissingErrorCode(500, "Error code missing."),
	MissingErrorDescription(500, "Error description missing."),
	MissingPayload(500, "Payload missing."),
	MissingPayloadChunksMACofMACs(500, "Payload chunks MACofMACs missing."),
	MissingPayloadEncryptionContext(500, "Payload EncryptionContext missing."),
	MissingChunk(500, "Chunk missing."),
	MissingChunkMsgId(500, "Chunk msgId missing."),
	MissingChunkData(500, "Chunk data missing."),
	MissingChunkMac(500, "Chunk MAC missing."),
	MissingChunkContinuationId(500, "Chunk continuationId missing."),
	MissingTransactionXID(500, "Missing transaction xid."),
	MissingLocalTransactionClientId(500, "Missing localtransaction clientId."),
	UnknownEncryptionScheme(500, "Unknown encryption scheme."),
	InvalidChunkMac(500, "Chunk MAC invalid."),
	InvalidMessageMacOfMac(500, "Message MACofMAC is invalid."),
	InvalidTransactionTimeout(500, "Invalid transaction timeout %d < tx <= %d seconds."),
	MissingTransaction(500, "Missing transaction or localtransaction."),
	InvalidTransaction(500, "Use either transaction or localtransaction, not both."),
	XATransactionTimeout(500, "Transaction XID %s already timed-out."),
	XATransactionUnknown(500, "Transaction XID unknown."),
	XATransactionNotPrepared(500, "Transaction is not prepared."),
	MissingAcknowledgeMsgId(500, "Missing acknowledge msgId."),
	InvalidMessageSource(500, "Message source invalid."),
	InvalidChunkPos(500, "Chunk pos invalid."),
	InvalidPayloadLength(500, "Payload length invalid."),
	InvalidPlaintextLength(500, "Payload plaintext length invalid."),
	InvalidChunkContinuationId(500, "Chunk continuationId invalid."),
	InvalidChunkOrder(500, "Chunks should be received in order - repeating only the last sent."),
	InvalidReceiveTransaction(500, "Invalid receive transaction."),
	InvalidReceiveNonTransaction(500, "Invalid receive non transaction."),
	InvalidMaxRedeliveryCount(500, "Invalid max redelivery count missing."),
	InvalidMinRedeliveryDelay(500, "Invalid min redelivery delay missing."),

	MissingChannelDestinationService(500, "ChannelDestination Service missing."),
	MissingChannelEndpointDomain(500, "ChannelEndpoint Domain missing."),
	MissingChannelEndpointLocalname(500, "ChannelEndpoint Localname missing."),

	ChannelAuthorizationDomainMismatch(500, "ChannelAuthorization domain mismatch."),
	ChannelAuthorizationSignerDomainMismatch(500, "ChannelAuthorization signing DAC domain mismatch."),
	OriginPermissionSignerDomainMismatch(500, "Origin permission signing DAC domain mismatch."),
	DestinationPermissionSignerDomainMismatch(500, "Destination permission signing DAC domain mismatch."),
	ChannelOriginUserDomainMismatch(500, "Channel origin domain does not match the sending User's domain."),
	ChannelOriginUserLocalNameMismatch(500, "Channel origin localname does not match the sending User's local name."),
	ChannelDestinationUserDomainMismatch(
			500,
			"Channel destination domain does not match the destination User's domain."),
	ChannelDestinationUserLocalNameMismatch(
			500,
			"Channel destination localname does not match the destination User's local name."),

	MissingEndpointPermission(500, "Channel EndpointPermission missing."),
	InvalidSignatureEndpointPermission(500, "Channel EndpointPermission signature invalid."),
	InvalidSignatureChannelAuthorization(500, "ChannelAuthorization signature invalid."),
	InvalidSignatureDestinationSession(500, "DestinationSession signature invalid."),
	InvalidSignerDestinationSession(500, "DestinationSession signature not signed by authenticated client."),
	InvalidSignatureMessage(500, "Message signature invalid."),
	InvalidSignatureDeliveryReceipt(500, "DeliveryReceipt signature invalid."),
	InvalidMsgId(500, "Message ID invalid."),
	InvalidTimeout(500, "Timeout invalid. Value must be >= 0 and <= %d"),
	InvalidReceiveAcknowledgeMode(
			500,
			"Invalid receive. Either use transactional receiving or auto acknowledge, not both."),
	InvalidAcknowledgeNoReceive(
			500,
			"Non transactional acknowledge without receiving the acknowledged message ( or transaction timeout occured before acknowledge)."),
	InvalidAcknowledgeNotReceived(500, "Non transactional acknowledge of message not received."),
	MissingReceiveAcknowledgeMode(
			500,
			"Invalid receive. Either use transactional receiving or a non transactional session."),
	InvalidDeliveryReceiptMsgIdMismatch(
			500,
			"Non transactional session %s acknowledged receipt of message %s but expected message %s."),
	InvalidDeliveryReceiptSignatureMismatch(
			500,
			"Non transactional session %s acknowledged receipt of message %s but provided wrong signature."),
	InvalidDeliveryReceiptExtRefMismatch(
			500,
			"Non transactional session %s acknowledged receipt of message %s but provided wrong external reference."),
	InvalidDeliveryReceiptSignerMismatch(
			500,
			"The signer of the delivery receipt must be the same as the message recipient."),
	InvalidChannelOrigin(500, "Channel Origin invalid."),
	InvalidChannelDestination(500, "Channel Origin invalid."),
	MissingPermissionEndpointPermission(500, "EndpointPermission permission missing."),
	MissingPlaintextSizeEndpointPermission(500, "Channel EndpointPermission signature missing."),

	MissingAdministratorSignature(500, "AdministratorSignature missing."),
	MissingAdministratorIdentity(500, "AdministratorIdentity missing."),
	MissingUserIdentity(500, "UserIdentity missing."),
	MissingUserPublicKey(500, "UserIdentity public key missing."),

	MissingUserSignature(500, "UserSignature missing."),
	MissingDomainAdministratorPublicKey(500, "AdministratorIdentity public key missing."),
	MissingZoneRootPublicKey(500, "Zone root public key missing."),

	MissingSignatureValue(500, "Signaturevalue missing."),
	MissingSignature(500, "Signature missing."),
	MissingSignatureTimestamp(500, "Signature Timestamp missing."),
	MissingSignatureAlgorithm(500, "Signature Algorithm missing."),

	MissingDestinationSessionEncryptionContextIdentifier(500, "DestinationSession identifier missing."),
	MissingDestinationSessionScheme(500, "DestinationSession scheme missing."),
	MissingDestinationSessionSessionKey(500, "DestinationSession sessionKey missing."),

	MissingRelayPayload(500, "Relay payload missing."),
	RelayChannelDestinationForwards(500, "Relay ChannelDestinationSession only allowed from destination to origin."),
	RelayFlowControlForwards(500, "Relay Flowcontrol open only allowed from destination to origin."),
	RelayFlowControlBackwardsClosed(500, "Relay Flowcontrol should only be opened from destination to origin."),

	RelayClientConstructionFailed(501, "MRS client construction failed. Reason=%s"),
	RelayChannelAuthorizationFault(501, "Relay ChannelAuthorization to remote MRS failed. Reason=%s"),
	RelayDestinationSessionFault(501, "Relay ChannelDestinationSession to remote MRS failed. Reason=%s"),
	RelayChannelMessageFault(501, "Relay Message to remote MRS failed. Reason=%s"),
	RelayMessageChunkFault(501, "Relay Chunk to remote MRS failed. Reason=%s"),
	RelayFlowControlOpenFault(501, "Relay FlowControl open to remote MRS failed. Reason=%s"),
	RelayGetSessionFault(501, "Relay setup call to remote SCS failed. Reason=%s"),
	ChunkDataLost(501, "Chunk data lost for msgId=%s and pos=%d"),
	ChunkDataNotRelayed(501, "Chunk data not relayed on the same segment."),
	CacheInvalidationFailed(501, "Cache invalidation failed for event=%s, cache=%s and key=%s. Reason=%s"),

	SenderChannelAuthorizationMissing(300, "Missing confirmation of sender's requested EndpointPermission."),
	SenderChannelAuthorizationMismatch(
			301,
			"Mismatch of sender's requested EndpointPermission. Changed since last read."),
	SenderChannelAuthorizationProvided(
			302,
			"Provided confirmation of non existent sender's requested EndpointPermission."),
	ReceiverChannelAuthorizationMissing(303, "Missing confirmation of receiver's requested EndpointPermission."),
	ReceiverChannelAuthorizationMismatch(
			304,
			"Mismatch of receiver's requested EndpointPermission. Changed since last read."),
	ReceiverChannelAuthorizationProvided(
			305,
			"Provided confirmation of non existent receiver's requested EndpointPermission."),

	SubmitFlowControlClosed(306, "FlowControl closed - submit prohibited."),
	ReceiveFlowControlClosed(306, "FlowControl closed - relay prohibited."),
	SubmitChannelClosed(306, "No channel authorization - submit prohibited."),
	ReceiveChannelClosed(306, "No channel authorization - relay and receive prohibited."),
	SubmitQuotaNotSufficient(306, "Not enough quota - submit prohibited."),
	SubmitMessageTooLarge(306, "Message too large - submit prohibited."),

	DnsZoneApexMissing(306, "Unable to locate TDMX zone apex information for domain %s in DNS."),

	;
	private final int errorCode;
	private final String errorDescription;

	private ErrorCode(int ec, String description) {
		this.errorCode = ec;
		this.errorDescription = description;
	}

	public int getErrorCode() {
		return errorCode;
	}

	public String getErrorDescription() {
		return errorDescription;
	}

	public String getErrorDescription(Object... params) {
		return String.format(errorDescription, params);
	}

	public static void setError(ErrorCode ec, Acknowledge ack, Object... params) {
		Error error = new Error();
		error.setCode(ec.getErrorCode());
		error.setDescription(ec.getErrorDescription(params));
		ack.setError(error);
		ack.setSuccess(false);
	}

	public static void setError(ErrorCode ec, Acknowledge ack) {
		Error error = new Error();
		error.setCode(ec.getErrorCode());
		error.setDescription(ec.getErrorDescription());
		ack.setError(error);
		ack.setSuccess(false);
	}

}