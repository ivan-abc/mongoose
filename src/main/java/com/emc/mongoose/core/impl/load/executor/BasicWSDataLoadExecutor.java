package com.emc.mongoose.core.impl.load.executor;
// mongoose-common.jar
import com.emc.mongoose.common.conf.Constants;
import com.emc.mongoose.common.conf.RunTimeConfig;
import com.emc.mongoose.common.io.IOWorker;
import com.emc.mongoose.common.log.Markers;
import com.emc.mongoose.common.net.http.conn.pool.HttpConnPool;
import com.emc.mongoose.common.net.http.conn.pool.FixedRouteSequencingConnPool;
import com.emc.mongoose.common.net.http.request.HostHeaderSetter;
import com.emc.mongoose.common.log.LogUtil;
// mongoose-core-api.jar
import com.emc.mongoose.core.api.item.container.Container;
import com.emc.mongoose.core.api.item.data.WSObject;
import com.emc.mongoose.core.api.item.base.ItemSrc;
import com.emc.mongoose.core.api.io.task.IOTask;
import com.emc.mongoose.core.api.io.task.WSDataIOTask;
import com.emc.mongoose.core.api.io.conf.WSRequestConfig;
import com.emc.mongoose.core.api.load.executor.WSDataLoadExecutor;
// mongoose-core-impl.jar
import com.emc.mongoose.core.impl.io.conf.WSRequestConfigBase;
import com.emc.mongoose.core.impl.io.task.BasicWSDataIOTask;
import com.emc.mongoose.core.impl.load.tasks.HttpClientRunTask;
//
import org.apache.http.ExceptionLogger;
import org.apache.http.HttpHost;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.nio.pool.BasicNIOPoolEntry;
import org.apache.http.message.HeaderGroup;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpProcessorBuilder;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestUserAgent;
//
import org.apache.http.nio.util.DirectByteBufferAllocator;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.DefaultHttpClientIODispatch;
import org.apache.http.impl.nio.pool.BasicNIOConnFactory;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.nio.NHttpClientEventHandler;
import org.apache.http.nio.pool.NIOConnFactory;
import org.apache.http.nio.protocol.HttpAsyncRequestExecutor;
import org.apache.http.nio.protocol.HttpAsyncRequester;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOReactorException;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
//
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
/**
 Created by kurila on 02.12.14.
 */
public class BasicWSDataLoadExecutor<T extends WSObject>
extends MutableDataLoadExecutorBase<T>
implements WSDataLoadExecutor<T> {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	private final HttpProcessor httpProcessor;
	private final HttpAsyncRequester client;
	private final ConnectingIOReactor ioReactor;
	private final Map<HttpHost, HttpConnPool<HttpHost, BasicNIOPoolEntry>> connPoolMap;
	private final WSRequestConfig<T, Container<T>> wsReqConfigCopy;
	private final boolean isPipeliningEnabled;
	//
	private final AtomicLong
		connLeaseCount = new AtomicLong(0),
		connReleaseCount = new AtomicLong(0);
	//
	@SuppressWarnings("unchecked")
	public BasicWSDataLoadExecutor(
		final RunTimeConfig rtConfig, final WSRequestConfig<T, ? extends Container<T>> reqConfig,
		final String[] addrs, final int connCountPerNode, final int threadCount,
		final ItemSrc<T> itemSrc, final long maxCount,
		final long sizeMin, final long sizeMax, final float sizeBias,
		final int manualTaskSleepMicroSecs, final float rateLimit, final int countUpdPerReq
	) {
		super(
			rtConfig, reqConfig, addrs, connCountPerNode, threadCount, itemSrc, maxCount,
			sizeMin, sizeMax, sizeBias, manualTaskSleepMicroSecs, rateLimit, countUpdPerReq
		);
		wsReqConfigCopy = (WSRequestConfig<T, Container<T>>) ioConfigCopy;
		isPipeliningEnabled = wsReqConfigCopy.getPipelining();
		//
		final HeaderGroup sharedHeaders = wsReqConfigCopy.getSharedHeaders();
		final String userAgent = rtConfig.getRunName() + "/" + rtConfig.getRunVersion();
		//
		httpProcessor = HttpProcessorBuilder
			.create()
			.add(wsReqConfigCopy)
			.add(new HostHeaderSetter())
			.add(new RequestConnControl())
			.add(new RequestUserAgent(userAgent))
			//.add(new RequestExpectContinue(true))
			.add(new RequestContent(false))
			.build();
		client = new HttpAsyncRequester(
			httpProcessor, DefaultConnectionReuseStrategy.INSTANCE,
			new ExceptionLogger() {
				@Override
				public final void log(final Exception e) {
					LogUtil.exception(LOG, Level.DEBUG, e, "HTTP client internal failure");
				}
			}
		);
		//
		final RunTimeConfig thrLocalConfig = RunTimeConfig.getContext();
		final int buffSize = wsReqConfigCopy.getBuffSize();
		final long timeOutMs = rtConfig.getLoadLimitTimeUnit().toMillis(
			rtConfig.getLoadLimitTimeValue()
		);
		final IOReactorConfig.Builder ioReactorConfigBuilder = IOReactorConfig
			.custom()
			.setIoThreadCount(threadCount)
			.setBacklogSize((int) thrLocalConfig.getSocketBindBackLogSize())
			.setInterestOpQueued(thrLocalConfig.getSocketInterestOpQueued())
			.setSelectInterval(thrLocalConfig.getSocketSelectInterval())
			.setShutdownGracePeriod(thrLocalConfig.getSocketTimeOut())
			.setSoKeepAlive(thrLocalConfig.getSocketKeepAliveFlag())
			.setSoLinger(thrLocalConfig.getSocketLinger())
			.setSoReuseAddress(thrLocalConfig.getSocketReuseAddrFlag())
			.setSoTimeout(thrLocalConfig.getSocketTimeOut())
			.setTcpNoDelay(thrLocalConfig.getSocketTCPNoDelayFlag())
			.setRcvBufSize(IOTask.Type.READ.equals(loadType) ? buffSize : Constants.BUFF_SIZE_LO)
			.setSndBufSize(IOTask.Type.READ.equals(loadType) ? Constants.BUFF_SIZE_LO : buffSize)
			.setConnectTimeout(
				timeOutMs > 0 && timeOutMs < Integer.MAX_VALUE ? (int) timeOutMs : Integer.MAX_VALUE
			);
		//
		final NHttpClientEventHandler reqExecutor = new HttpAsyncRequestExecutor();
		//
		final ConnectionConfig connConfig = ConnectionConfig
			.custom()
			.setBufferSize(buffSize)
			.setFragmentSizeHint(0)
			.build();
		final IOEventDispatch ioEventDispatch = new DefaultHttpClientIODispatch(
			reqExecutor, connConfig
		);
		//
		final IOWorker.Factory ioWorkerFactory = new IOWorker.Factory(getName());
		try {
			ioReactor = new DefaultConnectingIOReactor(
				ioReactorConfigBuilder.build(), ioWorkerFactory
			);
		} catch(final IOReactorException e) {
			throw new IllegalStateException("Failed to build the I/O reactor", e);
		}
		//
		final NIOConnFactory<HttpHost, NHttpClientConnection>
			connFactory = new BasicNIOConnFactory(
				null, null, null, null,
				DirectByteBufferAllocator.INSTANCE, connConfig
			);
		//
		connPoolMap = new HashMap<>(storageNodeCount);
		HttpHost nextRoute;
		HttpConnPool<HttpHost, BasicNIOPoolEntry> nextConnPool;
		for(int i = 0; i < storageNodeCount; i ++) {
			nextRoute = wsReqConfigCopy.getNodeHost(addrs[i]);
			nextConnPool = new FixedRouteSequencingConnPool(
				ioReactor, nextRoute, connFactory,
				timeOutMs > 0 && timeOutMs < Integer.MAX_VALUE ?
					(int) timeOutMs : Integer.MAX_VALUE,
				batchSize
			);
			nextConnPool.setDefaultMaxPerRoute(connCountPerNode);
			nextConnPool.setMaxTotal(connCountPerNode);
			connPoolMap.put(nextRoute, nextConnPool);
		}
		//
		mgmtTasks.add(new HttpClientRunTask(ioEventDispatch, ioReactor));
	}
	//
	@Override
	public final void logMetrics(final Marker logMarker) {
		super.logMetrics(logMarker);
		if(LOG.isTraceEnabled(Markers.MSG)) {
			LOG.trace(
				Markers.MSG, "Connections: leased={}, released={}",
				connLeaseCount.get(), connReleaseCount.get()
			);
		}
	}
	//
	@Override
	protected WSDataIOTask<T> getIOTask(final T item, final String nodeAddr) {
		return new BasicWSDataIOTask<>(item, nodeAddr, wsReqConfigCopy);
	}
	//
	@Override
	protected void interruptActually() {
		try {
			super.interruptActually();
		} finally {
			for(final HttpConnPool<HttpHost, BasicNIOPoolEntry> nextConnPool : connPoolMap.values()) {
				nextConnPool.closeExpired();
				LOG.debug(
					Markers.MSG, "{}: closed expired (if any) connections in the pool", getName()
				);
				try {
					nextConnPool.closeIdle(1, TimeUnit.MILLISECONDS);
					LOG.debug(
						Markers.MSG, "{}: closed idle connections (if any) in the pool", getName()
					);
				} finally {
					try {
						nextConnPool.shutdown(1);
						LOG.debug(Markers.MSG, "{}: connection pool has been shut down", getName());
					} catch(final IOException e) {
						LogUtil.exception(
							LOG, Level.WARN, e, "{}: connection pool shutdown failure", getName()
						);
					}
				}
			}
			//
			try {
				ioReactor.shutdown();
				LOG.debug(Markers.MSG, "{}: I/O reactor has been shut down", getName());
			} catch(final IOException e) {
				LogUtil.exception(
					LOG, Level.WARN, e, "{}: failed to shut down the I/O reactor", getName()
				);
			}
		}
	}
	//
	@Override
	protected <A extends IOTask<T>> Future<A> submitTaskActually(final A ioTask)
	throws RejectedExecutionException {
		//
		final WSDataIOTask<T> wsTask = (WSDataIOTask<T>) ioTask;
		final HttpConnPool<HttpHost, BasicNIOPoolEntry>
			connPool = connPoolMap.get(wsTask.getTarget());
		if(connPool.isShutdown()) {
			throw new RejectedExecutionException("Connection pool is shut down");
		}
		//
		final Future<WSDataIOTask<T>> futureResult;
		try {
			futureResult = client.execute(wsTask, wsTask, connPool, wsTask, futureCallback);
			if(LOG.isTraceEnabled(Markers.MSG)) {
				LOG.trace(
					Markers.MSG, "I/O task #{} has been submitted for execution", wsTask.hashCode()
				);
			}
		} catch(final Exception e) {
			throw new RejectedExecutionException(e);
		}
		return (Future<A>) futureResult;
	}
	//
	private final FutureCallback<WSDataIOTask<T>> futureCallback = new FutureCallback<WSDataIOTask<T>>() {
		@Override
		public final void completed(final WSDataIOTask<T> ioTask) {
			ioTaskCompleted(ioTask);
		}
		//
		public final void cancelled() {
			ioTaskCancelled(1);
		}
		//
		public final void failed(final Exception e) {
			ioTaskFailed(1, e);
		}
	};
	//
	@Override
	public final int submitTasks(final List<? extends IOTask<T>> ioTasks, int from, int to)
	throws RejectedExecutionException {
		int n = 0;
		if(isPipeliningEnabled) {
			if(ioTasks.size() > 0) {
				final List<WSDataIOTask<T>> wsIOTasks = (List<WSDataIOTask<T>>) ioTasks;
				final WSDataIOTask<T> anyTask = wsIOTasks.get(0);
				final HttpHost tgtHost = anyTask.getTarget();
				if(
					null == client.executePipelined(
						tgtHost, wsIOTasks, wsIOTasks, connPoolMap.get(tgtHost),
						HttpCoreContext.create(), new BatchFutureCallback(wsIOTasks)
					)
				) {
					return 0;
				}
			}
		} else {
			for(int i = from; i < to; i ++) {
				if(null != submitReq(ioTasks.get(i))) {
					n ++;
				} else {
					break;
				}
			}
		}
		return n;
	}
	//
	private final class BatchFutureCallback
	implements FutureCallback<List<WSDataIOTask<T>>> {
		//
		private final List<WSDataIOTask<T>> tasks;
		//
		private BatchFutureCallback(final List<WSDataIOTask<T>> tasks) {
			this.tasks = tasks;
		}
		//
		@Override
		public final void completed(final List<WSDataIOTask<T>> result) {
			ioTaskCompletedBatch(result, 0, result.size());
		}
		//
		@Override
		public final void failed(final Exception e) {
			ioTaskFailed(tasks.size(), e);
		}
		//
		@Override
		public final void cancelled() {
			ioTaskCancelled(tasks.size());
		}
	}
	////////////////////////////////////////////////////////////////////////////////////////////////
	// Balancing based on the connection pool stats
	////////////////////////////////////////////////////////////////////////////////////////////////
	/*private volatile Set<HttpHost> routes = null;
	private final static ThreadLocal<Map<HttpHost, String>>
		THREAD_CACHED_REVERSE_NODE_MAP = new ThreadLocal<>();
	@Override
	protected final String getNextNode() {
		HttpHost nodeHost = null;
		// connPool.getRoutes() is quite expensive, so reuse the routes set
		if(routes == null || routes.size() < storageNodeCount) {
			routes = connPool.getRoutes();
		} else {
			// select the route having the max count of the free connections in the pool
			// TODO think how to not to invoke connPool.getStats(HttpHost route)
			int maxConnCount = -1, nextConnCount;
			for(final HttpHost nextRoute : routes) {
				nextConnCount = connPool.getStats(nextRoute).getAvailable();
				if(nextConnCount > maxConnCount) {
					maxConnCount = nextConnCount;
					nodeHost = nextRoute;
				}
			}
		}
		//
		String nodeAddr;
		if(nodeHost == null) { // fallback
			nodeAddr = super.getNextNode();
		} else {
			Map<HttpHost, String> cachedReverseNodeMap = THREAD_CACHED_REVERSE_NODE_MAP.get();
			if(cachedReverseNodeMap == null) {
				cachedReverseNodeMap = new HashMap<>();
				THREAD_CACHED_REVERSE_NODE_MAP.set(cachedReverseNodeMap);
			}
			nodeAddr = cachedReverseNodeMap.get(nodeHost);
			if(nodeAddr == null) {
				nodeAddr = nodeHost.toHostString();
				cachedReverseNodeMap.put(nodeHost, nodeAddr);
			}
		}
		return nodeAddr;
	}*/
}