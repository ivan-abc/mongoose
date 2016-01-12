package com.emc.mongoose.common.log.appenders;
//
import com.emc.mongoose.common.conf.RunTimeConfig;
//
//
import com.emc.mongoose.common.log.appenders.processors.VSimplifierLogAdapter;
import org.apache.commons.collections4.queue.CircularFifoQueue;
//
import org.apache.logging.log4j.ThreadContext;
//
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.SerializedLayout;
//
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
/**
 Created by kurila on 23.10.14.
 */
@Plugin(name="WebUI", category="Core", elementType="appender", printObject=true)
public final class WebUIAppender
extends AbstractAppender {
	private final static int MAX_ELEMENTS_IN_THE_LIST = 10000;
	//
	private final static ConcurrentHashMap<String, CircularFifoQueue<LogEvent>>
			LOG_EVENTS_MAP = new ConcurrentHashMap<>();
	private final static ConcurrentHashMap<String, CircularFifoQueue<LogEvent>>
			OBJ_LOG_EVENTS_MAP = new ConcurrentHashMap<>();
	private final static List<WebSocketLogListener>
			LISTENERS = Collections.synchronizedList(new LinkedList<WebSocketLogListener>());
	//
	private final static Layout<? extends Serializable>
			DEFAULT_LAYOUT = SerializedLayout.createLayout();
	//
	private final String KEY_RUN_ID = RunTimeConfig.KEY_RUN_ID;
	//
	private static boolean ENABLED_FLAG;
	//
	private WebUIAppender(
		final String name, final Filter filter, final Layout<? extends Serializable> layout,
		final boolean ignoreExceptions
	) {
		super(name, filter, layout, ignoreExceptions);
	}
	//
	@PluginFactory
	public static WebUIAppender createAppender(
		final @PluginAttribute("name") String name,
		final @PluginAttribute("ignoreExceptions") boolean ignoreExceptions,
		final @PluginAttribute("enabled") Boolean enabled,
		final @PluginElement("Filters") Filter filter
	) {
		if(name == null) {
			LOGGER.error("No name provided for CustomAppender");
			return null;
		}
		ENABLED_FLAG = enabled;
		return new WebUIAppender(name, filter, DEFAULT_LAYOUT, ignoreExceptions);
	}
	//
	public static void register(final WebSocketLogListener listener) {
		if(ENABLED_FLAG) {
			sendPreviousLogs(listener);
			LISTENERS.add(listener);
		}
	}
	//
	public static void unregister(final WebSocketLogListener listener) {
		if(ENABLED_FLAG) {
			LISTENERS.remove(listener);
		}
	}
	//
	public synchronized static void sendPreviousLogs(final WebSocketLogListener listener) {
		final List<LogEvent> previousLogs = new ArrayList<>();
		for (final CircularFifoQueue<LogEvent> queue : LOG_EVENTS_MAP.values()) {
			for (final LogEvent logEvent : queue) {
				previousLogs.add(logEvent);
			}
		}
		listener.sendMessage(previousLogs);
	}
	//
	@Override
	public synchronized final void append(final LogEvent event) {
		if(ENABLED_FLAG) {
			final String currRunId;
			final Map<String, String> evtCtxMap = event.getContextMap();
			if(evtCtxMap.containsKey(KEY_RUN_ID)) {
				currRunId = evtCtxMap.get(KEY_RUN_ID);
			} else {
				currRunId = ThreadContext.get(KEY_RUN_ID);
			}
			//
			if(currRunId != null) {
				if(!LOG_EVENTS_MAP.containsKey(currRunId)) {
					LOG_EVENTS_MAP.put(
						currRunId, new CircularFifoQueue<LogEvent>(MAX_ELEMENTS_IN_THE_LIST)
					);
				}
				if(!OBJ_LOG_EVENTS_MAP.containsKey(currRunId)) {
					OBJ_LOG_EVENTS_MAP.put(
							currRunId, new CircularFifoQueue<LogEvent>(MAX_ELEMENTS_IN_THE_LIST)
					);
				}
				LOG_EVENTS_MAP.get(currRunId).add(event);
				if (event.getMessage().toString().contains("ObjectMessage")) {
					OBJ_LOG_EVENTS_MAP.get(currRunId).add(event);
					System.out.println("Event added");
				}
				if (OBJ_LOG_EVENTS_MAP.get(currRunId).size() == 4) {
					LogEvent[] logEventsArr = new LogEvent[4];
					OBJ_LOG_EVENTS_MAP.get(currRunId).toArray(logEventsArr);
					new VSimplifierLogAdapter(logEventsArr);
				}
				for (final WebSocketLogListener listener : LISTENERS) {
					listener.sendMessage(event);
				}
			} // else silently skip
		}
	}
	//
	public static void removeRunId(final String runId) {
		if (ENABLED_FLAG) {
			LOG_EVENTS_MAP.remove(runId);
		}
	}
}
