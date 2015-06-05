package com.emc.mongoose.common.io;
//
import com.emc.mongoose.common.conf.RunTimeConfig;
import com.emc.mongoose.common.conf.SizeUtil;
import com.emc.mongoose.common.logging.LogUtil;
//
import org.apache.http.nio.ContentDecoder;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
/**
 Created by kurila on 17.03.15.
 */
public final class IOUtils {
	//
	public final static int
		BUFF_SIZE_LO = (int) RunTimeConfig.getContext().getDataBufferSize(),
		BUFF_SIZE_HI = (int) RunTimeConfig.getContext().getDataRingSize();
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	public static long consumeQuietly(final InputStream contentStream, final int buffSize) {
		int n;
		long sum = 0;
		final byte buff[] = new byte[buffSize];
		try {
			while(true) {
				n = contentStream.read(buff);
				if(n < 0) {
					break;
				} else {
					sum += n;
				}
			}
		} catch(final IOException e) {
			LogUtil.exception(LOG, Level.DEBUG, e, "Content reading failure");
		}
		return sum;
	}
	//
	private final static ThreadLocal<Map<Integer, ByteBuffer>>
		THRLOC_BUFF_SIZE_MAP = new ThreadLocal<>();
	//
	public static long consumeQuietly(final ContentDecoder in, final long expectedSize) {
		long doneByteCount = 0;
		final int buffSize = (int) Math.max(BUFF_SIZE_LO, Math.min(expectedSize, BUFF_SIZE_HI));
		int lastByteCount;
		//
		Map<Integer, ByteBuffer> buffSizeMap = THRLOC_BUFF_SIZE_MAP.get();
		if(buffSizeMap == null) {
			buffSizeMap = new HashMap<>();
			THRLOC_BUFF_SIZE_MAP.set(buffSizeMap);
		}
		//
		ByteBuffer buff = buffSizeMap.get(buffSize);
		if(buff == null) {
			buff = ByteBuffer.allocateDirect(buffSize);
			buffSizeMap.put(buffSize, buff);
		}
		//
		try {
			while(in.isCompleted()) {
				buff.clear();
				lastByteCount = in.read(buff);
				if(lastByteCount < 0) {
					if(doneByteCount < expectedSize) {
						LOG.warn(
							LogUtil.MSG, "Expected size {} but got: {}",
							SizeUtil.formatSize(expectedSize), SizeUtil.formatSize(doneByteCount)
						);
					}
					break;
				} else if(lastByteCount > 0) {
					doneByteCount += lastByteCount;
				}
			}
		} catch(final IOException e) {
			LogUtil.exception(LOG, Level.DEBUG, e, "Content reading failure");
		}
		//
		if(doneByteCount > expectedSize) {
			LOG.warn(
				LogUtil.MSG, "Expected size {} but got: {}",
				SizeUtil.formatSize(expectedSize), SizeUtil.formatSize(doneByteCount)
			);
		}
		//
		return doneByteCount;
	}
	//
	public static long consumeQuietly(final ContentDecoder in) {
		long doneByteCount = 0;
		//
		Map<Integer, ByteBuffer> buffSizeMap = THRLOC_BUFF_SIZE_MAP.get();
		if(buffSizeMap == null) {
			buffSizeMap = new HashMap<>();
			THRLOC_BUFF_SIZE_MAP.set(buffSizeMap);
		}
		//
		int lastByteCount, nextByteCount = BUFF_SIZE_LO;
		ByteBuffer buff;
		//
		try {
			while(!in.isCompleted()) {
				//
				buff = buffSizeMap.get(nextByteCount);
				if(buff == null) {
					buff = ByteBuffer.allocateDirect(nextByteCount);
					buffSizeMap.put(nextByteCount, buff);
				} else {
					buff.clear();
				}
				//
				lastByteCount = in.read(buff);
				// try to adapt the direct buffer size
				if(lastByteCount > 0) {
					doneByteCount += lastByteCount;
					if(lastByteCount >= buff.capacity()) { // increase buffer size
						if(nextByteCount == BUFF_SIZE_LO) {
							nextByteCount *= 3;
						} else if(nextByteCount < BUFF_SIZE_HI) {
							nextByteCount *= 4;
						} // else keep this buffer size
					} else if(lastByteCount < buff.capacity() / 2) { // decrease buffer size
						if(nextByteCount / 3 > BUFF_SIZE_LO) {
							nextByteCount /= 4;
						} else if(nextByteCount > BUFF_SIZE_LO) {
							nextByteCount /= 3;
						} // else keep this buffer size
					} // else keep this buffer size
				} else if(lastByteCount < 0) {
					break;
				} else { // decrease buffer size
					if(nextByteCount / 3 > BUFF_SIZE_LO) {
						nextByteCount /= 4;
					} else if(nextByteCount > BUFF_SIZE_LO) {
						nextByteCount /= 3;
					} // else keep this buffer size
				}
				//
				LOG.info(
					LogUtil.MSG, "Byte count: done {}, last {}, next {}",
					doneByteCount, lastByteCount, nextByteCount
				);
			}
		} catch(final IOException e) {
			LogUtil.exception(LOG, Level.DEBUG, e, "Content reading failure");
		}
		//
		return doneByteCount;
	}
	//
}
