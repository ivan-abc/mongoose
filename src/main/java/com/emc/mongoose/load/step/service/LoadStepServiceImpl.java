package com.emc.mongoose.load.step.service;

import com.emc.mongoose.env.Extension;
import com.emc.mongoose.metrics.MetricsSnapshot;
import com.emc.mongoose.svc.ServiceBase;
import com.emc.mongoose.load.step.LoadStep;
import com.emc.mongoose.load.step.LoadStepFactory;
import com.emc.mongoose.logging.Loggers;
import static com.emc.mongoose.Constants.KEY_CLASS_NAME;
import static com.emc.mongoose.Constants.KEY_STEP_ID;

import com.github.akurilov.confuse.Config;

import static com.github.akurilov.commons.system.DirectMemUtil.REUSABLE_BUFF_SIZE_MAX;
import static org.apache.logging.log4j.CloseableThreadContext.Instance;
import static org.apache.logging.log4j.CloseableThreadContext.put;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class LoadStepServiceImpl
extends ServiceBase
implements LoadStepService {

	private final LoadStep localLoadStep;

	public LoadStepServiceImpl(
		final int port, final List<Extension> extensions, final String stepType, final Config config,
		final List<Map<String, Object>> stepConfigs
	) {
		super(port);

		// don't override the step-id value on the remote node again
		config.val("load-step-idAutoGenerated", false);

		final List<LoadStepFactory> loadStepFactories = extensions
			.stream()
			.filter(ext -> ext instanceof LoadStepFactory)
			.map(ext -> (LoadStepFactory) ext)
			.collect(Collectors.toList());

		final LoadStepFactory selectedFactory = loadStepFactories
			.stream()
			.filter(f -> stepType.endsWith(f.id()))
			.findFirst()
			.orElseThrow(
				() -> new IllegalStateException(
					"Failed to find the load step implementation for type \"" + stepType +
						"\", available types: " +
						Arrays.toString(
							loadStepFactories.stream().map(LoadStepFactory::id).toArray()
						)
				)
			);

		try(final Instance logCtx = put(KEY_CLASS_NAME, getClass().getSimpleName())) {
			localLoadStep = selectedFactory.createLocal(config, extensions, stepConfigs);
			Loggers.MSG.info("New step service for \"{}\"", config.stringVal("load-step-id"));
			super.doStart();
		}
	}

	@Override
	protected final void doStart() {
		try(
			final Instance logCtx = put(KEY_CLASS_NAME, getClass().getSimpleName()).put(KEY_STEP_ID, localLoadStep.id())
		) {
			localLoadStep.start();
			Loggers.MSG.info("Step service for \"{}\" is started", localLoadStep.id());
		} catch(final RemoteException ignored) {
		}
	}

	@Override
	protected void doStop() {
		try(
			final Instance logCtx = put(KEY_CLASS_NAME, getClass().getSimpleName()).put(KEY_STEP_ID, localLoadStep.id())
		) {
			localLoadStep.stop();
			Loggers.MSG.info("Step service for \"{}\" is stopped", localLoadStep.id());
		} catch(final RemoteException ignored) {
		}
	}

	@Override
	protected final void doClose()
	throws IOException {
		try(
			final Instance logCtx = put(KEY_CLASS_NAME, getClass().getSimpleName())
				.put(KEY_STEP_ID, localLoadStep.id())
		) {
			super.doStop();
			localLoadStep.close();
			Loggers.MSG.info("Step service for \"{}\" is closed", localLoadStep.id());
		}
	}

	@Override
	public String name() {
		return SVC_NAME_PREFIX + hashCode();
	}

	@Override
	public LoadStep config(final Map<String, Object> config)
	throws RemoteException {
		return localLoadStep.config(config);
	}

	@Override
	public final String id()
	throws RemoteException {
		return localLoadStep.id();
	}

	@Override
	public final String getTypeName()
	throws RemoteException {
		return localLoadStep.getTypeName();
	}

	@Override
	public final List<MetricsSnapshot> metricsSnapshots()
	throws RemoteException {
		return localLoadStep.metricsSnapshots();
	}

	@Override
	public final boolean await(final long timeout, final TimeUnit timeUnit)
	throws IllegalStateException, InterruptedException {
		try(
			final Instance logCtx = put(KEY_CLASS_NAME, getClass().getSimpleName()).put(KEY_STEP_ID, localLoadStep.id())
		) {
			return localLoadStep.await(timeout, timeUnit);
		} catch(final RemoteException ignored) {
		}
		return false;
	}

	@Override
	public final String newTmpFileName()
	throws IOException {
		return localLoadStep.newTmpFileName();
	}

	@Override
	public final byte[] readFromFile(final String fileName, final long offset)
	throws IOException {
		return localLoadStep.readFromFile(fileName, offset);
	}

	@Override
	public final void writeToFile(final String fileName, final byte[] buff)
	throws IOException {
		localLoadStep.writeToFile(fileName, buff);
	}

	@Override
	public final long fileSize(final String fileName)
	throws IOException {
		return localLoadStep.fileSize(fileName);
	}

	@Override
	public final void truncateFile(final String fileName, final long size)
	throws IOException {
		localLoadStep.truncateFile(fileName, size);
	}

	@Override
	public final void deleteFile(final String fileName)
	throws IOException {
		localLoadStep.deleteFile(fileName);
	}
}
