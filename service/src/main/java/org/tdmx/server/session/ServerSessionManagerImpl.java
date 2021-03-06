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
package org.tdmx.server.session;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tdmx.client.crypto.certificate.CertificateIOUtils;
import org.tdmx.client.crypto.certificate.PKIXCertificate;
import org.tdmx.core.system.lang.StringUtils;
import org.tdmx.lib.control.domain.PartitionControlServer;
import org.tdmx.lib.control.domain.Segment;
import org.tdmx.lib.control.job.NamedThreadFactory;
import org.tdmx.lib.control.service.PartitionControlServerService;
import org.tdmx.server.pcs.ServiceHandle;
import org.tdmx.server.pcs.protobuf.Common.AttributeValue.AttributeId;
import org.tdmx.server.pcs.protobuf.Common.ObjectType;
import org.tdmx.server.pcs.protobuf.WSClient.AddCertificateRequest;
import org.tdmx.server.pcs.protobuf.WSClient.CreateSessionRequest;
import org.tdmx.server.pcs.protobuf.WSClient.GetStatisticsRequest;
import org.tdmx.server.pcs.protobuf.WSClient.RemoveCertificateRequest;
import org.tdmx.server.pcs.protobuf.WSClient.SessionManagerProxy;
import org.tdmx.server.runtime.Manageable;
import org.tdmx.server.runtime.RpcAddressUtils;
import org.tdmx.server.tos.TransferObjectReceiver;
import org.tdmx.server.ws.ServerRuntimeContextService;
import org.tdmx.server.ws.session.WebServiceApiName;
import org.tdmx.server.ws.session.WebServiceSession;
import org.tdmx.server.ws.session.WebServiceSessionManager;

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
import com.googlecode.protobuf.pro.duplex.execute.ServerRpcController;
import com.googlecode.protobuf.pro.duplex.execute.ThreadPoolCallExecutor;
import com.googlecode.protobuf.pro.duplex.listener.RpcConnectionEventListener;
import com.googlecode.protobuf.pro.duplex.logging.CategoryPerServiceLogger;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

/**
 * The ServerSessionManager manages all the WebServiceSessionManagers and is controlled by the PartitionControlService
 * to create sessions. Notifies the PartitionControlService when sessions are idle and are removed.
 * 
 * @author Peter
 * 
 */
public class ServerSessionManagerImpl implements Manageable, Runnable, SessionCertificateInvalidationService,
		SessionManagerProxy.BlockingInterface, TransferObjectReceiver {

	// -------------------------------------------------------------------------
	// PUBLIC CONSTANTS
	// -------------------------------------------------------------------------

	// -------------------------------------------------------------------------
	// PROTECTED AND PRIVATE VARIABLES AND CONSTANTS
	// -------------------------------------------------------------------------
	private static final Logger log = LoggerFactory.getLogger(ServerSessionManagerImpl.class);

	private static final String PCS_ATTRIBUTE = "PCS";

	/**
	 * RPC endpoint for the TOS TransferObjectService
	 */
	private String tosAddress;
	private int tosPort;

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
	private Map<PartitionControlServer, LocalControlServiceListenerClient> serverProxyMap = new HashMap<>();

	/**
	 * Delay in seconds between session timeout checks.
	 */
	private int timeoutCheckIntervalSec = 60;
	/**
	 * Sessions not used since sessionIdleTimeoutMinutes are considered orphaned and discarded.
	 */
	private int sessionIdleTimeoutMinutes = 30;

	// - internal
	private List<WebServiceApiName> apiList;
	private Map<WebServiceApiName, WebServiceSessionManagerHolder> apiManagerMap = null;
	private Segment segment;
	private ScheduledExecutorService scheduledThreadPool = null;
	private ExecutorService sessionTimeoutExecutor = null;

	/**
	 * The runtime context providing the server's IP and certificate.
	 */
	private ServerRuntimeContextService runtimeService;

	/**
	 * The WebServiceSessionManagers for MOS, MDS, MRS, ZAS. A subset of these are started at startup time and placed in
	 * the apiManagerMap.
	 */
	private List<WebServiceSessionManager> webServiceSessionManagers;

	/**
	 * The PartitionControlService gives us the information about the PCS servers.
	 */
	private PartitionControlServerService partitionServerService;

	// -------------------------------------------------------------------------
	// CONSTRUCTORS
	// -------------------------------------------------------------------------

	// -------------------------------------------------------------------------
	// PUBLIC METHODS
	// -------------------------------------------------------------------------

	@Override
	public org.tdmx.server.pcs.protobuf.WSClient.ServiceStatistic createSession(RpcController controller,
			CreateSessionRequest request) throws ServiceException {
		WebServiceApiName api = mapApi(request.getApiName());
		WebServiceSessionManagerHolder h = apiManagerMap.get(api);
		if (h != null) {
			String controllerId = getControllerId(ServerRpcController.getRpcChannel(controller));
			PKIXCertificate cert = CertificateIOUtils.safeDecodeX509(request.getClientCert().toByteArray());
			int loadValue = h.getSessionManager().createSession(request.getSessionId(), controllerId, cert,
					mapAttributes(request.getAttributeList()));
			h.setLoadValue(loadValue);

			// formulate the result
			org.tdmx.server.pcs.protobuf.WSClient.ServiceStatistic.Builder statisticBuilder = org.tdmx.server.pcs.protobuf.WSClient.ServiceStatistic
					.newBuilder();
			statisticBuilder.setApiName(api.name());
			statisticBuilder.setHttpsUrl(h.getHandle().getHttpsUrl());
			statisticBuilder.setLoadValue(loadValue);
			return statisticBuilder.build();
		}
		return null;
	}

	@Override
	public org.tdmx.server.pcs.protobuf.WSClient.ServiceStatistic addSessionCertificate(RpcController controller,
			AddCertificateRequest request) throws ServiceException {
		WebServiceApiName api = mapApi(request.getApiName());
		WebServiceSessionManagerHolder h = apiManagerMap.get(api);
		if (h != null) {
			PKIXCertificate cert = CertificateIOUtils.safeDecodeX509(request.getClientCert().toByteArray());
			int loadValue = h.getSessionManager().addCertificate(request.getSessionId(), cert);
			h.setLoadValue(loadValue);

			// formulate the result
			org.tdmx.server.pcs.protobuf.WSClient.ServiceStatistic.Builder statisticBuilder = org.tdmx.server.pcs.protobuf.WSClient.ServiceStatistic
					.newBuilder();
			statisticBuilder.setApiName(api.name());
			statisticBuilder.setHttpsUrl(h.getHandle().getHttpsUrl());
			statisticBuilder.setLoadValue(loadValue);
			return statisticBuilder.build();
		}
		return null;
	}

	@Override
	public org.tdmx.server.pcs.protobuf.WSClient.ServerServiceStatistics removeCertificate(RpcController controller,
			RemoveCertificateRequest request) throws ServiceException {
		for (Entry<WebServiceApiName, WebServiceSessionManagerHolder> apis : apiManagerMap.entrySet()) {
			WebServiceSessionManagerHolder h = apis.getValue();
			PKIXCertificate cert = CertificateIOUtils.safeDecodeX509(request.getClientCert().toByteArray());
			int loadValue = h.getSessionManager().removeCertificate(cert);
			h.setLoadValue(loadValue);

			return getStatistics(controller, null);
		}
		return null;
	}

	@Override
	public org.tdmx.server.pcs.protobuf.WSClient.ServerServiceStatistics getStatistics(RpcController controller,
			GetStatisticsRequest request) throws ServiceException {
		org.tdmx.server.pcs.protobuf.WSClient.ServerServiceStatistics.Builder stats = org.tdmx.server.pcs.protobuf.WSClient.ServerServiceStatistics
				.newBuilder();
		for (Entry<WebServiceApiName, WebServiceSessionManagerHolder> apis : apiManagerMap.entrySet()) {
			WebServiceSessionManagerHolder h = apis.getValue();

			org.tdmx.server.pcs.protobuf.WSClient.ServiceStatistic.Builder stat = org.tdmx.server.pcs.protobuf.WSClient.ServiceStatistic
					.newBuilder();
			stat.setApiName(apis.getKey().name());
			stat.setHttpsUrl(h.getHandle().getHttpsUrl());
			stat.setLoadValue(h.getLoadValue());
			stats.addStatistics(stat);
		}
		return stats.build();
	}

	@Override
	public void start(Segment segment, List<WebServiceApiName> apis) {
		if (sessionIdleTimeoutMinutes <= 0) {
			throw new IllegalArgumentException("sessionIdleTimeoutMinutes must be positive");
		}
		this.apiList = Collections.unmodifiableList(apis);
		this.segment = segment;
		scheduledThreadPool = Executors
				.newSingleThreadScheduledExecutor(new NamedThreadFactory("SessionTimeoutExecutionService"));

		sessionTimeoutExecutor = Executors.newFixedThreadPool(1, new NamedThreadFactory("SessionTimeoutExecutor"));
		scheduledThreadPool.scheduleWithFixedDelay(this, timeoutCheckIntervalSec, timeoutCheckIntervalSec,
				TimeUnit.SECONDS);

		// construct the service list of this server in the apiManagerMap
		apiManagerMap = new HashMap<>();
		for (WebServiceSessionManager mgr : webServiceSessionManagers) {
			if (apiList.contains(mgr.getApiName())) {
				ServiceHandle service = new ServiceHandle(segment.getSegmentName(), mgr.getApiName(), mgr.getHttpsUrl(),
						runtimeService.getPublicKey());
				WebServiceSessionManagerHolder serviceHolder = new WebServiceSessionManagerHolder(service, mgr);
				apiManagerMap.put(mgr.getApiName(), serviceHolder);
			}
		}

		String localHostAddress = null;
		try {
			localHostAddress = InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			log.warn("Unable to determine localhost IP address.", e);
		}

		String tosHostname = StringUtils.hasText(tosAddress) ? tosAddress : localHostAddress;
		String tosTcpEndpoint = RpcAddressUtils.getRosAddress(tosHostname, tosPort);

		// create the protobuf interface to ALL the PCS servers which are known for the segment
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

				private void connection(RpcClientChannel clientChannel) {
					log.info("initial connect " + clientChannel);

					PartitionControlServer pcs = getPartitionControlServer(clientChannel);
					LocalControlServiceListenerClient client = new LocalControlServiceListenerClient(clientChannel);
					serverProxyMap.put(pcs, client);

					// register ourselves to receive broadcast messages and session events from the PCS
					// telling it our own TOS address to give to others.
					client.registerServer(getManagedServiceList(), null, tosTcpEndpoint);
				}

				private void disconnection(RpcClientChannel clientChannel) {
					PartitionControlServer pcs = getPartitionControlServer(clientChannel);
					LocalControlServiceListenerClient client = serverProxyMap.remove(pcs);
					if (client != null) {
						for (Entry<WebServiceApiName, WebServiceSessionManagerHolder> apis : apiManagerMap.entrySet()) {
							String controllerId = getControllerId(clientChannel);
							log.info("Disconnecting controller " + controllerId + " from " + apis.getKey());
							apis.getValue().getSessionManager().disconnectController(controllerId);
						}
					}
				}

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
				public void connectionChanged(RpcClientChannel clientChannel) {
					log.info("connectionChanged " + clientChannel);
					connection(clientChannel);
				}

				@Override
				public void connectionLost(RpcClientChannel clientChannel) {
					log.info("connectionLost " + clientChannel);
					disconnection(clientChannel);
				}

			};
			rpcEventNotifier.addEventListener(listener);
			clientFactory.registerConnectionEventListener(rpcEventNotifier);

			BlockingService sessionManagerServiceProxy = SessionManagerProxy.newReflectiveBlockingService(this);
			clientFactory.getRpcServiceRegistry().registerService(sessionManagerServiceProxy);

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
			watchdog.setThreadName("SSM-PCS RPCClient Watchdog");
			rpcEventNotifier.addEventListener(watchdog);
			watchdog.start();

			shutdownHandler = new CleanShutdownHandler();
			shutdownHandler.addResource(workers);
			shutdownHandler.addResource(rpcExecutor);
			shutdownHandler.addResource(watchdog);

			// connect to each PCS server
			serverList = partitionServerService.findBySegment(this.segment.getSegmentName());
			connectToPcsServers();
			// after connection the PCS remote cannot distinguish if we are a SCS or WS client
			// until WS clients call registerServer

		} catch (Exception e) {
			throw new RuntimeException("Unable to do initial connect to PCS", e);
		}
	}

	@Override
	public void stop() {
		if (scheduledThreadPool != null) {
			scheduledThreadPool.shutdown();
			try {
				scheduledThreadPool.awaitTermination(60, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				log.warn("Interrupted whilst waiting for termination of scheduledThreadPool.", e);
			}
			scheduledThreadPool = null;
		}

		if (sessionTimeoutExecutor != null) {
			sessionTimeoutExecutor.shutdown();
			try {
				sessionTimeoutExecutor.awaitTermination(60, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				log.warn("Interrupted whilst waiting for termination of jobRunners.", e);
			}
			sessionTimeoutExecutor = null;
		}

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
		bootstrap = null;
		clientFactory = null;
		apiManagerMap = null;
		segment = null;
		apiList = null;
		// although the channelMap should clear when each connection closes, we remove all entries forcibly.
		if (!serverProxyMap.isEmpty()) {
			log.warn("serverProxyMap should have been cleared on shutdown.");
			serverProxyMap.clear();
		}
	}

	@Override
	/**
	 * Perform a check on the existing Sessions if they need discarding.
	 */
	public void run() {
		for (Entry<WebServiceApiName, WebServiceSessionManagerHolder> apis : apiManagerMap.entrySet()) {
			WebServiceSessionManagerHolder h = apis.getValue();

			Calendar idleCutoff = Calendar.getInstance();
			idleCutoff.add(Calendar.MINUTE, 0 - sessionIdleTimeoutMinutes);
			Date idleCutoffDate = idleCutoff.getTime();

			log.info("Processing idle sessions for " + apis.getKey() + " in segment " + segment + " not used after "
					+ idleCutoffDate);
			List<WebServiceSession> sessions = h.getSessionManager().removeIdleSessions(idleCutoff.getTime());

			for (Entry<PartitionControlServer, LocalControlServiceListenerClient> channel : serverProxyMap.entrySet()) {
				Set<String> sessionIds = new HashSet<>();
				String controllerId = getControllerId(channel.getValue().getRpcClient());

				for (WebServiceSession session : sessions) {
					if (controllerId.equals(session.getControllerId())) {
						sessionIds.add(session.getSessionId());
					}
				}
				if (!sessionIds.isEmpty()) {
					channel.getValue().notifySessionsRemoved(apis.getKey(), sessionIds);

				}

			}
		}
	}

	@Override
	public void invalidateCertificate(PKIXCertificate cert) {
		// invalidate the certificate at all PCS instances.
		// this will have the ServerSessionController.invalidateCertificate at the PCS which calls WS server which knows
		// the certificate
		for (Entry<PartitionControlServer, LocalControlServiceListenerClient> pcsServer : serverProxyMap.entrySet()) {
			LocalControlServiceListenerClient client = pcsServer.getValue();

			client.invalidateCertificate(cert);
		}
	}

	@Override
	public boolean transferObject(String sessionId, WebServiceApiName api, ObjectType type,
			Map<AttributeId, Long> attributes) {
		// delegate the transferred object to it's specific session
		WebServiceSessionManagerHolder h = apiManagerMap.get(api);
		if (h != null) {
			return h.getSessionManager().transferObject(sessionId, type, attributes);
		}
		return false;
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

	/**
	 * A helper value type holding the ServerHandle.
	 * 
	 * @author Peter
	 *
	 */
	private static class WebServiceSessionManagerHolder {
		private final ServiceHandle handle;
		private final WebServiceSessionManager sessionManager;

		private int loadValue;

		public WebServiceSessionManagerHolder(ServiceHandle handle, WebServiceSessionManager sessionManager) {
			if (handle == null) {
				throw new IllegalArgumentException();
			}
			if (sessionManager == null) {
				throw new IllegalArgumentException();
			}
			this.handle = handle;
			this.sessionManager = sessionManager;
		}

		public ServiceHandle getHandle() {
			return handle;
		}

		public WebServiceSessionManager getSessionManager() {
			return sessionManager;
		}

		public int getLoadValue() {
			return loadValue;
		}

		public void setLoadValue(int loadValue) {
			this.loadValue = loadValue;
		}
	}

	private PartitionControlServer getPartitionControlServer(RpcClientChannel clientChannel) {
		PartitionControlServer pcs = (PartitionControlServer) clientChannel.getAttribute(PCS_ATTRIBUTE);
		if (pcs == null) {
			throw new IllegalStateException("No PCS attribute on clientChannel " + clientChannel);
		}
		return pcs;
	}

	private String getControllerId(RpcClientChannel channel) {
		return channel.getPeerInfo().toString();
	}

	private Map<AttributeId, Long> mapAttributes(List<org.tdmx.server.pcs.protobuf.Common.AttributeValue> attrs) {
		if (attrs == null) {
			return null;
		}
		Map<AttributeId, Long> attributes = new HashMap<>();
		for (org.tdmx.server.pcs.protobuf.Common.AttributeValue attr : attrs) {
			attributes.put(attr.getName(), attr.getValue());
		}
		return attributes;
	}

	private WebServiceApiName mapApi(String apiName) {
		return apiName != null ? WebServiceApiName.valueOf(apiName) : null;
	}

	private List<ServiceHandle> getManagedServiceList() {
		List<ServiceHandle> serviceList = new ArrayList<>();
		for (Entry<WebServiceApiName, WebServiceSessionManagerHolder> services : apiManagerMap.entrySet()) {
			serviceList.add(services.getValue().getHandle());
		}
		return serviceList;
	}

	// -------------------------------------------------------------------------
	// PUBLIC ACCESSORS (GETTERS / SETTERS)
	// -------------------------------------------------------------------------

	public ServerRuntimeContextService getRuntimeService() {
		return runtimeService;
	}

	public void setRuntimeService(ServerRuntimeContextService runtimeService) {
		this.runtimeService = runtimeService;
	}

	public List<WebServiceSessionManager> getWebServiceSessionManagers() {
		return webServiceSessionManagers;
	}

	public void setWebServiceSessionManagers(List<WebServiceSessionManager> webServiceSessionManagers) {
		this.webServiceSessionManagers = webServiceSessionManagers;
	}

	public PartitionControlServerService getPartitionServerService() {
		return partitionServerService;
	}

	public void setPartitionServerService(PartitionControlServerService partitionServerService) {
		this.partitionServerService = partitionServerService;
	}

	public int getTimeoutCheckIntervalSec() {
		return timeoutCheckIntervalSec;
	}

	public void setTimeoutCheckIntervalSec(int timeoutCheckIntervalSec) {
		this.timeoutCheckIntervalSec = timeoutCheckIntervalSec;
	}

	public int getSessionIdleTimeoutMinutes() {
		return sessionIdleTimeoutMinutes;
	}

	public void setSessionIdleTimeoutMinutes(int sessionIdleTimeoutMinutes) {
		this.sessionIdleTimeoutMinutes = sessionIdleTimeoutMinutes;
	}

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

	public String getTosAddress() {
		return tosAddress;
	}

	public void setTosAddress(String tosAddress) {
		this.tosAddress = tosAddress;
	}

	public int getTosPort() {
		return tosPort;
	}

	public void setTosPort(int tosPort) {
		this.tosPort = tosPort;
	}

}
