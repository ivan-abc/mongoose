package com.emc.mongoose.load.step;

import com.emc.mongoose.metrics.MetricsSnapshot;

import com.github.akurilov.commons.concurrent.AsyncRunnable;

import com.github.akurilov.confuse.Config;
import com.github.akurilov.confuse.impl.BasicConfig;

import java.rmi.RemoteException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface LoadStep
extends AsyncRunnable {

	/**
	 Configure the step. The actual behavior depends on the particular step type
	 @param config a dictionary of the configuration values to override the inherited config
	 @return <b>new/copied</b> step instance with the applied config values
	 */
	LoadStep config(final Map<String, Object> config)
	throws RemoteException;

	/**
	 @return the step id
	 */
	String id()
	throws RemoteException;

	String getTypeName()
	throws RemoteException;

	List<MetricsSnapshot> metricsSnapshots()
	throws RemoteException;

	static Config initConfigSlice(final Config config) {
		final Config configSlice = new BasicConfig(config);
		// disable the distributed mode on the slave nodes
		configSlice.val("load-step-node-addrs", Collections.EMPTY_LIST);
		return configSlice;
	}
}
