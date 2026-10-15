package com.extrarawstyle.veinminerplus.startup;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.w3c.dom.Node;

final class GifAnimation {
    static final String RESOURCE = "/assets/veinminerplus/textures/gui/startup.gif";
    final int width;
    final int height;
    final int[][] pixels;
    final int[] frameEnds;

    private GifAnimation(int width, int height, int[][] pixels, int[] frameEnds) {
        this.width = width;
        this.height = height;
        this.pixels = pixels;
        this.frameEnds = frameEnds;
    }

    static GifAnimation load() throws IOException {
        try (InputStream input = GifAnimation.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IOException("Missing startup GIF");
            }
            try (ImageInputStream stream = ImageIO.createImageInputStream(input)) {
                Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
                ImageReader reader = readers.next();
                try {
                    reader.setInput(stream);
                    int count = reader.getNumImages(true);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    int[][] pixels = new int[count][];
                    int[] frameEnds = new int[count];
                    int duration = 0;
                    for (int frame = 0; frame < count; frame++) {
                        BufferedImage image = reader.read(frame);
                        Node metadata = reader.getImageMetadata(frame).getAsTree("javax_imageio_gif_image_1.0");
                        Node descriptor = child(metadata, "ImageDescriptor");
                        Node control = child(metadata, "GraphicControlExtension");
                        // The bundled GIF contains complete frames with no inherited pixels.
                        if (image.getWidth() != width || image.getHeight() != height
                                || attribute(descriptor, "imageLeftPosition") != 0
                                || attribute(descriptor, "imageTopPosition") != 0
                                || !"restoreToBackgroundColor".equals(
                                        control.getAttributes().getNamedItem("disposalMethod").getNodeValue())) {
                            throw new IOException("Startup GIF must contain complete independent frames");
                        }
                        pixels[frame] = image.getRGB(0, 0, width, height, null, 0, width);
                        duration += Math.max(1, attribute(control, "delayTime") * 10);
                        frameEnds[frame] = duration;
                    }
                    return new GifAnimation(width, height, pixels, frameEnds);
                } finally {
                    reader.dispose();
                }
            }
        }
    }

    int frameAt(long elapsedMillis) {
        long position = Math.floorMod(elapsedMillis, frameEnds[frameEnds.length - 1]);
        for (int frame = 0; frame < frameEnds.length; frame++) {
            if (position < frameEnds[frame]) {
                return frame;
            }
        }
        return 0;
    }

    private static Node child(Node node, String name) throws IOException {
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (name.equals(child.getNodeName())) {
                return child;
            }
        }
        throw new IOException("Missing GIF metadata: " + name);
    }

    private static int attribute(Node node, String name) {
        return Integer.parseInt(node.getAttributes().getNamedItem(name).getNodeValue());
    }
}
