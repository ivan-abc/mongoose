package com.emc.mongoose.common.config;

import com.emc.mongoose.common.config.reader.ConfigReader;
import com.emc.mongoose.common.util.SizeInBytes;
import com.emc.mongoose.common.util.TimeUtil;
import org.junit.Test;

import java.util.Map;

import static com.emc.mongoose.common.config.ConfigMatcher.equalTo;
import static com.emc.mongoose.common.config.ConfigNullMatcher.nullValue;
import static com.emc.mongoose.common.config.ConfigNullMatcher.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 Created on 11.07.16.
 */
public class CommonDecoderTest {

	@SuppressWarnings("ConstantConditions")
	@Test
	public void shouldCreateConfig() throws Exception {
		final CommonConfig commonConfig = ConfigReader.loadConfig(new CommonDecoder());
		assertThat(commonConfig, notNullValue());
		assertThat(commonConfig.getName(), equalTo("mongoose", "name"));
		assertThat(commonConfig.getVersion(), equalTo("3.0.0-SNAPSHOT", "version"));
		final CommonConfig.IoConfig ioConfig = commonConfig.getIoConfig();
		assertThat(ioConfig, notNullValue());
		final CommonConfig.IoConfig.BufferConfig bufferConfig = ioConfig.getBufferConfig();
		assertThat(bufferConfig, notNullValue());
		assertThat(bufferConfig.getSize(), equalTo(new SizeInBytes("4KB-1MB"), "io.buffer.size"));
		final CommonConfig.SocketConfig socketConfig = commonConfig.getSocketConfig();
		assertThat(socketConfig, notNullValue());
		assertThat(socketConfig.getTimeoutInMilliseconds(), equalTo(1_000_000, "socket.timeoutMilliSec"));
		assertThat(socketConfig.getReusableAddress(), equalTo(true, "socket.reuseAddr"));
		assertThat(socketConfig.getKeepAlive(), equalTo(true, "socket.keepAlive"));
		assertThat(socketConfig.getTcpNoDelay(), equalTo(true, "socket.tcpNoDelay"));
		assertThat(socketConfig.getLinger(), equalTo(0, "socket.linger"));
		assertThat(socketConfig.getBindBackLogSize(), equalTo(0, "socket.bindBacklogSize"));
		assertThat(socketConfig.getInterestOpQueued(), equalTo(false, "socket.interestOpQueued"));
		assertThat(socketConfig.getSelectInterval(), equalTo(100, "socket.selectInterval"));
		final CommonConfig.ItemConfig itemConfig = commonConfig.getItemConfig();
		assertThat(itemConfig, notNullValue());
		assertThat(itemConfig.getType(), equalTo("data", "item.type"));
		final CommonConfig.ItemConfig.DataConfig dataConfig = itemConfig.getDataConfig();
		assertThat(dataConfig, notNullValue());
		final CommonConfig.ItemConfig.DataConfig.ContentConfig contentConfig =
			dataConfig.getContentConfig();
		assertThat(contentConfig, notNullValue());
		assertThat(contentConfig.getFile(), nullValue("item.data.content.file"));
		assertThat(contentConfig.getSeed(), equalTo("7a42d9c483244167", "item.data.content.seed"));
		assertThat(contentConfig.getRingSize(), equalTo(new SizeInBytes("4MB"), "item.data.content.ringSize"));
		assertThat(dataConfig.getRanges().getRandomCount(), equalTo(0, "item.data.ranges"));
		assertThat(dataConfig.getSize(), equalTo(new SizeInBytes("1MB"), "item.data.size"));
		assertThat(dataConfig.getVerify(), equalTo(true, "item.data.verify"));
		final CommonConfig.ItemConfig.InputConfig inputConfig = itemConfig.getInputConfig();
		assertThat(inputConfig, notNullValue());
		assertThat(inputConfig.getContainer(), nullValue("item.input.container"));
		assertThat(inputConfig.getFile(), nullValue("item.input.file"));
		final CommonConfig.ItemConfig.OutputConfig outputConfig= itemConfig.getOutputConfig();
		assertThat(outputConfig, notNullValue());
		assertThat(outputConfig.getContainer(), nullValue("item.output.container"));
		assertThat(outputConfig.getFile(), nullValue("item.output.file"));
		final CommonConfig.ItemConfig.NamingConfig namingConfig = itemConfig.getNamingConfig();
		assertThat(namingConfig, notNullValue());
		assertThat(namingConfig.getType(), equalTo("random", "item.naming.type"));
		assertThat(namingConfig.getPrefix(), nullValue("item.naming.prefix"));
		assertThat(namingConfig.getRadix(), equalTo(36, "item.naming.radix"));
		assertThat(namingConfig.getOffset(), equalTo(0L, "item.naming.offset"));
		assertThat(namingConfig.getLength(), equalTo(13, "item.naming.length"));
		final CommonConfig.LoadConfig loadConfig = commonConfig.getLoadConfig();
		assertThat(loadConfig, notNullValue());
		assertThat(loadConfig.getCircular(), equalTo(false, "load.circular"));
		assertThat(loadConfig.getType(), equalTo("create", "load.type"));
		assertThat(loadConfig.getConcurrency(), equalTo(1, "load.concurrency"));
		final CommonConfig.LoadConfig.LimitConfig limitConfig = loadConfig.getLimitConfig();
		assertThat(limitConfig, notNullValue());
		assertThat(limitConfig.getCount(), equalTo(0L, "load.limit.count"));
		assertThat(limitConfig.getRate(), equalTo(0.0, "load.limit.rate"));
		assertThat(limitConfig.getSize(), equalTo(0, "load.limit.size"));
		final String timeTestValue = "0s";
		assertThat(limitConfig.getTime(), equalTo(TimeUtil.getTimeUnit(timeTestValue).toSeconds(TimeUtil.getTimeValue(timeTestValue)), "load.limit.time"));
		final CommonConfig.LoadConfig.MetricsConfig metricsConfig = loadConfig.getMetricsConfig();
		assertThat(metricsConfig, notNullValue());
		assertThat(metricsConfig.getIntermediate(), equalTo(false, "load.metrics.intermediate"));
		final String periodTestValue = "10s";
		assertThat(metricsConfig.getPeriod(), equalTo((int) TimeUtil.getTimeUnit(periodTestValue).toSeconds(TimeUtil.getTimeValue(periodTestValue)), "load.metrics.period"));
		assertThat(metricsConfig.getPrecondition(), equalTo(false, "load.metrics.precondition"));
		final CommonConfig.RunConfig runConfig = commonConfig.getRunConfig();
		assertThat(runConfig, notNullValue());
		assertThat(runConfig.getId(), nullValue("run.id"));
		assertThat(runConfig.getFile(), nullValue("run.file"));
		final CommonConfig.StorageConfig storageConfig = commonConfig.getStorageConfig();
		assertThat(storageConfig, notNullValue());
		assertThat(storageConfig.getAddresses().get(0), equalTo("127.0.0.1", "storage.address"));
		final CommonConfig.StorageConfig.AuthConfig authConfig = storageConfig.getAuthConfig();
		assertThat(authConfig, notNullValue());
		assertThat(authConfig.getId(), nullValue("storage.auth.id"));
		assertThat(authConfig.getSecret(), nullValue("storage.auth.secret"));
		assertThat(authConfig.getToken(), nullValue("storage.auth.token"));
		final CommonConfig.StorageConfig.HttpConfig httpConfig = storageConfig.getHttpConfig();
		assertThat(httpConfig, notNullValue());
		assertThat(httpConfig.getApi(), equalTo("S3", "storage.http.api"));
		assertThat(httpConfig.getFsAccess(), equalTo(false, "storage.http.fsAccess"));
		final Map<String, String> headers = httpConfig.getHeaders();
		assertThat(headers, notNullValue());
		assertThat(headers.containsKey(CommonConfig.StorageConfig.HttpConfig.KEY_HEADER_CONNECTION),
			equalTo(true, "storage.http.headers[Connection]"));
		assertThat(headers.get(CommonConfig.StorageConfig.HttpConfig.KEY_HEADER_CONNECTION),
			equalTo("keep-alive", "storage.http.headers[Connection]"));
		assertThat(headers.containsKey(CommonConfig.StorageConfig.HttpConfig.KEY_HEADER_USER_AGENT),
			equalTo(true, "storage.http.headers[User-Agent]"));
		assertThat(headers.get(CommonConfig.StorageConfig.HttpConfig.KEY_HEADER_USER_AGENT),
			equalTo("mongoose/3.0.0-SNAPSHOT", "storage.http.headers[User-Agent]"));
		assertThat(httpConfig.getNamespace(), nullValue("storage.http.namespace"));
		assertThat(httpConfig.getVersioning(), equalTo(false, "storage.http.versioning"));
		assertThat(storageConfig.getPort(), equalTo(9020, "storage.port"));
		assertThat(storageConfig.isSsl(), equalTo(false, "storage.ssl"));
		assertThat(storageConfig.getType(), equalTo("http", "storage.type"));
		final CommonConfig.StorageConfig.MockConfig mockConfig = storageConfig.getMockConfig();
		assertThat(mockConfig, notNullValue());
		assertThat(mockConfig.getHeadCount(), equalTo(1, "storage.mock.headCount"));
		assertThat(mockConfig.getCapacity(), equalTo(1_000_000, "storage.mock.capacity"));
		final CommonConfig.StorageConfig.MockConfig.ContainerConfig containerConfig =
			mockConfig.getContainerConfig();
		assertThat(containerConfig, notNullValue());
		assertThat(containerConfig.getCapacity(), equalTo(1_000_000, "storage.mock.container.capacity"));
		assertThat(containerConfig.getCountLimit(), equalTo(1_000_000, "storage.mock.container.countLimit"));
	}

}
