package simpleRpc.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.apache.commons.collections.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import simpleRpc.protocol.*;

import java.util.HashMap;
import java.util.Map;

/**
 * @author yunsheng
 */
public class RpcServer implements InitializingBean, ApplicationContextAware {
    private static final Logger LOGGER = LoggerFactory.getLogger(RpcServer.class);

    private String serverAddress;
    private RegistryService registryAddress;

    public RpcServer(String serverAddress, RegistryService registryAddress) {
        this.serverAddress = serverAddress;
        this.registryAddress = registryAddress;
    }

    private Map<String, Object> exportServices = new HashMap<String, Object>();

    // 初始化bean之前执行
    // 开启NIO socketserver，准备接受请求
    // 向zookeeper进行服务注册
    public void afterPropertiesSet() throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        protected void initChannel(SocketChannel socketChannel) throws Exception {

                            socketChannel.pipeline()
                                    .addLast(new RpcDecoder(RpcRequest.class)) // 对请求就行解码
                                    .addLast(new RpcEncoder(RpcResponse.class)) // 对响应进行编码
                                    .addLast(new RpcChannelHandler(exportServices));          // 处理Rpc请求


                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            String[] split = serverAddress.split(":");
            String host = split[0];
            int port = Integer.parseInt(split[1]);

            ChannelFuture channelFuture = bootstrap.bind(host, port).sync();
            LOGGER.info("server started............");

            // 注册服务，data就是服务器的地址
            if (null != registryAddress) {
                registryAddress.register(serverAddress);
            }

            channelFuture.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }

    /**
     * 服务启动时，扫描得到所有注解暴露服务的类
     * setApplicationContext 是在afterPropertiesSet之前执行的
     *
     * @param applicationContext
     * @throws BeansException
     */
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(RpcService.class);
        if (MapUtils.isNotEmpty(beans)) {
            for (Object obj : beans.values()) {
                String interfaceName = obj.getClass().getAnnotation(RpcService.class).value().getName();
                exportServices.put(interfaceName, obj);
                LOGGER.info("put {} in exportServices", interfaceName);
            }
        }
    }
}