package com.emc.mongoose.ui.log;

import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.util.StringBuilderFormattable;

import static java.lang.ThreadLocal.withInitial;

/**
 Created by kurila on 26.10.16.
 */
public abstract class LogMessageBase
implements Message, StringBuilderFormattable {
	
	private static final ThreadLocal<StringBuilder> THRLOC_STRB = withInitial(StringBuilder::new);
	
	@Override
	public final String getFormattedMessage() {
		final StringBuilder strb = THRLOC_STRB.get();
		strb.setLength(0);
		formatTo(strb);
		return strb.toString();
	}
	
	@Override
	public final String getFormat() {
		return "";
	}
	
	@Override
	public final Object[] getParameters() {
		return null;
	}
	
	@Override
	public final Throwable getThrowable() {
		return null;
	}
	
	protected static String formatFixedWidth(final double value, final int count) {
		final String valueStr = Double.toString(value);
		if(value < Math.pow(10, count) && valueStr.length() > count) {
			return valueStr.substring(0, count);
		} else if(value < Math.pow(10, - count + 2)) {
			return "0";
		} else {
			return valueStr;
		}
	}
}
