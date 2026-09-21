package pdx.dev.hypergravel.tab;

public enum HeadIcon {

    MARK(0x17171b, 0xff8a3d, 0xffd9b0, new String[] {
        "........",
        ".####...",
        ".#..##..",
        ".#...#..",
        ".#..##..",
        ".####...",
        ".#......",
        ".#......",
    }, '\u2726'),

    OPEN(0x14231a, 0x4ade80, 0xbbf7d0, new String[] {
        "........",
        "..####..",
        ".#oooo#.",
        ".#oooo#.",
        ".#oooo#.",
        ".#oooo#.",
        "..####..",
        "........",
    }, '\u25cf'),

    DUEL(0x1b1417, 0xf87171, 0xfecaca, new String[] {
        "......##",
        ".....##.",
        "....##..",
        "...##...",
        "..##....",
        "###.....",
        "##......",
        "#.......",
    }, '\u2694'),

    KIT_IRON(0x1b1b1f, 0xd8d8d8, 0x8a6a3a, new String[] {
        "......##",
        ".....##.",
        "....##..",
        "...##...",
        "..##....",
        "ooo.....",
        "oo......",
        "o.......",
    }, '\u2692'),

    KIT_DIAMOND(0x14232a, 0x4aedd9, 0x8a6a3a, new String[] {
        "......##",
        ".....##.",
        "....##..",
        "...##...",
        "..##....",
        "ooo.....",
        "oo......",
        "o.......",
    }, '\u2756'),

    KIT_NETHERITE(0x201a1a, 0x8a7d7d, 0xffd9b0, new String[] {
        "......##",
        ".....##.",
        "....##..",
        "...##...",
        "..##....",
        "ooo.....",
        "oo......",
        "o.......",
    }, '\u25c6'),

    KIT_CLASSIC(0x1b1b1f, 0x9a9a9a, 0x8a6a3a, new String[] {
        "......##",
        ".....##.",
        "....##..",
        "...##...",
        "..##....",
        "ooo.....",
        "oo......",
        "o.......",
    }, '\u25b2'),

    KIT_AXE(0x1b1b1f, 0xd8d8d8, 0x8a6a3a, new String[] {
        "..####..",
        ".#####..",
        ".####o..",
        "..##oo..",
        "....oo..",
        "....oo..",
        "....oo..",
        "....oo..",
    }, '\u2691'),

    KIT_ARCHER(0x191b16, 0x8a6a3a, 0xe8e8e8, new String[] {
        "...##...",
        "..#..o..",
        ".#...o..",
        ".#....o.",
        ".#...o..",
        "..#..o..",
        "...##...",
        "........",
    }, '\u27a4'),

    KIT_NODEBUFF(0x231620, 0xff5c8a, 0xffc2d4, new String[] {
        "...##...",
        "...##...",
        "..####..",
        ".#oooo#.",
        "#oooooo#",
        "#oooooo#",
        ".#oooo#.",
        "..####..",
    }, '\u2697'),

    KIT_SUMO(0x231f16, 0xc8a06a, 0xfde68a, new String[] {
        "........",
        ".##.....",
        ".##.....",
        ".##.....",
        ".##.....",
        ".##.###.",
        ".######.",
        "..oooo..",
    }, '\u25c9'),

    CROWN(0x17171b, 0xf5b301, 0xffe98a, new String[] {
        "........",
        "#.#..#.#",
        "#o#oo#o#",
        "#oooooo#",
        "#oooooo#",
        "########",
        ".######.",
        "........",
    }, '\u2654'),

    STAR(0x17171b, 0xffd54a, 0xfff3b0, new String[] {
        "...##...",
        "...##...",
        "########",
        ".######.",
        "..####..",
        ".##..##.",
        "##....##",
        "........",
    }, '\u2605'),

    SKULL(0x1a1a1e, 0xe8e8e8, 0x9a9a9a, new String[] {
        ".######.",
        "#oooooo#",
        "#o#oo#o#",
        "#o#oo#o#",
        "#oooooo#",
        "#o####o#",
        ".#o..o#.",
        "..####..",
    }, '\u2620'),

    BOLT(0x1b1720, 0xa78bfa, 0xe9d5ff, new String[] {
        "....###.",
        "...###..",
        "..###...",
        ".######.",
        "..####..",
        "..###...",
        ".###....",
        ".##.....",
    }, '\u2727'),

    SPACER(0, 0, 0, null, '\u0020');

    private final int plate;
    private final int ink;
    private final int highlight;
    private final String[] stencil;
    private final char glyph;

    HeadIcon(int plate, int ink, int highlight, String[] stencil, char glyph) {
        this.plate = plate;
        this.ink = ink;
        this.highlight = highlight;
        this.stencil = stencil;
        this.glyph = glyph;
    }

    public char glyph() {
        return glyph;
    }

    int plate() {
        return plate;
    }

    int ink() {
        return ink;
    }

    int highlight() {
        return highlight;
    }

    String[] stencil() {
        return stencil;
    }

    public static HeadIcon byName(String name) {
        if (name == null) {
            return null;
        }
        try {
            return valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
