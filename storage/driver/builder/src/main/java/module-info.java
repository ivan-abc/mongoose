module com.emc.mongoose.storage.driver.builder {

	requires com.emc.mongoose.api.common;
	requires com.emc.mongoose.api.model;
	requires com.emc.mongoose.storage.driver.base;
	requires com.emc.mongoose.ui;
	requires log4j.api;
	requires java.base;
	requires java.rmi;

	uses com.emc.mongoose.storage.driver.base.StorageDriverFactory;

	exports com.emc.mongoose.storage.driver.builder;
}