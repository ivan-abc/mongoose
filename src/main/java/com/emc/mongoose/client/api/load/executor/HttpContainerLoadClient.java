package com.emc.mongoose.client.api.load.executor;
//
import com.emc.mongoose.core.api.v1.item.container.Container;
import com.emc.mongoose.core.api.v1.item.data.HttpDataItem;
//
import com.emc.mongoose.core.api.v1.load.executor.HttpContainerLoadExecutor;
import com.emc.mongoose.server.api.load.executor.HttpContainerLoadSvc;
/**
 Created by kurila on 21.10.15.
 */
public interface HttpContainerLoadClient<
	T extends HttpDataItem, C extends Container<T>, W extends HttpContainerLoadSvc<T, C>
> extends ContainerLoadClient<T, C, W>, HttpContainerLoadExecutor<T, C> {
}
