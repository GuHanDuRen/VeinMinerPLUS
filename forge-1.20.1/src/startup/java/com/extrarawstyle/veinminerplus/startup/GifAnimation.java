package com.extrarawstyle.veinminerplus.startup;

import static org.lwjgl.opengl.GL32C.*;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import org.lwjgl.PointerBuffer;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import net.minecraftforge.fml.earlydisplay.ElementShader;
import net.minecraftforge.fml.earlydisplay.QuadHelper;
import net.minecraftforge.fml.earlydisplay.RenderElement;
import net.minecraftforge.fml.earlydisplay.SimpleBufferBuilder;

final class GifAnimation implements AutoCloseable {
    static final String RESOURCE = "/assets/veinminerplus/textures/gui/startup.gif";
    private final ByteBuffer pixels;
    private final int width;
    private final int height;
    private final int[] frameEnds;
    private final int bottomInset;
    private final Field globalAlpha;
    private final long startTime = System.nanoTime();
    private SimpleBufferBuilder buffer;
    private int texture;
    private int uploadedFrame = -1;
    private boolean closed;

    private GifAnimation(ByteBuffer pixels, int width, int height, int[] frameEnds, int bottomInset, Field globalAlpha) {
        this.pixels = pixels;
        this.width = width;
        this.height = height;
        this.frameEnds = frameEnds;
        this.bottomInset = bottomInset;
        this.globalAlpha = globalAlpha;
    }

    static GifAnimation load(int bottomInset) throws IOException {
        Field globalAlpha;
        try {
            globalAlpha = RenderElement.class.getDeclaredField("globalAlpha");
            globalAlpha.setAccessible(true);
        } catch (ReflectiveOperationException exception) {
            throw new IOException("Could not access the startup fade", exception);
        }
        byte[] bytes;
        try (InputStream stream = GifAnimation.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IOException("Missing startup GIF");
            }
            bytes = stream.readAllBytes();
        }
        ByteBuffer encoded = MemoryUtil.memAlloc(bytes.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            encoded.put(bytes).flip();
            PointerBuffer delayPointer = stack.callocPointer(1);
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer count = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);
            ByteBuffer pixels = STBImage.stbi_load_gif_from_memory(encoded, delayPointer, width, height, count, channels, 4);
            if (pixels == null) {
                throw new IOException("Could not decode startup GIF: " + STBImage.stbi_failure_reason());
            }
            IntBuffer delays = MemoryUtil.memIntBuffer(delayPointer.get(0), count.get(0));
            int[] frameEnds = new int[count.get(0)];
            int duration = 0;
            for (int index = 0; index < frameEnds.length; index++) {
                duration += Math.max(1, delays.get(index));
                frameEnds[index] = duration;
            }
            MemoryUtil.memFree(delays);
            return new GifAnimation(pixels, width.get(0), height.get(0), frameEnds, bottomInset, globalAlpha);
        } finally {
            MemoryUtil.memFree(encoded);
        }
    }

    RenderElement createElement() throws ReflectiveOperationException {
        Class<?> initializerType = Class.forName("net.minecraftforge.fml.earlydisplay.RenderElement$Initializer");
        Class<?> rendererType = Class.forName("net.minecraftforge.fml.earlydisplay.RenderElement$Renderer");
        Object renderer = Proxy.newProxyInstance(rendererType.getClassLoader(), new Class<?>[] { rendererType },
                (proxy, method, arguments) -> {
                    if ("accept".equals(method.getName())) {
                        render((SimpleBufferBuilder) arguments[0], (RenderElement.DisplayContext) arguments[1]);
                    }
                    return null;
                });
        Object initializer = Proxy.newProxyInstance(initializerType.getClassLoader(), new Class<?>[] { initializerType },
                (proxy, method, arguments) -> renderer);
        // FML exposes RenderElement but keeps its callback interfaces package-private.
        Constructor<RenderElement> constructor = RenderElement.class.getDeclaredConstructor(initializerType);
        constructor.setAccessible(true);
        RenderElement element = constructor.newInstance(initializer);
        Field bufferField = RenderElement.class.getDeclaredField("bb");
        bufferField.setAccessible(true);
        buffer = (SimpleBufferBuilder) bufferField.get(element);
        return element;
    }

    private void render(SimpleBufferBuilder vertices, RenderElement.DisplayContext context) throws IllegalAccessException {
        int previousUnit = glGetInteger(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE0);
        int previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        if (texture == 0) {
            texture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, texture);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        } else {
            glBindTexture(GL_TEXTURE_2D, texture);
        }
        int frame = frameAt((System.nanoTime() - startTime) / 1_000_000L);
        if (frame != uploadedFrame) {
            int frameBytes = width * height * 4;
            ByteBuffer image = pixels.slice(frame * frameBytes, frameBytes);
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, image);
            uploadedFrame = frame;
        }
        context.elementShader().updateTextureUniform(0);
        context.elementShader().updateRenderTypeUniform(ElementShader.RenderType.TEXTURE);
        float size = 128 * context.scale();
        float right = context.scaledWidth() - 12 * context.scale();
        float bottom = context.scaledHeight() - bottomInset;
        vertices.begin(SimpleBufferBuilder.Format.POS_TEX_COLOR, SimpleBufferBuilder.Mode.QUADS);
        QuadHelper.loadQuad(vertices, right - size, right, bottom - size, bottom, 0, 1, 0, 1,
                globalAlpha.getInt(null) << 24 | 0xFFFFFF);
        vertices.draw();
        glBindTexture(GL_TEXTURE_2D, previousTexture);
        glActiveTexture(previousUnit);
    }

    int frameAt(long elapsedMillis) {
        long position = Math.floorMod(elapsedMillis, frameEnds[frameEnds.length - 1]);
        for (int index = 0; index < frameEnds.length; index++) {
            if (position < frameEnds[index]) {
                return index;
            }
        }
        return 0;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (texture != 0) {
            glDeleteTextures(texture);
        }
        if (buffer != null) {
            buffer.close();
        }
        STBImage.stbi_image_free(pixels);
    }
}
