package com.emc.mongoose.common.io;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 Created by kurila on 08.12.15.
 */
public final class UniformOptionSelector<S>
implements Input<S> {
	
	private static final ThreadLocal<List<Object>> BEST_CHOICES = new ThreadLocal<List<Object>>() {
		@Override
		protected final List<Object> initialValue() {
			return new ArrayList<>();
		}
	};
	private final AtomicInteger rrc = new AtomicInteger(0);
	private final S options[];
	private final Map<S, AtomicInteger> leaseMap;
	
	public UniformOptionSelector(final S options[]) {
		this.options = options;
		if(options == null) {
			this.leaseMap = null;
		} else {
			this.leaseMap = new HashMap<>();
			for(final S option : options) {
				leaseMap.put(option, new AtomicInteger(0));
			}
		}
	}
	
	@Override
	public final S get() {
		
		if(options == null) {
			return null;
		}
		if(options.length == 1) {
			return options[0];
		}
		
		final List<S> bestChoices = (List<S>) this.BEST_CHOICES.get();
		bestChoices.clear();
		final S bestChoice;
		
		int minLeaseCount = Integer.MAX_VALUE, nextLeaseCount;
		for(final S nextChoice : options) {
			nextLeaseCount = leaseMap.get(nextChoice).get();
			if(nextLeaseCount < minLeaseCount) {
				minLeaseCount = nextLeaseCount;
				bestChoices.clear();
				bestChoices.add(nextChoice);
			} else if(nextLeaseCount == minLeaseCount) {
				bestChoices.add(nextChoice);
			}
		}
		
		// select using round robin counter if there are more than 1 best options
		if(rrc.compareAndSet(Integer.MAX_VALUE, 0)) {
			bestChoice = bestChoices.get(0);
		} else {
			bestChoice = bestChoices.get(rrc.incrementAndGet() % bestChoices.size());
			rrc.incrementAndGet();
		}
		leaseMap.get(bestChoice).incrementAndGet();
		
		return bestChoice;
	}
	
	@Override
	public final int get(final List<S> buffer, final int limit) {
		
		if(options == null) {
			return 0;
		}
		if(options.length == 1) {
			final S choice = options[0];
			for(int i = 0; i < limit; i ++) {
				buffer.set(0, choice);
			}
		}
		
		final List<S> bestChoices = (List<S>) this.BEST_CHOICES.get();
		bestChoices.clear();
		int minLeaseCount = Integer.MAX_VALUE, nextLeaseCount;
		
		for(final S nextChoice : options) {
			nextLeaseCount = leaseMap.get(nextChoice).get();
			if(nextLeaseCount < minLeaseCount) {
				minLeaseCount = nextLeaseCount;
				bestChoices.clear();
				bestChoices.add(nextChoice);
			} else if(nextLeaseCount == minLeaseCount) {
				bestChoices.add(nextChoice);
			}
		}
		
		final int bestChoicesCount = bestChoices.size();
		S nextChoice;
		for(int i = 0; i < limit; i ++) {
			nextChoice = bestChoices.get(i % bestChoicesCount);
			leaseMap.get(nextChoice).incrementAndGet();
			buffer.add(nextChoice);
		}
		
		return limit;
	}
	
	@Override
	public final long skip(final long count) {
		rrc.addAndGet((int) (count % Integer.MAX_VALUE));
		return count;
	}
	
	@Override
	public final void reset() {
		rrc.set(0);
	}
	
	@Override
	public final void close() {
		for(int i = 0; i < options.length; i ++) {
			options[i] = null;
		}
		leaseMap.clear();
	}
}
