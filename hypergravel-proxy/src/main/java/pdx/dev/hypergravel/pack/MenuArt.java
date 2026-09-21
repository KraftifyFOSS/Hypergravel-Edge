package pdx.dev.hypergravel.pack;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

final class MenuArt {

    static final int WELL_X = 7;
    static final int WELL_Y_CONTAINER = 17;

    static final int SLOT_ORIGIN_X = 8;
    static final int SLOT_ORIGIN_Y = 18;
    static final int SLOT_PITCH = 18;
    static final int SLOT_SIZE = 18;

    static final int WIDTH = 176;
    static final int HEIGHT = height(6);

    static int height(int rows) {
        return 114 + rows * 18;
    }

    static int inventoryWellY(int rows) {
        return 18 * rows + 31;
    }

    static int hotbarWellY(int rows) {
        return 18 * rows + 89;
    }

    static final int BACKGROUND_ASCENT = 12;

    private static final int PANEL = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int SHADE = 0xFF555555;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int SLOT_SHADE = 0xFF373737;
    private static final int OUTLINE = 0xFF000000;

    private static final int ARROW_INK = 0xFF373737;
    private static final int ON_INK = 0xFF48c052;
    private static final int ON_SHADE = 0xFF2c7a34;
    private static final int OFF_INK = 0xFF6b6b6b;
    private static final int OFF_SHADE = 0xFF3f3f3f;
    private static final int BLADE = 0xFFf2f2f6;
    private static final int BLADE_SHADE = 0xFF5c5c66;
    private static final int HILT = 0xFF6b4527;
    private static final int GUARD = 0xFFd8a83a;
    private static final int AXIS = 0xFF373737;
    private static final int GOLD = 0xFFf2c14e;
    private static final int GOLD_LIGHT = 0xFFfff0b0;
    private static final int GOLD_SHADE = 0xFFbd8f2a;
    private static final int COG = 0xFF9a9aa2;
    private static final int COG_SHADE = 0xFF5a5a62;
    private static final int EARTH_EDGE = 0xFF16324f;
    private static final int EARTH_SEA = 0xFF2f6fb5;
    private static final int EARTH_SEA_SHADE = 0xFF1d4a7d;
    private static final int EARTH_LAND = 0xFF57a04b;
    private static final int EARTH_LAND_SHADE = 0xFF3b7335;
    private static final int EARTH_SHEEN = 0xFF7fb4e6;
    private static final int NOTE = 0xFF6d6de0;
    private static final int NOTE_SHADE = 0xFF4a4ab0;

    private static final int EXIT_INK = 0xFF000000;
    private static final int TRACK = 0xFF5a5a62;
    private static final int KNOB = 0xFFdcdce4;
    private static final int KNOB_SHADE = 0xFF8e8e98;
    private static final int EYE_WHITE = 0xFFf0f0f4;

    private static final int EYE_IRIS = 0xFF9a9aa4;

    
    
    private static final int INK = 0xFF23232b;
    private static final int WALL = 0xFFe4dccb;
    private static final int WALL_SHADE = 0xFFbfb6a3;
    private static final int ROOF = 0xFFb0563f;
    private static final int PANE = 0xFFf2c14e;
    private static final int DOOR = 0xFF6b4527;
    private static final int STONE_LIGHT = 0xFFc9c9d1;
    private static final int ROAD = 0xFF4e4e57;
    private static final int KERB = 0xFFb9b9c2;
    private static final int MARK = 0xFFf4f4f8;
    private static final int LEAF = 0xFF4f9b4a;
    private static final int WATER = 0xFF3f78c8;
    private static final int SOIL = 0xFF7a5230;
    private static final int SOIL_DARK = 0xFF5b3c23;
    private static final int CLOTH = 0xFFd0483f;

    private MenuArt() {
    }

    static byte[] background(int[] slots, int[] cardSlots, int[] buttonSlots) {
        return background(6, slots, cardSlots, buttonSlots);
    }

    static byte[] background(int rows, int[] slots, int[] cardSlots, int[] buttonSlots) {
        return background(rows, slots, cardSlots, buttonSlots, new int[0]);
    }

    static byte[] background(int rows, int[] slots, int[] cardSlots, int[] buttonSlots,
                             int[] barRows) {
        return background(rows, slots, cardSlots, buttonSlots, barRows, false);
    }

    static byte[] background(int rows, int[] slots, int[] cardSlots, int[] buttonSlots,
                             int[] barRows, boolean titleBar) {
        return background(rows, slots, cardSlots, buttonSlots, barRows, titleBar, new int[0][]);
    }

    static byte[] background(int rows, int[] slots, int[] cardSlots, int[] buttonSlots,
                             int[] barRows, boolean titleBar, int[][] blocks) {
        return background(rows, slots, cardSlots, buttonSlots, barRows, titleBar, blocks, false);
    }

    static byte[] background(int rows, int[] slots, int[] cardSlots, int[] buttonSlots,
                             int[] barRows, boolean titleBar, int[][] blocks, boolean portal) {
        return background(rows, slots, cardSlots, buttonSlots, barRows, titleBar, blocks,
                portal ? portalTile : null);
    }

    





    static byte[] background(int rows, int[] slots, int[] cardSlots, int[] buttonSlots,
                             int[] barRows, boolean titleBar, int[][] blocks, BufferedImage backdrop) {
        return png(panelImage(rows, slots, cardSlots, buttonSlots, barRows, titleBar, blocks,
                backdrop));
    }

    






    private static BufferedImage panelImage(int rows, int[] slots, int[] cardSlots,
                                            int[] buttonSlots, int[] barRows, boolean titleBar,
                                            int[][] blocks, BufferedImage backdrop) {
        final int HEIGHT = height(rows);
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);

        fill(image, 0, 0, WIDTH, HEIGHT, PANEL);
        fill(image, 1, 1, WIDTH - 2, 2, LIGHT);
        fill(image, 1, 1, 2, HEIGHT - 2, LIGHT);
        fill(image, 1, HEIGHT - 3, WIDTH - 2, 2, SHADE);
        fill(image, WIDTH - 3, 1, 2, HEIGHT - 2, SHADE);
        fill(image, 0, 0, WIDTH, 1, OUTLINE);
        fill(image, 0, 0, 1, HEIGHT, OUTLINE);
        fill(image, 0, HEIGHT - 1, WIDTH, 1, OUTLINE);
        fill(image, WIDTH - 1, 0, 1, HEIGHT, OUTLINE);

        cutCorners(image, 5);

        if (backdrop != null) {
            int tileW = backdrop.getWidth();
            int tileH = backdrop.getHeight();
            for (int x = 3; x < WIDTH - 3; x++) {
                for (int y = 3; y < HEIGHT - 3; y++) {
                    image.setRGB(x, y, backdrop.getRGB(x % tileW, y % tileH));
                }
            }
        }

        if (titleBar) {
            raised(image, 4, 3, WIDTH - 8, 13);
        }

        for (int[] block : blocks) {
            int x = WELL_X + (block[0] % 9) * SLOT_PITCH;
            int y = WELL_Y_CONTAINER + (block[0] / 9) * SLOT_PITCH;
            int width = block[1] * SLOT_PITCH;
            int height = block[2] * SLOT_PITCH;
            if (block[3] == 1) {
                raised(image, x, y, width, height);
            } else {
                recess(image, x, y, width, height);
            }
        }

        for (int slot : cardSlots) {

            int x = WELL_X + (slot % 9 - 1) * SLOT_PITCH;
            int y = WELL_Y_CONTAINER + (slot / 9 - 1) * SLOT_PITCH;
            recess(image, x, y, SLOT_PITCH * 3, SLOT_PITCH * 3);
        }

        for (int row : barRows) {
            recess(image, WELL_X, WELL_Y_CONTAINER + row * SLOT_PITCH,
                    SLOT_PITCH * 9, SLOT_PITCH);
        }

        for (int slot : buttonSlots) {
            button(image, WELL_X + (slot % 9) * SLOT_PITCH,
                    WELL_Y_CONTAINER + (slot / 9) * SLOT_PITCH);
        }
        for (int slot : slots) {
            if (contains(cardSlots, slot) || contains(buttonSlots, slot)) {
                continue;
            }
            slot(image, WELL_X + (slot % 9) * SLOT_PITCH,
                    WELL_Y_CONTAINER + (slot / 9) * SLOT_PITCH);
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                slot(image, WELL_X + column * SLOT_PITCH,
                        inventoryWellY(rows) + row * SLOT_PITCH);
            }
        }
        for (int column = 0; column < 9; column++) {
            slot(image, WELL_X + column * SLOT_PITCH, hotbarWellY(rows));
        }

        return image;
    }

    private static int clamp(int value) {
        return Math.min(255, Math.max(0, value));
    }

    
    private static void dim(BufferedImage image, int x, int y) {
        if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) {
            return;
        }
        image.setRGB(x, y, blend(image.getRGB(x, y), 0xFF7f8ba8, 0.6));
    }

    private static int blend(int under, int over, double amount) {
        int red = (int) ((under >> 16 & 0xFF) * (1 - amount) + (over >> 16 & 0xFF) * amount);
        int green = (int) ((under >> 8 & 0xFF) * (1 - amount) + (over >> 8 & 0xFF) * amount);
        int blue = (int) ((under & 0xFF) * (1 - amount) + (over & 0xFF) * amount);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    

    







    private static final int W_PANEL = 0xFF2b303a;
    private static final int W_LIGHT = 0xFF454c58;
    private static final int W_SHADE = 0xFF171a20;
    private static final int W_WELL = 0xFF1b1f26;
    private static final int W_WELL_EDGE = 0xFF0d1015;
    private static final int W_INK = 0xFFe6ebf4;
    private static final int W_DIM = 0xFF79839a;

    
    private static final int[] W_ACCENT = {0xFFb4534a, 0xFF4e9a5a, 0xFFc79a4a};

    static final String[] WORLD_STYLES = {"board", "list", "carousel", "console", "radar"};
    private static final String[] WORLD_NAMES = {"SURVIVAL", "ARENA", "CREATIVE"};

    private static int slotX(int slot) {
        return WELL_X + (slot % 9) * SLOT_PITCH;
    }

    private static int slotY(int slot) {
        return WELL_Y_CONTAINER + (slot / 9) * SLOT_PITCH;
    }

    
    private static BufferedImage worldsBase() {
        final int rows = 6;
        final int height = height(rows);
        BufferedImage image = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_ARGB);
        fill(image, 0, 0, WIDTH, height, W_PANEL);
        fill(image, 1, 1, WIDTH - 2, 2, W_LIGHT);
        fill(image, 1, 1, 2, height - 2, W_LIGHT);
        fill(image, 1, height - 3, WIDTH - 2, 2, W_SHADE);
        fill(image, WIDTH - 3, 1, 2, height - 2, W_SHADE);
        fill(image, 0, 0, WIDTH, 1, OUTLINE);
        fill(image, 0, 0, 1, height, OUTLINE);
        fill(image, 0, height - 1, WIDTH, 1, OUTLINE);
        fill(image, WIDTH - 1, 0, 1, height, OUTLINE);
        cutCorners(image, 5);

        
        
        fill(image, 4, 3, WIDTH - 8, height(rows) - 3 - 96, darken(W_PANEL, 10));

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                cellIn(image, WELL_X + column * SLOT_PITCH,
                        inventoryWellY(rows) + row * SLOT_PITCH, W_WELL, W_WELL_EDGE);
            }
        }
        for (int column = 0; column < 9; column++) {
            cellIn(image, WELL_X + column * SLOT_PITCH, hotbarWellY(rows), W_WELL, W_WELL_EDGE);
        }
        return image;
    }

    
    private static void worldsHeader(BufferedImage image, String text) {
        raisedIn(image, 4, 3, WIDTH - 8, 13, darken(W_PANEL, 4), W_LIGHT, W_SHADE);
        int width = 6 * text.length() - 1;
        word(image, text, (WIDTH - width) / 2, 6, 1, W_DIM);
    }

    






    private static void accentWell(BufferedImage image, int x, int y, int size, int accent) {
        recessIn(image, x, y, size, size, W_WELL, W_WELL_EDGE, lighten(W_WELL, 26));
    }

    






    static byte[] radarBlip() {
        int frames = 8;
        BufferedImage strip = new BufferedImage(16, 16 * frames, BufferedImage.TYPE_INT_ARGB);
        for (int frame = 0; frame < frames; frame++) {
            double phase = Math.sin(Math.PI * frame / (frames - 1.0));
            int radius = (int) Math.round(1 + phase * 2.4);
            int alpha = (int) Math.round(90 + phase * 150);
            for (int y = -radius; y <= radius; y++) {
                for (int x = -radius; x <= radius; x++) {
                    if (x * x + y * y > radius * radius) {
                        continue;
                    }
                    boolean core = x * x + y * y <= 1;
                    int colour = core ? 0x00cfe8ff : 0x008fb8d8;
                    strip.setRGB(8 + x, frame * 16 + 8 + y,
                            (Math.min(255, core ? alpha + 40 : alpha) << 24) | colour);
                }
            }
        }
        return png(strip);
    }

    










    static byte[] gamesPanel() {
        BufferedImage image = worldsBase();
        worldsHeader(image, "CHOOSE A KIT");
        for (int card = 0; card < 3; card++) {
            int x = WELL_X + card * 3 * SLOT_PITCH;
            int y = slotY(9);
            int size = 3 * SLOT_PITCH;
            int accent = W_ACCENT[card];
            raisedIn(image, x, y, size, size, W_PANEL, W_LIGHT, W_SHADE);
            fill(image, x + 2, y + 1, size - 4, 3, accent);
            
            
            
            int well = 36;
            recessIn(image, x + (size - well) / 2, y + (size - well) / 2, well, well,
                    W_WELL, W_WELL_EDGE, lighten(W_WELL, 24));
        }
        
        
        
        
        recessIn(image, WELL_X, slotY(36), SLOT_PITCH * 9, SLOT_PITCH,
                darken(W_PANEL, 14), W_WELL_EDGE, lighten(W_PANEL, 10));
        for (int card = 0; card < 3; card++) {
            int x = WELL_X + card * 3 * SLOT_PITCH;
            if (card > 0) {
                fill(image, x, slotY(36) + 2, 1, SLOT_PITCH - 4, darken(W_PANEL, 26));
            }
            for (int tick = 0; tick < 3; tick++) {                  
                fill(image, x + SLOT_PITCH * 2 + 4, slotY(36) + 6 + tick * 2, 9, 1,
                        darken(W_PANEL, 26));
            }
        }
        
        fill(image, WELL_X, slotY(45) - 2, SLOT_PITCH * 9, 1, lighten(W_PANEL, 16));
        for (int seat : new int[] {45, 46}) {
            raisedIn(image, slotX(seat), slotY(seat), SLOT_SIZE, SLOT_SIZE,
                    darken(W_PANEL, 4), W_LIGHT, W_SHADE);
        }
        for (int seat : new int[] {48, 50, 51, 52}) {
            raisedIn(image, slotX(seat), slotY(seat), SLOT_SIZE, SLOT_SIZE,
                    W_PANEL, W_LIGHT, W_SHADE);
        }
        raisedIn(image, slotX(53), slotY(53), SLOT_SIZE, SLOT_SIZE,
                0xFF5a2b2b, 0xFF7d4444, 0xFF331717);
        return png(image);
    }

    









    static byte[] ladderPanel() {
        BufferedImage image = worldsBase();
        worldsHeader(image, "LEADERBOARD");
        String[] names = {"SECOND", "FIRST", "THIRD"};
        int[] metals = {0xFFb9c2d0, 0xFFe0b046, 0xFFb07a46};
        for (int place = 0; place < 3; place++) {
            int x = WELL_X + place * 3 * SLOT_PITCH;
            int y = WELL_Y_CONTAINER;
            int size = 3 * SLOT_PITCH;
            boolean winner = place == 1;
            int face = winner ? lighten(W_PANEL, 10) : W_PANEL;
            raisedIn(image, x, y, size, size, face, W_LIGHT, W_SHADE);
            fill(image, x + 2, y + 1, size - 4, winner ? 6 : 4, metals[place]);
            int width = 6 * names[place].length() - 1;
            
            
            
            
            
            
            int well = 36;
            recessIn(image, x + (size - well) / 2, y + (size - well) / 2, well, well,
                    W_WELL, W_WELL_EDGE, lighten(W_WELL, 24));
            
            
            word(image, names[place], x + (size - width) / 2, y + size - 8, 1,
                    winner ? W_INK : W_DIM);
        }
        
        recessIn(image, WELL_X, slotY(27), SLOT_PITCH * 9, SLOT_PITCH,
                darken(W_PANEL, 14), W_WELL_EDGE, lighten(W_PANEL, 10));
        for (int seat = 28; seat <= 34; seat++) {
            fill(image, slotX(seat), slotY(seat) + 2, 1, SLOT_PITCH - 4, darken(W_PANEL, 26));
        }
        fill(image, slotX(35), slotY(35) + 2, 1, SLOT_PITCH - 4, darken(W_PANEL, 26));
        
        recessIn(image, WELL_X, slotY(45), SLOT_PITCH * 9, SLOT_PITCH,
                darken(W_PANEL, 6), W_WELL_EDGE, lighten(W_PANEL, 12));
        word(image, "YOU", slotX(50) + 2, slotY(45) + 6, 1, W_DIM);
        raisedIn(image, slotX(45), slotY(45), SLOT_SIZE, SLOT_SIZE,
                darken(W_PANEL, 4), W_LIGHT, W_SHADE);
        raisedIn(image, slotX(49), slotY(49), SLOT_SIZE, SLOT_SIZE, W_PANEL, W_LIGHT, W_SHADE);
        return png(image);
    }

    






    static byte[] watchPanel() {
        BufferedImage image = worldsBase();
        worldsHeader(image, "LIVE NOW");
        for (int row = 0; row < 4; row++) {
            int y = slotY(9 + row * 9);
            raisedIn(image, WELL_X, y, SLOT_PITCH * 9, SLOT_PITCH, W_PANEL, W_LIGHT, W_SHADE);
            
            
            fill(image, WELL_X + SLOT_PITCH * 9 - 3, y + 1, 2, SLOT_PITCH - 2,
                    W_ACCENT[row % W_ACCENT.length]);
            
            
            recessIn(image, WELL_X + 1, y + 1, SLOT_SIZE - 2, SLOT_SIZE - 2,
                    W_WELL, W_WELL_EDGE, lighten(W_WELL, 24));
            for (int tick = 0; tick < 3; tick++) {                  
                fill(image, WELL_X + SLOT_PITCH + 6, y + 6 + tick * 2, SLOT_PITCH * 5,
                        1, darken(W_PANEL, 18));
            }
        }
        raisedIn(image, slotX(45), slotY(45), SLOT_SIZE, SLOT_SIZE,
                darken(W_PANEL, 4), W_LIGHT, W_SHADE);
        return png(image);
    }

    






    static byte[] profilePanel() {
        BufferedImage image = worldsBase();
        worldsHeader(image, "YOUR RECORD");
        int x = WELL_X + 3 * SLOT_PITCH;
        int y = WELL_Y_CONTAINER;
        int size = 3 * SLOT_PITCH;
        raisedIn(image, x, y, size, size, lighten(W_PANEL, 6), W_LIGHT, W_SHADE);
        fill(image, x + 2, y + 1, size - 4, 4, W_ACCENT[1]);
        int well = 36;
        recessIn(image, x + (size - well) / 2, y + (size - well) / 2, well, well,
                W_WELL, W_WELL_EDGE, lighten(W_WELL, 24));
        
        String[] labels = {"RECORD", "STREAK", "TOP KIT"};
        recessIn(image, WELL_X, slotY(27), SLOT_PITCH * 9, SLOT_PITCH,
                darken(W_PANEL, 14), W_WELL_EDGE, lighten(W_PANEL, 10));
        for (int bay = 0; bay < 3; bay++) {
            int bx = WELL_X + bay * 3 * SLOT_PITCH;
            if (bay > 0) {
                fill(image, bx, slotY(27) + 2, 1, SLOT_PITCH - 4, darken(W_PANEL, 26));
            }
            int width = 6 * labels[bay].length() - 1;
            word(image, labels[bay], bx + (SLOT_PITCH * 3 - width) / 2, slotY(36) + 5, 1, W_DIM);
        }
        raisedIn(image, slotX(45), slotY(45), SLOT_SIZE, SLOT_SIZE,
                darken(W_PANEL, 4), W_LIGHT, W_SHADE);
        return png(image);
    }

    static byte[] worldsPanel(int style) {
        return switch (style) {
            case 1 -> png(listPanel());
            case 2 -> png(carouselPanel());
            case 3 -> png(consolePanel());
            case 4 -> png(radarPanel());
            default -> png(boardPanel());
        };
    }

    





    private static BufferedImage boardPanel() {
        BufferedImage image = worldsBase();
        worldsHeader(image, "WHERE TO");
        for (int card = 0; card < 3; card++) {
            int x = WELL_X + card * 3 * SLOT_PITCH;
            int y = slotY(9);
            int size = 3 * SLOT_PITCH;
            int accent = W_ACCENT[card];

            raisedIn(image, x, y, size, size, W_PANEL, W_LIGHT, W_SHADE);
            
            
            
            
            fill(image, x + 2, y + 1, size - 4, 9, accent);
            String name = WORLD_NAMES[card];
            int width = 6 * name.length() - 1;
            word(image, name, x + (size - width) / 2, y + 2, 1, 0xFF12161c);

            int well = 34;
            recessIn(image, x + (size - well) / 2, y + (size - well) / 2, well, well,
                    W_WELL, W_WELL_EDGE, lighten(W_WELL, 24));
        }
        
        for (int card = 0; card < 3; card++) {
            int x = WELL_X + card * 3 * SLOT_PITCH;
            recessIn(image, x, slotY(36), SLOT_PITCH * 2, SLOT_PITCH,
                    darken(W_PANEL, 12), W_WELL_EDGE, lighten(W_PANEL, 14));
            word(image, "ON", x + SLOT_PITCH * 2 + 3, slotY(36) + 6, 1, W_DIM);
        }
        raisedIn(image, slotX(45), slotY(45), SLOT_SIZE, SLOT_SIZE, W_PANEL, W_LIGHT, W_SHADE);
        return image;
    }

    





    private static BufferedImage listPanel() {
        BufferedImage image = worldsBase();
        worldsHeader(image, "SERVERS");
        for (int row = 0; row < 3; row++) {
            int y = slotY(9 + row * 9);
            int accent = W_ACCENT[row];
            raisedIn(image, WELL_X, y, SLOT_PITCH * 9, SLOT_PITCH, W_PANEL, W_LIGHT, W_SHADE);
            fill(image, WELL_X + 1, y + 1, 2, SLOT_PITCH - 2, accent);
            
            recessIn(image, WELL_X + 1, y + 1, SLOT_SIZE - 2, SLOT_SIZE - 2,
                    W_WELL, W_WELL_EDGE, lighten(W_WELL, 24));
            recessIn(image, WELL_X + SLOT_PITCH * 7, y + 1, SLOT_PITCH * 2 - 2, SLOT_PITCH - 2,
                    darken(W_PANEL, 12), W_WELL_EDGE, lighten(W_PANEL, 14));
            String name = WORLD_NAMES[row];
            word(image, name, WELL_X + SLOT_PITCH + 8, y + 6, 1, W_INK);
        }
        recessIn(image, WELL_X, slotY(36), SLOT_PITCH * 9, SLOT_PITCH,
                darken(W_PANEL, 14), W_WELL_EDGE, lighten(W_PANEL, 10));
        word(image, "PLAYERS ON EACH", WELL_X + 6, slotY(36) + 6, 1, W_DIM);
        raisedIn(image, slotX(45), slotY(45), SLOT_SIZE, SLOT_SIZE, W_PANEL, W_LIGHT, W_SHADE);
        return image;
    }

    





    private static BufferedImage carouselPanel() {
        BufferedImage image = worldsBase();
        worldsHeader(image, "ONE AT A TIME");
        int heroX = WELL_X + 3 * SLOT_PITCH;
        int heroY = slotY(9);
        raisedIn(image, heroX, heroY, SLOT_PITCH * 3, SLOT_PITCH * 3, W_PANEL, W_LIGHT, W_SHADE);
        recessIn(image, heroX + 5, heroY + 5, SLOT_PITCH * 3 - 10, SLOT_PITCH * 3 - 10,
                W_WELL, W_WELL_EDGE, lighten(W_WELL, 26));
        
        for (int side = 0; side < 2; side++) {
            int x = side == 0 ? slotX(19) : slotX(25);
            raisedIn(image, x, slotY(19), SLOT_SIZE, SLOT_SIZE, darken(W_PANEL, 6), W_LIGHT, W_SHADE);
        }
        
        for (int dot = 0; dot < 3; dot++) {
            int cx = WIDTH / 2 - 8 + dot * 8;
            fill(image, cx, heroY + SLOT_PITCH * 3 + 3, 4, 4, dot == 0 ? W_INK : darken(W_PANEL, 18));
        }
        raisedIn(image, WELL_X, slotY(36), SLOT_PITCH * 9, SLOT_PITCH,
                0xFF2f5a3a, 0xFF4a7d55, 0xFF1b3624);
        word(image, "GO", WIDTH / 2 - 5, slotY(36) + 6, 1, 0xFFdcf2e2);
        raisedIn(image, slotX(45), slotY(45), SLOT_SIZE, SLOT_SIZE, W_PANEL, W_LIGHT, W_SHADE);
        return image;
    }

    





    private static BufferedImage consolePanel() {
        BufferedImage image = worldsBase();
        worldsHeader(image, "HYPERGRAVEL NET");
        
        int screenY = slotY(9);
        recessIn(image, WELL_X, screenY, SLOT_PITCH * 9, SLOT_PITCH * 3,
                0xFF10161a, 0xFF060a0c, 0xFF243038);
        for (int y = screenY + 2; y < screenY + SLOT_PITCH * 3 - 1; y += 3) {
            fill(image, WELL_X + 2, y, SLOT_PITCH * 9 - 4, 1, 0xFF141c21);
        }
        for (int line = 0; line < 3; line++) {
            int y = screenY + 2 + line * SLOT_PITCH;
            word(image, String.valueOf((char) ('1' + line)), WELL_X + 5, y + 4, 1, W_ACCENT[line]);
            fill(image, WELL_X + 12, y + 4, 1, 7, darken(W_ACCENT[line], 40));
            word(image, WORLD_NAMES[line], WELL_X + 17, y + 4, 1, 0xFF9fd8b4);
        }
        recessIn(image, WELL_X, slotY(36), SLOT_PITCH * 9, SLOT_PITCH,
                0xFF10161a, 0xFF060a0c, 0xFF243038);
        word(image, "PRESS A KEY BELOW", WELL_X + 6, slotY(36) + 6, 1, 0xFF5f7f6e);
        
        for (int key = 0; key < 3; key++) {
            int x = slotX(46 + key * 3);
            raisedIn(image, x, slotY(46), SLOT_PITCH * 2, SLOT_SIZE,
                    darken(W_PANEL, 4), W_LIGHT, W_SHADE);
            fill(image, x + 2, slotY(46) + 2, SLOT_PITCH * 2 - 4, 2, W_ACCENT[key]);
        }
        raisedIn(image, slotX(45), slotY(45), SLOT_SIZE, SLOT_SIZE, W_PANEL, W_LIGHT, W_SHADE);
        return image;
    }

    






    private static BufferedImage radarPanel() {
        BufferedImage image = worldsBase();
        worldsHeader(image, "THE NETWORK");
        int centreX = slotX(22) + SLOT_SIZE / 2;
        int centreY = slotY(22) + SLOT_SIZE / 2;
        for (int ring = 1; ring <= 3; ring++) {
            int radius = ring * 17;
            for (int step = 0; step < 360; step += 2) {
                int x = centreX + (int) Math.round(Math.cos(Math.toRadians(step)) * radius);
                int y = centreY + (int) Math.round(Math.sin(Math.toRadians(step)) * radius * 0.55);
                if (x > 5 && x < WIDTH - 6 && y > 18 && y < slotY(45) - 2) {
                    set(image, x, y, lighten(W_PANEL, 22));
                }
            }
        }
        
        int[] nodes = {19, 25, 40};
        int[] order = {0, 2, 1};
        for (int index = 0; index < 3; index++) {
            int node = nodes[index];
            int accent = W_ACCENT[order[index]];
            int nx = slotX(node) + SLOT_SIZE / 2;
            int ny = slotY(node) + SLOT_SIZE / 2;
            int steps = Math.max(Math.abs(nx - centreX), Math.abs(ny - centreY));
            for (int step = 0; step <= steps; step++) {
                set(image, centreX + (nx - centreX) * step / steps,
                        centreY + (ny - centreY) * step / steps,
                        step % 5 == 4 ? accent : lighten(W_PANEL, 26));
            }
        }
        accentWell(image, slotX(22) - 1, slotY(22) - 1, SLOT_SIZE + 2, lighten(W_PANEL, 34));
        for (int index = 0; index < 3; index++) {
            int node = nodes[index];
            int accent = W_ACCENT[order[index]];
            accentWell(image, slotX(node) - 1, slotY(node) - 1, SLOT_SIZE + 2, accent);
            String name = WORLD_NAMES[order[index]];
            int width = 6 * name.length() - 1;
            int wx = Math.max(5, Math.min(WIDTH - 6 - width,
                    slotX(node) + (SLOT_SIZE - width) / 2));
            int wy = slotY(node) - 11;
            
            
            
            fill(image, wx - 4, wy - 2, width + 8, 11, darken(W_PANEL, 10));
            word(image, name, wx, wy, 1, W_DIM);
        }
        raisedIn(image, slotX(45), slotY(45), SLOT_SIZE, SLOT_SIZE, W_PANEL, W_LIGHT, W_SHADE);
        return image;
    }

    private static void raisedIn(BufferedImage image, int x, int y, int width, int height,
                                 int face, int light, int shade) {
        fill(image, x, y, width, height, OUTLINE);
        fill(image, x + 1, y + 1, width - 2, height - 2, face);
        fill(image, x + 1, y + 1, width - 2, 1, light);
        fill(image, x + 1, y + 1, 1, height - 2, light);
        fill(image, x + 1, y + height - 2, width - 2, 1, shade);
        fill(image, x + width - 2, y + 1, 1, height - 2, shade);
    }

    private static void recessIn(BufferedImage image, int x, int y, int width, int height,
                                 int fill, int dark, int light) {
        fill(image, x, y, width, height, fill);
        fill(image, x, y, width - 1, 1, dark);
        fill(image, x, y, 1, height - 1, dark);
        fill(image, x + 1, y + height - 1, width - 1, 1, light);
        fill(image, x + width - 1, y + 1, 1, height - 1, light);
    }

    
    private static void cellIn(BufferedImage image, int x, int y, int fill, int edge) {
        roundRect(image, x + 1, y + 1, SLOT_SIZE - 2, SLOT_SIZE - 2, 3, fill, edge);
    }

    private static void slotIn(BufferedImage image, int x, int y, int fill, int dark, int light) {
        recessIn(image, x, y, SLOT_SIZE, SLOT_SIZE, fill, dark, light);
    }

    
    private static int darken(int colour, int amount) {
        int red = Math.max(0, (colour >> 16 & 0xFF) - amount);
        int green = Math.max(0, (colour >> 8 & 0xFF) - amount);
        int blue = Math.max(0, (colour & 0xFF) - amount);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    
    private static int lighten(int colour, int amount) {
        int red = Math.min(255, (colour >> 16 & 0xFF) + amount);
        int green = Math.min(255, (colour >> 8 & 0xFF) + amount);
        int blue = Math.min(255, (colour & 0xFF) + amount);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static void slot(BufferedImage image, int x, int y) {
        fill(image, x, y, SLOT_SIZE, SLOT_SIZE, SLOT);
        fill(image, x, y, SLOT_SIZE - 1, 1, SLOT_SHADE);
        fill(image, x, y, 1, SLOT_SIZE - 1, SLOT_SHADE);
        fill(image, x + 1, y + SLOT_SIZE - 1, SLOT_SIZE - 1, 1, LIGHT);
        fill(image, x + SLOT_SIZE - 1, y + 1, 1, SLOT_SIZE - 1, LIGHT);
    }

    private static void button(BufferedImage image, int x, int y) {
        raised(image, x, y, SLOT_SIZE, SLOT_SIZE);
    }

    private static void raised(BufferedImage image, int x, int y, int width, int height) {
        fill(image, x, y, width, height, OUTLINE);
        fill(image, x + 1, y + 1, width - 2, height - 2, PANEL);
        fill(image, x + 1, y + 1, width - 2, 1, LIGHT);
        fill(image, x + 1, y + 1, 1, height - 2, LIGHT);
        fill(image, x + 1, y + height - 2, width - 2, 1, SHADE);
        fill(image, x + width - 2, y + 1, 1, height - 2, SHADE);
    }

    private static void recess(BufferedImage image, int x, int y, int width, int height) {
        fill(image, x, y, width, height, SLOT);
        fill(image, x, y, width - 1, 1, SLOT_SHADE);
        fill(image, x, y, 1, height - 1, SLOT_SHADE);
        fill(image, x + 1, y + height - 1, width - 1, 1, LIGHT);
        fill(image, x + width - 1, y + 1, 1, height - 1, LIGHT);
    }

    
    static final int LIGHT_INK = 0xFFdfe3ee;

    static byte[] arrow(boolean left) {
        return arrow(left, ARROW_INK);
    }

    static byte[] arrow(boolean left, int ink) {
        arrowInk = ink;
        try {
            return arrowArt(left);
        } finally {
            arrowInk = ARROW_INK;
        }
    }

    private static int arrowInk = ARROW_INK;

    private static byte[] arrowArt(boolean left) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);

        for (int x = 2; x <= 8; x++) {
            for (int y = 6; y <= 9; y++) {
                put(image, left, x, y);
            }
        }

        for (int x = 8; x <= 13; x++) {
            int half = 13 - x + 1;
            for (int y = 8 - half; y <= 7 + half; y++) {
                put(image, left, x, y);
            }
        }
        return png(image);
    }

    static byte[] exit(int ink) {
        exitInk = ink;
        try {
            return exit();
        } finally {
            exitInk = EXIT_INK;
        }
    }

    private static int exitInk = EXIT_INK;

    static byte[] exit() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double dx = x - 7.5;
                double dy = y - 7.5;
                if (Math.sqrt(dx * dx + dy * dy) > 7.4) {
                    continue;
                }
                double toMain = Math.abs(dx - dy) / Math.sqrt(2);
                double toAnti = Math.abs(dx + dy) / Math.sqrt(2);
                if (Math.min(toMain, toAnti) <= 2.1) {
                    image.setRGB(x, y, exitInk);
                }
            }
        }
        return png(image);
    }

    static byte[] eye() {
        return stencil("WIBLD", new int[] {EYE_WHITE, EYE_IRIS, 0xFF101018, 0xFFf6f8ff, 0xFF2b2b33},
                "................",
                "................",
                "................",
                "................",
                "....DDDDDDDD....",
                "..DDWWWWWWWWDD..",
                ".DWWWWIIIIWWWWD.",
                ".DWWWIILBIIWWWD.",
                ".DWWWIIBBIIWWWD.",
                ".DWWWWIIIIWWWWD.",
                "..DDWWWWWWWWDD..",
                "....DDDDDDDD....",
                "................",
                "................",
                "................",
                "................");
    }

    static byte[] toggle(boolean on) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        int ink = on ? ON_INK : OFF_INK;
        int shade = on ? ON_SHADE : OFF_SHADE;
        double cx = 7.5;
        double cy = 7.5;
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                double dx = x - cx;
                double dy = y - cy;
                double distance = Math.sqrt(dx * dx + dy * dy);
                if (distance < 4.6 || distance > 7.2) {
                    continue;
                }

                if (dy > 2.5 && Math.abs(dx) < 3.0) {
                    continue;
                }
                set(image, x, y, distance > 6.4 ? shade : ink);
            }
        }

        for (int i = 0; i <= 4; i++) {
            int px = (int) Math.round(cx + (on ? i : -i) * 0.85);
            int py = (int) Math.round(cy + (on ? -i : i) * 0.85);
            set(image, px, py, ink);
            set(image, px + 1, py, ink);
        }
        return png(image);
    }

    private static byte[] stencil(String keys, int[] colours, String... rows) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < rows.length; y++) {
            String row = rows[y];
            for (int x = 0; x < row.length(); x++) {
                int index = keys.indexOf(row.charAt(x));
                if (index >= 0) {
                    set(image, x, y, colours[index]);
                }
            }
        }
        return png(image);
    }

    static byte[] swords() {
        return stencil("WwGH", new int[] {BLADE, BLADE_SHADE, GUARD, HILT},
                ".w............w.",
                ".WW..........WW.",
                "..WWw......wWW..",
                "...WWw....wWW...",
                "....WWw..wWW....",
                ".....WWwwWW.....",
                "......WWWW......",
                "......WWWW......",
                ".....WWwwWW.....",
                "....WWw..wWW....",
                "...GGGG..GGGG...",
                "...HH......HH...",
                "..HH........HH..",
                ".HH..........HH.",
                "................",
                "................");
    }

    







    private static final String[] WORLD = {
        "................................",
        ".................##..######.....",
        "...###...........####..######...",
        "..#####..........#####..#####...",
        "..#####.........######...###....",
        "...####.........######..........",
        "....##..........#####...........",
        ".....#..........####............",
        "........##......####........##..",
        ".......####.....###.........###.",
        ".......####.....###.........##..",
        "........###......##.............",
        "........##.......#..............",
        ".........#......................",
        "................................",
        "................................",
    };

    private static boolean landAt(double lon, double lat) {
        int column = (int) Math.floor((lon + Math.PI) / (2 * Math.PI) * WORLD[0].length());
        column = Math.floorMod(column, WORLD[0].length());
        int row = (int) Math.floor((Math.PI / 2 - lat) / Math.PI * WORLD.length);
        row = Math.min(WORLD.length - 1, Math.max(0, row));
        return WORLD[row].charAt(column) == '#';
    }

    













    static byte[] earthFrames(int frames) {
        BufferedImage strip = new BufferedImage(16, 16 * frames, BufferedImage.TYPE_INT_ARGB);
        double lightX = -0.50;
        double lightY = -0.50;
        double lightZ = 0.71;
        for (int frame = 0; frame < frames; frame++) {
            double spin = 2 * Math.PI * frame / frames;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    double nx = (x + 0.5 - 8) / 7.0;
                    double ny = (y + 0.5 - 8) / 7.0;
                    double radius = nx * nx + ny * ny;
                    if (radius > 1.0) {
                        continue;
                    }
                    int colour;
                    if (radius > 0.72) {
                        colour = EARTH_EDGE;
                    } else {
                        double z = Math.sqrt(1.0 - radius);
                        double light = nx * lightX + ny * lightY + z * lightZ;
                        double lat = Math.asin(-ny);
                        double lon = Math.atan2(nx, z) + spin;
                        
                        
                        
                        
                        if (landAt(lon, lat)) {
                            colour = light > 0.52 ? EARTH_LAND : EARTH_LAND_SHADE;
                        } else if (light > 0.90) {
                            colour = EARTH_SHEEN;
                        } else {
                            colour = light > 0.50 ? EARTH_SEA : EARTH_SEA_SHADE;
                        }
                    }
                    strip.setRGB(x, y + frame * 16, colour);
                }
            }
        }
        return png(strip);
    }

    











    static byte[] earth() {
        return stencil("EOoGgH", new int[] {EARTH_EDGE, EARTH_SEA, EARTH_SEA_SHADE,
                EARTH_LAND, EARTH_LAND_SHADE, EARTH_SHEEN},
                "................",
                ".....EEEEEE.....",
                "...EHHOOOOOOE...",
                "..EHGGGOOGGOOE..",
                "..EOGGOOGGGGOE..",
                ".EOOGGOOGGGGOoE.",
                ".EOOOOOOGGGGooE.",
                ".EOGGOOOOGGgooE.",
                ".EOGGGOOOGgoooE.",
                ".EOOGGGOOgooooE.",
                ".EOOGGOOooooooE.",
                "..EOGGOooooooE..",
                "..EOOOoooooooE..",
                "...EOoooooooE...",
                ".....EooooE.....",
                "................");
    }

    static byte[] trophy() {
        return stencil("LGD", new int[] {GOLD_LIGHT, GOLD, GOLD_SHADE},
                "................",
                "..DDDDDDDDDDDD..",
                "..LLGGGGGGGGGD..",
                ".D.LGGGGGGGGG.D.",
                "D..LGGGGGGGGG..D",
                "D..LGGGGGGGGG..D",
                ".D..LGGGGGGG.DD.",
                "..DD.LGGGGG.DD..",
                "......GGGG......",
                ".......GG.......",
                ".......GG.......",
                "......DGGD......",
                "....DDGGGGDD....",
                "...DDDDDDDDDD...",
                "................",
                "................");
    }

    static byte[] cog() {
        return stencil("TKk", new int[] {TRACK, KNOB, KNOB_SHADE},
                "................",
                "................",
                "....Kk..........",
                ".TTTKkTTTTTTTT..",
                "....Kk..........",
                "................",
                "................",
                ".........Kk.....",
                ".TTTTTTTTKkTTTT.",
                ".........Kk.....",
                "................",
                "................",
                "......Kk........",
                ".TTTTTKkTTTTTTT.",
                "......Kk........",
                "................");
    }

    private static final String[] DIGITS = {
        "111101101101111", "010110010010111", "111001111100111", "111001111001111",
        "101101111001001", "111100111001111", "111100111101111", "111001001001001",
        "111101111101111", "111101111001111",
    };

    static byte[] digit(int value) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        String bits = DIGITS[Math.floorMod(value, 10)];
        for (int row = 0; row < 5; row++) {
            for (int column = 0; column < 3; column++) {
                if (bits.charAt(row * 3 + column) != '1') {
                    continue;
                }
                for (int dy = 0; dy < 2; dy++) {
                    for (int dx = 0; dx < 2; dx++) {
                        set(image, 5 + column * 2 + dx, 3 + row * 2 + dy, EXIT_INK);
                    }
                }
            }
        }
        return png(image);
    }

    static byte[] play() {
        return stencil("D", new int[] {EXIT_INK},
                "................",
                "................",
                "................",
                "......DD........",
                ".....DDDD.......",
                ".....DDDDD......",
                ".....DDDDDD.....",
                ".....DDDDDDD....",
                ".....DDDDDDD....",
                ".....DDDDDD.....",
                ".....DDDDD......",
                ".....DDDD.......",
                "......DD........",
                "................",
                "................",
                "................");
    }

    static byte[] note() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        fill(image, 9, 2, 2, 9, NOTE);
        fill(image, 11, 2, 2, 4, NOTE);
        fill(image, 13, 4, 1, 3, NOTE);
        for (int x = 5; x <= 10; x++) {
            for (int y = 10; y <= 13; y++) {
                boolean corner = (x == 5 || x == 10) && (y == 10 || y == 13);
                if (!corner) {
                    set(image, x, y, x < 7 ? NOTE_SHADE : NOTE);
                }
            }
        }
        return png(image);
    }

    static byte[] chart() {
        return stencil("LA", new int[] {GOLD, AXIS},
                "................",
                "................",
                "..A.............",
                "..A........LLL..",
                "..A.........LL..",
                "..A........LLL..",
                "..A.......LL....",
                "..A......LL.....",
                "..A.....LL......",
                "..A..L.LL.......",
                "..A.LLLL........",
                "..ALL.L.........",
                "..AL............",
                "..AAAAAAAAAAAAAA",
                "................",
                "................");
    }

    




    static BufferedImage mapTile() {
        final int SIZE = 32;
        final int PAPER = 0xFFe6ddc9;
        final int GRID = 0xFFd6cbb2;
        final int BLOCK = 0xFFd0c4a6;
        final int ROAD_LINE = 0xFFc0b498;
        BufferedImage tile = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                int colour = PAPER;
                if (x % 8 == 0 || y % 8 == 0) {
                    colour = GRID;
                }
                
                
                if (x == 12 || x == 13 || y == 20 || y == 21) {
                    colour = ROAD_LINE;
                } else if ((x > 2 && x < 11 && y > 3 && y < 18)
                        || (x > 15 && x < 29 && y > 2 && y < 12)
                        || (x > 17 && x < 27 && y > 23 && y < 30)) {
                    colour = BLOCK;
                }
                tile.setRGB(x, y, colour);
            }
        }
        return tile;
    }











    







    static byte[] phoneItem() {
        return stencil("IBSnH123456789", new int[] {
                        0xFF0a1420, 0xFF20293b, APP_BODY, 0xFF0a1420, 0xFFdfe6f5,
                        0xFF4a86e8, 0xFF7c6bd6, 0xFFf09a33,
                        0xFF6b7a8f, 0xFFa2705a, 0xFF4caf50,
                        0xFFc8a13a, 0xFFe05c4a, 0xFF3fb6c8},
                "...IIIIIIIIII...",
                "...IBBBnnBBBI...",
                "...ISSSSSSSSI...",
                "...I11S22S33I...",
                "...I11S22S33I...",
                "...ISSSSSSSSI...",
                "...I44S55S66I...",
                "...I44S55S66I...",
                "...ISSSSSSSSI...",
                "...I77S88S99I...",
                "...I77S88S99I...",
                "...ISSSSSSSSI...",
                "...ISSSSSSSSI...",
                "...IBBHHHHBBI...",
                "...IBBBBBBBBI...",
                "...IIIIIIIIII...");
    }

    
    
    
    
    

    private static final int APP_FRAME = 0xFF11395f;
    private static final int APP_BODY = 0xFF2f86d8;
    private static final int APP_BODY_LIGHT = 0xFF62b0f2;
    private static final int APP_BODY_DARK = 0xFF1e63a8;
    private static final int APP_CELL = 0xFF1b5b9c;
    private static final int APP_CELL_EDGE = 0xFF14456f;
    private static final int APP_HEADER = 0xFF4c9fe8;
    private static final int APP_TEXT = 0xFFFFFFFF;

    






    private static final java.util.Map<Character, String[]> LETTERS = new java.util.HashMap<>();
    static {
        LETTERS.put('A', new String[] {".111.", "1...1", "1...1", "11111", "1...1", "1...1", "1...1"});
        LETTERS.put('B', new String[] {"1111.", "1...1", "1...1", "1111.", "1...1", "1...1", "1111."});
        LETTERS.put('C', new String[] {".1111", "1....", "1....", "1....", "1....", "1....", ".1111"});
        LETTERS.put('D', new String[] {"1111.", "1...1", "1...1", "1...1", "1...1", "1...1", "1111."});
        LETTERS.put('E', new String[] {"11111", "1....", "1....", "1111.", "1....", "1....", "11111"});
        LETTERS.put('F', new String[] {"11111", "1....", "1....", "1111.", "1....", "1....", "1...."});
        LETTERS.put('G', new String[] {".1111", "1....", "1....", "1.111", "1...1", "1...1", ".111."});
        LETTERS.put('H', new String[] {"1...1", "1...1", "1...1", "11111", "1...1", "1...1", "1...1"});
        LETTERS.put('I', new String[] {"11111", "..1..", "..1..", "..1..", "..1..", "..1..", "11111"});
        LETTERS.put('J', new String[] {"....1", "....1", "....1", "....1", "1...1", "1...1", ".111."});
        LETTERS.put('K', new String[] {"1...1", "1..1.", "1.1..", "11...", "1.1..", "1..1.", "1...1"});
        LETTERS.put('L', new String[] {"1....", "1....", "1....", "1....", "1....", "1....", "11111"});
        LETTERS.put('M', new String[] {"1...1", "11.11", "1.1.1", "1...1", "1...1", "1...1", "1...1"});
        LETTERS.put('N', new String[] {"1...1", "11..1", "1.1.1", "1..11", "1...1", "1...1", "1...1"});
        LETTERS.put('O', new String[] {".111.", "1...1", "1...1", "1...1", "1...1", "1...1", ".111."});
        LETTERS.put('P', new String[] {"1111.", "1...1", "1...1", "1111.", "1....", "1....", "1...."});
        LETTERS.put('Q', new String[] {".111.", "1...1", "1...1", "1...1", "1.1.1", "1..1.", ".11.1"});
        LETTERS.put('R', new String[] {"1111.", "1...1", "1...1", "1111.", "1.1..", "1..1.", "1...1"});
        LETTERS.put('S', new String[] {".1111", "1....", "1....", ".111.", "....1", "....1", "1111."});
        LETTERS.put('T', new String[] {"11111", "..1..", "..1..", "..1..", "..1..", "..1..", "..1.."});
        LETTERS.put('U', new String[] {"1...1", "1...1", "1...1", "1...1", "1...1", "1...1", ".111."});
        LETTERS.put('V', new String[] {"1...1", "1...1", "1...1", "1...1", "1...1", ".1.1.", "..1.."});
        LETTERS.put('W', new String[] {"1...1", "1...1", "1...1", "1...1", "1.1.1", "11.11", "1...1"});
        LETTERS.put('X', new String[] {"1...1", "1...1", ".1.1.", "..1..", ".1.1.", "1...1", "1...1"});
        LETTERS.put('Y', new String[] {"1...1", "1...1", ".1.1.", "..1..", "..1..", "..1..", "..1.."});
        LETTERS.put('Z', new String[] {"11111", "....1", "...1.", "..1..", ".1...", "1....", "11111"});
    }

    private static int wordWidth(String word, int scale) {
        return word.length() * (5 * scale + scale) - scale;
    }

    private static void word(BufferedImage image, String text, int x, int y, int scale, int colour) {
        int cursor = x;
        for (char c : text.toCharArray()) {
            String[] glyph = LETTERS.get(c);
            if (glyph != null) {
                for (int row = 0; row < glyph.length; row++) {
                    for (int column = 0; column < 5; column++) {
                        if (glyph[row].charAt(column) != '1') continue;
                        fill(image, cursor + column * scale, y + row * scale, scale, scale, colour);
                    }
                }
            }
            cursor += 5 * scale + scale;
        }
    }

    
    private static void cell(BufferedImage image, int x, int y) {
        roundRect(image, x, y, SLOT_SIZE, SLOT_SIZE, 2, APP_CELL, APP_CELL_EDGE);
    }

    
    static final int[] APP_ROWS = {1, 3};
    static final int[] APP_COLUMNS = {1, 3, 5, 7};
    
    static final int APP_TILE = 34;

    






    private static void appTile(BufferedImage image, int x, int y) {
        roundRect(image, x + 1, y + 2, APP_TILE, APP_TILE, 6, APP_BODY_DARK, APP_BODY_DARK);
        roundRect(image, x, y, APP_TILE, APP_TILE, 6, APP_CELL, APP_CELL_EDGE);
        
        fill(image, x + 6, y + 1, APP_TILE - 12, 1, APP_HEADER);
    }

    










    static byte[] appScreen(int rows) {
        final int H = height(rows);
        BufferedImage image = new BufferedImage(WIDTH, H, BufferedImage.TYPE_INT_ARGB);

        roundRect(image, 0, 0, WIDTH, H, 5, APP_BODY, APP_FRAME);
        cutCorners(image, 5);
        fill(image, 2, 2, WIDTH - 4, 1, APP_BODY_LIGHT);

        
        roundRect(image, 5, 3, WIDTH - 10, 13, 3, APP_HEADER, APP_BODY_DARK);
        word(image, "PLACES", (WIDTH - wordWidth("PLACES", 1)) / 2, 6, 1, APP_TEXT);

        for (int row : APP_ROWS) {
            for (int column : APP_COLUMNS) {
                appTile(image,
                        WELL_X + column * SLOT_PITCH + SLOT_SIZE / 2 - APP_TILE / 2,
                        WELL_Y_CONTAINER + row * SLOT_PITCH + SLOT_SIZE / 2 - APP_TILE / 2);
            }
        }

        
        int footerY = WELL_Y_CONTAINER + 5 * SLOT_PITCH;
        roundRect(image, 5, footerY - 2, WIDTH - 10, SLOT_SIZE + 3, 3, APP_BODY_DARK, APP_FRAME);
        for (int column : new int[] {1, 4, 7}) {
            roundRect(image, WELL_X + column * SLOT_PITCH, footerY, SLOT_SIZE, SLOT_SIZE, 2,
                    APP_HEADER, APP_BODY_DARK);
        }

        
        
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                cell(image, WELL_X + column * SLOT_PITCH, inventoryWellY(rows) + row * SLOT_PITCH);
            }
        }
        for (int column = 0; column < 9; column++) {
            cell(image, WELL_X + column * SLOT_PITCH, hotbarWellY(rows));
        }
        return png(image);
    }

    







    static byte[] micGlyph() {
        final int W = 0xFFf2f4fb, D = 0xFF9aa3bb, I = 0xFF11141d;
        return stencil("WDI", new int[] {W, D, I},
                "......IIII......",
                ".....IWWWWI.....",
                ".....IWWWWI.....",
                ".....IWWWWI.....",
                ".....IWWWWI.....",
                ".....IWWWWI.....",
                ".....IWWDDI.....",
                ".....IWWDDI.....",
                "......IIII......",
                "...I........I...",
                "...ID......DI...",
                "...IDD....DDI...",
                "....IDDDDDDI....",
                "......IWWI......",
                "......IWWI......",
                "....IIIIIIII....");
    }


    






    static byte[] micMutedGlyph() {
        final int W = 0xFFc8ccd8, D = 0xFF7f8598, I = 0xFF11141d, S = 0xFFf2f4fb;
        return stencil("WDIS", new int[] {W, D, I, S},
                "SSI...IIII......",
                "ISSI.IWWWWI.....",
                ".ISSIIWWWWI.....",
                "..ISSIWWWWI.....",
                "...ISSWWWWI.....",
                "....ISSWWWI.....",
                ".....ISSDDI.....",
                ".....IWSSDI.....",
                "......IISSI.....",
                "...I....ISSII...",
                "...ID....ISSI...",
                "...IDD....DSSI..",
                "....IDDDDDDISSI.",
                "......IWWI..ISSI",
                "......IWWI...ISS",
                "....IIIIIIII..IS");
    }

    
    
    
    
    

    
    private static void themedInventory(BufferedImage image, int rows, int fill, int edge) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                roundRect(image, WELL_X + column * SLOT_PITCH, inventoryWellY(rows) + row * SLOT_PITCH,
                        SLOT_SIZE, SLOT_SIZE, 2, fill, edge);
            }
        }
        for (int column = 0; column < 9; column++) {
            roundRect(image, WELL_X + column * SLOT_PITCH, hotbarWellY(rows), SLOT_SIZE, SLOT_SIZE, 2, fill, edge);
        }
    }

    private static int wellX(int column) { return WELL_X + column * SLOT_PITCH; }

    private static int wellY(int row) { return WELL_Y_CONTAINER + row * SLOT_PITCH; }

    






    static byte[] atm(int rows) {
        final int H = height(rows);
        final int CASE = 0xFF39404e, CASE_DARK = 0xFF161a22, CASE_LIGHT = 0xFF5b6478;
        final int SCREEN = 0xFF0d2b1c, SCREEN_LINE = 0xFF12452a, KEY = 0xFFc3c8d4;
        BufferedImage image = new BufferedImage(WIDTH, H, BufferedImage.TYPE_INT_ARGB);

        roundRect(image, 0, 0, WIDTH, H, 5, CASE, CASE_DARK);
        fill(image, 2, 2, WIDTH - 4, 1, CASE_LIGHT);
        word(image, "CASH MACHINE", (WIDTH - wordWidth("CASH MACHINE", 1)) / 2, 5, 1, 0xFFFFFFFF);

        
        int sy = wellY(1) - 2, sh = SLOT_PITCH + 4;
        roundRect(image, 5, sy, WIDTH - 10, sh, 3, SCREEN, CASE_DARK);
        for (int y = sy + 2; y < sy + sh - 2; y += 2) {
            fill(image, 7, y, WIDTH - 14, 1, SCREEN_LINE);
        }

        
        for (int column : new int[] {1, 2, 3, 4, 6, 8}) {
            roundRect(image, wellX(column), wellY(2), SLOT_SIZE, SLOT_SIZE, 2, KEY, CASE_DARK);
        }
        
        roundRect(image, wellX(1), wellY(3) + 4, SLOT_PITCH * 6, SLOT_SIZE - 6, 2, CASE_DARK, 0xFF000000);
        fill(image, wellX(1) + 4, wellY(3) + 7, SLOT_PITCH * 6 - 8, 2, 0xFF000000);

        themedInventory(image, rows, CASE, CASE_DARK);
        cutCorners(image, 5);
        return png(image);
    }

    


    static byte[] bank(int rows) {
        final int H = height(rows);
        final int WOOD = 0xFF6b4a2f, WOOD_DARK = 0xFF3d2a1a, CREAM = 0xFFe6dcc8;
        final int CARD = 0xFFcdc0a6, CARD_EDGE = 0xFF8d7d63, STRIP = 0xFF3f3327;
        BufferedImage image = new BufferedImage(WIDTH, H, BufferedImage.TYPE_INT_ARGB);

        roundRect(image, 0, 0, WIDTH, H, 5, CREAM, WOOD_DARK);
        
        roundRect(image, 3, 3, WIDTH - 6, 13, 3, WOOD, WOOD_DARK);
        word(image, "BANK", (WIDTH - wordWidth("BANK", 1)) / 2, 6, 1, 0xFFf3e6cf);

        
        roundRect(image, 5, wellY(1) - 2, WIDTH - 10, SLOT_PITCH + 4, 3, STRIP, WOOD_DARK);

        
        for (int row = 2; row <= 4; row++) {
            for (int column = 0; column < 9; column++) {
                roundRect(image, wellX(column), wellY(row), SLOT_SIZE, SLOT_SIZE, 2, CARD, CARD_EDGE);
            }
        }
        
        roundRect(image, 3, wellY(5) - 3, WIDTH - 6, SLOT_SIZE + 5, 3, WOOD, WOOD_DARK);
        roundRect(image, wellX(4), wellY(5), SLOT_SIZE, SLOT_SIZE, 2, CREAM, WOOD_DARK);

        themedInventory(image, rows, CARD, CARD_EDGE);
        cutCorners(image, 5);
        return png(image);
    }

    





    static byte[] waiting(int rows) {
        final int H = height(rows);
        final int BOARD = 0xFF1e2740, BOARD_DARK = 0xFF0b0f1c, BOARD_LIGHT = 0xFF3a4770;
        final int LINE = 0xFF2b3557, LINE_EDGE = 0xFF141a2c, INK = 0xFFdfe6f5;
        BufferedImage image = new BufferedImage(WIDTH, H, BufferedImage.TYPE_INT_ARGB);

        roundRect(image, 0, 0, WIDTH, H, 5, BOARD, BOARD_DARK);
        fill(image, 2, 2, WIDTH - 4, 1, BOARD_LIGHT);
        roundRect(image, 4, 3, WIDTH - 8, 13, 3, BOARD_LIGHT, BOARD_DARK);
        word(image, "WAITING", (WIDTH - wordWidth("WAITING", 1)) / 2, 6, 1, INK);

        
        for (int row = 1; row <= 4; row++) {
            roundRect(image, 4, wellY(row) + 1, WIDTH - 8, SLOT_SIZE - 2, 2, LINE, LINE_EDGE);
            
            fill(image, 6, wellY(row) + 4, 2, SLOT_SIZE - 8, BOARD_LIGHT);
        }

        
        roundRect(image, 3, wellY(5) - 3, WIDTH - 6, SLOT_SIZE + 5, 3, BOARD_LIGHT, BOARD_DARK);
        roundRect(image, wellX(4), wellY(5), SLOT_SIZE, SLOT_SIZE, 2, LINE, LINE_EDGE);

        themedInventory(image, rows, LINE, LINE_EDGE);
        cutCorners(image, 5);
        return png(image);
    }

    













    static byte[] phone(int rows) {
        final int H = height(rows);
        BufferedImage image = new BufferedImage(WIDTH, H, BufferedImage.TYPE_INT_ARGB);

        final int DESK = 0xFF15161c;
        final int BODY = 0xFF2b2e39;
        final int BODY_LIGHT = 0xFF4a4f60;
        final int BODY_DARK = 0xFF0d0e12;
        final int HOME_BAR = 0xFFb9bdcc;

        fill(image, 0, 0, WIDTH, H, DESK);

        
        fill(image, 0, 0, WIDTH, 1, OUTLINE);
        fill(image, 0, 0, 1, H, OUTLINE);
        fill(image, 0, H - 1, WIDTH, 1, OUTLINE);
        fill(image, WIDTH - 1, 0, 1, H, OUTLINE);

        cutCorners(image, 5);

        final int bx = 36, by = 2, bw = 104, bh = 128;
        roundRect(image, bx, by, bw, bh, 6, BODY, BODY_DARK);
        
        fill(image, bx + 1, by + 2, 1, bh - 5, BODY_LIGHT);
        fill(image, bx + 2, by + 1, bw - 5, 1, BODY_LIGHT);

        final int sx = 42, sy = 8, sw = 92, sh = 116;
        BufferedImage paper = wallpaperTile();
        for (int x = 0; x < sw; x++) {
            for (int y = 0; y < sh; y++) {
                if (rounded(x, y, sw, sh, 3)) continue;
                image.setRGB(sx + x, sy + y, paper.getRGB((sx + x) % paper.getWidth(), (sy + y) % paper.getHeight()));
            }
        }
        outlineRound(image, sx - 1, sy - 1, sw + 2, sh + 2, 3, BODY_DARK);

        
        fill(image, 79, 4, 18, 2, BODY_DARK);
        fill(image, 68, 126, 40, 2, HOME_BAR);

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                slot(image, WELL_X + column * SLOT_PITCH, inventoryWellY(rows) + row * SLOT_PITCH);
            }
        }
        for (int column = 0; column < 9; column++) {
            slot(image, WELL_X + column * SLOT_PITCH, hotbarWellY(rows));
        }
        return png(image);
    }

    








    static void cutCorners(BufferedImage image, int radius) {
        int w = image.getWidth(), h = image.getHeight();
        for (int dx = 0; dx < radius; dx++) {
            for (int dy = 0; dy < radius - dx; dy++) {
                image.setRGB(dx, dy, 0);
                image.setRGB(w - 1 - dx, dy, 0);
                image.setRGB(dx, h - 1 - dy, 0);
                image.setRGB(w - 1 - dx, h - 1 - dy, 0);
            }
        }
    }

    
    private static boolean rounded(int x, int y, int width, int height, int radius) {
        int dx = Math.min(x, width - 1 - x);
        int dy = Math.min(y, height - 1 - y);
        return dx + dy < radius;
    }

    private static void roundRect(BufferedImage image, int x, int y, int width, int height,
                                  int radius, int fill, int edge) {
        for (int px = 0; px < width; px++) {
            for (int py = 0; py < height; py++) {
                if (rounded(px, py, width, height, radius)) continue;
                boolean border = rounded(px, py, width, height, radius + 1)
                        || px == 0 || py == 0 || px == width - 1 || py == height - 1;
                image.setRGB(x + px, y + py, border ? edge : fill);
            }
        }
    }

    private static void outlineRound(BufferedImage image, int x, int y, int width, int height,
                                     int radius, int colour) {
        for (int px = 0; px < width; px++) {
            for (int py = 0; py < height; py++) {
                if (rounded(px, py, width, height, radius)) continue;
                boolean border = rounded(px, py, width, height, radius + 1)
                        || px == 0 || py == 0 || px == width - 1 || py == height - 1;
                if (border) image.setRGB(x + px, y + py, colour);
            }
        }
    }

    






    static BufferedImage wallpaperTile() {
        final int SIZE = 32;
        BufferedImage tile = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                
                double sweep = Math.sin(((x + y) % SIZE) / (double) SIZE * Math.PI * 2) * 0.5 + 0.5;
                int red = (int) (0x22 + 0x12 * sweep);
                int green = (int) (0x28 + 0x14 * sweep);
                int blue = (int) (0x3e + 0x1c * sweep);
                if (x % 8 == 4 && y % 8 == 4) {
                    red += 0x10; green += 0x10; blue += 0x14;
                }
                tile.setRGB(x, y, 0xFF000000 | red << 16 | green << 8 | blue);
            }
        }
        return tile;
    }

    private static BufferedImage portalTile;

    static void portalTile(BufferedImage frame) {
        portalTile = frame;
    }

    
    static BufferedImage portalTile() {
        return portalTile;
    }

    private static int haze(int x, int y) {
        double drift = Math.sin(x * 0.11 + Math.sin(y * 0.07) * 2.3)
                + Math.sin(y * 0.13 + Math.sin(x * 0.05) * 1.7);
        int hash = x * 374761393 + y * 668265263;
        hash = (hash ^ (hash >> 13)) * 1274126177;
        double grain = ((hash >>> 8) & 0xFF) / 255.0;
        double mix = Math.min(1, Math.max(0, (drift + 2) / 4 * 0.85 + grain * 0.15));
        int red = (int) (0x3b + (0x9a - 0x3b) * mix);
        int green = (int) (0x1e + 0x5e * mix);
        int blue = (int) (0x63 + (0xd8 - 0x63) * mix);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static void set(BufferedImage image, int x, int y, int colour) {
        if (x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight()) {
            image.setRGB(x, y, colour);
        }
    }

    
    static byte[] arrowVertical(boolean up) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 2; y <= 8; y++) {
            for (int x = 6; x <= 9; x++) {
                putV(image, up, x, y);
            }
        }
        for (int y = 8; y <= 13; y++) {
            int half = 13 - y + 1;
            for (int x = 8 - half; x <= 7 + half; x++) {
                putV(image, up, x, y);
            }
        }
        return png(image);
    }

    private static void putV(BufferedImage image, boolean up, int x, int y) {
        if (x < 0 || x > 15) {
            return;
        }
        image.setRGB(x, up ? 15 - y : y, ARROW_INK);
    }

    private static void put(BufferedImage image, boolean left, int x, int y) {
        if (y < 0 || y > 15) {
            return;
        }
        image.setRGB(left ? 15 - x : x, y, arrowInk);
    }

    private static boolean contains(int[] values, int value) {
        for (int candidate : values) {
            if (candidate == value) {
                return true;
            }
        }
        return false;
    }

    private static void fill(BufferedImage image, int x, int y, int width, int height, int colour) {
        for (int dx = 0; dx < width; dx++) {
            for (int dy = 0; dy < height; dy++) {
                int px = x + dx;
                int py = y + dy;
                if (px >= 0 && py >= 0 && px < image.getWidth() && py < image.getHeight()) {
                    image.setRGB(px, py, colour);
                }
            }
        }
    }

    private static void rect(BufferedImage image, int x, int y, int width, int height, int colour) {
        fill(image, x, y, width, 1, colour);
        fill(image, x, y + height - 1, width, 1, colour);
        fill(image, x, y, 1, height, colour);
        fill(image, x + width - 1, y, 1, height, colour);
    }

    private static byte[] png(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot encode menu art", e);
        }
        return out.toByteArray();
    }
}
