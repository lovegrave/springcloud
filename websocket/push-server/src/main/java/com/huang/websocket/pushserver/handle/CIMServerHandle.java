package com.huang.websocket.pushserver.handle;

import com.alibaba.fastjson.JSONObject;
import com.huang.websocket.common.constant.Constants;
import com.huang.websocket.common.exception.CIMException;
import com.huang.websocket.common.kit.HeartBeatHandler;
import com.huang.websocket.common.pojo.CIMUserInfo;
import com.huang.websocket.common.protocol.CIMRequestProto;
import com.huang.websocket.common.util.NettyAttrUtil;
import com.huang.websocket.pushserver.config.AppConfiguration;
import com.huang.websocket.pushserver.kit.ServerHeartBeatHandlerImpl;
import com.huang.websocket.pushserver.util.SessionSocketHolder;
import com.huang.websocket.pushserver.util.SpringBeanFactory;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import okhttp3.*;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Function:
 *
 * @author crossoverJie
 *         Date: 17/05/2018 18:52
 * @since JDK 1.8
 */
@ChannelHandler.Sharable
public class CIMServerHandle extends SimpleChannelInboundHandler<Object> {

    private static final java.util.logging.Logger logger = Logger.getLogger(WebSocketServerHandshaker.class.getName());
    private WebSocketServerHandshaker handshaker;

    private final MediaType mediaType = MediaType.parse("application/json");
    /**
     * channel 通道 action 活跃的 当客户端主动链接服务端的链接后，这个通道就是活跃的了。也就是客户端与服务端建立了通信通道并且可以传输数据
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // 添加

    }
    /**
     * channel 通道 Inactive 不活跃的 当客户端主动断开服务端的链接后，这个通道就是不活跃的。也就是说客户端与服务端关闭了通信通道并且不可以传输数据
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        /**
         * 移除
         */
        CIMUserInfo userInfo = SessionSocketHolder.getUserId((NioSocketChannel) ctx.channel());
        if (userInfo != null){
            userOffLine(userInfo, (NioSocketChannel) ctx.channel());
            ctx.channel().close();
        }
    }

    /**
     * 用户下线
     * @param userInfo
     * @param channel
     * @throws IOException
     */
    private void userOffLine(CIMUserInfo userInfo, NioSocketChannel channel) throws IOException {
        SessionSocketHolder.remove(channel);
        SessionSocketHolder.removeSession(userInfo.getUserId());

        //清除路由关系
        clearRouteInfo(userInfo);
    }

    /**
     * 下线，清除路由关系
     *
     * @param userInfo
     * @throws IOException
     */
    private void clearRouteInfo(CIMUserInfo userInfo) throws IOException {
        OkHttpClient okHttpClient = SpringBeanFactory.getBean(OkHttpClient.class);
        AppConfiguration configuration = SpringBeanFactory.getBean(AppConfiguration.class);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("userId", userInfo.getUserId());
        jsonObject.put("msg", "offLine");
        RequestBody requestBody = RequestBody.create(mediaType, jsonObject.toString());

        Request request = new Request.Builder()
                .url(configuration.getClearRouteUrl())
                .post(requestBody)
                .build();

        Response response = null;
        try {
            response = okHttpClient.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
        } finally {
            response.body().close();
        }
    }
    /**
     * 接收客户端发送的消息 channel 通道 Read 读 简而言之就是从通道中读取数据，也就是服务端接收客户端发来的数据。但是这个数据在不进行解码时它是ByteBuf类型的
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        /**
         * 传统的HTTP接入
         */
        if (msg instanceof FullHttpRequest) {
            handleHttpRequest(ctx, ((FullHttpRequest) msg));
            /**
             * WebSocket接入
             */
        } else if (msg instanceof WebSocketFrame) {
            handlerWebSocketFrame(ctx, (WebSocketFrame) msg);
        }
    }
    /**
     * channel 通道 Read 读取 Complete 完成 在通道读取完成后会在这个方法里通知，对应可以做刷新操作 ctx.flush()
     */
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    private void handlerWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        /**
         * 判断是否关闭链路的指令
         */
        if (frame instanceof CloseWebSocketFrame) {
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
            return;
        }
        /**
         * 判断是否ping消息
         */
        if (frame instanceof PingWebSocketFrame) {
            ctx.channel().write(new PongWebSocketFrame(frame.content().retain()));
            return;
        }
        /**
         * 本例程仅支持文本消息，不支持二进制消息
         */
        if (!(frame instanceof TextWebSocketFrame)) {
            throw new UnsupportedOperationException(
                    String.format("%s frame types not supported", frame.getClass().getName()));
        }
        /**
         *  返回应答消息
         */
        String request = ((TextWebSocketFrame) frame).text();
        if (logger.isLoggable(Level.FINE)) {
            logger.fine(String.format("%s received %s", ctx.channel(), request));
        }
        String[] strings = request.split(",");
        TextWebSocketFrame tws = null;
        if(strings[0].equalsIgnoreCase("ping")){
            tws = new TextWebSocketFrame("pong");
        }

        ctx.channel().writeAndFlush(tws);
    }
    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
        /**
         * 如果HTTP解码失败，返回HHTP异常
         */
        if (!req.decoderResult().isSuccess() || (!"websocket".equals(req.headers().get("Upgrade")))) {
            sendHttpResponse(ctx,
                    new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
            return;
        }
        /**
         * 获取url后置参数
         */
        HttpMethod method=req.method();
        String uri=req.uri();
        QueryStringDecoder queryStringDecoder = new QueryStringDecoder(uri);
        Map<String, List<String>> parameters = queryStringDecoder.parameters();
        String id = parameters.get("storeId").get(0);


        SessionSocketHolder.put(Long.valueOf(id), (NioSocketChannel) ctx.channel());
        SessionSocketHolder.saveSession(Long.valueOf(id),parameters.get("storeId").get(1));



        if(method==HttpMethod.GET&&uri.toLowerCase().startsWith("/webssss")){
            ctx.channel().attr(AttributeKey.valueOf("type")).set("anzhuo");
        }else if(method==HttpMethod.GET&&uri.toLowerCase().startsWith("/websocket")){
            ctx.channel().attr(AttributeKey.valueOf("type")).set("live");
        }
        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
                getWebSocketLocation(ctx.pipeline(),req,uri), null, false);
        handshaker = wsFactory.newHandshaker(req);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
        } else {
            handshaker.handshake(ctx.channel(), req);
        }
    }
    private static void sendHttpResponse(ChannelHandlerContext ctx, DefaultFullHttpResponse res) {
        // 返回应答给客户端
        if (res.status().code() != 200) {
            ByteBuf buf = Unpooled.copiedBuffer(res.status().toString(), CharsetUtil.UTF_8);
            res.content().writeBytes(buf);
            buf.release();
        }
        // 如果是非Keep-Alive，关闭连接
        ChannelFuture f = ctx.channel().writeAndFlush(res);
        if (!ctx.channel().isActive() || res.status().code() != 200) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }
    /**
     * exception 异常 Caught 抓住 抓住异常，当发生异常的时候，可以做一些相应的处理，比如打印日志、关闭链接
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.log(Level.ALL,cause.getMessage());
        ctx.close();
    }
    private static String getWebSocketLocation(ChannelPipeline cp, HttpRequest req, String path) {
        String protocol = "ws";
        if (cp.get(SslHandler.class) != null) {
            // SSL in use so use Secure WebSockets
            protocol = "wss";
        }
        return protocol + "://" + req.headers().get(HttpHeaderNames.HOST) + path;
    }

}
