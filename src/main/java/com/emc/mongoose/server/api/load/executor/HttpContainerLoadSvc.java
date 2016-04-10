package com.emc.mongoose.server.api.load.executor;
//
import com.emc.mongoose.core.api.v1.item.container.Container;
import com.emc.mongoose.core.api.v1.item.data.HttpDataItem;
import com.emc.mongoose.core.api.v1.load.executor.HttpContainerLoadExecutor;
/**
 Created by kurila on 21.10.15.
 */
public interface HttpContainerLoadSvc<T extends HttpDataItem, C extends Container<T>>
extends ContainerLoadSvc<T, C>, HttpContainerLoadExecutor<T, C> {
}
