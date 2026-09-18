package com.peterungab.sms.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The .ico written for the Windows launcher must be a valid multi-image icon (runs headless). */
class IconToolTest {

    @TempDir
    Path dir;

    @Test
    void writesOneNativelyRenderedPngEntryPerSize() throws Exception {
        Path ico = dir.resolve("app.ico");
        int[] sizes = {16, 32, 48, 256};
        IconTool.writeIco(ico, sizes);

        ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(ico)).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0, b.getShort());          // reserved
        assertEquals(1, b.getShort());          // type: icon
        assertEquals(sizes.length, b.getShort());
        int expectedOffset = 6 + 16 * sizes.length;
        for (int size : sizes) {
            int width = Byte.toUnsignedInt(b.get());
            int height = Byte.toUnsignedInt(b.get());
            assertEquals(size == 256 ? 0 : size, width, "width byte (0 means 256)");
            assertEquals(width, height);
            assertEquals(0, b.get());           // palette
            assertEquals(0, b.get());           // reserved
            assertEquals(1, b.getShort());      // planes
            assertEquals(32, b.getShort());     // bpp
            int length = b.getInt();
            int offset = b.getInt();
            assertEquals(expectedOffset, offset, "entries are laid out back to back");
            expectedOffset += length;

            byte[] png = new byte[length];
            b.mark();
            b.position(offset);
            b.get(png);
            b.reset();
            assertEquals((byte) 0x89, png[0]);
            assertEquals('P', png[1]);
            assertEquals('N', png[2]);
            assertEquals('G', png[3]);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            assertNotNull(image, "entry decodes as PNG");
            assertEquals(size, image.getWidth(), "rendered at its own size, not scaled from another entry");
            assertEquals(size, image.getHeight());
            assertTrue(image.getColorModel().hasAlpha());
            int corner = image.getRGB(0, 0) >>> 24;
            int centre = image.getRGB(size / 2, size / 2) >>> 24;
            assertTrue(corner < 128, "rounded tile leaves the corner transparent at " + size + "px");
            assertEquals(255, centre, "tile is opaque in the middle at " + size + "px");
        }
        assertEquals(expectedOffset, Files.size(ico), "no trailing bytes");
    }

    @Test
    void mainUsesTheDefaultSizesAndCreatesParentFolders() throws Exception {
        Path ico = dir.resolve("nested/out/app.ico");
        IconTool.main(new String[]{ico.toString()});
        ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(ico)).order(ByteOrder.LITTLE_ENDIAN);
        b.position(4);
        assertEquals(IconTool.DEFAULT_SIZES.length, b.getShort());
    }
}
