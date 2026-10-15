package com.extrarawstyle.veinminerplus.startup;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;

import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;

import net.minecraftforge.fml.common.FMLLog;

public final class StartupRenderer {
    private static GifAnimation animation;
    private static IntBuffer upload;
    private static int texture;
    private static int uploadedFrame = -1;
    private static long startTime;
    private static boolean failed;

    private StartupRenderer() {
    }

    public static boolean draw() {
        if (failed) {
            return false;
        }
        try {
            if (animation == null) {
                animation = GifAnimation.load();
                upload = BufferUtils.createIntBuffer(animation.width * animation.height);
                startTime = System.nanoTime();
            }
            int previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
            try {
                glEnable(GL_TEXTURE_2D);
                if (texture == 0) {
                    texture = glGenTextures();
                    glBindTexture(GL_TEXTURE_2D, texture);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, animation.width, animation.height, 0,
                            GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, (IntBuffer) null);
                } else {
                    glBindTexture(GL_TEXTURE_2D, texture);
                }
                int frame = animation.frameAt((System.nanoTime() - startTime) / 1000000L);
                if (frame != uploadedFrame) {
                    upload.clear();
                    upload.put(animation.pixels[frame]).flip();
                    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, animation.width, animation.height,
                            GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, upload);
                    uploadedFrame = frame;
                }
                float size = Math.min(128, Math.min(Display.getWidth(), Display.getHeight()) - 24);
                float right = 320 + Display.getWidth() / 2 - 12;
                float bottom = 240 + Display.getHeight() / 2 - 12;
                glPushMatrix();
                try {
                    glLoadIdentity();
                    glColor4f(1, 1, 1, 1);
                    glBegin(GL_QUADS);
                    glTexCoord2f(0, 0);
                    glVertex2f(right - size, bottom - size);
                    glTexCoord2f(0, 1);
                    glVertex2f(right - size, bottom);
                    glTexCoord2f(1, 1);
                    glVertex2f(right, bottom);
                    glTexCoord2f(1, 0);
                    glVertex2f(right, bottom - size);
                    glEnd();
                } finally {
                    glPopMatrix();
                }
                return true;
            } finally {
                glBindTexture(GL_TEXTURE_2D, previousTexture);
                glDisable(GL_TEXTURE_2D);
            }
        } catch (Exception | LinkageError exception) {
            failed = true;
            FMLLog.log.warn("Could not render the startup animation; keeping the Forge logo", exception);
            return false;
        }
    }

    public static void close() {
        if (texture != 0) {
            glDeleteTextures(texture);
            texture = 0;
        }
        animation = null;
        upload = null;
        uploadedFrame = -1;
    }
}
