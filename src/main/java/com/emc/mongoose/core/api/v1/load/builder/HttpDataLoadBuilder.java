package com.emc.mongoose.core.api.v1.load.builder;
//
import com.emc.mongoose.core.api.v1.item.data.HttpDataItem;
import com.emc.mongoose.core.api.v1.load.executor.LoadExecutor;
/**
 Created by kurila on 01.10.14.
 */
public interface HttpDataLoadBuilder<T extends HttpDataItem, U extends LoadExecutor<T>>
extends DataLoadBuilder<T, U> {
}
