package com.emc.mongoose.core.impl.v1.item.container;
//
import com.emc.mongoose.core.api.v1.item.container.Directory;
import com.emc.mongoose.core.api.v1.item.data.FileItem;
import com.emc.mongoose.core.api.v1.item.data.ContentSource;
/**
 Created by andrey on 22.11.15.
 */
public class BasicDirectory<T extends FileItem>
extends BasicContainer<T>
implements Directory<T> {
	//
	public BasicDirectory() {
		super();
	}
	//
	public BasicDirectory(final String name) {
		super(name);
	}
	//
	public BasicDirectory(final String name, final ContentSource contentSrc) {
		super(name, contentSrc);
	}
}
