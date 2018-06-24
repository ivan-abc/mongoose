package com.emc.mongoose.load.step.linear;

import com.emc.mongoose.env.Extension;
import com.emc.mongoose.exception.OmgShootMyFootException;
import com.emc.mongoose.data.DataInput;
import com.emc.mongoose.item.io.IoType;
import com.emc.mongoose.item.Item;
import com.emc.mongoose.item.ItemFactory;
import com.emc.mongoose.item.ItemInfoFileOutput;
import com.emc.mongoose.item.ItemType;
import com.emc.mongoose.load.controller.LoadController;
import com.emc.mongoose.load.generator.LoadGenerator;
import com.emc.mongoose.load.step.local.LoadStepLocalBase;
import com.emc.mongoose.storage.driver.StorageDriver;
import com.emc.mongoose.load.controller.LoadControllerImpl;
import com.emc.mongoose.load.generator.LoadGeneratorBuilderImpl;
import com.emc.mongoose.load.step.LoadStepBase;
import com.emc.mongoose.logging.LogUtil;
import com.emc.mongoose.logging.Loggers;

import com.github.akurilov.commons.io.Output;
import com.github.akurilov.commons.system.SizeInBytes;

import static com.emc.mongoose.load.step.linear.LinearLoadStep.initConfig;
import static com.github.akurilov.commons.collection.TreeUtil.reduceForest;
import com.github.akurilov.commons.concurrent.throttle.RateThrottle;

import com.github.akurilov.confuse.Config;
import static com.github.akurilov.confuse.Config.deepToMap;
import com.github.akurilov.confuse.exceptions.InvalidValuePathException;
import com.github.akurilov.confuse.exceptions.InvalidValueTypeException;
import com.github.akurilov.confuse.impl.BasicConfig;

import org.apache.logging.log4j.Level;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

public class LinearLoadStepLocal
extends LoadStepLocalBase {

	public LinearLoadStepLocal(
		final Config baseConfig, final List<Extension> extensions,
		final List<Map<String, Object>> overrides
	) {
		super(baseConfig, extensions, overrides);
	}

	@Override
	public String getTypeName() {
		return LinearLoadStepExtension.TYPE;
	}

	@Override
	protected LoadStepBase copyInstance(final List<Map<String, Object>> stepConfigs) {
		return new LinearLoadStepLocal(baseConfig, extensions, stepConfigs);
	}

	@Override
	protected void init() {

		final Config config = initConfig(baseConfig, stepConfigs);
		actualConfig(config);

		final Config loadConfig = config.configVal("load");
		final Config stepConfig = loadConfig.configVal("step");
		final IoType ioType = IoType.valueOf(loadConfig.stringVal("type").toUpperCase());
		final int concurrency = stepConfig.intVal("limit-concurrency");
		final Config outputConfig = config.configVal("output");
		final Config metricsConfig = outputConfig.configVal("metrics");
		final SizeInBytes itemDataSize = new SizeInBytes(config.stringVal("item-data-size"));
		final int originIndex = 0;
		final boolean outputColorFlag = outputConfig.boolVal("color");
		initMetrics(originIndex, ioType, concurrency, metricsConfig, itemDataSize, outputColorFlag);

		final Config itemConfig = config.configVal("item");
		final Config storageConfig = config.configVal("storage");
		final Config dataConfig = itemConfig.configVal("data");
		final Config dataInputConfig = dataConfig.configVal("input");
		final Config limitConfig = stepConfig.configVal("limit");
		final Config dataLayerConfig = dataInputConfig.configVal("layer");

		final String testStepId = stepConfig.stringVal("id");

		try {

			final DataInput dataInput = DataInput.instance(
				dataInputConfig.stringVal("file"), dataInputConfig.stringVal("seed"),
				new SizeInBytes(dataLayerConfig.stringVal("size")),
				dataLayerConfig.intVal("cache")
			);

			try {

				final StorageDriver driver = StorageDriver.instance(
					extensions, loadConfig, storageConfig, dataInput,
					dataConfig.boolVal("verify"), testStepId
				);
				drivers.add(driver);

				final ItemType itemType = ItemType.valueOf(
					itemConfig.stringVal("type").toUpperCase()
				);
				final ItemFactory<Item> itemFactory = ItemType.getItemFactory(itemType);
				final double rateLimit = stepConfig.doubleVal("limit-rate");

				try {
					final LoadGenerator generator = new LoadGeneratorBuilderImpl<>()
						.itemConfig(itemConfig)
						.loadConfig(loadConfig)
						.limitConfig(limitConfig)
						.itemType(itemType)
						.itemFactory((ItemFactory) itemFactory)
						.storageDriver(driver)
						.authConfig(storageConfig.configVal("auth"))
						.originIndex(0)
						.rateThrottle(rateLimit > 0 ? new RateThrottle(rateLimit) : null)
						.weightThrottle(null)
						.build();
					generators.add(generator);

					final LoadController controller = new LoadControllerImpl<>(
						testStepId, generator, driver, metricsContexts.get(0), limitConfig,
						outputConfig.boolVal("metrics-trace-persist"),
						loadConfig.intVal("batch-size"),
						loadConfig.intVal("generator-recycle-limit")
					);
					controllers.add(controller);

					final String itemOutputFile = itemConfig.stringVal("output-file");
					if(itemOutputFile != null && itemOutputFile.length() > 0) {
						final Path itemOutputPath = Paths.get(itemOutputFile);
						if(Files.exists(itemOutputPath)) {
							Loggers.ERR.warn("Items output file \"{}\" already exists", itemOutputPath);
						}
						try {
							final Output<? extends Item> itemOutput = new ItemInfoFileOutput<>(itemOutputPath);
							controller.ioResultsOutput(itemOutput);
						} catch(final IOException e) {
							LogUtil.exception(
								Level.ERROR, e,
								"Failed to initialize the item output, the processed items " +
									"info won't be persisted"
							);
						}
					}
				} catch(final OmgShootMyFootException e) {
					throw new IllegalStateException(
						"Failed to initialize the load generator", e
					);
				}
			} catch(final OmgShootMyFootException e) {
				throw new IllegalStateException("Failed to initialize the storage driver", e);
			} catch(final InterruptedException e) {
				throw new CancellationException();
			}
		} catch(final IOException e) {
			throw new IllegalStateException("Failed to initialize the data input", e);
		}
	}
}
