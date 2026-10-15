package com.extrarawstyle.veinminerplus.startup;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.toml.TomlParser;

public final class StartupAnimationCheck {
    public static void main(String[] arguments) throws Exception {
        checkAnimation();
        checkConfiguration();
        checkLaunchTargets();
        System.out.println("Startup animation checks passed: 29 frames, exact transparency, timing, cleanup, and client config.");
    }

    private static void checkAnimation() throws Exception {
        try (GifAnimation animation = GifAnimation.load(0)) {
            int width = (int) field(animation, "width");
            int height = (int) field(animation, "height");
            int[] frameEnds = (int[]) field(animation, "frameEnds");
            ByteBuffer pixels = (ByteBuffer) field(animation, "pixels");
            check(width == 500 && height == 500, "Original GIF dimensions changed");
            check(frameEnds.length == 29, "Original GIF frame count changed");
            check(pixels.remaining() == 29_000_000, "Decoded RGBA byte count is incorrect");
            for (int frame = 0; frame < frameEnds.length; frame++) {
                check(frameEnds[frame] == (frame + 1) * 20, "GIF frame delay changed at frame " + frame);
            }
            check(animation.frameAt(0) == 0, "First frame is incorrect");
            check(animation.frameAt(19) == 0, "First frame ends too early");
            check(animation.frameAt(20) == 1, "Second frame starts too late");
            check(animation.frameAt(579) == 28, "Last frame is incorrect");
            check(animation.frameAt(580) == 0, "GIF does not loop at 580 ms");
            check(animation.frameAt(600) == 1, "Second loop timing is incorrect");
            check(animation.frameAt(1_160) == 0, "GIF does not keep looping");
            check(animation.frameAt(580_000_020L) == 1, "Long-running animation timing is incorrect");
            check(animation.frameAt(-1) == 28, "Negative elapsed time does not wrap");
            comparePixels(pixels, width, height, frameEnds.length);

            Object element = animation.createElement();
            Object buffer = field(animation, "buffer");
            check(buffer == field(element, "bb"), "Render element does not use the owned vertex buffer");
            check(Proxy.isProxyClass(field(element, "renderer").getClass()), "Render callback proxy was not created");
            check((long) field(buffer, "bufferAddr") != 0L, "Render buffer was not allocated");
            animation.close();
            check((boolean) field(animation, "closed"), "Animation did not close");
            check((long) field(buffer, "bufferAddr") == 0L, "Render buffer was not freed");
            animation.close();
        }
    }

    private static void comparePixels(ByteBuffer pixels, int width, int height, int frameCount) throws Exception {
        Set<String> hashes = new HashSet<>();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        int[] transparentCounts = new int[frameCount];
        try (InputStream source = GifAnimation.class.getResourceAsStream(GifAnimation.RESOURCE);
                ImageInputStream imageInput = ImageIO.createImageInputStream(source)) {
            ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
            try {
                reader.setInput(imageInput);
                check(reader.getNumImages(true) == frameCount, "STB and ImageIO disagree on the frame count");
                for (int frame = 0; frame < frameCount; frame++) {
                    BufferedImage image = reader.read(frame);
                    check(image.getWidth() == width && image.getHeight() == height,
                            "Source frame dimensions changed at frame " + frame);
                    int[] expected = image.getRGB(0, 0, width, height, null, 0, width);
                    int offset = frame * width * height * 4;
                    for (int pixel = 0; pixel < expected.length; pixel++) {
                        int position = offset + pixel * 4;
                        int alpha = Byte.toUnsignedInt(pixels.get(position + 3));
                        int actual = alpha << 24
                                | Byte.toUnsignedInt(pixels.get(position)) << 16
                                | Byte.toUnsignedInt(pixels.get(position + 1)) << 8
                                | Byte.toUnsignedInt(pixels.get(position + 2));
                        // Transparent RGB has no visual meaning; alpha must still match exactly.
                        if (expected[pixel] >>> 24 == 0) {
                            transparentCounts[frame]++;
                            if (alpha != 0) {
                                throw new AssertionError("Transparent GIF pixel retained a ghost at frame "
                                        + frame + ", pixel " + pixel);
                            }
                        } else if (actual != expected[pixel]) {
                            throw new AssertionError("GIF color differs from ImageIO at frame "
                                    + frame + ", pixel " + pixel);
                        }
                    }
                    check(transparentCounts[frame] > 0, "Frame lost all transparency: " + frame);
                    digest.update(pixels.slice(offset, width * height * 4));
                    hashes.add(HexFormat.of().formatHex(digest.digest()));
                }
            } finally {
                reader.dispose();
            }
        }
        check(hashes.size() == 29, "GIF frames were duplicated or dropped");
        check(transparentCounts[0] == 112_311, "First frame transparency changed");
        check(transparentCounts[8] == 116_788, "Frame 8 transparency changed");
        check(transparentCounts[19] == 118_783, "Frame 19 transparency changed");
        check(transparentCounts[28] == 110_518, "Last frame transparency changed");
    }

    private static void checkConfiguration() throws Exception {
        Path directory = Files.createTempDirectory("veinminerplus-startup-check-");
        try {
            Path missing = directory.resolve("config/veinminerplus-client.toml");
            check(StartupBootstrap.readEnabled(missing), "Missing config did not enable the animation");
            check(Boolean.TRUE.equals(readConfig(missing).get("customStartupAnimation")),
                    "Missing config did not write the enabled default");
            byte[] generated = Files.readAllBytes(missing);
            check(StartupBootstrap.readEnabled(missing), "Generated config stopped enabling the animation");
            check(Arrays.equals(generated, Files.readAllBytes(missing)), "Existing config was unnecessarily rewritten");

            Path disabled = directory.resolve("disabled.toml");
            Files.writeString(disabled, "# Keep disabled\ncustomStartupAnimation = false\nextraSetting = 7\n");
            byte[] originalDisabled = Files.readAllBytes(disabled);
            check(!StartupBootstrap.readEnabled(disabled), "Explicit false did not restore the default fox");
            check(Arrays.equals(originalDisabled, Files.readAllBytes(disabled)), "Disabled config was modified");

            Path old = directory.resolve("old.toml");
            Files.writeString(old, "# Keep this comment\nextraSetting = \"preserve me\"\n\n[unrelated]\nnumber = 7\n");
            check(StartupBootstrap.readEnabled(old), "Old config did not receive the enabled default");
            CommentedConfig upgraded = readConfig(old);
            check(Boolean.TRUE.equals(upgraded.get("customStartupAnimation")), "Old config did not receive the key");
            check("preserve me".equals(upgraded.get("extraSetting")), "Existing config value was lost");
            check(((Number) upgraded.get("unrelated.number")).intValue() == 7, "Existing TOML table was lost");
            check(upgraded.getComment("extraSetting").contains("Keep this comment"), "Existing comment was lost");

            Path corrupt = directory.resolve("corrupt.toml");
            Files.writeString(corrupt, "customStartupAnimation = [\n");
            byte[] originalCorrupt = Files.readAllBytes(corrupt);
            boolean rejected = false;
            try {
                StartupBootstrap.readEnabled(corrupt);
            } catch (ParsingException exception) {
                rejected = true;
            }
            check(rejected, "Corrupt TOML did not trigger the startup fallback");
            check(Arrays.equals(originalCorrupt, Files.readAllBytes(corrupt)), "Corrupt TOML was overwritten");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }

    private static void checkLaunchTargets() {
        for (String target : new String[] { "forgeclient", "forgeclientdev", "forgeclientuserdev" }) {
            check(StartupBootstrap.isClientLaunch(new String[] { "--launchTarget", target }),
                    "Client launch target was missed: " + target);
            check(StartupBootstrap.isClientLaunch(new String[] { "--other", "value", "--launchTarget=" + target }),
                    "Inline client launch target was missed: " + target);
        }
        for (String target : new String[] { "forgeserver", "forgeserverdev", "forgeserveruserdev", "unknown" }) {
            check(!StartupBootstrap.isClientLaunch(new String[] { "--launchTarget", target }),
                    "Non-client launch target enabled the animation: " + target);
            check(!StartupBootstrap.isClientLaunch(new String[] { "--launchTarget=" + target }),
                    "Inline non-client launch target enabled the animation: " + target);
        }
        check(!StartupBootstrap.isClientLaunch(new String[0]), "Missing launch target enabled the animation");
        check(!StartupBootstrap.isClientLaunch(new String[] { "--launchTarget" }), "Incomplete launch target enabled the animation");
        check(!StartupBootstrap.isClientLaunch(new String[] { "--other", "forgeclient" }), "Unrelated argument enabled the animation");
    }

    private static CommentedConfig readConfig(Path file) throws Exception {
        try (Reader reader = Files.newBufferedReader(file)) {
            return new TomlParser().parse(reader);
        }
    }

    private static Object field(Object instance, String name) throws ReflectiveOperationException {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
