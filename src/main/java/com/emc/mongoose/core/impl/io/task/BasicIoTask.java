package com.emc.mongoose.core.impl.io.task;
//
import com.emc.mongoose.common.conf.enums.LoadType;
import com.emc.mongoose.common.log.Markers;
//
import com.emc.mongoose.core.api.item.base.Item;
import com.emc.mongoose.core.api.io.task.IoTask;
import com.emc.mongoose.core.api.load.metrics.IoStats;
//
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
/**
 Created by kurila on 20.10.15.
 */
public class BasicIoTask<T extends Item>
implements IoTask<T> {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	protected final LoadType ioType;
	protected final T item;
	//
	protected volatile IoTask.Status status = IoTask.Status.FAIL_UNKNOWN;
	protected volatile long
		reqTimeStart = 0, reqTimeDone = 0,
		respTimeStart = 0, respDataTimeStart = 0, respTimeDone = 0,
		countBytesDone = 0;
	//
	public BasicIoTask(final T item, final LoadType ioType) {
		this.ioType = ioType;
		this.item = item;
	}
	//
	@Override
	public final T getItem() {
		return item;
	}
	//
	@Override
	public final LoadType getLoadType() {
		return ioType;
	}
	//
	@Override
	public final Status getStatus() {
		return status;
	}
	//
	protected final static ThreadLocal<StringBuilder>
		PERF_TRACE_MSG_BUILDER = new ThreadLocal<StringBuilder>() {
			@Override
			protected final StringBuilder initialValue() {
				return new StringBuilder();
			}
		};
	//
	@Override
	public final void mark(final IoStats ioStats) {
		// perf traces logging
		final int
			reqDuration = (int) (respTimeDone - reqTimeStart),
			respLatency = (int) (respTimeStart - reqTimeDone),
			respDataLatency = (int) (respDataTimeStart - reqTimeDone);
		if(LOG.isInfoEnabled(Markers.PERF_TRACE)) {
			StringBuilder strBuilder = PERF_TRACE_MSG_BUILDER.get();
			if(strBuilder == null) {
				strBuilder = new StringBuilder();
				PERF_TRACE_MSG_BUILDER.set(strBuilder);
			} else {
				strBuilder.setLength(0); // clear/reset
			}
			LOG.info(
				Markers.PERF_TRACE,
				strBuilder
					.append(item.getName()).append(',')
					.append(countBytesDone).append(',')
					.append(status.code).append(',')
					.append(reqTimeStart).append(',')
					.append(respLatency > 0 ? respLatency : 0).append(',')
					.append(respDataTimeStart > 0 ? respDataLatency : -1).append(',')
					.append(reqDuration)
					.toString()
			);
		}
		// stats refreshing
		if(status == IoTask.Status.SUCC) {
			// update the metrics with success
			if(respLatency > 0 && respLatency > reqDuration) {
				LOG.warn(
					Markers.ERR, "{}: latency {} is more than duration: {}", this, respLatency,
					reqDuration
				);
			}
			ioStats.markSucc(countBytesDone, reqDuration, respLatency);
		}
	}
}