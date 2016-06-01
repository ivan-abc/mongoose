package com.emc.mongoose.client.impl.load.builder;
//
import com.emc.mongoose.client.api.load.builder.DataLoadBuilderClient;
import com.emc.mongoose.client.api.load.executor.DataLoadClient;
//
import com.emc.mongoose.common.conf.AppConfig;
import com.emc.mongoose.common.conf.BasicConfig;
import com.emc.mongoose.common.conf.Constants;
import com.emc.mongoose.common.conf.DataRangesConfig;
import com.emc.mongoose.common.conf.SizeInBytes;
import com.emc.mongoose.common.conf.enums.ItemNamingType;
import com.emc.mongoose.common.io.Input;
//
import com.emc.mongoose.common.log.Markers;
import com.emc.mongoose.core.api.io.conf.IoConfig;
import com.emc.mongoose.core.api.item.data.DataItem;
import com.emc.mongoose.core.api.item.data.FileDataItemInput;
//
import com.emc.mongoose.core.impl.item.data.CsvFileDataItemInput;
import com.emc.mongoose.server.api.load.builder.DataLoadBuilderSvc;
import com.emc.mongoose.server.api.load.executor.DataLoadSvc;
//
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.IOException;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.util.NoSuchElementException;
/**
 Created by kurila on 20.10.15.
 */
public abstract class DataLoadBuilderClientBase<
	T extends DataItem,
	W extends DataLoadSvc<T>,
	U extends DataLoadClient<T, W>,
	V extends DataLoadBuilderSvc<T, W>
>
extends LoadBuilderClientBase<T, W, U, V>
implements DataLoadBuilderClient<T, W, U> {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	protected SizeInBytes sizeConfig;
	protected DataRangesConfig rangesConfig = null;
	//
	protected DataLoadBuilderClientBase()
	throws IOException {
		this(BasicConfig.THREAD_CONTEXT.get());
	}
	//
	protected DataLoadBuilderClientBase(final AppConfig appConfig)
	throws IOException {
		super(appConfig);
	}
	//
	@Override
	public DataLoadBuilderClientBase<T, W, U, V> clone()
	throws CloneNotSupportedException {
		final DataLoadBuilderClientBase<T, W, U, V>
			lb = (DataLoadBuilderClientBase<T, W, U, V>) super.clone();
		lb.sizeConfig = sizeConfig;
		lb.rangesConfig = rangesConfig;
		return lb;
	}
	//
	@Override
	public DataLoadBuilderClient<T, W, U> setAppConfig(final AppConfig appConfig)
	throws IllegalStateException, RemoteException {
		super.setAppConfig(appConfig);
		setDataSize(appConfig.getItemDataSize());
		setDataRanges(appConfig.getItemDataRanges());
		//
		try {
			final String listFile = appConfig.getItemSrcFile();
			if(itemsFileExists(listFile) && loadSvcMap != null) {
				setInput(
					new CsvFileDataItemInput<>(
						Paths.get(listFile), (Class<T>) ioConfig.getItemClass(),
						ioConfig.getContentSource()
					)
				);
				// disable file-based item sources on the load servers side
				for(final V nextLoadBuilder : loadSvcMap.values()) {
					nextLoadBuilder.setInput(null);
				}
			}
		} catch(final NoSuchElementException e) {
			LOG.warn(Markers.ERR, "No \"data.src.fpath\" value was set");
		} catch(final IOException e) {
			LOG.warn(Markers.ERR, "Invalid items input file path: {}", itemInput);
		} catch(final SecurityException | NoSuchMethodException e) {
			LOG.warn(Markers.ERR, "Unexpected exception", e);
		}
		return this;
	}
	//
	@Override @SuppressWarnings("unchecked")
	public DataLoadBuilderClient<T, W, U> setInput(final Input<T> itemInput)
	throws RemoteException {
		super.setInput(itemInput);
		//
		if(itemInput instanceof FileDataItemInput) {
			// calculate approx average data item size
			final FileDataItemInput<T> fileInput = (FileDataItemInput<T>) itemInput;
			final long approxDataItemsSize = fileInput.getAvgDataSize(
				appConfig.getItemSrcBatchSize()
			);
			ioConfig.setBuffSize(
				approxDataItemsSize < Constants.BUFF_SIZE_LO ?
					Constants.BUFF_SIZE_LO :
					approxDataItemsSize > Constants.BUFF_SIZE_HI ?
						Constants.BUFF_SIZE_HI : (int) approxDataItemsSize
			);
		}
		return this;
	}
	//
	@Override
	public DataLoadBuilderClient<T, W, U> setDataSize(final SizeInBytes dataSize)
	throws IllegalArgumentException, RemoteException {
		this.sizeConfig = dataSize;
		if(loadSvcMap != null) {
			for(final String svcAddr : loadSvcMap.keySet()) {
				loadSvcMap.get(svcAddr).setDataSize(dataSize);
			}
		}
		return this;
	}
	//
	@Override
	public DataLoadBuilderClient<T, W, U> setDataRanges(final DataRangesConfig rangesConfig)
	throws IllegalArgumentException, RemoteException {
		this.rangesConfig = rangesConfig;
		if(loadSvcMap != null) {
			for(final String svcAddr : loadSvcMap.keySet()) {
				loadSvcMap.get(svcAddr).setDataRanges(rangesConfig);
			}
		}
		return this;
	}
	//
	@Override
	protected Input<T> getNewItemInput(final IoConfig<T, ?> ioConfigCopy)
	throws NoSuchMethodException {
		final ItemNamingType namingType = appConfig.getItemNamingType();
		return ioConfigCopy.getNewDataItemsInput(
			namingType, ioConfigCopy.getItemClass(), sizeConfig
		);
	}
}
