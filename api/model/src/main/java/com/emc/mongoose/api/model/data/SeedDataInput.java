package com.emc.mongoose.api.model.data;

import com.emc.mongoose.api.common.env.DirectMemUtil;

import static com.emc.mongoose.api.model.data.DataInput.generateData;

/**
 Created by kurila on 23.07.14.
 A uniform data input for producing uniform data items.
 Implemented as finite buffer of pseudo random bytes.
 */
public final class SeedDataInput
extends CachedDataInput {

	public SeedDataInput() {
		super();
	}

	public SeedDataInput(final long seed, final long size, final int cacheLimit) {
		super(DirectMemUtil.allocate((int) size), cacheLimit);
		generateData(inputBuff, seed);
	}

	public SeedDataInput(final SeedDataInput anotherContentSource) {
		super(anotherContentSource);
	}
}
