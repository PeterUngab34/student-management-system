package com.peterungab.sms.tools;

import com.peterungab.sms.ui.AppIcon;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Writes the app mark ({@link AppIcon}) as a multi-resolution Windows {@code .ico} file. Every
 * entry is rendered from the vector artwork at its own size (no upscaled 32px bitmap) and stored
 * PNG-compressed, which Windows supports since Vista. Used by {@code scripts/package-windows.cmd}
 * to give the jpackage launcher its icon.
 * <pre>
 *   java -cp student-management-system.jar com.peterungab.sms.tools.IconTool target/icon/app.ico [size ...]
 * </pre>
 */
public final class IconTool {

    /** Sizes Windows asks for in Explorer, the taskbar, Alt+Tab and the Start menu. */
    public static final int[] DEFAULT_SIZES = {16, 20, 24, 32, 40, 48, 64, 128, 256};

    private IconTool() {
    }

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "app.ico");
        int[] sizes = args.length > 1
                ? Arrays.stream(args).skip(1).mapToInt(Integer::parseInt).toArray()
                : DEFAULT_SIZES;
        writeIco(out, sizes);
        System.out.println("Wrote " + out.toAbsolutePath() + " with " + sizes.length + " images: "
                + Arrays.toString(sizes));
    }

    /** Renders the icon at each size and writes them as one {@code .ico}. */
    public static void writeIco(Path file, int... sizes) throws IOException {
        List<byte[]> images = new ArrayList<>();
        for (int size : sizes) {
            images.add(png(render(size)));
        }
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(file, ico(sizes, images));
    }

    /**
     * Builds the ICO container: ICONDIR header, one 16-byte ICONDIRENTRY per image, then the
     * PNG streams back to back. All integers are little-endian.
     */
    static byte[] ico(int[] sizes, List<byte[]> images) {
        if (sizes.length != images.size() || sizes.length == 0 || sizes.length > 0xFFFF) {
            throw new IllegalArgumentException("sizes and images must match and be non-empty");
        }
        int directorySize = 6 + 16 * sizes.length;
        int total = directorySize + images.stream().mapToInt(b -> b.length).sum();
        ByteBuffer b = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) 0);            // reserved
        b.putShort((short) 1);            // type: 1 = icon
        b.putShort((short) sizes.length); // image count
        int offset = directorySize;
        for (int i = 0; i < sizes.length; i++) {
            int size = sizes[i];
            if (size < 1 || size > 256) {
                throw new IllegalArgumentException("ICO images must be 1..256 px, got " + size);
            }
            byte dimension = (byte) (size == 256 ? 0 : size); // 0 means 256
            b.put(dimension);             // width
            b.put(dimension);             // height
            b.put((byte) 0);              // colours in palette (0 = no palette)
            b.put((byte) 0);              // reserved
            b.putShort((short) 1);        // colour planes
            b.putShort((short) 32);       // bits per pixel
            b.putInt(images.get(i).length);
            b.putInt(offset);
            offset += images.get(i).length;
        }
        for (byte[] image : images) {
            b.put(image);
        }
        return b.array();
    }

    static BufferedImage render(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            AppIcon.paint(g, size);
        } finally {
            g.dispose();
        }
        return img;
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", out)) {
            throw new IOException("No PNG writer available");
        }
        return out.toByteArray();
    }
}
