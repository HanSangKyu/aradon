package net.ion.nradon.netty;

import static org.jboss.netty.channel.Channels.pipeline;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLEngine;

import net.ion.framework.util.ListUtil;
import net.ion.nradon.HttpHandler;
import net.ion.nradon.Radon;
import net.ion.nradon.config.RadonConfiguration;
import net.ion.nradon.handler.event.ServerEvent.EventType;
import net.ion.radon.core.config.AradonConstant;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpContentCompressor;
import org.jboss.netty.handler.codec.http.HttpContentDecompressor;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.handler.stream.ChunkedWriteHandler;

public class NettyWebServer extends Radon {
	private final List<ExecutorService> executorServices = ListUtil.newList();
	private ServerBootstrap bootstrap;
	private Channel channel;

	protected long nextId = 1;
	
	private ConnectionTrackingHandler connectionTrackingHandler;
	private StaleConnectionTrackingHandler staleConnectionTrackingHandler;

	private RadonConfiguration config ;
	
	public NettyWebServer(RadonConfiguration config){
		if (config.executor() instanceof ExecutorService) {
			this.executorServices.add((ExecutorService) config.executor()) ;
		}
		this.config = config ;
	}
	
	
	public RadonConfiguration getConfig(){
		return this.config ;
	}

	public synchronized NettyWebServer start() {
		if (isRunning()) {
			throw new IllegalStateException("Server already started.");
		}

		getConfig().aradon().getServiceContext().putAttribute(AradonConstant.CONFIG_PORT, getConfig().getPort()) ;
		getConfig().aradon().getServiceContext().putAttribute(Radon.class.getCanonicalName(), this) ;
		
		// Configure the server.
		bootstrap = new ServerBootstrap();

		// Set up the event pipeline factory.
		bootstrap.setPipelineFactory(new ChannelPipelineFactory() {

			public ChannelPipeline getPipeline() throws Exception {
				long timestamp = timestamp();
				Object id = nextId();
				ChannelPipeline pipeline = pipeline();
				if (getConfig().getSslContext() != null) {
                    SSLEngine sslEngine = getConfig().getSslContext().createSSLEngine();
                    sslEngine.setUseClientMode(false);
                    pipeline.addLast("ssl", new SslHandler(sslEngine));
                }
				
				pipeline.addLast("staleconnectiontracker", staleConnectionTrackingHandler);
				pipeline.addLast("connectiontracker", connectionTrackingHandler);
				pipeline.addLast("flashpolicydecoder", new FlashPolicyFileDecoder(getConfig().getPort()));
				pipeline.addLast("decoder", new HttpRequestDecoder(getConfig().maxInitialLineLength(), getConfig().maxHeaderSize(), getConfig().maxChunkSize()));
				pipeline.addLast("aggregator", new HttpChunkAggregator(getConfig().maxContentLength()));
				pipeline.addLast("chunker", new ChunkedWriteHandler()) ;
				pipeline.addLast("decompressor", new HttpContentDecompressor());
				pipeline.addLast("encoder", new HttpResponseEncoder());
				pipeline.addLast("compressor", new HttpContentCompressor());
				pipeline.addLast("handler", new NettyHttpChannelHandler(getConfig().executor(), getConfig().handlers(), id, timestamp, getConfig().exceptionHandler(), getConfig().ioExceptionHandler()));
				return pipeline;
			}
		});

		staleConnectionTrackingHandler = new StaleConnectionTrackingHandler(getConfig().staleConnectionTimeout(), getConfig().executor());
		ScheduledExecutorService staleCheckExecutor = Executors.newSingleThreadScheduledExecutor();
		staleCheckExecutor.scheduleWithFixedDelay(new Runnable() {

			public void run() {
				staleConnectionTrackingHandler.closeStaleConnections();
			}
		}, getConfig().staleConnectionTimeout() / 2, getConfig().staleConnectionTimeout() / 2, TimeUnit.MILLISECONDS);
		executorServices.add(staleCheckExecutor);

		connectionTrackingHandler = new ConnectionTrackingHandler();
		ExecutorService bossExecutor = Executors.newSingleThreadExecutor();
		executorServices.add(bossExecutor);
		ExecutorService workerExecutor = Executors.newSingleThreadExecutor();
		executorServices.add(workerExecutor);
		bootstrap.setFactory(new NioServerSocketChannelFactory(bossExecutor, workerExecutor, 1));
		channel = bootstrap.bind(getConfig().socketAddress());
		
		fireEvent(EventType.START) ;
		return this;
	}
	
	public void fireEvent(EventType eventType){
		HttpHandler[] handlers = getConfig().handlers().toArray(new HttpHandler[0]);
		
		for (HttpHandler handler : handlers) {
			handler.onEvent(eventType, this) ;
		}
	}
	

	public boolean isRunning() {
		return channel != null && channel.isBound();
	}

	public synchronized NettyWebServer stop() throws IOException {
		fireEvent(EventType.STOP) ;
		
		if (channel != null) {
			channel.close();
		}
		if (connectionTrackingHandler != null) {
			connectionTrackingHandler.closeAllConnections();
			connectionTrackingHandler = null;
		}
		if (bootstrap != null) {
			bootstrap.releaseExternalResources();
		}
		for (ExecutorService executorService : executorServices) {
			executorService.shutdown();
		}

		bootstrap = null;

		return this;
	}

	public synchronized NettyWebServer join() throws InterruptedException {
		if (channel != null) {
			channel.getCloseFuture().await();
		}
		return this;
	}


	

	protected long timestamp() {
		return System.currentTimeMillis();
	}

	protected Object nextId() {
		return nextId++;
	}

}
