package com.emc.mongoose.tests.system.suite;

import com.emc.mongoose.tests.system.CircularReadSingleItemTest;
import com.emc.mongoose.tests.system.CreateLimitBySizeTest;
import com.emc.mongoose.tests.system.HttpStorageMetricsThresholdTest;
import com.emc.mongoose.tests.system.ReadVerificationAfterCircularUpdateTest;
import com.emc.mongoose.tests.system.TlsAndNodeBalancingTest;

import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 Created by kurila on 15.06.17.
 */

@RunWith(Suite.class)

@Suite.SuiteClasses({
	// s3, 1, 1/10/100, 0/10KB/1MB
	CircularReadSingleItemTest.class,
	// s3, 1, 10, 10KB/1MB
	CreateLimitBySizeTest.class,
	// atmos, 2, 1000, 100MB
	HttpStorageMetricsThresholdTest.class,
	// s3, 1, 100, 10KB
	ReadVerificationAfterCircularUpdateTest.class,
	//
	TlsAndNodeBalancingTest.class,
})

@Ignore

public class QuarantineSuite {
}
