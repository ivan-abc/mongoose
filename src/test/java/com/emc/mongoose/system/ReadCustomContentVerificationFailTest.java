package com.emc.mongoose.system;

import com.emc.mongoose.config.BundledDefaultsProvider;
import com.emc.mongoose.item.io.IoType;
import com.emc.mongoose.item.io.task.IoTask;
import com.emc.mongoose.svc.ServiceUtil;
import com.emc.mongoose.system.base.params.*;
import com.emc.mongoose.system.util.DirWithManyFilesDeleter;
import com.emc.mongoose.system.util.docker.HttpStorageMockContainer;
import com.emc.mongoose.system.util.docker.MongooseContainer;
import com.emc.mongoose.system.util.docker.MongooseSlaveNodeContainer;
import com.github.akurilov.commons.concurrent.AsyncRunnableBase;
import com.github.akurilov.confuse.Config;
import com.github.akurilov.confuse.SchemaProvider;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.emc.mongoose.Constants.APP_NAME;
import static com.emc.mongoose.config.CliArgUtil.ARG_PATH_SEP;
import static com.emc.mongoose.system.util.LogValidationUtil.testIoTraceLogRecords;
import static com.emc.mongoose.system.util.TestCaseUtil.stepId;
import static com.emc.mongoose.system.util.docker.MongooseContainer.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class ReadCustomContentVerificationFailTest {

    @Parameterized.Parameters(name = "{0}, {1}, {2}, {3}")
    public static List<Object[]> envParams() {
        return EnvParams.PARAMS;
    }

    private final int timeoutInMillis = 1000_000;
    private final String ITEM_LIST_FILE = CONTAINER_SHARE_PATH + File.separator + "/example/content/textexample";
    private final Map<String, HttpStorageMockContainer> storageMocks = new HashMap<>();
    private final Map<String, MongooseSlaveNodeContainer> slaveNodes = new HashMap<>();
    private final MongooseContainer testContainer;
    private final String stepId;
    private final StorageType storageType;
    private final RunMode runMode;
    private final Concurrency concurrency;
    private final ItemSize itemSize;
    private final Config config;
    private final String containerItemOutputPath;
    private final String hostItemOutputFile = HOST_SHARE_PATH + File.separator
            + CreateLimitBySizeTest.class.getSimpleName() + ".csv";

    private String stdOutContent = null;

    public ReadCustomContentVerificationFailTest(
            final StorageType storageType, final RunMode runMode, final Concurrency concurrency,
            final ItemSize itemSize
    ) throws Exception {

        final Map<String, Object> schema = SchemaProvider.resolveAndReduce(
                APP_NAME, Thread.currentThread().getContextClassLoader());
        config = new BundledDefaultsProvider().config(ARG_PATH_SEP, schema);

        stepId = stepId(getClass(), storageType, runMode, concurrency, itemSize);
        containerItemOutputPath = MongooseContainer.getContainerItemOutputPath(stepId);

        try {
            FileUtils.deleteDirectory(MongooseContainer.HOST_LOG_PATH.toFile());
        } catch (final IOException ignored) {
        }

        this.storageType = storageType;
        this.runMode = runMode;
        this.concurrency = concurrency;
        this.itemSize = itemSize;

        if (storageType.equals(StorageType.FS)) {
            try {
                DirWithManyFilesDeleter.deleteExternal(containerItemOutputPath);
            } catch (final Exception e) {
                e.printStackTrace(System.err);
            }
        }
        try {
            Files.delete(Paths.get(hostItemOutputFile));
        } catch (final Exception ignored) {
        }

        final List<String> env = System.getenv()
                .entrySet()
                .stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.toList());

        final List<String> args = new ArrayList<>();

        args.add("--item-data-input-file=" + ITEM_LIST_FILE);

        switch (storageType) {
            case ATMOS:
            case S3:
            case SWIFT:
                final HttpStorageMockContainer storageMock = new HttpStorageMockContainer(
                        HttpStorageMockContainer.DEFAULT_PORT, false, null, null, Character.MAX_RADIX,
                        HttpStorageMockContainer.DEFAULT_CAPACITY,
                        HttpStorageMockContainer.DEFAULT_CONTAINER_CAPACITY,
                        HttpStorageMockContainer.DEFAULT_CONTAINER_COUNT_LIMIT,
                        HttpStorageMockContainer.DEFAULT_FAIL_CONNECT_EVERY,
                        HttpStorageMockContainer.DEFAULT_FAIL_RESPONSES_EVERY,
                        0
                );
                final String addr = "127.0.0.1:" + HttpStorageMockContainer.DEFAULT_PORT;
                storageMocks.put(addr, storageMock);
                args.add(
                        "--storage-net-node-addrs="
                                + storageMocks.keySet().stream().collect(Collectors.joining(","))
                );
                break;
        }

        switch (runMode) {
            case DISTRIBUTED:
                final String localExternalAddr = ServiceUtil.getAnyExternalHostAddress();
                for (int i = 1; i < runMode.getNodeCount(); i++) {
                    final int port = MongooseSlaveNodeContainer.DEFAULT_PORT + i;
                    final MongooseSlaveNodeContainer nodeSvc = new MongooseSlaveNodeContainer(port);
                    final String addr = localExternalAddr + ":" + port;
                    slaveNodes.put(addr, nodeSvc);
                }
                args.add(
                        "--load-step-node-addrs="
                                + slaveNodes.keySet().stream().collect(Collectors.joining(","))
                );
                break;
        }

        final String containerScenarioPath = containerScenarioPath(getClass());
        testContainer = new MongooseContainer(
                stepId, storageType, runMode, concurrency, itemSize, containerScenarioPath, env, args
        );
    }

    @Before
    public final void setUp()
            throws Exception {
        storageMocks.values().forEach(AsyncRunnableBase::start);
        slaveNodes.values().forEach(AsyncRunnableBase::start);
        testContainer.start();
        testContainer.await(timeoutInMillis, TimeUnit.MILLISECONDS);
        stdOutContent = testContainer.stdOutContent();
    }

    @After
    public final void tearDown()
            throws Exception {

        testContainer.close();

        slaveNodes.values().parallelStream().forEach(
                storageMock -> {
                    try {
                        storageMock.close();
                    } catch (final Throwable t) {
                        t.printStackTrace(System.err);
                    }
                }
        );
        storageMocks.values().parallelStream().forEach(
                storageMock -> {
                    try {
                        storageMock.close();
                    } catch (final Throwable t) {
                        t.printStackTrace(System.err);
                    }
                }
        );
    }

    @Test
    public final void test()
            throws Exception {
        final LongAdder ioTraceRecCount = new LongAdder();
        final Consumer<CSVRecord> ioTraceRecTestFunc = ioTraceRec -> {
            assertEquals(
                    "Record #" + ioTraceRecCount.sum() + ": unexpected operation type " +
                            ioTraceRec.get("IoTypeCode"),
                    IoType.READ,
                    IoType.values()[Integer.parseInt(ioTraceRec.get("IoTypeCode"))]
            );
            assertEquals(
                    "Record #" + ioTraceRecCount.sum() + ": unexpected status code " +
                            ioTraceRec.get("StatusCode"),
                    IoTask.Status.RESP_FAIL_CORRUPT,
                    IoTask.Status.values()[Integer.parseInt(ioTraceRec.get("StatusCode"))]
            );
            ioTraceRecCount.increment();
        };
        testIoTraceLogRecords(stepId, ioTraceRecTestFunc);
        assertTrue("The count of the I/O trace records should be > 0", ioTraceRecCount.sum() > 0);
    }
}