package com.emc.mongoose.storage.mock.impl.base;
//
import com.codahale.metrics.Counter;
import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
//
import com.emc.mongoose.common.conf.RunTimeConfig;
import com.emc.mongoose.common.log.LogUtil;
import com.emc.mongoose.common.log.Markers;
import com.emc.mongoose.common.net.ServiceUtils;
//
import com.emc.mongoose.core.api.load.executor.LoadExecutor;
//
import com.emc.mongoose.storage.mock.api.StorageMock;
import com.emc.mongoose.storage.mock.api.IOStats;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import javax.management.MBeanServer;
import java.util.concurrent.TimeUnit;
/**
 Created by kurila on 13.05.15.
 */
public final class BasicStorageIOStats
extends Thread
implements IOStats {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	private final MetricRegistry metricRegistry = new MetricRegistry();
	private final JmxReporter jmxReporter;
	private final Counter
		countFailWrite = metricRegistry.counter(
			MetricRegistry.name(
				StorageMock.class, IOType.WRITE.name(), LoadExecutor.METRIC_NAME_FAIL
			)
		),
		countFailRead = metricRegistry.counter(
			MetricRegistry.name(
				StorageMock.class, IOType.READ.name(), LoadExecutor.METRIC_NAME_FAIL
			)
		),
		countFailDelete = metricRegistry.counter(
			MetricRegistry.name(
				StorageMock.class, IOType.DELETE.name(), LoadExecutor.METRIC_NAME_FAIL
			)
		);
	private final Meter
		tpWrite = metricRegistry.meter(
			MetricRegistry.name(
				StorageMock.class, IOType.WRITE.name(), LoadExecutor.METRIC_NAME_TP
			)
		),
		tpRead = metricRegistry.meter(
			MetricRegistry.name(
				StorageMock.class, IOType.READ.name(), LoadExecutor.METRIC_NAME_TP
			)
		),
		tpDelete = metricRegistry.meter(
			MetricRegistry.name(
				StorageMock.class, IOType.DELETE.name(), LoadExecutor.METRIC_NAME_TP
			)
		),
		bwWrite = metricRegistry.meter(
			MetricRegistry.name(
				StorageMock.class, IOType.WRITE.name(), LoadExecutor.METRIC_NAME_BW
			)
		),
		bwRead = metricRegistry.meter(
			MetricRegistry.name(
				StorageMock.class, IOType.READ.name(), LoadExecutor.METRIC_NAME_BW
			)
		);
	//
	private final long updateMilliPeriod;
	private final StorageMock storage;
	//
	public BasicStorageIOStats(
		final RunTimeConfig runTimeConfig, final StorageMock storage
	) {
		super(BasicStorageIOStats.class.getSimpleName());
		setDaemon(true);
		updateMilliPeriod = TimeUnit.SECONDS.toMillis(runTimeConfig.getLoadMetricsPeriodSec());
		this.storage = storage;
		//
		final boolean flagServeIfNotLoadServer = runTimeConfig.getFlagServeIfNotLoadServer();
		if(flagServeIfNotLoadServer) {
			final MBeanServer mBeanServer = ServiceUtils.getMBeanServer(
				RunTimeConfig.getContext().getRemotePortExport()
			);
			jmxReporter = JmxReporter.forRegistry(metricRegistry)
				.convertDurationsTo(TimeUnit.SECONDS)
				.convertRatesTo(TimeUnit.SECONDS)
				.registerWith(mBeanServer)
				.build();
		} else {
			jmxReporter = null;
		}
	}
	//
	private final static String
		MSG_FMT_METRICS = "Capacity used: %.1f%%\n" +
			"\tOperation |Count       |Failed      |TP[/s]avg   |TP[/s]1min  |BW[MB/s]avg |BW[MB/s]1min\n" +
			"\t----------|------------|------------|------------|------------|------------|------------\n" +
			"\tWrite     |%12d|%12d|%12.3f|%12.3f|%12.3f|%12.3f\n" +
			"\tRead      |%12d|%12d|%12.3f|%12.3f|%12.3f|%12.3f\n" +
			"\tDelete    |%12d|%12d|%12.3f|%12.3f|            |";
	//
	@Override
	public final String toString() {
		return String.format(
			LogUtil.LOCALE_DEFAULT, MSG_FMT_METRICS,
			//
			100.0 * storage.getSize() / storage.getCapacity(),
			//
			tpWrite.getCount(), countFailWrite.getCount(),
			tpWrite.getMeanRate(), tpWrite.getOneMinuteRate(),
			bwWrite.getMeanRate() / 1048576, bwWrite.getOneMinuteRate() / 1048576,
			//
			tpRead.getCount(), countFailRead.getCount(),
			tpRead.getMeanRate(), tpRead.getOneMinuteRate(),
			bwRead.getMeanRate() / 1048576, bwRead.getOneMinuteRate() / 1048576,
			//
			tpDelete.getCount(), countFailDelete.getCount(),
			tpDelete.getMeanRate(), tpDelete.getOneMinuteRate()
		);
	}
	//
	@Override
	public final void start() {
		LOG.debug(Markers.MSG, "Start");
		if(jmxReporter != null) {
			jmxReporter.start();
		}
		super.start();
	}
	//
	@Override
	public final void run() {
		LOG.debug(Markers.MSG, "Running");
		try {
			while(updateMilliPeriod > 0 && !isInterrupted()) {
				LOG.info(Markers.PERF_AVG, toString());
				Thread.sleep(updateMilliPeriod);
			}
		} catch(final InterruptedException ignored) {
			LOG.debug(Markers.MSG, "Interrupted");
		} catch(final Exception e) {
			LogUtil.exception(LOG, Level.WARN, e, "Failure");
		} finally {
			close();
		}
	}
	//
	@Override
	public final void close() {
		if(!isInterrupted()) {
			interrupt();
		}
		if(jmxReporter != null) {
			try {
				jmxReporter.close();
			} catch(final Exception e) {
				LogUtil.exception(LOG, Level.DEBUG, e, "Closing the metrics reporter failure");
			}
		}
	}
	//
	@Override
	public final void markWrite(final boolean succ, final long size) {
		if(succ) {
			tpWrite.mark();
			bwWrite.mark(size);
		} else {
			countFailWrite.inc();
		}
	}
	//
	@Override
	public final void markRead(final boolean succ, final long size) {
		if(succ) {
			tpRead.mark();
			bwRead.mark(size);
		} else {
			countFailRead.inc();
		}
	}
	//
	@Override
	public final void markDelete(final boolean succ) {
		if(succ) {
			tpDelete.mark();
		} else {
			countFailDelete.inc();
		}
	}
	////////////////////////////////////////////////////////////////////////////////////////////////
	// methods necessary for throttling, perf adaptation, etc
	////////////////////////////////////////////////////////////////////////////////////////////////
	@Override
	public final double getRate() {
		return tpWrite.getOneMinuteRate() + tpRead.getOneMinuteRate() + tpDelete.getOneMinuteRate();
	}
	//
	@Override
	public final double getRateBytes() {
		return bwWrite.getOneMinuteRate() + bwRead.getOneMinuteRate();
	}
}
