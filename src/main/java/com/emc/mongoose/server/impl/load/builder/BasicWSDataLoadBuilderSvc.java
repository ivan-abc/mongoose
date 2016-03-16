package com.emc.mongoose.server.impl.load.builder;
//mongoose-common.jar
import com.emc.mongoose.common.conf.Constants;
import com.emc.mongoose.common.conf.RunTimeConfig;
import com.emc.mongoose.common.conf.SizeUtil;
import com.emc.mongoose.common.exceptions.DuplicateSvcNameException;
import com.emc.mongoose.common.log.LogUtil;
import com.emc.mongoose.common.log.Markers;
import com.emc.mongoose.common.net.ServiceUtil;
//mongoose-core-api.jar
import com.emc.mongoose.core.api.item.data.WSObject;
import com.emc.mongoose.core.api.io.task.IOTask;
import com.emc.mongoose.core.api.load.executor.LoadExecutor;
import com.emc.mongoose.core.api.io.conf.WSRequestConfig;
//mongoose-server-api.jar
import com.emc.mongoose.server.api.load.executor.WSDataLoadSvc;
import com.emc.mongoose.server.api.load.builder.WSDataLoadBuilderSvc;
// mongoose-core-impl.jar
import com.emc.mongoose.core.impl.load.builder.BasicWSDataLoadBuilder;
// mongoose-server-impl.jar
import com.emc.mongoose.server.impl.load.executor.BasicWSDataLoadSvc;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
/**
 Created by kurila on 30.05.14.
 */
public class BasicWSDataLoadBuilderSvc<T extends WSObject, U extends WSDataLoadSvc<T>>
extends BasicWSDataLoadBuilder<T, U>
implements WSDataLoadBuilderSvc<T, U> {
	//
	private final static Logger LOG = LogManager.getLogger();
	private final static AtomicInteger FORK_COUNTER = new AtomicInteger(0);
	//
	private String name = getClass().getName();
	//
	public BasicWSDataLoadBuilderSvc(final RunTimeConfig runTimeConfig)
	throws RemoteException {
		super(runTimeConfig);
	}
	//
	@Override
	public final int fork()
	throws RemoteException {
		try {
			final BasicWSDataLoadBuilderSvc<T, U>
				forkedSvc = (BasicWSDataLoadBuilderSvc<T, U>) clone();
			final int forkNum = FORK_COUNTER.getAndIncrement();
			forkedSvc.name = name + forkNum;
			forkedSvc.start();
			LOG.info(Markers.MSG, "Service \"" + name + "\" started");
			return forkNum;
		} catch(final CloneNotSupportedException e) {
			throw new RemoteException(e.toString());
		}
	}
	//
	@Override
	public String buildRemotely()
	throws RemoteException {
		U loadSvc = build();
		LOG.info(Markers.MSG, rtConfig.toString());
		ServiceUtil.create(loadSvc);
		return loadSvc.getName();
	}
	//
	@Override
	public final String getName() {
		return name;
	}
	//
	@Override
	public final int getNextInstanceNum(final String runId) {
		return LoadExecutor.NEXT_INSTANCE_NUM.get();
	}
	//
	@Override
	public final void setNextInstanceNum(final String runId, final int instanceN) {
		LoadExecutor.NEXT_INSTANCE_NUM.set(instanceN);
	}
	//
	@Override
	public final void invokePreConditions() {} // discard any precondition invocations in load server mode
	//
	@Override @SuppressWarnings("unchecked")
	protected final U buildActually()
	throws IllegalStateException {
		if(ioConfig == null) {
			throw new IllegalStateException("Should specify request builder instance before instancing");
		}
		//
		final WSRequestConfig wsReqConf = WSRequestConfig.class.cast(ioConfig);
		final RunTimeConfig localRunTimeConfig = RunTimeConfig.getContext();
		// the statement below fixes hi-level API distributed mode usage and tests
		localRunTimeConfig.setProperty(RunTimeConfig.KEY_RUN_MODE, Constants.RUN_MODE_SERVER);
		if(minObjSize > maxObjSize) {
			throw new IllegalStateException(
				String.format(
					LogUtil.LOCALE_DEFAULT, "Min object size %s should be less than upper bound %s",
					SizeUtil.formatSize(minObjSize), SizeUtil.formatSize(maxObjSize)
				)
			);
		}
		//
		final IOTask.Type loadType = ioConfig.getLoadType();
		final int
			connPerNode = loadTypeConnPerNode.get(loadType),
			minThreadCount = getMinIOThreadCount(
				loadTypeWorkerCount.get(loadType), storageNodeAddrs.length, connPerNode
			);
		//
		return (U) new BasicWSDataLoadSvc<>(
			localRunTimeConfig, wsReqConf, storageNodeAddrs, connPerNode, minThreadCount,
			itemSrc == null ? getDefaultItemSource() : itemSrc,
			maxCount, minObjSize, maxObjSize, objSizeBias,
			manualTaskSleepMicroSecs, rateLimit, updatesPerItem
		);
	}
	//
	public final void start()
	throws RemoteException {
		LOG.debug(Markers.MSG, "Load builder service instance created");
		try {
		/*final RemoteStub stub = */
		ServiceUtil.create(this);
		/*LOG.debug(Markers.MSG, stub.toString());*/
		} catch (final DuplicateSvcNameException e) {
			LogUtil.exception(LOG, Level.ERROR, e, "Possible load service usage collision");
		}
		LOG.info(Markers.MSG, "Server started and waiting for the requests");
	}
	//
	@Override
	public void shutdown()
	throws RemoteException, IllegalStateException {
	}
	//
	@Override
	public void await()
	throws RemoteException, InterruptedException {
		await(Long.MAX_VALUE, TimeUnit.DAYS);
	}
	//
	@Override
	public void await(final long timeOut, final TimeUnit timeUnit)
	throws RemoteException, InterruptedException {
		timeUnit.sleep(timeOut);
	}
	//
	@Override
	public void interrupt()
	throws RemoteException {
	}
	//
	@Override
	public final void close()
	throws IOException {
		super.close();
		ServiceUtil.close(this);
		LOG.info(Markers.MSG, "Service \"{}\" closed", name);
	}
	//
	@Override
	protected boolean itemsFileExists(final String filePathStr) {
		if(filePathStr != null && !filePathStr.isEmpty()) {
			final Path listFilePath = Paths.get(filePathStr);
			if(!Files.exists(listFilePath)) {
				LOG.debug(Markers.MSG, "Specified input file \"{}\" doesn't exists", listFilePath);
			} else if(!Files.isReadable(listFilePath)) {
				LOG.debug(Markers.MSG, "Specified input file \"{}\" isn't readable", listFilePath);
			} else if(Files.isDirectory(listFilePath)) {
				LOG.debug(Markers.MSG, "Specified input file \"{}\" is a directory", listFilePath);
			} else {
				return true;
			}
		}
		return false;
	}
}