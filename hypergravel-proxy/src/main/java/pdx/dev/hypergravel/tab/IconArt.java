package pdx.dev.hypergravel.tab;

public final class IconArt {

    private IconArt() {
    }

    public static byte[] glyph(HeadIcon icon) {
        return IconRenderer.renderGlyph(icon);
    }

    public static byte[] skin(HeadIcon icon) {
        return IconRenderer.render(icon);
    }
}
