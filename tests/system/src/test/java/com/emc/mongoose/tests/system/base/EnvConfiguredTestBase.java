package com.emc.mongoose.tests.system.base;

import com.emc.mongoose.common.api.SizeInBytes;
import com.emc.mongoose.common.concurrent.Daemon;
import com.emc.mongoose.storage.driver.builder.StorageDriverBuilderSvc;
import com.emc.mongoose.storage.driver.service.BasicStorageDriverBuilderSvc;
import com.emc.mongoose.storage.mock.impl.http.StorageMockFactory;
import static com.emc.mongoose.ui.config.Config.ItemConfig;
import static com.emc.mongoose.ui.config.Config.StorageConfig;
import static com.emc.mongoose.ui.config.Config.StorageConfig.NetConfig.NodeConfig;
import static com.emc.mongoose.ui.config.Config.TestConfig.StepConfig;
import static com.emc.mongoose.ui.config.Config.StorageConfig.DriverConfig;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 Created by andrey on 04.06.17.
 */
public class EnvConfiguredTestBase
extends ConfiguredTestBase {

	public static final String KEY_ENV_STORAGE_DRIVER_TYPE = "STORAGE_DRIVER_TYPE";
	public static final String KEY_ENV_STORAGE_DRIVER_REMOTE = "STORAGE_DRIVER_REMOTE";
	public static final String KEY_ENV_STORAGE_DRIVER_CONCURRENCY = "STORAGE_DRIVER_CONCURRENCY";
	public static final String KEY_ENV_ITEM_DATA_SIZE = "ITEM_DATA_SIZE";
	protected static String STORAGE_DRIVER_TYPE = null;
	protected static boolean DISTRIBUTED_MODE_FLAG = false;
	protected static int CONCURRENCY = 0;
	protected static SizeInBytes ITEM_DATA_SIZE = null;
	protected static Map<String, Daemon> HTTP_STORAGE_MOCKS = null;
	protected static final int HTTP_STORAGE_NODE_COUNT = 1;
	protected static final int STORAGE_DRIVERS_COUNT = 2;
	private static List<StorageDriverBuilderSvc> STORAGE_DRIVER_BUILDER_SVCS = null;
	protected static final String STORAGE_TYPE_FS_KEY = "fs";

	@BeforeClass
	public static void setUpClass()
	throws Exception {
		ConfiguredTestBase.setUpClass();
		final Map<String, String> env = System.getenv();
		setUpStorageMockIfNeeded(env);
		setUpDistributedModeIfNeeded(env);
		setUpConcurrency(env);
		setUpItemDataSize(env);
	}

	@AfterClass
	public static void tearDownClass()
	throws Exception {
		tearDownStorageMockIfNeeded();
		tearDownDistributedModeIfNeeded();
		ConfiguredTestBase.tearDownClass();
	}

	private static void setUpStorageMockIfNeeded(final Map<String, String> env)
	throws Exception {
		if(!env.containsKey(KEY_ENV_STORAGE_DRIVER_TYPE)) {
			throw new IllegalArgumentException(
				"Environment property w/ name \"" + KEY_ENV_STORAGE_DRIVER_TYPE +
					"\" is not defined"
			);
		}
		STORAGE_DRIVER_TYPE = System.getenv(KEY_ENV_STORAGE_DRIVER_TYPE);
		final StorageConfig storageConfig = CONFIG.getStorageConfig();
		storageConfig.getDriverConfig().setType(STORAGE_DRIVER_TYPE);
		if(!STORAGE_TYPE_FS_KEY.equals(STORAGE_DRIVER_TYPE)) {
			HTTP_STORAGE_MOCKS = new HashMap<>();
			final NodeConfig nodeConfig = storageConfig.getNetConfig().getNodeConfig();
			final ItemConfig itemConfig = CONFIG.getItemConfig();
			final StepConfig stepConfig = CONFIG.getTestConfig().getStepConfig();
			final int port = nodeConfig.getPort();
			final List<String> nodeAddrs = new ArrayList<>();
			String nextNodeAddr;
			Daemon storageMock;
			final StorageMockFactory storageMockFactory = new StorageMockFactory(
				storageConfig, itemConfig, stepConfig
			);
			for(int i = 0; i < HTTP_STORAGE_NODE_COUNT; i ++) {
				nodeConfig.setPort(port + i);
				storageMock = storageMockFactory.newStorageMock();
				nextNodeAddr = "127.0.0.1:" + (port + i);
				storageMock.start();
				HTTP_STORAGE_MOCKS.put(nextNodeAddr, storageMock);
				nodeAddrs.add(nextNodeAddr);
			}
			nodeConfig.setAddrs(nodeAddrs);
			nodeConfig.setPort(port);
		}
	}

	private static void tearDownStorageMockIfNeeded()
	throws Exception {
		if(HTTP_STORAGE_MOCKS != null) {
			for(final Daemon storageMock : HTTP_STORAGE_MOCKS.values()) {
				storageMock.close();
			}
			HTTP_STORAGE_MOCKS.clear();
			HTTP_STORAGE_MOCKS = null;
		}
	}

	private static void setUpDistributedModeIfNeeded(final Map<String, String> env)
	throws Exception {
		if(!env.containsKey(KEY_ENV_STORAGE_DRIVER_REMOTE)) {
			throw new IllegalArgumentException(
				"Environment property w/ name \"" + KEY_ENV_STORAGE_DRIVER_REMOTE +
					"\" is not defined"
			);
		}
		DISTRIBUTED_MODE_FLAG = Boolean.parseBoolean(
			System.getenv(KEY_ENV_STORAGE_DRIVER_REMOTE)
		);
		if(DISTRIBUTED_MODE_FLAG) {
			STORAGE_DRIVER_BUILDER_SVCS = new ArrayList<>(STORAGE_DRIVERS_COUNT);
			final DriverConfig driverConfig = CONFIG.getStorageConfig().getDriverConfig();
			final List<String> storageDriverAddrs = new ArrayList<>(STORAGE_DRIVERS_COUNT);
			int nextStorageDriverPort;
			StorageDriverBuilderSvc nextStorageDriverBuilder;
			for(int i = 0; i < STORAGE_DRIVERS_COUNT; i ++) {
				nextStorageDriverPort = driverConfig.getPort() + i;
				nextStorageDriverBuilder = new BasicStorageDriverBuilderSvc(nextStorageDriverPort);
				nextStorageDriverBuilder.start();
				STORAGE_DRIVER_BUILDER_SVCS.add(nextStorageDriverBuilder);
				storageDriverAddrs.add("127.0.0.1:" + nextStorageDriverPort);
			}
			driverConfig.setAddrs(storageDriverAddrs);
			driverConfig.setRemote(true);
		}
	}

	private static void tearDownDistributedModeIfNeeded()
	throws Exception {
		if(DISTRIBUTED_MODE_FLAG && STORAGE_DRIVER_BUILDER_SVCS != null) {
			for(final StorageDriverBuilderSvc svc : STORAGE_DRIVER_BUILDER_SVCS) {
				svc.close();
			}
			STORAGE_DRIVER_BUILDER_SVCS.clear();
			STORAGE_DRIVER_BUILDER_SVCS = null;
		}
	}

	private static void setUpConcurrency(final Map<String, String> env)
	throws Exception {
		if(!env.containsKey(KEY_ENV_STORAGE_DRIVER_CONCURRENCY)) {
			throw new IllegalArgumentException(
				"Environment property w/ name \"" + KEY_ENV_STORAGE_DRIVER_CONCURRENCY +
					"\" is not defined"
			);
		}
		CONCURRENCY = Integer.parseInt(System.getenv(KEY_ENV_STORAGE_DRIVER_CONCURRENCY));
		if(CONCURRENCY < 1) {
			throw new IllegalArgumentException("Concurrency level should be an integer > 0");
		}
		CONFIG.getStorageConfig().getDriverConfig().setConcurrency(CONCURRENCY);
	}

	private static void setUpItemDataSize(final Map<String, String> env)
	throws Exception {
		if(!env.containsKey(KEY_ENV_ITEM_DATA_SIZE)) {
			throw new IllegalArgumentException(
				"Environment property w/ name \"" + KEY_ENV_ITEM_DATA_SIZE + "\" is not defined"
			);
		}
		ITEM_DATA_SIZE = new SizeInBytes(System.getenv(KEY_ENV_ITEM_DATA_SIZE));
		CONFIG.getItemConfig().getDataConfig().setSize(ITEM_DATA_SIZE);
	}
}