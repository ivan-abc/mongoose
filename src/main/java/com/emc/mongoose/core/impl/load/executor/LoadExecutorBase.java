package com.emc.mongoose.core.impl.load.executor;
// mongoose-common.jar
import com.emc.mongoose.common.conf.Constants;
import com.emc.mongoose.common.conf.RunTimeConfig;
import com.emc.mongoose.common.conf.SizeUtil;
import com.emc.mongoose.common.log.LogUtil;
import com.emc.mongoose.common.log.Markers;
// mongoose-core-api.jar
import com.emc.mongoose.core.api.data.model.DataItemInput;
import com.emc.mongoose.core.api.data.model.FileDataItemOutput;
import com.emc.mongoose.core.api.io.task.IOTask;
import com.emc.mongoose.core.api.io.req.RequestConfig;
import com.emc.mongoose.core.api.data.DataItem;
import com.emc.mongoose.core.api.data.model.DataSource;
import com.emc.mongoose.core.api.load.model.Consumer;
import com.emc.mongoose.core.api.load.executor.LoadExecutor;
import com.emc.mongoose.core.api.load.model.Producer;
import com.emc.mongoose.core.api.load.model.LoadState;
// mongoose-core-impl.jar
import com.emc.mongoose.core.api.load.model.metrics.IOStats;
import com.emc.mongoose.core.impl.data.model.CSVFileItemOutput;
import com.emc.mongoose.core.impl.load.model.AsyncConsumerBase;
import com.emc.mongoose.core.impl.load.model.metrics.BasicIOStats;
import com.emc.mongoose.core.impl.load.tasks.LoadCloseHook;
import com.emc.mongoose.core.impl.load.model.BasicLoadState;
import com.emc.mongoose.core.impl.load.model.DataItemInputProducer;
//
import org.apache.commons.lang.StringUtils;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.ThreadContext;
//
import java.io.IOException;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
/**
 Created by kurila on 15.10.14.
 */
public abstract class LoadExecutorBase<T extends DataItem>
extends AsyncConsumerBase<T>
implements LoadExecutor<T> {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	protected final int instanceNum, storageNodeCount;
	protected final String storageNodeAddrs[];
	//
	protected final Class<T> dataCls;
	protected final RunTimeConfig rtConfig;
	//
	protected final DataSource dataSrc;
	protected final RequestConfig<T> reqConfigCopy;
	protected final IOTask.Type loadType;
	//
	protected volatile Producer<T> producer = null;
	protected volatile Consumer<T> consumer;
	protected volatile FileDataItemOutput<T> itemsFileBuff = null;
	//
	private final long maxCount;
	protected final int totalConnCount;
	// METRICS section
	private final int metricsUpdatePeriodSec;
	private final IOStats ioStats;
	protected volatile IOStats.Snapshot lastStats = null;
	//
	private T lastDataItem;
	private final DataItemInput<T> itemsSrc;
	//
	private final Thread
		metricsDaemon = new Thread() {
			//
			{ setDaemon(true); } // do not block process exit
			//
			@Override
			public final void run() {
				// required for int tests passing
				ThreadContext.put(RunTimeConfig.KEY_RUN_ID, rtConfig.getRunId());
				//
				final long
					metricsUpdatePeriodMilliSec = TimeUnit.SECONDS.toMillis(metricsUpdatePeriodSec);
				try {
					if(metricsUpdatePeriodMilliSec > 0) {
						while(!isClosed.get()) {
							logMetrics(Markers.PERF_AVG);
							Thread.sleep(metricsUpdatePeriodMilliSec);
						}
					} else {
						Thread.sleep(Long.MAX_VALUE);
					}
				} catch(final InterruptedException e) {
					LOG.debug(Markers.MSG, "{}: interrupted", getName());
				}
			}
		},
		releaseDaemon = new Thread() {
			//
			{ setDaemon(true); }
			//
			@Override
			public final void run() {
				while(!isClosed.get() && !isInterrupted()) {
					//
					LockSupport.parkNanos(1);
					if(isDoneAllSubm() || isDoneMaxCount()) {
						lock.lock();
						try {
							condProducerDone.signalAll();
							//if(LOG.isTraceEnabled(Markers.MSG)) {
								LOG.debug(
									Markers.MSG,
									"{}: done signal emitted because of condition",
									getName(),
									isAllSubm.get(), counterSubm.get(), counterResults.get()
								);
							//}
						} finally {
							lock.unlock();
						}
					}
					//
					LockSupport.parkNanos(1);
					lastStats = ioStats.getSnapshot();
					if(
						lastStats.getFailCount() > 1000000 &&
						lastStats.getFailRateLast() < lastStats.getSuccRateLast()
					) {
						LOG.fatal(
							Markers.ERR,
							"There's a more than 1M of failures and the failure rate is higher " +
							"than success rate for at least last {}[sec]. Exiting in order to " +
							"avoid the memory exhaustion. Please check your environment.",
							metricsUpdatePeriodSec
						);
						try {
							LoadExecutorBase.this.close();
						} catch(final IOException e) {
							LogUtil.exception(LOG, Level.WARN, e, "Failed to close the load job");
						}
						break;
					}
					//
					LockSupport.parkNanos(1);
				}
			}
		};
	// STATES section //////////////////////////////////////////////////////////////////////////////
	private final Map<String, AtomicInteger> activeTasksStats = new HashMap<>();
	private LoadState<T> loadedPrevState = null;
	private AtomicBoolean isLoadFinished = new AtomicBoolean(false);
	protected final AtomicLong
		counterSubm = new AtomicLong(0),
		countRej = new AtomicLong(0),
		counterResults = new AtomicLong(0);
	private final AtomicBoolean
		isInterrupted = new AtomicBoolean(false),
		isClosed = new AtomicBoolean(false);
	private final Lock lock = new ReentrantLock();
	private final Condition condProducerDone = lock.newCondition();
	////////////////////////////////////////////////////////////////////////////////////////////////
	protected LoadExecutorBase(
		final Class<T> dataCls,
		final RunTimeConfig rtConfig, final RequestConfig<T> reqConfig, final String[] addrs,
		final int connCountPerNode, final int threadCount,
		final DataItemInput<T> itemSrc, final long maxCount
	) {
		super(
			maxCount, rtConfig.getTasksMaxQueueSize(),
			rtConfig.isShuffleItemsEnabled(), rtConfig.getBatchSize()
		);
		//
		this.dataCls = dataCls;
		this.rtConfig = rtConfig;
		this.itemsSrc = itemSrc;
		if (!INSTANCE_NUMBERS.containsKey(rtConfig.getRunId())) {
			INSTANCE_NUMBERS.put(rtConfig.getRunId(), new AtomicInteger(0));
		}
		instanceNum = INSTANCE_NUMBERS.get(rtConfig.getRunId()).getAndIncrement();
		storageNodeCount = addrs.length;
		//
		setName(
			Integer.toString(instanceNum) + '-' +
				StringUtils.capitalize(reqConfig.getAPI().toLowerCase()) + '-' +
				StringUtils.capitalize(reqConfig.getLoadType().toString().toLowerCase()) +
				(maxCount > 0 ? Long.toString(maxCount) : "") + '-' +
				Integer.toString(connCountPerNode) + 'x' + Integer.toString(storageNodeCount)
		);
		//
		totalConnCount = connCountPerNode * storageNodeCount;
		//
		RequestConfig<T> reqConfigClone = null;
		try {
			reqConfigClone = reqConfig.clone();
		} catch(final CloneNotSupportedException e) {
			LogUtil.exception(LOG, Level.ERROR, e, "Failed to clone the request config");
		} finally {
			this.reqConfigCopy = reqConfigClone;
		}
		loadType = reqConfig.getLoadType();
		//
		metricsUpdatePeriodSec = rtConfig.getLoadMetricsPeriodSec();
		final String runMode = rtConfig.getRunMode();
		final boolean flagServeJMX = rtConfig.getFlagServeJMX();
		if(flagServeJMX) {
			ioStats = new BasicIOStats(
				getName(), rtConfig.getRemotePortMonitor(), metricsUpdatePeriodSec
			);
		} else {
			ioStats = new BasicIOStats(getName(), 0, metricsUpdatePeriodSec);
		}
		lastStats = ioStats.getSnapshot();
		//
		this.maxCount = maxCount > 0 ? maxCount : Long.MAX_VALUE;
		// prepare the nodes array
		storageNodeAddrs = addrs.clone();
		for(final String addr : storageNodeAddrs) {
			activeTasksStats.put(addr, new AtomicInteger(0));
		}
		dataSrc = reqConfig.getDataSource();
		/*
		if(listFile != null && listFile.length() > 0) {
			final Path dataItemsListPath = Paths.get(listFile);
			if(!Files.exists(dataItemsListPath)) {
				LOG.warn(
					Markers.ERR, "Data items source file \"{}\" doesn't exist",
					dataItemsListPath
				);
			} else if(!Files.isReadable(dataItemsListPath)) {
				LOG.warn(
					Markers.ERR, "Data items source file \"{}\" is not readable",
					dataItemsListPath
				);
			} else {
				try {
					producer = new DataItemInputProducer<>(
						new CSVFileItemInput<>(Paths.get(listFile), dataCls)
					);
					LOG.debug(
						Markers.MSG, "{} will use file-based producer: {}", getName(), listFile
					);
				} catch(final NoSuchMethodException | IOException e) {
					LogUtil.exception(
						LOG, Level.FATAL, e,
						"Failed to create file producer for the class \"{}\" and src file \"{}\"",
						dataCls.getName(), listFile
					);
				}
			}
		} else if(loadType == IOTask.Type.CREATE) {
			try {
				producer = new BasicDataItemGenerator<>(
					dataCls, maxCount, sizeMin, sizeMax, sizeBias
				);
				LOG.debug(Markers.MSG, "{} will use new data items producer", getName());
			} catch(final NoSuchMethodException e) {
				LogUtil.exception(
					LOG, Level.FATAL, e,
					"Failed to create new data items producer for class \"{}\"",
					dataCls.getName()
				);
			}
		} else {
			producer = reqConfig.getContainerListInput(maxCount, addrs[0]);
			LOG.debug(Markers.MSG, "{} will use {} as data items producer", getName(), producer);
		}*/
		if(itemsSrc != null) {
			producer = new DataItemInputProducer<>(
				itemsSrc, rtConfig.getBatchSize(), rtConfig.isDataSrcCircularEnabled()
			);
			try {
				producer.setConsumer(this);
			} catch(final RemoteException e) {
				LogUtil.exception(LOG, Level.WARN, e, "Unexpected failure");
			}
		}
		//
		LoadCloseHook.add(this);
	}
	//
	@Override
	public final String toString() {
		return getName();
	}
	//
	////////////////////////////////////////////////////////////////////////////////////////////////
	// Producer implementation /////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////
	@Override
	public final void logMetrics(final Marker logMarker) {
		LOG.info(
			logMarker,
			Markers.PERF_SUM.equals(logMarker) ?
				"\"" + getName() + "\" summary: " + lastStats :
				lastStats
		);
	}
	//
	@Override
	public void start() {
		if(isStarted.compareAndSet(false, true)) {
			LOG.debug(Markers.MSG, "Starting {}", getName());
			ioStats.start();
			//
			if(rtConfig.isRunResumeEnabled()) {
				if (rtConfig.getRunMode().equals(Constants.RUN_MODE_STANDALONE)) {
					try {
						if(!RESTORED_STATES_MAP.containsKey(rtConfig.getRunId())) {
							BasicLoadState.restoreScenarioState(rtConfig);
						}
						setLoadState(BasicLoadState.<T>findStateByLoadNumber(instanceNum, rtConfig));
					} catch (final Exception e) {
						LogUtil.exception(LOG, Level.ERROR, e, "Unexpected failure");
					}
				}
			}
			//
			if(isLoadFinished.get()) {
				try {
					close();
				} catch (final IOException e) {
					LogUtil.exception(LOG, Level.ERROR, e,
						"Couldn't close the load executor \"{}\"", getName());
				}
				return;
			}
			//
			releaseDaemon.setName("releaseDaemon<" + getName() + ">");
			releaseDaemon.start();
			//
			super.startActually();
			//
			itemsFileLock.lock();
			try {
				if(itemsFileBuff != null) {
					itemsFileBuff.close();
					final Path itemsFilePath = itemsFileBuff.getFilePath();
					LOG.debug(
						Markers.MSG, "{}: accumulated for input {} of data items metadata in the temporary file \"{}\"",
						getName(), SizeUtil.formatSize(itemsFilePath.toFile().length()), itemsFilePath
					);
					isShutdown.compareAndSet(true, false); // cancel if shut down before start
					producer = new DataItemInputProducer<>(
						itemsFileBuff.getInput(),
						rtConfig.getBatchSize(),
						rtConfig.isDataSrcCircularEnabled()
					);
				}
			} catch(final IOException e) {
				LogUtil.exception(LOG, Level.WARN, e, "Failed to close the items buffer file");
			} finally {
				itemsFileLock.unlock();
			}
			//
			if(producer == null) {
				LOG.debug(Markers.MSG, "{}: using an external data items producer", getName());
			} else {
				try {
					producer.setConsumer(this);
					if(
						producer instanceof DataItemInputProducer &&
						counterResults.get() > 0
					) {
						final DataItemInputProducer<T> inputProducer
							= (DataItemInputProducer<T>) producer;
						inputProducer.setSkippedItemsCount(counterResults.get());
						inputProducer.setLastDataItem(loadedPrevState.getLastDataItem());
					}
					producer.start();
					LOG.debug(Markers.MSG, "Started object producer {}", producer);
				} catch(final IOException e) {
					LogUtil.exception(LOG, Level.WARN, e, "Failed to start the producer");
				}
			}
			//
			metricsDaemon.setName(getName());
			metricsDaemon.start();
			//
			LOG.debug(Markers.MSG, "Started \"{}\"", getName());
		} else {
			LOG.warn(Markers.ERR, "Second start attempt - skipped");
		}
	}
	//
	@Override
	public void interrupt() {
		if(isLoadFinished.get()) {
			return;
		}
		if(isInterrupted.compareAndSet(false, true)) {
			final StringBuilder sb = new StringBuilder("Interrupt came from:");
			final StackTraceElement stackTrace[] = Thread.currentThread().getStackTrace();
			for(final StackTraceElement ste : stackTrace) {
				sb.append("\n\t").append(ste.toString());
			}
			LOG.debug(Markers.MSG, sb);
			metricsDaemon.interrupt();
			shutdown();
			try {
				reqConfigCopy.close(); // disables connection drop failures
			} catch(final IOException e) {
				LogUtil.exception(LOG, Level.WARN, e, "Failed to close the request configurator");
			}
			// releasing the blocked join() methods, if any
			lock.lock();
			try {
				condProducerDone.signalAll();
				LOG.debug(
					Markers.MSG, "{}: done signal emitted by the interruption", getName()
				);
			} finally {
				lock.unlock();
			}
			//
			try {
				if(isStarted.get()) { // if was executing
					lastStats = ioStats.getSnapshot();
					ioStats.close();
					logMetrics(Markers.PERF_SUM); // provide summary metrics
					// calculate the efficiency and report
					final float
						loadDurMicroSec = lastStats.getElapsedTime(),
						eff = lastStats.getDurationSum() / loadDurMicroSec / totalConnCount;
					LOG.debug(
						Markers.MSG,
						String.format(
							LogUtil.LOCALE_DEFAULT,
							"%s: load execution duration: %3.3f[sec], efficiency estimation: %3.1f[%%]",
							getName(), loadDurMicroSec / 1e6, 100 * eff
						)
					);
				} else {
					LOG.debug(Markers.ERR, "{}: trying to interrupt while not started", getName());
				}
			} catch(final Throwable t) {
				t.printStackTrace(System.err);
			}
			//
			LOG.debug(Markers.MSG, "{} interrupted", getName());
		} else {
			LOG.debug(Markers.MSG, "{} was already interrupted", getName());
		}

	}
	//
	@Override
	public final Consumer<T> getConsumer() {
		return consumer;
	}
	//
	@Override
	public final void setConsumer(final Consumer<T> consumer) {
		this.consumer = consumer;
		LOG.debug(
			Markers.MSG, "Appended the consumer \"{}\" for producer \"{}\"", consumer, getName()
		);
	}
	////////////////////////////////////////////////////////////////////////////////////////////////
	// Consumer implementation /////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////
	private final Lock itemsFileLock = new ReentrantLock();
	//
	@Override
	public void feed(final T dataItem)
	throws InterruptedException, RemoteException, RejectedExecutionException {
		try {
			if(isStarted.get()) {
				super.feed(dataItem);
			} else { // accumulate until started
				itemsFileLock.lock();
				try {
					if(itemsFileBuff == null) {
						itemsFileBuff = new CSVFileItemOutput<>(dataCls);
						LOG.debug(
							Markers.MSG,
							"{}: not started yet, consuming into the temporary file @ {}",
							getName(), itemsFileBuff.getFilePath()
						);
					}
					itemsFileBuff.write(dataItem);
				} catch(final IOException | NoSuchMethodException e) {
					throw new RejectedExecutionException(e);
				} finally {
					itemsFileLock.unlock();
				}
			}
		} catch(final RejectedExecutionException e) {
			countRej.incrementAndGet();
			throw e;
		}
	}
	//
	@Override
	public void feedBatch(final List<T> dataItems)
	throws InterruptedException, RemoteException, RejectedExecutionException {
		try {
			if(isStarted.get()) {
				super.feedBatch(dataItems);
			} else { // accumulate until started
				itemsFileLock.lock();
				try {
					if(itemsFileBuff == null) {
						itemsFileBuff = new CSVFileItemOutput<>(dataCls);
						LOG.debug(
							Markers.MSG,
							"{}: not started yet, consuming into the temporary file @ {}",
							getName(), itemsFileBuff.getFilePath()
						);
					}
					itemsFileBuff.write(dataItems);
				} catch(final IOException | NoSuchMethodException e) {
					throw new RejectedExecutionException(e);
				} finally {
					itemsFileLock.unlock();
				}
			}
		} catch(final RejectedExecutionException e) {
			countRej.incrementAndGet();
			throw e;
		}
	}
	//
	@Override @SuppressWarnings("unchecked")
	protected final void feedSeq(final T dataItem)
	throws InterruptedException, RemoteException {
		if(counterSubm.get() + countRej.get() >= maxCount) {
			LOG.debug(
				Markers.MSG, "{}: all tasks has been submitted ({}) or rejected ({})", getName(),
				counterSubm.get(), countRej.get()
			);
			super.interrupt();
			return;
		}
		// prepare the I/O task instance (make the link between the data item and load type)
		final String nextNodeAddr = storageNodeCount == 1 ? storageNodeAddrs[0] : getNextNode();
		final IOTask<T> ioTask = getIOTask(dataItem, nextNodeAddr);
		// try to sleep while underlying connection pool becomes more free if it's going too fast
		// warning: w/o such sleep the behaviour becomes very ugly
		while(
			!isAllSubm.get() && !isInterrupted.get() &&
			counterSubm.get() - counterResults.get() >= maxQueueSize
		) {
			LockSupport.parkNanos(1);
		}
		//
		try {
			if(null == submitReq(ioTask)) {
				throw new RejectedExecutionException("Null future returned");
			}
			counterSubm.incrementAndGet();
			activeTasksStats.get(nextNodeAddr).incrementAndGet(); // increment node's usage counter
		} catch(final RejectedExecutionException e) {
			if(!isInterrupted.get()) {
				countRej.incrementAndGet();
				LogUtil.exception(LOG, Level.DEBUG, e, "Rejected the I/O task {}", ioTask);
			}
		}
	}
	//
	@Override
	protected final void feedSeqBatch(final List<T> dataItems)
	throws InterruptedException, RemoteException {
		final long remaining = maxCount - counterSubm.get() - countRej.get();
		final int n = dataItems.size();
		if(remaining > 0) {
			if(remaining < n) {
				feedSeqBatch(dataItems.subList(0, (int) remaining));
				super.interrupt();
			} else {
				// prepare the I/O tasks list (make the link between the data item and load type)
				final String nextNodeAddr = storageNodeCount == 1 ?
					storageNodeAddrs[0] : getNextNode();
				final List<? extends IOTask<T>> ioTasks = getIOTasks(dataItems, nextNodeAddr);
				// try to sleep while underlying connection pool becomes more free if it's going too fast
				// warning: w/o such sleep the behaviour becomes very ugly
				while(
					!isAllSubm.get() && !isInterrupted.get() &&
					counterSubm.get() - counterResults.get() >= maxQueueSize
				) {
					LockSupport.parkNanos(1);
				}
				//
				try {
					if(null == submitBatchReq(ioTasks)) {
						throw new RejectedExecutionException("Null future returned");
					}
					counterSubm.addAndGet(n);
					activeTasksStats.get(nextNodeAddr).addAndGet(n); // increment node's usage counter
				} catch(final RejectedExecutionException e) {
					if(!isInterrupted.get()) {
						countRej.incrementAndGet();
						LogUtil.exception(LOG, Level.DEBUG, e, "Rejected {} I/O tasks", n);
					}
				}
			}
		} else {
			super.interrupt();
			if(n > 0) {
				countRej.addAndGet(n);
				LOG.debug(Markers.MSG, "Rejected {} I/O tasks", n);
			}
		}

	}
	//
	protected abstract IOTask<T> getIOTask(final T dataItem, final String nextNodeAddr);
	//
	protected List<IOTask<T>> getIOTasks(
		final List<T> dataItems, final String nextNodeAddr
	) {
		final List<IOTask<T>> ioTasks = new ArrayList<>(dataItems.size());
		for(final T dataItem : dataItems) {
			if(dataItem == null) {
				break;
			} else {
				ioTasks.add(getIOTask(dataItem, nextNodeAddr));
			}
		}
		return ioTasks;
	}
	////////////////////////////////////////////////////////////////////////////////////////////////
	// Balancing implementation
	////////////////////////////////////////////////////////////////////////////////////////////////
	// round-robin variant:
	/*private final AtomicInteger rountRobinCounter = new AtomicInteger(0);
	protected String getNextNode() {
		return storageNodeAddrs[rountRobinCounter.incrementAndGet() % storageNodeCount];
	}*/
	protected String getNextNode() {
		String bestNode = null;
		//final StringBuilder sb = new StringBuilder("Active tasks stats: ");
		int minActiveTaskCount = Integer.MAX_VALUE, nextActiveTaskCount;
		for(final String nextNode : storageNodeAddrs) {
			nextActiveTaskCount = activeTasksStats.get(nextNode).get();
			//sb.append(nextNode).append("=").append(nextActiveTaskCount).append(", ");
			if(nextActiveTaskCount < minActiveTaskCount) {
				minActiveTaskCount = nextActiveTaskCount;
				bestNode = nextNode;
			}
		}
		//LOG.trace(LogUtil.MSG, sb.append("best: ").append(bestNode).toString());
		return bestNode;
	}
	////////////////////////////////////////////////////////////////////////////////////////////////
	private void markSucc(final IOTask<T> ioTask) {
		final int
			duration = ioTask.getDuration(),
			latency = ioTask.getLatency();
		// update the metrics with success
		if(latency > duration) {
			LOG.warn(
				Markers.ERR, "{}: latency {} is more than duration: {}",
				ioTask, latency, duration
			);
		}
		ioStats.markSucc(ioTask.getCountBytesDone(), duration, latency);
		if(LOG.isTraceEnabled(Markers.MSG)) {
			LOG.trace(
				Markers.MSG, "Task #{}: successful, {}/{}",
				ioTask.hashCode(), lastStats.getSuccCount(),
				ioTask.getCountBytesDone()
			);
		}
	}
	@Override
	public final void handleResult(final IOTask<T> ioTask)
	throws RemoteException {
		// producing was interrupted?
		if(isInterrupted.get()) {
			return;
		}
		// update the metrics
		activeTasksStats.get(ioTask.getNodeAddr()).decrementAndGet();
		final IOTask.Status status = ioTask.getStatus();
		final T dataItem = ioTask.getDataItem();
		if(status == IOTask.Status.SUCC) {
			lastDataItem = dataItem;
			// update the metrics with success
			markSucc(ioTask);
			// feed the data item to the consumer and finally check for the finish state
			try {
				// is this an end of consumer-producer chain?
				if(consumer == null) {
					if(LOG.isInfoEnabled(Markers.DATA_LIST)) {
						LOG.info(Markers.DATA_LIST, dataItem);
					}
				} else { // feed to the consumer
					if(LOG.isTraceEnabled(Markers.MSG)) {
						LOG.trace(
							Markers.MSG, "Going to feed the data item {} to the consumer {}",
							dataItem, consumer
						);
					}
					consumer.feed(dataItem);
					if(LOG.isTraceEnabled(Markers.MSG)) {
						LOG.trace(
							Markers.MSG, "The data item {} is passed to the consumer {} successfully",
							dataItem, consumer
						);
					}
				}
			} catch(final InterruptedException e) {
				LOG.debug(Markers.MSG, "Interrupted while submitting to the consumer");
			} catch(final RemoteException e) {
				LogUtil.exception(
					LOG, Level.WARN, e, "Failed to feed the data item \"{}\" to \"{}\"",
					dataItem, consumer
				);
			} catch(final RejectedExecutionException e) {
				if(LOG.isTraceEnabled(Markers.ERR)) {
					LogUtil.exception(
						LOG, Level.TRACE, e, "\"{}\" rejected the data item \"{}\"", consumer,
						dataItem
					);
				}
			}
		} else {
			ioStats.markFail();
		}
		//
		counterResults.incrementAndGet();
	}
	//
	@Override
	public final void handleBatchResult(final List<IOTask<T>> ioTasks) {
		// producing was interrupted?
		if(isInterrupted.get()) {
			return;
		}
		//
		final int n = ioTasks.size();
		if(n > 0) {
			final List<T> passedItems = new ArrayList<>(n);
			final String nodeAddr = ioTasks.get(0).getNodeAddr();
			activeTasksStats.get(nodeAddr).addAndGet(-n);
			//
			IOTask.Status status;
			T dataItem;
			for(final IOTask<T> ioTask : ioTasks) {
				status = ioTask.getStatus();
				dataItem = ioTask.getDataItem();
				if(status == IOTask.Status.SUCC) {
					lastDataItem = dataItem;
					markSucc(ioTask);
				} else {
					ioStats.markFail();
				}
			}
			// feed the data items to the consumer and finally check for the finish state
			try {
				// is this an end of consumer-producer chain?
				if(consumer == null) {
					for(final T passedItem : passedItems) {
						if(LOG.isInfoEnabled(Markers.DATA_LIST)) {
							LOG.info(Markers.DATA_LIST, passedItem);
						}
					}
				} else { // feed to the consumer
					if(LOG.isTraceEnabled(Markers.MSG)) {
						LOG.trace(
							Markers.MSG, "Going to feed {} data items to the consumer {}",
							passedItems.size(), consumer
						);
					}
					consumer.feedBatch(passedItems);
					if(LOG.isTraceEnabled(Markers.MSG)) {
						LOG.trace(
							Markers.MSG, "{} data items were passed to the consumer {} successfully",
							passedItems.size(), consumer
						);
					}
				}
			} catch(final InterruptedException e) {
				LOG.debug(Markers.MSG, "Interrupted while submitting to the consumer");
			} catch(final RemoteException e) {
				LogUtil.exception(
					LOG, Level.WARN, e, "Failed to feed {} data items to \"{}\"",
					passedItems.size(), consumer
				);
			} catch(final RejectedExecutionException e) {
				if(LOG.isTraceEnabled(Markers.ERR)) {
					LogUtil.exception(
						LOG, Level.TRACE, e, "\"{}\" rejected {} data items", consumer,
						passedItems.size()
					);
				}
			}
			//
			counterResults.addAndGet(n);
		}
	}
	//
	@Override
	public void setLoadState(final LoadState<T> state) {
		if(state != null) {
			if(state.isLoadFinished(rtConfig)) {
				isLoadFinished.compareAndSet(false, true);
				LOG.warn(Markers.MSG, "\"{}\": nothing to do more", getName());
				return;
			}
			// apply parameters from loadState to current load executor
			final IOStats.Snapshot statsSnapshot = state.getStatsSnapshot();
			final long
				countSucc = statsSnapshot.getSuccCount(),
				countFail = statsSnapshot.getFailCount();
			counterSubm.addAndGet(countSucc + countFail);
			counterResults.set(countSucc + countFail);
			ioStats.markSucc(
				countSucc, statsSnapshot.getByteCount(), statsSnapshot.getDurationValues(),
				statsSnapshot.getLatencyValues()
			);
			ioStats.markFail(countFail);
			ioStats.markElapsedTime(statsSnapshot.getElapsedTime());
			loadedPrevState = state;
		}
	}
	//
	@Override @SuppressWarnings("unchecked")
	public LoadState<T> getLoadState()
	throws RemoteException {
		return new BasicLoadState.Builder<T, BasicLoadState<T>>()
			.setLoadNumber(instanceNum)
			.setRunTimeConfig(rtConfig)
			.setStatsSnapshot(lastStats)
			.setLastDataItem(lastDataItem)
			.build();
	}
	//
	@Override
	public IOStats.Snapshot getStatsSnapshot() {
		return lastStats;
	}
	//
	private boolean isDoneMaxCount() {
		return counterResults.get() >= maxCount;
	}
	//
	private boolean isDoneAllSubm() {
		if(LOG.isTraceEnabled(Markers.MSG)) {
			LOG.trace(
					Markers.MSG, "{}: all submitted: {}, results: {}, submitted: {}",
					getName(), isAllSubm.get(), counterResults.get(), counterSubm.get()
			);
		}
		return isAllSubm.get() && counterResults.get() >= counterSubm.get();
	}
	//
	@Override
	public final void shutdown() {
		if(isStarted.get() && !isShutdown.get()) {
			try {
				if(producer != null) {
					producer.interrupt(); // stop the producing right now
					LOG.debug(
						Markers.MSG, "Stopped the producer \"{}\" for \"{}\"", producer, getName()
					);
				}
			} catch(final IOException e) {
				LogUtil.exception(LOG, Level.WARN, e, "Failed to stop the producer: {}", producer);
			} finally {
				super.shutdown();
			}
		} else {
			LOG.debug(
				Markers.MSG,
				"{}: ignoring the shutdown invocation because has not been started yet",
				getName()
			);
		}
	}
	//
	@Override
	public void close()
	throws IOException {
		// interrupt the producing
		if(isClosed.compareAndSet(false, true)) {
			LOG.debug(Markers.MSG, "Invoked close for {}", getName());
			if(itemsFileBuff != null) {
				itemsFileBuff.getFilePath().toFile().delete();
			}
			interrupt();
			try {
				LOG.debug(Markers.MSG, "Forcing the shutdown");
				super.close();
				if(consumer != null) {
					consumer.shutdown(); // poison the consumer
					LOG.debug(Markers.MSG, "Consumer \"{}\" has been poisoned", consumer);
				}
			} catch(final IllegalStateException | RejectedExecutionException e) {
				LogUtil.exception(LOG, Level.DEBUG, e, "Failed to poison the consumer");
			} finally {
				releaseDaemon.interrupt();
				LoadCloseHook.del(this);
				if(loadedPrevState != null) {
					if(RESTORED_STATES_MAP.containsKey(rtConfig.getRunId())) {
						RESTORED_STATES_MAP.get(rtConfig.getRunId()).remove(loadedPrevState);
					}
				}
				LOG.debug(Markers.MSG, "\"{}\" closed successfully", getName());
			}
		} else {
			LOG.debug(
				Markers.MSG,
				"Not closing \"{}\" because it has been closed before already", getName()
			);
		}
	}
	//
	@Override
	protected final void finalize()
	throws Throwable {
		try {
			close();
		} catch(final IOException e) {
			LogUtil.exception(LOG, Level.WARN, e, "{}: failed to close", getName());
		} finally {
			super.finalize();
		}
	}
	//
	@Override
	public final Producer<T> getProducer() {
		return producer;
	}
	//
	@Override
	public final long getMaxCount() {
		return maxCount;
	}
	//
	@Override
	public final RequestConfig<T> getRequestConfig() {
		return reqConfigCopy;
	}
	//
	@Override
	public final void await()
	throws InterruptedException {
		await(Long.MAX_VALUE, TimeUnit.DAYS);
	}
	//
	@Override
	public final void await(final long timeOut, final TimeUnit timeUnit)
	throws InterruptedException {
		if(isInterrupted.get() || isClosed.get()) {
			return;
		}
		//
		long timeOutMicroSec = timeUnit.toMicros(timeOut);
		if(loadedPrevState != null) {
			if(isLoadFinished.get()) {
				return;
			}
			timeOutMicroSec -= loadedPrevState.getStatsSnapshot().getElapsedTime();
		}
		//
		lock.lock();
		try {
			LOG.debug(
				Markers.MSG, "{}: await for the done condition at most for {}[us]",
				getName(), timeOutMicroSec
			);
			if(condProducerDone.await(timeOutMicroSec, TimeUnit.MICROSECONDS)) {
				LOG.debug(Markers.MSG, "{}: await for the done condition is finished", getName());
			} else {
				LOG.debug(
					Markers.MSG, "{}: await timeout, unhandled results left: {}",
					getName(), counterSubm.get() - counterResults.get()
				);
			}
		} finally {
			lock.unlock();
		}
	}
}
