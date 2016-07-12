package com.emc.mongoose.common.config;

import com.emc.mongoose.common.config.reader.ConfigReader;
import org.junit.Test;

import javax.json.JsonObject;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 Created on 11.07.16.
 */
public class CommonDecoderTest {

	private static String parameterErrorMessage(final String content) {
		return "Wrong " + content + " parameter";
	}

	@Test
	public void shouldCreateConfig() throws Exception{
		final CommonDecoder commonDecoder = new CommonDecoder();
		final JsonObject defaults = ConfigReader.readJson("defaults.json");
		assertNotNull("The configuration file was read wrong", defaults);
		final CommonConfig commonConfig =
			commonDecoder.decode(defaults);
		final CommonConfig.NetworkConfig.SocketConfig socketConfig = commonConfig.getNetworkConfig().getSocketConfig();
		assertEquals(parameterErrorMessage("name"),
			commonConfig.getName(), "mongoose");
		assertEquals(parameterErrorMessage("getNetworkConfig.socketConfig.timeoutMilliSec"),
			socketConfig.getTimeoutInMilliseconds(), 1_000_000);
		assertEquals(parameterErrorMessage("getNetworkConfig.socketConfig.reuseAddr"),
			socketConfig.getReusableAddress(), true);
		assertEquals(parameterErrorMessage("getNetworkConfig.socketConfig.keepAlive"),
			socketConfig.getKeepAlive(), true);
		assertEquals(parameterErrorMessage("getNetworkConfig.socketConfig.tcpNoDelay"),
			socketConfig.getTcpNoDelay(), true);
		assertEquals(parameterErrorMessage("getNetworkConfig.socketConfig.linger"),
			socketConfig.getLinger(), 0);
		assertEquals(parameterErrorMessage("getNetworkConfig.socketConfig.bindBacklogSize"),
			socketConfig.getBindBackLogSize(), 0);
		assertEquals(parameterErrorMessage("getNetworkConfig.socketConfig.interestOpQueued"),
			socketConfig.getInterestOpQueued(), false);
		assertEquals(parameterErrorMessage("getNetworkConfig.socketConfig.selectInterval"),
			socketConfig.getSelectInterval(), 100);
		final CommonConfig.StorageConfig storage = commonConfig.getStorageConfig();
		assertEquals(parameterErrorMessage("storage.addrs"),
			storage.getAddresses().get(0), "127.0.0.1");
		final CommonConfig.StorageConfig.AuthConfig auth = storage.getAuthConfig();
		assertNull(parameterErrorMessage("storage.auth.id"), auth.getId());
		assertNull(parameterErrorMessage("storage.auth.secret"), auth.getSecret());
		assertNull(parameterErrorMessage("storage.auth.token"), auth.getToken());
		final CommonConfig.StorageConfig.HttpConfig http = storage.getHttpConfig();
		assertEquals(parameterErrorMessage("storage.http.api"), http.getApi(), "S3");
		assertEquals(parameterErrorMessage("storage.http.fsAccess"), http.getFsAccess(), false);
		final Map<String, String> headers = http.getHeaders();
		assertEquals(parameterErrorMessage("storage.http.headers[\"Connection\"]"), headers.get(
			CommonConfig.StorageConfig.HttpConfig.KEY_HEADER_CONNECTION), "keep-alive");
		assertEquals(parameterErrorMessage("storage.http.headers[\"User-Agent\"]"), headers.get(
			CommonConfig.StorageConfig.HttpConfig.KEY_HEADER_USER_AGENT), "mongoose/3.0.0");
		assertNull("storage.http.namespace", http.getNamespace());
		assertEquals("storage.http.versioning", http.getVersioning(), false);
		assertEquals("storage.port", storage.getPort(), 9020);
		assertEquals("storage.type", storage.getType(), "http");
		final CommonConfig.ItemConfig itemConfig = commonConfig.getItemConfig();
		assertEquals(parameterErrorMessage("getItemConfig.type"), itemConfig.getType(), "data");
		final CommonConfig.ItemConfig.DataConfig dataConfig = itemConfig.getDataConfig();
		final CommonConfig.ItemConfig.DataConfig.ContentConfig contentConfig = dataConfig.getContentConfig();
		assertNull(parameterErrorMessage("getItemConfig.data.content.file"), contentConfig.getFile());
		assertEquals(parameterErrorMessage("getItemConfig.data.content.seed"), contentConfig.getSeed(), "7a42d9c483244167");
		assertEquals(parameterErrorMessage("getItemConfig.data.content.ringSize"), contentConfig.getRingSize(), "4MB");
		assertEquals(parameterErrorMessage("getItemConfig.data.ranges"), dataConfig.getRanges(), 0);
		assertEquals(parameterErrorMessage("getItemConfig.data.size"), dataConfig.getSize(), "1MB");
		assertEquals(parameterErrorMessage("getItemConfig.data.verify"), dataConfig.getVerify(), true);
		final CommonConfig.ItemConfig.DestinationConfig destinationConfig = itemConfig.getDestinationConfig();
		assertNull(parameterErrorMessage("getItemConfig.dst.container"), destinationConfig.getContainer());
		assertNull(parameterErrorMessage("getItemConfig.dst.file"), destinationConfig.getFile());
		final CommonConfig.ItemConfig.SourceConfig sourceConfig = itemConfig.getSourceConfig();
		assertNull(parameterErrorMessage("getItemConfig.src.container"), sourceConfig.getContainer());
		assertNull(parameterErrorMessage("getItemConfig.src.file"), sourceConfig.getFile());
		assertEquals(parameterErrorMessage("getItemConfig.src.batchSize"), sourceConfig.getBatchSize(), 4096);
		final CommonConfig.ItemConfig.NamingConfig namingConfig = itemConfig.getNamingConfig();
		assertEquals(parameterErrorMessage("naming.type"), namingConfig.getType(), "random");
		assertNull(parameterErrorMessage("naming.prefix"), namingConfig.getPrefix());
		assertEquals(parameterErrorMessage("naming.radix"), namingConfig.getRadix(), 36);
		assertEquals(parameterErrorMessage("naming.offset"), namingConfig.getOffset(), 0);
		assertEquals(parameterErrorMessage("naming.length"), namingConfig.getLength(), 13);
	}

}