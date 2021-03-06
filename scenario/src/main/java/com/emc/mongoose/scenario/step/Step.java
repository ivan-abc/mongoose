package com.emc.mongoose.scenario.step;

import java.io.Closeable;

/**
 A runnable step configuration container. The collected configuration is applied upon invocation.
 */
public interface Step
extends Closeable, Runnable {
}
