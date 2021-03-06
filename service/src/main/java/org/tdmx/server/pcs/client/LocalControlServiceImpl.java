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
package org.tdmx.server.pcs.client;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tdmx.client.crypto.certificate.PKIXCertificate;
import org.tdmx.lib.common.domain.ProcessingState;
import org.tdmx.lib.control.domain.PartitionControlServer;
import org.tdmx.lib.control.domain.Segment;
import org.tdmx.lib.control.job.NamedThreadFactory;
import org.tdmx.lib.control.service.PartitionControlServerService;
import org.tdmx.server.cache.CacheInvalidationInstruction;
import org.tdmx.server.cache.CacheInvalidationListener;
import org.tdmx.server.cache.CacheInvalidationNotifier;
import org.tdmx.server.pcs.RelayControlService;
import org.tdmx.server.pcs.SessionControlService;
import org.tdmx.server.pcs.SessionHandle;
import org.tdmx.server.pcs.protobuf.Cache.CacheServiceProxy;
import org.tdmx.server.pcs.protobuf.Cache.InvalidateCacheRequest;
import org.tdmx.server.pcs.protobuf.Cache.InvalidateCacheResponse;
import org.tdmx.server.pcs.protobuf.Common.AttributeValue.AttributeId;
import org.tdmx.server.pcs.protobuf.PCSServer.FindApiSessionResponse;
import org.tdmx.server.runtime.Manageable;
import org.tdmx.server.session.WebServiceSessionEndpoint;
import org.tdmx.server.ws.ErrorCode;
import org.tdmx.server.ws.session.WebServiceApiName;

import com.google.protobuf.BlockingService;
import com.google.protobuf.RpcController;
import com.google.protobuf.ServiceException;
import com.googlecode.protobuf.pro.duplex.CleanShutdownHandler;
import com.googlecode.protobuf.pro.duplex.PeerInfo;
import com.googlecode.protobuf.pro.duplex.RpcClientChannel;
import com.googlecode.protobuf.pro.duplex.RpcConnectionEventNotifier;
import com.googlecode.protobuf.pro.duplex.client.DuplexTcpClientPipelineFactory;
import com.googlecode.protobuf.pro.duplex.client.RpcClientConnectionWatchdog;
import com.googlecode.protobuf.pro.duplex.execute.RpcServerCallExecutor;
import com.googlecode.protobuf.pro.duplex.execute.ThreadPoolCallExecutor;
import com.googlecode.protobuf.pro.duplex.listener.RpcConnectionEventListener;
import com.googlecode.protobuf.pro.duplex.logging.CategoryPerServiceLogger;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

/**
 * The PCC ( PartitionControlClient ) is multi-functional service connecting to the PCS server.
 * 
 * The CacheInvalidationListener sends the cache invalidation request to ONE PCS server, which then sends the request
 * BACK to this server and all other PCS clients via reverse RPC.
 * 
 * @author Peter
 *
 */
public class LocalControlServiceImpl
		implements SessionControlService, RelayControlService, CacheInvalidationNotifier, Manageable {

	// -------------------------------------------------------------------------
	// PUBLIC CONSTANTS
	// -------------------------------------------------------------------------

	// -------------------------------------------------------------------------
	// PROTECTED AND PRIVATE VARIABLES AND CONSTANTS
	// -------------------------------------------------------------------------
	private static Logger log = LoggerFactory.getLogger(LocalControlServiceImpl.class);

	private static final String PCS_ATTRIBUTE = "PCS";

	private int connectTimeoutMillis = 5000;
	private int connectResponseTimeoutMillis = 10000;
	private int coreRpcExecutorThreads = 2;
	private int maxRpcExecutorThreads = 10;
	private int ioThreads = 16;
	private int ioBufferSize = 1048576;
	private boolean tcpNoDelay = true;
	private long shutdownTimeoutMs = 10000;

	private DuplexTcpClientPipelineFactory clientFactory;
	private Bootstrap bootstrap;
	private CleanShutdownHandler shutdownHandler;

	private List<PartitionControlServer> serverList;
	private Map<PartitionControlServer, LocalControlServiceClient> serverProxyMap = new HashMap<>();

	private Segment segment = null;

	private PartitionControlServerService partitionServerService;

	/**
	 * Delegate for handling inbound Broadcast events.
	 */
	private CacheInvalidationListener cacheInvalidationListener;

	// -------------------------------------------------------------------------
	// CONSTRUCTORS
	// -------------------------------------------------------------------------

	// -------------------------------------------------------------------------
	// PUBLIC METHODS
	// -------------------------------------------------------------------------

	@Override
	public ProcessingState invalidateCache(Segment segment, CacheInvalidationInstruction instruction) {
		// if we are connected to any PCS at all, then taking one is enough.
		// we use a hash distribution of the unique ID but we could do round-robin instead.
		LocalControlServiceClient clientProxy = consistentHashToServer(instruction.getId());
		if (clientProxy != null) {
			return clientProxy.invalidateCache(segment, instruction);
		} else {
			String errorInfo = "No PCS client proxy found for " + consistentHashCode(instruction.getId());
			ProcessingState error = ProcessingState.error(ErrorCode.CacheInvalidationFailed.getErrorCode(),
					ErrorCode.CacheInvalidationFailed.getErrorDescription(instruction.getId(), instruction.getName(),
							instruction.getKey(), errorInfo));
			return error;
		}
	}

	@Override
	public WebServiceSessionEndpoint associateApiSession(SessionHandle sessionData, PKIXCertificate clientCertificate) {
		LocalControlServiceClient clientProxy = consistentHashToServer(sessionData.getSessionKey());
		if (clientProxy != null) {
			return clientProxy.associateApiSession(sessionData, clientCertificate);
		} else {
			log.warn("No PCS client proxy found for " + consistentHashCode(sessionData.getSessionKey()));
		}
		return null;
	}

	@Override
	public String assignRelayServer(String channelKey, String segment, Map<AttributeId, Long> attributes) {
		LocalControlServiceClient clientProxy = consistentHashToServer(channelKey);
		if (clientProxy != null) {
			return clientProxy.assignRelayServer(channelKey, segment, attributes);
		} else {
			log.warn("No PCS client proxy found for " + consistentHashCode(channelKey));
		}
		return null;
	}

	@Override
	public FindApiSessionResponse findApiSession(String segment, WebServiceApiName api, String sessionKey) {
		LocalControlServiceClient clientProxy = consistentHashToServer(sessionKey);
		if (clientProxy != null) {
			return clientProxy.findApiSession(segment, api, sessionKey);
		} else {
			log.warn("No PCS client proxy found for " + consistentHashCode(sessionKey));
		}
		return null;
	}

	@Override
	public void start(Segment segment, List<WebServiceApiName> apis) {
		this.segment = segment;
		try {
			clientFactory = new DuplexTcpClientPipelineFactory();

			clientFactory.setConnectResponseTimeoutMillis(connectResponseTimeoutMillis);
			RpcServerCallExecutor rpcExecutor = new ThreadPoolCallExecutor(coreRpcExecutorThreads,
					maxRpcExecutorThreads);
			clientFactory.setRpcServerCallExecutor(rpcExecutor);

			// RPC payloads are uncompressed when logged - so reduce logging
			CategoryPerServiceLogger logger = new CategoryPerServiceLogger();
			logger.setLogRequestProto(false);
			logger.setLogResponseProto(false);
			clientFactory.setRpcLogger(logger);

			// Set up the event pipeline factory.
			// setup a RPC event listener - it just logs what happens
			RpcConnectionEventNotifier rpcEventNotifier = new RpcConnectionEventNotifier();

			final RpcConnectionEventListener listener = new RpcConnectionEventListener() {

				@Override
				public void connectionReestablished(RpcClientChannel clientChannel) {
					log.info("connectionReestablished " + clientChannel);
					connection(clientChannel);
				}

				@Override
				public void connectionOpened(RpcClientChannel clientChannel) {
					log.info("connectionOpened " + clientChannel);
					connection(clientChannel);
				}

				@Override
				public void connectionLost(RpcClientChannel clientChannel) {
					log.info("connectionLost " + clientChannel);
					disconnection(clientChannel);
				}

				@Override
				public void connectionChanged(RpcClientChannel clientChannel) {
					log.info("connectionChanged " + clientChannel);
					connection(clientChannel);
				}

				private void disconnection(RpcClientChannel clientChannel) {
					PartitionControlServer pcs = getPartitionControlServer(clientChannel);
					serverProxyMap.remove(pcs);
					clientChannel.setOobMessageCallback(null, null);
				}

				private void connection(RpcClientChannel clientChannel) {
					PartitionControlServer pcs = getPartitionControlServer(clientChannel);
					serverProxyMap.put(pcs, new LocalControlServiceClient(clientChannel));
				}

				private PartitionControlServer getPartitionControlServer(RpcClientChannel clientChannel) {
					PartitionControlServer pcs = (PartitionControlServer) clientChannel.getAttribute(PCS_ATTRIBUTE);
					if (pcs == null) {
						throw new IllegalStateException("No PCS attribute on clientChannel " + clientChannel);
					}
					return pcs;
				}
			};
			rpcEventNotifier.addEventListener(listener);
			clientFactory.registerConnectionEventListener(rpcEventNotifier);

			BlockingService cacheServiceProxy = CacheServiceProxy
					.newReflectiveBlockingService(new CacheServiceProxy.BlockingInterface() {
						@Override
						public InvalidateCacheResponse invalidateCache(RpcController controller,
								InvalidateCacheRequest request) throws ServiceException {
							CacheInvalidationListener cil = cacheInvalidationListener;
							if (cil != null) {
								CacheInvalidationInstruction cii = CacheInvalidationInstruction
										.newInstruction(request.getId(), request.getCacheName(), request.getKeyValue());
								log.info("Handling inbound cache invalidation request " + cii);
								cil.invalidateCache(cii);
							}
							InvalidateCacheResponse.Builder response = InvalidateCacheResponse.newBuilder();
							return response.build();
						}
					});

			clientFactory.getRpcServiceRegistry().registerService(cacheServiceProxy);

			bootstrap = new Bootstrap();
			EventLoopGroup workers = new NioEventLoopGroup(ioThreads, new NamedThreadFactory("PCS-client-workers"));

			bootstrap.group(workers);
			bootstrap.handler(clientFactory);
			bootstrap.channel(NioSocketChannel.class);
			bootstrap.option(ChannelOption.TCP_NODELAY, tcpNoDelay);
			bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis);
			bootstrap.option(ChannelOption.SO_SNDBUF, ioBufferSize);
			bootstrap.option(ChannelOption.SO_RCVBUF, ioBufferSize);

			RpcClientConnectionWatchdog watchdog = new RpcClientConnectionWatchdog(clientFactory, bootstrap);
			watchdog.setThreadName("PCC-PCS RpcClient Watchdog");
			rpcEventNotifier.addEventListener(watchdog);
			watchdog.start();

			shutdownHandler = new CleanShutdownHandler();
			shutdownHandler.addResource(workers);
			shutdownHandler.addResource(rpcExecutor);
			shutdownHandler.addResource(watchdog);

			serverList = partitionServerService.findBySegment(this.segment.getSegmentName());
			connectToPcsServers();
		} catch (Exception e) {
			throw new RuntimeException("Unable to do initial connect to PCS", e);
		}
	}

	@Override
	public void stop() {
		if (shutdownHandler != null) {
			Future<Boolean> shutdownResult = shutdownHandler.shutdownAwaiting(shutdownTimeoutMs);
			try {
				if (!shutdownResult.get()) {
					log.warn("Unable to shut down within " + shutdownTimeoutMs + "ms");
				} else {
					log.info("Shutdown RPC client.");
				}
			} catch (InterruptedException e) {
				log.warn("Interupted shutting down.", e);
			} catch (ExecutionException e) {
				log.warn("Error shutting down.", e);
			}
			shutdownHandler = null;
		}
		serverList = null;
		bootstrap = null;
		clientFactory = null;
		segment = null;
		// the serverProxyMap should clear automatically when each PCS server connection closes, however we do this just
		// in case.
		if (!serverProxyMap.isEmpty()) {
			log.warn("serverProxyMap should have been cleared on shutdown.");
			serverProxyMap.clear();
		}
	}

	// -------------------------------------------------------------------------
	// PROTECTED METHODS
	// -------------------------------------------------------------------------

	// -------------------------------------------------------------------------
	// PRIVATE METHODS
	// -------------------------------------------------------------------------

	/**
	 * Connect to each of the PCS servers.
	 * 
	 * @throws IOException
	 */
	private void connectToPcsServers() throws IOException {
		for (PartitionControlServer pcs : serverList) {
			if (pcs.getServerModulo() < 0 || pcs.getServerModulo() > serverList.size()) {
				throw new IllegalStateException("pcs modulo " + pcs.getServerModulo() + " inconsistent for " + pcs);
			}

			PeerInfo server = new PeerInfo(pcs.getIpAddress(), pcs.getPort());

			Map<String, Object> attributes = new HashMap<>();
			attributes.put(PCS_ATTRIBUTE, pcs);

			clientFactory.peerWith(server, bootstrap, attributes);
			// the event listener hooks up the localproxy
		}
	}

	private int consistentHashCode(String key) {
		if (serverList == null || serverList.isEmpty()) {
			return -1;
		}
		return key.hashCode() % serverList.size();
	}

	/**
	 * Return the PCS server to which the key maps to with a consistent hash.
	 * 
	 * @param key
	 * @return null if the PCS server is not connected to, otherwise the PCS server to which the key maps consistently.
	 */
	private LocalControlServiceClient consistentHashToServer(String key) {
		if (serverList == null || serverList.isEmpty()) {
			return null;
		}
		int serverNo = consistentHashCode(key);
		PartitionControlServer server = serverList.get(serverNo);
		LocalControlServiceClient localProxy = serverProxyMap.get(server);
		return localProxy;
	}

	// -------------------------------------------------------------------------
	// PUBLIC ACCESSORS (GETTERS / SETTERS)
	// -------------------------------------------------------------------------

	public int getConnectTimeoutMillis() {
		return connectTimeoutMillis;
	}

	public void setConnectTimeoutMillis(int connectTimeoutMillis) {
		this.connectTimeoutMillis = connectTimeoutMillis;
	}

	public int getConnectResponseTimeoutMillis() {
		return connectResponseTimeoutMillis;
	}

	public void setConnectResponseTimeoutMillis(int connectResponseTimeoutMillis) {
		this.connectResponseTimeoutMillis = connectResponseTimeoutMillis;
	}

	public int getCoreRpcExecutorThreads() {
		return coreRpcExecutorThreads;
	}

	public void setCoreRpcExecutorThreads(int coreRpcExecutorThreads) {
		this.coreRpcExecutorThreads = coreRpcExecutorThreads;
	}

	public int getMaxRpcExecutorThreads() {
		return maxRpcExecutorThreads;
	}

	public void setMaxRpcExecutorThreads(int maxRpcExecutorThreads) {
		this.maxRpcExecutorThreads = maxRpcExecutorThreads;
	}

	public int getIoThreads() {
		return ioThreads;
	}

	public void setIoThreads(int ioThreads) {
		this.ioThreads = ioThreads;
	}

	public int getIoBufferSize() {
		return ioBufferSize;
	}

	public void setIoBufferSize(int ioBufferSize) {
		this.ioBufferSize = ioBufferSize;
	}

	public boolean isTcpNoDelay() {
		return tcpNoDelay;
	}

	public void setTcpNoDelay(boolean tcpNoDelay) {
		this.tcpNoDelay = tcpNoDelay;
	}

	public long getShutdownTimeoutMs() {
		return shutdownTimeoutMs;
	}

	public void setShutdownTimeoutMs(long shutdownTimeoutMs) {
		this.shutdownTimeoutMs = shutdownTimeoutMs;
	}

	public PartitionControlServerService getPartitionServerService() {
		return partitionServerService;
	}

	public void setPartitionServerService(PartitionControlServerService partitionServerService) {
		this.partitionServerService = partitionServerService;
	}

	public CacheInvalidationListener getCacheInvalidationListener() {
		return cacheInvalidationListener;
	}

	public void setCacheInvalidationListener(CacheInvalidationListener cacheInvalidationListener) {
		this.cacheInvalidationListener = cacheInvalidationListener;
	}

}
