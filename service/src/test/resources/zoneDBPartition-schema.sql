
    create table Address (
        id bigint not null,
        localName varchar(255) not null,
        domain_id bigint not null,
        primary key (id)
    );

    create table AgentCredential (
        id bigint not null,
        certificateChainPem varchar(12000) not null,
        credentialStatus varchar(12) not null,
        credentialType varchar(4) not null,
        fingerprint varchar(64) not null,
        address_id bigint,
        domain_id bigint,
        zone_id bigint not null,
        primary key (id)
    );

    create table Domain (
        id bigint not null,
        domainName varchar(255) not null,
        zone_id bigint not null,
        primary key (id)
    );

    create table FlowTarget (
        id bigint not null,
        primaryIdentifier varchar(256),
        primaryScheme varchar(16),
        primarySession varbinary(8000),
        primaryValidFrom timestamp,
        secondaryIdentifier varchar(256),
        secondaryScheme varchar(16),
        secondarySession varbinary(8000),
        secondaryValidFrom timestamp,
        signatureAlgorithm varchar(255),
        signatureDate timestamp,
        signatureValue varchar(128),
        concurrency_id bigint not null,
        service_id bigint not null,
        target_id bigint not null,
        primary key (id),
        unique (concurrency_id)
    );

    create table FlowTargetConcurrency (
        id bigint not null,
        concurrencyLevel integer not null,
        concurrencyLimit integer not null,
        primary key (id)
    );

    create table Service (
        id bigint not null,
        concurrencyLimit integer not null,
        serviceName varchar(255) not null,
        domain_id bigint not null,
        primary key (id)
    );

    create table Zone (
        id bigint not null,
        accountZoneId bigint not null,
        zoneApex varchar(255) not null,
        primary key (id)
    );

    create table Channel (
        id bigint not null,
        destDomain varchar(255) not null,
        destAddress varchar(255) not null,
        destService varchar(255) not null,
        destSP varchar(255) not null,
        originDomain varchar(255) not null,
        originAddress varchar(255) not null,
        originSP varchar(255) not null,
        authorization_id bigint not null,
        domain_id bigint not null,
        primary key (id),
        unique (authorization_id)
    );

    create table ChannelAuthorization (
        id bigint not null,
        processingErrorCode integer,
        processingErrorMessage varchar(2048),
        processingStatus varchar(12) not null,
        processingId varchar(32) not null,
        processingTimestamp timestamp not null,
        recvGrant varchar(4),
        recvMaxPlaintextBytes numeric,
        recvSignAlg varchar(16),
        recvSignerPem varchar(12000),
        recvSignDate timestamp,
        recvSignature varchar(128),
        recvValidUntil timestamp,
        reqRecvGrant varchar(4),
        reqRecvMaxPlaintextBytes numeric,
        reqRecvSignAlg varchar(16),
        reqRecvSignerPem varchar(12000),
        reqRecvSignDate timestamp,
        reqRecvSignature varchar(128),
        reqRecvValidUntil timestamp,
        reqSendGrant varchar(4),
        reqSendMaxPlaintextBytes numeric,
        reqSendSignAlg varchar(16),
        reqSendSignerPem varchar(12000),
        reqSendSignDate timestamp,
        reqSendSignature varchar(128),
        reqSendValidUntil timestamp,
        sendGrant varchar(4),
        sendMaxPlaintextBytes numeric,
        sendSignAlg varchar(16),
        sendSignerPem varchar(12000),
        sendSignDate timestamp,
        sendSignature varchar(128),
        sendValidUntil timestamp,
        signatureAlg varchar(16),
        signerPem varchar(12000),
        signatureDate timestamp,
        signature varchar(128),
        undeliveredHigh numeric,
        undeliveredLow numeric,
        unsentHigh numeric,
        unsentLow numeric,
        primary key (id)
    );

    create table ChannelFlowTarget (
        id bigint not null,
        primaryIdentifier varchar(256),
        primaryScheme varchar(16),
        primarySession varbinary(8000),
        primaryValidFrom timestamp,
        secondaryIdentifier varchar(256),
        secondaryScheme varchar(16),
        secondarySession varbinary(8000),
        secondaryValidFrom timestamp,
        signatureAlgorithm varchar(16),
        targetPem varchar(12000),
        signatureDate timestamp,
        signature varchar(128),
        processingErrorCode integer,
        processingErrorMessage varchar(2048),
        processingStatus varchar(12) not null,
        processingId varchar(32) not null,
        processingTimestamp timestamp not null,
        targetFingerprint varchar(64) not null,
        channel_id bigint not null,
        primary key (id)
    );

    create table FlowQuota (
        id bigint not null,
        receiverStatus varchar(8) not null,
        senderStatus varchar(8) not null,
        undeliveredBytes numeric not null,
        unsentBytes numeric not null,
        primary key (id)
    );

    create table ChannelFlowOrigin (
        id bigint not null,
        sourceCertificateChainPem varchar(12000) not null,
        sourceFingerprint varchar(64) not null,
        undeliveredHigh numeric,
        undeliveredLow numeric,
        unsentHigh numeric,
        unsentLow numeric,
        flowTarget_id bigint not null,
        quota_id bigint not null,
        primary key (id),
        unique (quota_id)
    );

    create table ChannelFlowMessage (
        id bigint not null,
        msgId varchar(64) not null,
        payloadSize bigint not null,
        sentTimestamp timestamp not null,
        ttlTimestamp timestamp not null,
        flowOrigin_id bigint not null,
        primary key (id)
    );


    alter table Address 
        add constraint FK1ED033D4E7351234 
        foreign key (domain_id) 
        references Domain;

    alter table AgentCredential 
        add constraint FKDEAF7D1C981D3FB4 
        foreign key (zone_id) 
        references Zone;

    alter table AgentCredential 
        add constraint FKDEAF7D1CE7351234 
        foreign key (domain_id) 
        references Domain;

    alter table AgentCredential 
        add constraint FKDEAF7D1CE78C5580 
        foreign key (address_id) 
        references Address;

    alter table Channel 
        add constraint FK8F4414E3E7351234 
        foreign key (domain_id) 
        references Domain;

    alter table Channel 
        add constraint FK8F4414E398CAEC51 
        foreign key (authorization_id) 
        references ChannelAuthorization;
    
    alter table ChannelFlowMessage 
        add constraint FKA6AD3E162BD68783 
        foreign key (flowOrigin_id) 
        references ChannelFlowOrigin;

    alter table ChannelFlowOrigin 
        add constraint FKB6E7F337635CB8F2 
        foreign key (quota_id) 
        references FlowQuota;

    alter table ChannelFlowOrigin 
        add constraint FKB6E7F337EB7EBA3 
        foreign key (flowTarget_id) 
        references ChannelFlowTarget;

    alter table ChannelFlowTarget 
        add constraint FKBE84B5A294FB8720 
        foreign key (channel_id) 
        references Channel;

    alter table Domain 
        add constraint FK7A58C0E4981D3FB4 
        foreign key (zone_id) 
        references Zone;

    alter table FlowTarget 
        add constraint FK8F7F207F473F0CC1 
        foreign key (concurrency_id) 
        references FlowTargetConcurrency;

    alter table FlowTarget 
        add constraint FK8F7F207F38E4420B 
        foreign key (target_id) 
        references AgentCredential;

    alter table FlowTarget 
        add constraint FK8F7F207FEB7BD1E0 
        foreign key (service_id) 
        references Service;

    alter table Service 
        add constraint FKD97C5E95E7351234 
        foreign key (domain_id) 
        references Domain;

    create table MaxValueEntry (
         NAME varchar(255),
         value integer 
    ) ;
