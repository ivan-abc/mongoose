package com.emc.mongoose.tests.system;

import com.emc.mongoose.common.api.SizeInBytes;
import com.emc.mongoose.common.env.PathUtil;
import com.emc.mongoose.model.io.IoType;
import com.emc.mongoose.model.io.task.IoTask;
import static com.emc.mongoose.model.io.task.IoTask.Status;
import com.emc.mongoose.run.scenario.JsonScenario;
import com.emc.mongoose.tests.system.base.HttpStorageDistributedScenarioTestBase;
import com.emc.mongoose.ui.cli.CliArgParser;
import com.emc.mongoose.ui.config.reader.jackson.ConfigParser;
import com.emc.mongoose.ui.log.appenders.LoadJobLogFileManager;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.CloseableThreadContext;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static com.emc.mongoose.common.Constants.KEY_STEP_NAME;
import static org.junit.Assert.assertEquals;

/**
 Created by kurila on 30.05.17.
 */
public final class ReadVerificationFailTest
extends HttpStorageDistributedScenarioTestBase {
	
	private static final SizeInBytes EXPECTED_ITEM_DATA_SIZE = new SizeInBytes("10KB");
	private static final int EXPECTED_CONCURRENCY = 15;
	private static final long EXPECTED_COUNT = 1000;
	private static final String ITEM_OUTPUT_FILE = ReadVerificationFailTest.class.getSimpleName() +
		".csv";
	
	@BeforeClass
	public static void setUpClass()
	throws Exception {
		try {
			Files.delete(Paths.get(ITEM_OUTPUT_FILE));
		} catch(final IOException ignored) {
		}
		
		JOB_NAME = ReadVerificationFailTest.class.getSimpleName() + "0";
		try(
			final CloseableThreadContext.Instance logCtx = CloseableThreadContext
				.put(KEY_STEP_NAME, JOB_NAME)
		) {
			FileUtils.deleteDirectory(Paths.get(PathUtil.getBaseDir(), "log", JOB_NAME).toFile());
			CONFIG_ARGS.add("--item-data-size=" + EXPECTED_ITEM_DATA_SIZE.toString());
			CONFIG_ARGS.add("--item-output-file=" + ITEM_OUTPUT_FILE);
			CONFIG_ARGS.add("--storage-driver-concurrency=" + EXPECTED_CONCURRENCY);
			CONFIG_ARGS.add("--test-step-limit-count=" + EXPECTED_COUNT);
			HttpStorageDistributedScenarioTestBase.setUpClass();
			SCENARIO.run();
		}
		
		JOB_NAME = ReadVerificationFailTest.class.getSimpleName() + "1";
		try(
			final CloseableThreadContext.Instance logCtx = CloseableThreadContext
				.put(KEY_STEP_NAME, JOB_NAME)
		) {
			FileUtils.deleteDirectory(Paths.get(PathUtil.getBaseDir(), "log", JOB_NAME).toFile());
			CONFIG_ARGS.clear();
			CONFIG_ARGS.add("--update");
			CONFIG_ARGS.add("--item-data-ranges-random=1");
			CONFIG_ARGS.add("--item-input-file=" + ITEM_OUTPUT_FILE);
			CONFIG_ARGS.add("--storage-driver-concurrency=" + EXPECTED_CONCURRENCY);
			CONFIG = ConfigParser.loadDefaultConfig();
			CONFIG.apply(
				CliArgParser.parseArgs(
					CONFIG.getAliasingConfig(), CONFIG_ARGS.toArray(new String[CONFIG_ARGS.size()])
				)
			);
			CONFIG.getTestConfig().getStepConfig().setName(JOB_NAME);
			CONFIG.getItemConfig().getOutputConfig().setFile(null);
			CONFIG.getTestConfig().getStepConfig().getLimitConfig().setCount(0);
			SCENARIO = new JsonScenario(CONFIG, SCENARIO_PATH.toFile());
			SCENARIO.run();
		}
		
		JOB_NAME = ReadVerificationFailTest.class.getSimpleName() + "2";
		try(
			final CloseableThreadContext.Instance logCtx = CloseableThreadContext
				.put(KEY_STEP_NAME, JOB_NAME)
		) {
			FileUtils.deleteDirectory(Paths.get(PathUtil.getBaseDir(), "log", JOB_NAME).toFile());
			CONFIG_ARGS.clear();
			CONFIG_ARGS.add("--read");
			CONFIG_ARGS.add("--item-data-verify=" + Boolean.TRUE.toString());
			CONFIG_ARGS.add("--item-input-file=" + ITEM_OUTPUT_FILE);
			CONFIG_ARGS.add("--storage-driver-concurrency=" + EXPECTED_CONCURRENCY);
			CONFIG = ConfigParser.loadDefaultConfig();
			CONFIG.apply(
				CliArgParser.parseArgs(
					CONFIG.getAliasingConfig(), CONFIG_ARGS.toArray(new String[CONFIG_ARGS.size()])
				)
			);
			CONFIG.getTestConfig().getStepConfig().setName(JOB_NAME);
			SCENARIO = new JsonScenario(CONFIG, SCENARIO_PATH.toFile());
			SCENARIO.run();
		}
		LoadJobLogFileManager.flush(JOB_NAME);
	}
	
	@AfterClass
	public static void tearDownClass()
	throws Exception {
		HttpStorageDistributedScenarioTestBase.tearDownClass();
	}
	
	@Test
	public void testIoTraceLogFile()
	throws Exception {
		final List<CSVRecord> ioTraceRecords = getIoTraceLogRecords();
		assertEquals(
			"There should be " + EXPECTED_COUNT + " records in the I/O trace log file, but got: " +
				ioTraceRecords.size(),
			EXPECTED_COUNT, ioTraceRecords.size()
		);
		CSVRecord csvRecord;
		for(int i = 0; i < ioTraceRecords.size(); i ++) {
			csvRecord = ioTraceRecords.get(i);
			assertEquals(
				"Record #" + i + ": unexpected operation type " + csvRecord.get("IoTypeCode"),
				IoType.READ,
				IoType.values()[Integer.parseInt(csvRecord.get("IoTypeCode"))]
			);
			assertEquals(
				"Record #" + i + ": unexpected status code " + csvRecord.get("StatusCode"),
				Status.RESP_FAIL_CORRUPT,
				IoTask.Status.values()[Integer.parseInt(csvRecord.get("StatusCode"))]
			);
		}
	}
}
