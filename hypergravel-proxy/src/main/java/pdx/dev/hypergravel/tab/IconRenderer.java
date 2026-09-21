package pdx.dev.hypergravel.tab;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

final class IconRenderer {

    private static final int FACE_X = 8;
    private static final int FACE_Y = 8;
    private static final int FACE_SIZE = 8;

    private IconRenderer() {
    }

    static byte[] renderGlyph(HeadIcon icon) {
        BufferedImage image = new BufferedImage(FACE_SIZE, FACE_SIZE, BufferedImage.TYPE_INT_ARGB);
        if (icon.stencil() == null) {
            return png(image);
        }
        int plate = 0xFF000000 | icon.plate();
        int ink = 0xFF000000 | icon.ink();
        int highlight = 0xFF000000 | icon.highlight();
        String[] stencil = icon.stencil();
        for (int row = 0; row < FACE_SIZE && row < stencil.length; row++) {
            String line = stencil[row];
            for (int column = 0; column < FACE_SIZE; column++) {
                char cell = column < line.length() ? line.charAt(column) : '.';
                image.setRGB(column, row, switch (cell) {
                    case '#' -> ink;
                    case 'o' -> highlight;
                    default -> plate;
                });
            }
        }
        return png(image);
    }

    static byte[] render(HeadIcon icon) {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);

        if (icon.stencil() == null) {

            image.setRGB(24, 8, 0xFF000000);
            return png(image);
        }

        int plate = 0xFF000000 | icon.plate();
        for (int x = 0; x < 32; x++) {
            for (int y = 0; y < 16; y++) {
                image.setRGB(x, y, plate);
            }
        }

        int ink = 0xFF000000 | icon.ink();
        int highlight = 0xFF000000 | icon.highlight();
        String[] stencil = icon.stencil();
        for (int row = 0; row < FACE_SIZE && row < stencil.length; row++) {
            String line = stencil[row];
            for (int column = 0; column < FACE_SIZE && column < line.length(); column++) {
                int colour = switch (line.charAt(column)) {
                    case '#' -> ink;
                    case 'o' -> highlight;
                    default -> plate;
                };
                image.setRGB(FACE_X + column, FACE_Y + row, colour);
            }
        }

        return png(image);
    }

    private static byte[] png(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(2048);
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot encode a head icon", e);
        }
        return out.toByteArray();
    }
}
