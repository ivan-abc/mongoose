package com.emc.mongoose.storage.mock.impl.http;

import com.emc.mongoose.storage.mock.api.MutableDataItemMock;
import com.emc.mongoose.storage.mock.impl.base.BasicMutableDataItemMock;
import com.emc.mongoose.storage.mock.impl.base.StorageMockBase;
import com.emc.mongoose.storage.mock.impl.http.request.AtmosRequestHandler;
import com.emc.mongoose.storage.mock.impl.http.request.RequestHandlerBase;
import com.emc.mongoose.storage.mock.impl.http.request.S3RequestHandler;
import com.emc.mongoose.storage.mock.impl.http.request.SwiftRequestHandler;
import com.emc.mongoose.ui.config.Config;
import com.emc.mongoose.ui.log.LogUtil;
import com.emc.mongoose.ui.log.Markers;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 Created on 11.07.16.
 */
public class Nagaina extends StorageMockBase<MutableDataItemMock>{


	private final static Logger LOG = LogManager.getLogger();

	private final int port;
	private final EventLoopGroup[] dispatchGroups;
	private final EventLoopGroup[] workGroups;
	private final Channel[] channels;
	private final RequestHandlerBase s3RequestHandler, swiftRequestHandler, atmosRequestHandler;

	@SuppressWarnings("ConstantConditions")
	public Nagaina(
		final Config.StorageConfig storageConfig,
		final Config.LoadConfig.MetricsConfig metricsConfig,
		final Config.ItemConfig itemConfig) {
		super(storageConfig.getMockConfig(),metricsConfig, itemConfig);
		port = storageConfig.getPort();
		final int headCount = storageConfig.getMockConfig().getHeadCount();
		dispatchGroups = new NioEventLoopGroup[headCount];
		workGroups = new NioEventLoopGroup[headCount];
		channels = new Channel[headCount];
		LOG.info(Markers.MSG, "Starting with {} head(s)", headCount);
		s3RequestHandler = new S3RequestHandler();
		swiftRequestHandler = new SwiftRequestHandler();
		atmosRequestHandler = new AtmosRequestHandler();
	}

	@Override
	protected void doStart()
	throws IllegalStateException {
		super.start();
		final int portsNumber = dispatchGroups.length;
		for (int i = 0; i < portsNumber; i++) {
			try {
				dispatchGroups[i] =
					new NioEventLoopGroup(0, new DefaultThreadFactory("dispatcher-" + i));
				workGroups[i] = new NioEventLoopGroup();
				final ServerBootstrap serverBootstrap = new ServerBootstrap();
				serverBootstrap.group(dispatchGroups[i], workGroups[i])
					.channel(NioServerSocketChannel.class)
					.childHandler(new ChannelInitializer<SocketChannel>() {
						@Override
						protected void initChannel(final SocketChannel socketChannel)
						throws Exception {
							final ChannelPipeline pipeline = socketChannel.pipeline();
							pipeline.addLast(new HttpServerCodec());
							pipeline.addLast(swiftRequestHandler);
							pipeline.addLast(atmosRequestHandler);
							pipeline.addLast(s3RequestHandler);
						}
					});
				channels[i] = serverBootstrap.bind(port + i).sync().channel();
			} catch(final Exception e) {
				LogUtil.exception(
					LOG, Level.ERROR, e, "Failed to start the head at port #{}", port + i
				);
				throw new IllegalStateException();
			}
		}
		if(portsNumber > 1) {
			LOG.info(Markers.MSG, "Listening the ports {} .. {}",
				port, port + portsNumber - 1);
		} else {
			LOG.info(Markers.MSG, "Listening the port {}", port);
		}
	}

	@Override
	public boolean await()
	throws InterruptedException {
		return await(Long.MAX_VALUE, TimeUnit.DAYS);
	}

	@Override
	public boolean await(long timeout, final TimeUnit timeUnit)
	throws InterruptedException {
		for(final Channel channel : channels) {
			try {
				channel.closeFuture().await(timeout, timeUnit);
			} catch(final InterruptedException e) {
				LOG.info(Markers.MSG, "Interrupting the Nagaina");
			}
		}
		return true;
	}

	@Override
	public void close()
	throws IOException {
		for (final Channel channel: channels) {
			channel.close();
		}
	}

	@Override
	protected MutableDataItemMock newDataObject(final String id, final long offset, final long size) {
		return new BasicMutableDataItemMock(id, offset, size, 0, contentSrc);
	}
}