package com.emc.mongoose.core.api.v1.load.executor;
import com.emc.mongoose.core.api.v1.item.container.Directory;
import com.emc.mongoose.core.api.v1.item.data.FileItem;
/**
 Created by andrey on 22.11.15.
 */
public interface DirectoryLoadExecutor<T extends FileItem, C extends Directory<T>>
extends ContainerLoadExecutor<T, C> {
}
