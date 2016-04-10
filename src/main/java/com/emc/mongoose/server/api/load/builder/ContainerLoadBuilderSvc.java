package com.emc.mongoose.server.api.load.builder;
//
import com.emc.mongoose.core.api.v1.item.container.Container;
import com.emc.mongoose.core.api.v1.item.data.DataItem;
import com.emc.mongoose.core.api.v1.load.builder.ContainerLoadBuilder;
import com.emc.mongoose.server.api.load.executor.ContainerLoadSvc;
/**
 Created by kurila on 21.10.15.
 */
public interface ContainerLoadBuilderSvc<
	T extends DataItem, C extends Container<T>, U extends ContainerLoadSvc<T, C>
> extends ContainerLoadBuilder<T, C, U>, LoadBuilderSvc<C, U> {
}
