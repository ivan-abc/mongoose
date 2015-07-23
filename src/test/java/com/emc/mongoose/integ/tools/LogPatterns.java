package com.emc.mongoose.integ.tools;
import java.util.regex.Pattern;
/**
 Created by kurila on 16.07.15.
 */
public interface LogPatterns {
	Pattern
		DATE_TIME_ISO8601 = Pattern.compile(
			"(?<dateTime>[\\d]{4}\\-[\\d]{2}-[\\d]{2}T[\\d]{2}:[\\d]{2}:[\\d]{2},[\\d]{3})"
		),
		LOG_LEVEL = Pattern.compile("(?<levelLog>[FEWIDT])"),
		CLASS_NAME = Pattern.compile("[A-Za-z]+[\\w]*"),
		THREAD_NAME = Pattern.compile("(?<nameThread>\\w[\\w\\s#\\.\\-]+\\w)"),
		//
		NUM_LOAD = Pattern.compile("(?<numLoad>[\\d]+)"),
		TYPE_API = Pattern.compile("(?<typeApi>[A-Za-z0-9]+)"),
		TYPE_LOAD = Pattern.compile("(?<typeLoad>[CreatRdDlUpAn]{4,6})"),
		//
		DATA_ID = Pattern.compile("(?<dataID>[\\w\\d]+)"),
		DATA_OFFSET = Pattern.compile("(?<dataOffset>[\\w\\d]+)"),
		API_NAME = Pattern.compile("(?<apiName>[S3ATMOWIF]{2,5})"),
		ITEM_COUNTS_SUCC = Pattern.compile("(?<countSucc>[\\d]+)"),
		ITEM_COUNTS_FAIL = Pattern.compile("(?<countFail>[\\d]+)"),
		//
		CONSOLE_LOAD_NAME_SUFFIX = Pattern.compile(
			"(?<countLimit>[\\d]*)\\-(?<countConn>[\\d]*)x(?<countNodes>[\\d]*)"
		),
		CONSOLE_LOAD_NAME_SUFFIX_CLIENT = Pattern.compile(
			CONSOLE_LOAD_NAME_SUFFIX.pattern() + "x(?<countServers>[\\d]*)"
		),
		CONSOLE_FULL_LOAD_NAME = Pattern.compile(
			NUM_LOAD.pattern() + "\\-" + TYPE_API.pattern() + "\\-" + TYPE_LOAD.pattern() +
			CONSOLE_LOAD_NAME_SUFFIX.pattern()
		),
		CONSOLE_FULL_LOAD_NAME_CLIENT = Pattern.compile(
			NUM_LOAD.pattern() + "\\-" + TYPE_API.pattern() + "\\-" + TYPE_LOAD.pattern() +
			CONSOLE_LOAD_NAME_SUFFIX_CLIENT.pattern()
		),
		//
		CONSOLE_ITEM_COUNTS_AVG = Pattern.compile(
			"count=\\((?<countSucc>\\d+)/(\\-?\\d+)/\\\u001B*\\[*\\d*m*(?<countFail>\\d+)\\\u001B*\\[*\\d*m*\\)"
		),
		CONSOLE_ITEM_COUNTS_SUM = Pattern.compile(
			"count=\\((?<countSucc>\\d+)/\\\u001B*\\[*\\d*m*(?<countFail>\\d+)\\\u001B*\\[*\\d*m*\\)"
		),
		CONSOLE_LATENCY = Pattern.compile(
			"latency\\[us\\]=\\((\\d+)/(\\d+)/(\\d+)/(\\d+)\\)"
		),
		LATENCY = Pattern.compile(
			"(?<latency>(\\d+),(\\d+),(\\d+),(\\d+))"
		),
		CONSOLE_TP = Pattern.compile(
			"TP\\[/s\\]=\\(([\\.\\d]+)/([\\.\\d]+)/([\\.\\d]+)/([\\.\\d]+)\\)"
		),
		TP = Pattern.compile(
			"(?<TP>([\\.\\d]+),([\\.\\d]+),([\\.\\d]+),([\\.\\d]+))"
		),
		CONSOLE_BW = Pattern.compile(
			"BW\\[MB/s\\]=\\(([\\.\\d]+)/([\\.\\d]+)/([\\.\\d]+)/([\\.\\d]+)\\)"
		),
		BW = Pattern.compile(
			"(?<BW>([\\.\\d]+),([\\.\\d]+),([\\.\\d]+),([\\.\\d]+))"
		),
		//
		CONSOLE_METRICS_AVG = Pattern.compile(
			DATE_TIME_ISO8601.pattern() + "[\\s]+" +
			LOG_LEVEL.pattern() + "[\\s]+" + CLASS_NAME.pattern() + "[\\s]+" +
			CONSOLE_FULL_LOAD_NAME + "[\\s]+" +
			CONSOLE_ITEM_COUNTS_AVG.pattern() + ";[\\s]+" +
			CONSOLE_LATENCY + ";[\\s]+" + CONSOLE_TP + ";[\\s]+" + CONSOLE_BW + "$"
		),
		//
		CONSOLE_METRICS_AVG_CLIENT = Pattern.compile(
			DATE_TIME_ISO8601.pattern() + "[\\s]+" +
			LOG_LEVEL.pattern() + "[\\s]+" + THREAD_NAME.pattern() + "[\\s]+" +
			CONSOLE_FULL_LOAD_NAME_CLIENT + "[\\w#\\-]*[\\s]+" +
			CONSOLE_ITEM_COUNTS_AVG.pattern() + ";[\\s]+" +
			CONSOLE_LATENCY + ";[\\s]+" + CONSOLE_TP + ";[\\s]+" + CONSOLE_BW
		),
		//
		CONSOLE_METRICS_SUM = Pattern.compile(
			DATE_TIME_ISO8601.pattern() + "[\\s]+" +
			LOG_LEVEL.pattern() + "[\\s]+" + THREAD_NAME.pattern() + "[\\s]+\"" +
			CONSOLE_FULL_LOAD_NAME.pattern() + "\"[\\s]+summary:[\\s]+" +
			CONSOLE_ITEM_COUNTS_SUM.pattern() + ";[\\s]+" +
			CONSOLE_LATENCY + ";[\\s]+" + CONSOLE_TP + ";[\\s]+" + CONSOLE_BW
		),
		//
		CONSOLE_METRICS_SUM_CLIENT = Pattern.compile(
			"\"" + CONSOLE_FULL_LOAD_NAME_CLIENT.pattern() + "\"[\\s]+summary:[\\s]+" +
			CONSOLE_ITEM_COUNTS_SUM.pattern() + ";[\\s]+" +
			CONSOLE_LATENCY + ";[\\s]+" + CONSOLE_TP + ";[\\s]+" + CONSOLE_BW
		),
		//
		DATA_ITEMS_FILE = Pattern.compile(
		"^" + DATA_ID.pattern() + "," + DATA_OFFSET.pattern() + "," + ",[\\d]+,[\\d]+/[\\p{XDigit}]+$"
		),
		PERF_SUM_FILE = Pattern.compile(
			"^\"" + DATE_TIME_ISO8601 +"\"," +
			"[\\d]+," + API_NAME.pattern() + "," + TYPE_LOAD.pattern() + ",[\\d]+," +
			"[\\d]+,[\\d]*+," + ITEM_COUNTS_SUCC.pattern() + "," + ITEM_COUNTS_FAIL.pattern()+ "," +
			LATENCY.pattern() + "," + TP.pattern() + "," + BW.pattern()+ "$"
		);
}
