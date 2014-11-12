package com.emc.mongoose.web.ui.websockets.interfaces;

import org.apache.logging.log4j.core.LogEvent;

import java.util.EventListener;

/**
 * Created by gusakk on 10/26/14.
 */
public interface WebSocketLogListener extends EventListener {

	public void sendMessage(final LogEvent message);

}
