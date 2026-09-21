package pdx.dev.hypergravel.pack;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import pdx.dev.hypergravel.tab.HeadIcon;
import pdx.dev.hypergravel.tab.IconArt;

public final class PackBuilder {

    private static final Logger LOGGER = LogManager.getLogger(PackBuilder.class);

    public static final char MENU_BACKGROUND = '\uE300';

    public static final char LIST_BACKGROUND = '\uE301';

    public static final char SEARCH_BACKGROUND = '\uE302';

    public static final char LADDER_BACKGROUND = '\uE303';

    public static final char WATCH_BACKGROUND = '\uE304';

    public static final char LIST_BACKGROUND_BASE = '\uE305';

    public static final char PROFILE_BACKGROUND = '\uE309';

    public static final char SETTINGS_BACKGROUND = '\uE30A';

    public static final char SERVER_BACKGROUND = '\uE30B';

    public static final char PORTAL_BACKGROUND = '\uE30C';

    public static final char GATE_BACKGROUND = '\uE30D';

    public static final char SHOP_BACKGROUND = '\uE30E';

    public static final char QUEST_BACKGROUND = '\uE30F';
    public static final char KITS_BACKGROUND = '\uE310';
    
    public static final char PLACES_BACKGROUND = '\uE311';
    
    public static final char PHONE_BACKGROUND = '\uE312';
    
    public static final char APP_BACKGROUND = '\uE313';
    
    public static final char MIC_GLYPH = '\uE320';
    
    public static final char MIC_OFF_GLYPH = '\uE321';

    
    public static final char ATM_BACKGROUND = '\uE314';
    public static final char BANK_BACKGROUND = '\uE315';
    public static final char WAITING_BACKGROUND = '\uE316';

    






    public static final char WORLDS_BACKGROUND = '\uE317';

    






    public static final int WORLDS_STYLES = 5;

    
    public static final char GAMES_BACKGROUND = '\uE31C';

    
    public static final char LADDER_DARK_BACKGROUND = '\uE31D';

    
    public static final char WATCH_DARK_BACKGROUND = '\uE31E';
    public static final char PROFILE_DARK_BACKGROUND = '\uE31F';

    private static volatile int backgroundAscent = MenuArt.BACKGROUND_ASCENT;

    public static void setBackgroundAscent(int ascent) {
        backgroundAscent = ascent;
    }

    public static final char SHIFT_BASE = '\uE200';

    private static final int[] MENU_SLOTS = {10, 13, 16, 29, 31, 33, 38, 40, 42, 49};
    private static final int[] MENU_CARD_SLOTS = {10, 13, 16};

    private static final int[] MENU_BUTTON_SLOTS = {29, 33};

    private static final long FIXED_TIME = 0L;

    private PackBuilder() {
    }

    public record Built(byte[] zip, String sha1) {}

    public static Built build() {
        return build(java.util.Map.of());
    }

    
    public static final char GLYPH_RPG = '\uE410';
    public static final char GLYPH_SHOW = '\uE411';
    public static final char GLYPH_NOTE = '\uE412';
    public static final char GLYPH_EXIT = '\uE413';
    private static final int GLYPH_HEIGHT = 10;
    private static final int GLYPH_ASCENT = 8;

    
    public static final char LOGO = '\uE400';
    private static final int LOGO_HEIGHT = 16;
    private static final int LOGO_ASCENT = 12;

    
    private static volatile java.util.Map<String, byte[]> OVERRIDES = java.util.Map.of();

    public static void overrides(java.util.Map<String, byte[]> files) {
        OVERRIDES = files == null ? java.util.Map.of() : files;
    }

    
    private static final ThreadLocal<java.util.Set<String>> USED =
            ThreadLocal.withInitial(java.util.HashSet::new);

    
    private static void menuGlyph(ZipOutputStream zip, JsonArray providers, String name,
                                  byte[] art, String glyph) throws IOException {
        if (art == null) {
            return;
        }
        write(zip, "assets/hypergravel/textures/font/" + name + ".png", art);
        JsonObject provider = new JsonObject();
        provider.addProperty("type", "bitmap");
        provider.addProperty("file", "hypergravel:font/" + name + ".png");
        provider.addProperty("ascent", GLYPH_ASCENT);
        provider.addProperty("height", GLYPH_HEIGHT);
        JsonArray chars = new JsonArray();
        chars.add(glyph);
        provider.add("chars", chars);
        providers.add(provider);
    }

    



    private static byte[] keyed(byte[] png) {
        if (png == null) {
            return null;
        }
        try {
            java.awt.image.BufferedImage in = javax.imageio.ImageIO.read(
                    new java.io.ByteArrayInputStream(png));
            if (in == null) {
                return png;
            }
            int w = in.getWidth();
            int h = in.getHeight();
            java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(
                    w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            int[] pixels = new int[w * h];
            in.getRGB(0, 0, w, h, pixels, 0, w);
            out.setRGB(0, 0, w, h, pixels, 0, w);
            int ground = out.getRGB(0, 0);
            boolean[] seen = new boolean[w * h];
            java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
            for (int x = 0; x < w; x++) {
                queue.add(new int[] {x, 0});
                queue.add(new int[] {x, h - 1});
            }
            for (int y = 0; y < h; y++) {
                queue.add(new int[] {0, y});
                queue.add(new int[] {w - 1, y});
            }
            while (!queue.isEmpty()) {
                int[] at = queue.poll();
                int x = at[0];
                int y = at[1];
                if (x < 0 || y < 0 || x >= w || y >= h || seen[y * w + x]) {
                    continue;
                }
                seen[y * w + x] = true;
                if (out.getRGB(x, y) != ground) {
                    continue;
                }
                out.setRGB(x, y, 0);
                queue.add(new int[] {x + 1, y});
                queue.add(new int[] {x - 1, y});
                queue.add(new int[] {x, y + 1});
                queue.add(new int[] {x, y - 1});
            }
            ByteArrayOutputStream cut = new ByteArrayOutputStream(1024);
            javax.imageio.ImageIO.write(out, "png", cut);
            return cut.toByteArray();
        } catch (IOException e) {
            LOGGER.warn("pack: could not key out an icon", e);
            return png;
        }
    }

    public static Built build(java.util.Map<Character, byte[]> faces) {
        USED.get().clear();

        byte[] portalTexture = readResource("/pack/nether_portal.png");
        if (portalTexture != null) {
            try {
                java.awt.image.BufferedImage strip = javax.imageio.ImageIO.read(
                        new java.io.ByteArrayInputStream(portalTexture));
                if (strip != null) {
                    MenuArt.portalTile(strip.getSubimage(0, 0, strip.getWidth(),
                            strip.getWidth()));
                }
            } catch (IOException e) {
                LOGGER.warn("pack: could not read the portal texture", e);
            }
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream(16 * 1024);
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.setLevel(9);

            JsonArray providers = new JsonArray();
            for (HeadIcon icon : HeadIcon.values()) {
                if (icon == HeadIcon.SPACER) {
                    continue;
                }
                String file = "tab_" + icon.name().toLowerCase(java.util.Locale.ROOT) + ".png";
                write(zip, "assets/hypergravel/textures/font/" + file, IconArt.glyph(icon));

                JsonObject provider = new JsonObject();
                provider.addProperty("type", "bitmap");
                provider.addProperty("file", "hypergravel:font/" + file);
                provider.addProperty("ascent", 7);
                provider.addProperty("height", 8);
                JsonArray chars = new JsonArray();
                chars.add(String.valueOf(icon.glyph()));
                provider.add("chars", chars);
                providers.add(provider);
            }

            for (java.util.Map.Entry<Character, byte[]> face : faces.entrySet()) {
                String file = String.format("face_%04x.png", (int) face.getKey());
                write(zip, "assets/hypergravel/textures/font/" + file, face.getValue());

                JsonObject provider = new JsonObject();
                provider.addProperty("type", "bitmap");
                provider.addProperty("file", "hypergravel:font/" + file);
                provider.addProperty("ascent", 7);
                provider.addProperty("height", 8);
                JsonArray chars = new JsonArray();
                chars.add(String.valueOf(face.getKey()));
                provider.add("chars", chars);
                providers.add(provider);
            }

            
            
            
            byte[] logo = readResource("/pack/logo.png");
            if (logo != null) {
                write(zip, "assets/hypergravel/textures/font/logo.png", logo);
                JsonObject mark = new JsonObject();
                mark.addProperty("type", "bitmap");
                mark.addProperty("file", "hypergravel:font/logo.png");
                mark.addProperty("ascent", LOGO_ASCENT);
                mark.addProperty("height", LOGO_HEIGHT);
                JsonArray markChars = new JsonArray();
                markChars.add(String.valueOf(LOGO));
                mark.add("chars", markChars);
                providers.add(mark);
            } else {
                LOGGER.warn("pack: no logo.png in resources - the menu will say HYPERGRAVEL");
            }

            
            
            
            
            
            menuGlyph(zip, providers, "menu_rpg",
                    MenuArt.trophy(), String.valueOf(GLYPH_RPG));
            menuGlyph(zip, providers, "menu_show",
                    MenuArt.eye(), String.valueOf(GLYPH_SHOW));
            menuGlyph(zip, providers, "menu_note",
                    MenuArt.note(), String.valueOf(GLYPH_NOTE));
            menuGlyph(zip, providers, "menu_exit",
                    MenuArt.exit(), String.valueOf(GLYPH_EXIT));

            write(zip, "assets/hypergravel/textures/font/menu_bg.png",
                    MenuArt.background(MENU_SLOTS, MENU_CARD_SLOTS, MENU_BUTTON_SLOTS));
            JsonObject background = new JsonObject();
            background.addProperty("type", "bitmap");
            background.addProperty("file", "hypergravel:font/menu_bg.png");
            background.addProperty("ascent", backgroundAscent);
            background.addProperty("height", MenuArt.HEIGHT);
            JsonArray backgroundChars = new JsonArray();
            backgroundChars.add(String.valueOf(MENU_BACKGROUND));
            background.add("chars", backgroundChars);
            providers.add(background);

            int[] listSlots = new int[54];
            for (int i = 0; i < listSlots.length; i++) {
                listSlots[i] = i;
            }
            write(zip, "assets/hypergravel/textures/font/list_bg.png",
                    MenuArt.background(listSlots, new int[0], new int[] {45, 53}));
            JsonObject list = new JsonObject();
            list.addProperty("type", "bitmap");
            list.addProperty("file", "hypergravel:font/list_bg.png");
            list.addProperty("ascent", backgroundAscent);
            list.addProperty("height", MenuArt.HEIGHT);
            JsonArray listChars = new JsonArray();
            listChars.add(String.valueOf(LIST_BACKGROUND));
            list.add("chars", listChars);
            providers.add(list);

            write(zip, "assets/hypergravel/textures/font/search_bg.png",
                    MenuArt.background(4, new int[0], new int[] {13}, new int[] {31}));
            JsonObject search = new JsonObject();
            search.addProperty("type", "bitmap");
            search.addProperty("file", "hypergravel:font/search_bg.png");
            search.addProperty("ascent", backgroundAscent);
            search.addProperty("height", MenuArt.height(4));
            JsonArray searchChars = new JsonArray();
            searchChars.add(String.valueOf(SEARCH_BACKGROUND));
            search.add("chars", searchChars);
            providers.add(search);

            int[] ladderWells = {28, 29, 30, 31, 32, 33, 34, 49};
            write(zip, "assets/hypergravel/textures/font/ladder_bg.png",
                    MenuArt.background(6, ladderWells, new int[] {10, 13, 16}, new int[] {45}));
            JsonObject ladder = new JsonObject();
            ladder.addProperty("type", "bitmap");
            ladder.addProperty("file", "hypergravel:font/ladder_bg.png");
            ladder.addProperty("ascent", backgroundAscent);
            ladder.addProperty("height", MenuArt.height(6));
            JsonArray ladderChars = new JsonArray();
            ladderChars.add(String.valueOf(LADDER_BACKGROUND));
            ladder.add("chars", ladderChars);
            providers.add(ladder);

            write(zip, "assets/hypergravel/textures/font/watch_bg.png",
                    MenuArt.background(6, new int[0], new int[0],
                            new int[] {45},
                            new int[] {1, 2, 3, 4}));
            JsonObject watch = new JsonObject();
            watch.addProperty("type", "bitmap");
            watch.addProperty("file", "hypergravel:font/watch_bg.png");
            watch.addProperty("ascent", backgroundAscent);
            watch.addProperty("height", MenuArt.height(6));
            JsonArray watchChars = new JsonArray();
            watchChars.add(String.valueOf(WATCH_BACKGROUND));
            watch.add("chars", watchChars);
            providers.add(watch);

            for (int rows = 3; rows <= 6; rows++) {
                int[] all = new int[rows * 9];
                for (int i = 0; i < all.length; i++) {
                    all[i] = i;
                }
                String file = "list" + rows + "_bg.png";
                write(zip, "assets/hypergravel/textures/font/" + file,
                        MenuArt.background(rows, all, new int[0], new int[0]));
                JsonObject panel = new JsonObject();
                panel.addProperty("type", "bitmap");
                panel.addProperty("file", "hypergravel:font/" + file);
                panel.addProperty("ascent", backgroundAscent);
                panel.addProperty("height", MenuArt.height(rows));
                JsonArray chars = new JsonArray();
                chars.add(String.valueOf((char) (LIST_BACKGROUND_BASE + rows - 3)));
                panel.add("chars", chars);
                providers.add(panel);
            }

            write(zip, "assets/hypergravel/textures/font/profile_bg.png",
                    MenuArt.background(5, new int[] {28, 30, 32, 34},
                            new int[] {13}, new int[] {40}));
            JsonObject profile = new JsonObject();
            profile.addProperty("type", "bitmap");
            profile.addProperty("file", "hypergravel:font/profile_bg.png");
            profile.addProperty("ascent", backgroundAscent);
            profile.addProperty("height", MenuArt.height(5));
            JsonArray profileChars = new JsonArray();
            profileChars.add(String.valueOf(PROFILE_BACKGROUND));
            profile.add("chars", profileChars);
            providers.add(profile);

            write(zip, "assets/hypergravel/textures/font/settings_bg.png",
                    MenuArt.background(3, new int[0], new int[0], new int[] {8, 18},
                            new int[0], true));
            JsonObject settings = new JsonObject();
            settings.addProperty("type", "bitmap");
            settings.addProperty("file", "hypergravel:font/settings_bg.png");
            settings.addProperty("ascent", backgroundAscent);
            settings.addProperty("height", MenuArt.height(3));
            JsonArray settingsChars = new JsonArray();
            settingsChars.add(String.valueOf(SETTINGS_BACKGROUND));
            settings.add("chars", settingsChars);
            providers.add(settings);

            write(zip, "assets/hypergravel/textures/font/server_bg.png",
                    MenuArt.background(3, new int[0], new int[0], new int[] {13, 18},
                            new int[0], true));
            JsonObject serverPanel = new JsonObject();
            serverPanel.addProperty("type", "bitmap");
            serverPanel.addProperty("file", "hypergravel:font/server_bg.png");
            serverPanel.addProperty("ascent", backgroundAscent);
            serverPanel.addProperty("height", MenuArt.height(3));
            JsonArray serverChars = new JsonArray();
            serverChars.add(String.valueOf(SERVER_BACKGROUND));
            serverPanel.add("chars", serverChars);
            providers.add(serverPanel);

            
            
            
            write(zip, "assets/hypergravel/textures/font/games_bg.png", MenuArt.gamesPanel());
            for (Object[] extra : new Object[][] {
                    {"watch_dark_bg", MenuArt.watchPanel(), WATCH_DARK_BACKGROUND},
                    {"profile_dark_bg", MenuArt.profilePanel(), PROFILE_DARK_BACKGROUND}}) {
                write(zip, "assets/hypergravel/textures/font/" + extra[0] + ".png",
                        (byte[]) extra[1]);
                JsonObject panel = new JsonObject();
                panel.addProperty("type", "bitmap");
                panel.addProperty("file", "hypergravel:font/" + extra[0] + ".png");
                panel.addProperty("ascent", backgroundAscent);
                panel.addProperty("height", MenuArt.height(6));
                JsonArray chars = new JsonArray();
                chars.add(String.valueOf((char) (Character) extra[2]));
                panel.add("chars", chars);
                providers.add(panel);
            }
            JsonObject ladderDark = new JsonObject();
            write(zip, "assets/hypergravel/textures/font/ladder_dark_bg.png", MenuArt.ladderPanel());
            ladderDark.addProperty("type", "bitmap");
            ladderDark.addProperty("file", "hypergravel:font/ladder_dark_bg.png");
            ladderDark.addProperty("ascent", backgroundAscent);
            ladderDark.addProperty("height", MenuArt.height(6));
            JsonArray ladderDarkChars = new JsonArray();
            ladderDarkChars.add(String.valueOf(LADDER_DARK_BACKGROUND));
            ladderDark.add("chars", ladderDarkChars);
            providers.add(ladderDark);
            JsonObject games = new JsonObject();
            games.addProperty("type", "bitmap");
            games.addProperty("file", "hypergravel:font/games_bg.png");
            games.addProperty("ascent", backgroundAscent);
            games.addProperty("height", MenuArt.height(6));
            JsonArray gamesChars = new JsonArray();
            gamesChars.add(String.valueOf(GAMES_BACKGROUND));
            games.add("chars", gamesChars);
            providers.add(games);

            for (int style = 0; style < WORLDS_STYLES; style++) {
                String file = "worlds" + style + "_bg";
                write(zip, "assets/hypergravel/textures/font/" + file + ".png",
                        MenuArt.worldsPanel(style));
                JsonObject worldsStyle = new JsonObject();
                worldsStyle.addProperty("type", "bitmap");
                worldsStyle.addProperty("file", "hypergravel:font/" + file + ".png");
                worldsStyle.addProperty("ascent", backgroundAscent);
                worldsStyle.addProperty("height", MenuArt.height(6));
                JsonArray styleChars = new JsonArray();
                styleChars.add(String.valueOf((char) (WORLDS_BACKGROUND + style)));
                worldsStyle.add("chars", styleChars);
                providers.add(worldsStyle);
            }
            write(zip, "assets/hypergravel/textures/font/portal_bg.png",
                    MenuArt.background(6, new int[0], new int[0], new int[] {45},
                            new int[0], true, new int[][] {
                                    {10, 3, 3, 0},
                                    {13, 5, 1, 1},
                                    {22, 5, 1, 1},
                                    {31, 5, 1, 1},
                                    {46, 8, 1, 1},
                            }));
            JsonObject portal = new JsonObject();
            portal.addProperty("type", "bitmap");
            portal.addProperty("file", "hypergravel:font/portal_bg.png");
            portal.addProperty("ascent", backgroundAscent);
            portal.addProperty("height", MenuArt.height(6));
            JsonArray portalChars = new JsonArray();
            portalChars.add(String.valueOf(PORTAL_BACKGROUND));
            portal.add("chars", portalChars);
            providers.add(portal);

            record Panel(String file, char glyph, int rows, int[][] blocks, int[] buttons,
                         java.awt.image.BufferedImage backdrop) { }
            java.awt.image.BufferedImage portalBackdrop = MenuArt.portalTile();
            java.awt.image.BufferedImage map = MenuArt.mapTile();
            for (Panel panel : new Panel[] {
                    new Panel("gate_bg", GATE_BACKGROUND, 6, GATE_BLOCKS, new int[] {45}, portalBackdrop),
                    
                    
                    
                    new Panel("places_bg", PLACES_BACKGROUND, 6, new int[][] {
                            {0, 8, 1, 1}, {9, 8, 1, 1}, {18, 8, 1, 1}, {27, 8, 1, 1}, {36, 8, 1, 1},
                            {17, 1, 3, 0},
                            {46, 8, 1, 0},
                    }, new int[] {8, 44, 45}, map),
                    new Panel("shop_bg", SHOP_BACKGROUND, 6, new int[][] {
                            {10, 2, 2, 0}, {13, 2, 2, 0}, {16, 2, 2, 0},
                            {36, 9, 1, 1},
                            {46, 8, 1, 1},
                    }, new int[] {45}, portalBackdrop),
                    new Panel("quest_bg", QUEST_BACKGROUND, 6, new int[][] {
                            {9, 9, 1, 1}, {18, 9, 1, 1}, {27, 9, 1, 1},
                            {36, 9, 1, 0},
                    }, new int[] {45, 53}, portalBackdrop),
                    
                    
                    
                    
                    new Panel("kits_bg", KITS_BACKGROUND, 6, new int[][] {
                            {10, 2, 3, 0}, {13, 2, 3, 0}, {16, 2, 3, 0},
                            {37, 7, 1, 1},
                    }, new int[] {45, 53}, portalBackdrop),
            }) {
                write(zip, "assets/hypergravel/textures/font/" + panel.file() + ".png",
                        MenuArt.background(panel.rows(), new int[0], new int[0], panel.buttons(),
                                new int[0], true, panel.blocks(), panel.backdrop()));
                JsonObject provider = new JsonObject();
                provider.addProperty("type", "bitmap");
                provider.addProperty("file", "hypergravel:font/" + panel.file() + ".png");
                provider.addProperty("ascent", backgroundAscent);
                provider.addProperty("height", MenuArt.height(panel.rows()));
                JsonArray chars = new JsonArray();
                chars.add(String.valueOf(panel.glyph()));
                provider.add("chars", chars);
                providers.add(provider);
            }

            
            
            write(zip, "assets/hypergravel/textures/font/phone_bg.png", MenuArt.phone(6));
            JsonObject phone = new JsonObject();
            phone.addProperty("type", "bitmap");
            phone.addProperty("file", "hypergravel:font/phone_bg.png");
            phone.addProperty("ascent", backgroundAscent);
            phone.addProperty("height", MenuArt.height(6));
            JsonArray phoneChars = new JsonArray();
            phoneChars.add(String.valueOf(PHONE_BACKGROUND));
            phone.add("chars", phoneChars);
            providers.add(phone);

            
            
            write(zip, "assets/hypergravel/textures/font/mic.png", MenuArt.micGlyph());
            JsonObject mic = new JsonObject();
            mic.addProperty("type", "bitmap");
            mic.addProperty("file", "hypergravel:font/mic.png");
            mic.addProperty("ascent", 10);
            mic.addProperty("height", 12);
            JsonArray micChars = new JsonArray();
            micChars.add(String.valueOf(MIC_GLYPH));
            mic.add("chars", micChars);
            providers.add(mic);

            write(zip, "assets/hypergravel/textures/font/mic_off.png", MenuArt.micMutedGlyph());
            JsonObject micOff = new JsonObject();
            micOff.addProperty("type", "bitmap");
            micOff.addProperty("file", "hypergravel:font/mic_off.png");
            micOff.addProperty("ascent", 10);
            micOff.addProperty("height", 12);
            JsonArray micOffChars = new JsonArray();
            micOffChars.add(String.valueOf(MIC_OFF_GLYPH));
            micOff.add("chars", micOffChars);
            providers.add(micOff);

            write(zip, "assets/hypergravel/textures/font/app_bg.png", MenuArt.appScreen(6));
            JsonObject app = new JsonObject();
            app.addProperty("type", "bitmap");
            app.addProperty("file", "hypergravel:font/app_bg.png");
            app.addProperty("ascent", backgroundAscent);
            app.addProperty("height", MenuArt.height(6));
            JsonArray appChars = new JsonArray();
            appChars.add(String.valueOf(APP_BACKGROUND));
            app.add("chars", appChars);
            providers.add(app);

            
            record Drawn(String file, char glyph, int rows, byte[] art) { }
            for (Drawn drawn : new Drawn[] {
                    new Drawn("atm_bg", ATM_BACKGROUND, 4, MenuArt.atm(4)),
                    new Drawn("bank_bg", BANK_BACKGROUND, 6, MenuArt.bank(6)),
                    new Drawn("waiting_bg", WAITING_BACKGROUND, 6, MenuArt.waiting(6)),
            }) {
                write(zip, "assets/hypergravel/textures/font/" + drawn.file() + ".png", drawn.art());
                JsonObject city = new JsonObject();
                city.addProperty("type", "bitmap");
                city.addProperty("file", "hypergravel:font/" + drawn.file() + ".png");
                city.addProperty("ascent", backgroundAscent);
                city.addProperty("height", MenuArt.height(drawn.rows()));
                JsonArray cityChars = new JsonArray();
                cityChars.add(String.valueOf(drawn.glyph()));
                city.add("chars", cityChars);
                providers.add(city);
            }

            writeMenuModels(zip);
            writePortal(zip);
            writeEarth(zip);
            writeBlip(zip);
            writeWorldsIcons(zip);
            writeFruitTek(zip);
            writeFruitTekBlocks(zip);
            writePodium(zip);

            providers.add(spaceProvider());

            JsonObject font = new JsonObject();
            font.add("providers", providers);
            write(zip, "assets/hypergravel/font/ui.json",
                    font.toString().getBytes(StandardCharsets.UTF_8));

            JsonObject meta = new JsonObject();
            JsonObject pack = new JsonObject();
            pack.addProperty("pack_format", 64);
            JsonObject supported = new JsonObject();
            supported.addProperty("min_inclusive", 15);
            supported.addProperty("max_inclusive", 9999);
            pack.add("supported_formats", supported);
            pack.addProperty("description", "§fHyperGravel §8- tab icons");
            meta.add("pack", pack);
            write(zip, "pack.mcmeta", meta.toString().getBytes(StandardCharsets.UTF_8));

            
            
            
            for (java.util.Map.Entry<String, byte[]> override : OVERRIDES.entrySet()) {
                if (USED.get().contains(override.getKey())) continue;
                write(zip, override.getKey(), override.getValue());
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot build the icon pack", e);
        }

        byte[] zip = bytes.toByteArray();
        return new Built(zip, sha1(zip));
    }

    public static String publish(Path destination) throws IOException {
        return publish(destination, java.util.Map.of());
    }

    public static String publish(Path destination, java.util.Map<Character, byte[]> faces)
            throws IOException {
        Built built = build(faces);
        if (Files.exists(destination)) {
            byte[] existing = Files.readAllBytes(destination);
            if (sha1(existing).equals(built.sha1())) {
                LOGGER.info("pack: {} is already current (sha1 {})",
                        destination.getFileName(), built.sha1().substring(0, 8));
                return built.sha1();
            }
        }
        Files.createDirectories(destination.getParent());
        Files.write(destination, built.zip());
        LOGGER.info("pack: wrote {} ({} bytes, sha1 {})",
                destination, built.zip().length, built.sha1());
        return built.sha1();
    }

    private static final String[] OVERSIZED_ITEMS = {
        "iron_sword", "diamond_sword", "netherite_sword", "stone_sword", "netherite_axe",
        "bow", "splash_potion", "leather_boots", "nether_star",

        
        
        
        "lingering_potion", "enchanted_golden_apple", "golden_apple", "mushroom_stew",
        "diamond_axe", "iron_axe", "diamond_pickaxe", "shield", "arrow", "cooked_beef",
        "milk_bucket", "potion", "cobblestone",

        
        "end_crystal", "mace", "totem_of_undying", "wind_charge", "slime_block",

        "diamond", "gold_ingot", "iron_ingot",

        
        "ender_eye", "water_bucket", "book", "comparator", "writable_book",
        "paper", "ender_pearl", "emerald",
    };

    private static void writeMenuModels(ZipOutputStream zip) throws IOException {

        write(zip, "assets/hypergravel/items/avatar.json", specialItem("hypergravel:ui/avatar"));
        write(zip, "assets/hypergravel/items/head_big.json", specialItem("hypergravel:ui/head_big"));

        write(zip, "assets/hypergravel/models/ui/avatar.json",
                entityModel("front", 2.0, 8, "[0,0,0]").getBytes(StandardCharsets.UTF_8));
        write(zip, "assets/hypergravel/models/ui/head_big.json",
                entityModel("side", 3.5, 13, "[25,45,0]").getBytes(StandardCharsets.UTF_8));

        for (String block : OVERSIZED_BLOCKS) {
            write(zip, "assets/hypergravel/items/big_" + block + ".json",
                    "grass_block".equals(block)
                            ? tintedModelItem("hypergravel:ui/big_" + block)
                            : modelItem("hypergravel:ui/big_" + block));
            write(zip, "assets/hypergravel/models/ui/big_" + block + ".json",
                    bigBlockModel(block).getBytes(StandardCharsets.UTF_8));
        }

        for (String item : OVERSIZED_ITEMS) {
            write(zip, "assets/hypergravel/items/big_" + item + ".json",
                    modelItem("hypergravel:ui/big_" + item));
            write(zip, "assets/hypergravel/models/ui/big_" + item + ".json",
                    bigItemModel(item).getBytes(StandardCharsets.UTF_8));
        }

        write(zip, "assets/hypergravel/textures/item/eye.png", MenuArt.eye());
        write(zip, "assets/hypergravel/models/ui/eye.json",
                ("{\"parent\":\"minecraft:item/generated\","
                        + "\"textures\":{\"layer0\":\"hypergravel:item/eye\"}}")
                        .getBytes(StandardCharsets.UTF_8));
        write(zip, "assets/hypergravel/items/eye.json", modelItem("hypergravel:ui/eye"));

        Map<String, byte[]> flat = new LinkedHashMap<>();
        flat.put("toggle_on", MenuArt.toggle(true));
        flat.put("toggle_off", MenuArt.toggle(false));
        
        
        
        
        
        
        
        
        
        
        flat.put("icon_swords", readResource("/pack/icon_swords.png"));
        flat.put("icon_trophy", MenuArt.trophy());
        flat.put("icon_cog", MenuArt.cog());
        flat.put("icon_chart", MenuArt.chart());
        flat.put("icon_note", MenuArt.note());
        flat.put("icon_play", MenuArt.play());
        flat.put("phone", MenuArt.phoneItem());
        
        
        
        for (String place : new String[] {"place_house", "place_tower", "place_shop", "place_keep",
                "place_gate", "place_park", "place_farm", "place_hall"}) {
            byte[] art = readResource("/pack/" + place + ".png");
            if (art == null) {
                LOGGER.warn("pack: {} is missing, its slot will be empty", place);
                continue;
            }
            flat.put(place, art);
        }
        for (int value = 0; value <= 9; value++) {
            flat.put("digit_" + value, MenuArt.digit(value));
        }
        for (Map.Entry<String, byte[]> icon : flat.entrySet()) {
            String name = icon.getKey();
            write(zip, "assets/hypergravel/textures/item/" + name + ".png", icon.getValue());
            write(zip, "assets/hypergravel/models/ui/" + name + ".json",
                    ("{\"parent\":\"minecraft:item/generated\","
                            + "\"textures\":{\"layer0\":\"hypergravel:item/" + name + "\"}}")
                            .getBytes(StandardCharsets.UTF_8));
            write(zip, "assets/hypergravel/items/" + name + ".json", modelItem("hypergravel:ui/" + name));

            
            
            
            
            if (name.startsWith("place_") || name.equals("icon_swords")) {
                write(zip, "assets/hypergravel/models/ui/" + name + "_big.json",
                        ("{\"parent\":\"minecraft:item/generated\","
                                + "\"textures\":{\"layer0\":\"hypergravel:item/" + name + "\"},"
                                + "\"display\":{\"gui\":{\"rotation\":[0,0,0],"
                                + "\"translation\":[0,1,0],\"scale\":[2,2,2]}}}")
                                .getBytes(StandardCharsets.UTF_8));
                write(zip, "assets/hypergravel/items/" + name + "_big.json",
                        modelItem("hypergravel:ui/" + name + "_big"));
            }
        }

        
        for (String[] light : new String[][] {
                {"exit_light", "exit"}, {"arrow_left_light", "left"}, {"arrow_right_light", "right"}}) {
            byte[] art = switch (light[1]) {
                case "exit" -> MenuArt.exit(MenuArt.LIGHT_INK);
                case "left" -> MenuArt.arrow(true, MenuArt.LIGHT_INK);
                default -> MenuArt.arrow(false, MenuArt.LIGHT_INK);
            };
            write(zip, "assets/hypergravel/textures/item/" + light[0] + ".png", art);
            write(zip, "assets/hypergravel/models/ui/" + light[0] + ".json",
                    ("{\"parent\":\"minecraft:item/generated\","
                            + "\"textures\":{\"layer0\":\"hypergravel:item/" + light[0] + "\"}}")
                            .getBytes(StandardCharsets.UTF_8));
            write(zip, "assets/hypergravel/items/" + light[0] + ".json", modelItem("hypergravel:ui/" + light[0]));
        }

        write(zip, "assets/hypergravel/textures/item/exit.png", MenuArt.exit());
        write(zip, "assets/hypergravel/models/ui/exit.json",
                ("{\"parent\":\"minecraft:item/generated\","
                        + "\"textures\":{\"layer0\":\"hypergravel:item/exit\"}}")
                        .getBytes(StandardCharsets.UTF_8));
        write(zip, "assets/hypergravel/items/exit.json", modelItem("hypergravel:ui/exit"));

        for (boolean up : new boolean[] {true, false}) {
            String name = up ? "arrow_up" : "arrow_down";
            write(zip, "assets/hypergravel/textures/item/" + name + ".png", MenuArt.arrowVertical(up));
            write(zip, "assets/hypergravel/models/ui/" + name + ".json",
                    ("{\"parent\":\"minecraft:item/generated\","
                            + "\"textures\":{\"layer0\":\"hypergravel:item/" + name + "\"}}")
                            .getBytes(StandardCharsets.UTF_8));
            write(zip, "assets/hypergravel/items/" + name + ".json",
                    modelItem("hypergravel:ui/" + name));
        }

        
        
        
        
        byte[] window = readResource("/pack/generic_54.png");
        if (window != null) {
            write(zip, "assets/minecraft/textures/gui/container/generic_54.png", window);
        } else {
            LOGGER.warn("pack: generic_54.png is missing - menu corners will show vanilla's");
        }

        write(zip, "CREDITS.txt",
                ("The place icons in this pack, and the three on the worlds board, are Kenney's\n"
                        + "\"1-Bit Pack\" glyphs, released under CC0 (public domain) at\n"
                        + "https://kenney.nl/assets/1-bit-pack.\n"
                        + "Credit is not required by that licence; it is here because it is deserved.\n")
                        .getBytes(StandardCharsets.UTF_8));

        for (boolean left : new boolean[] {true, false}) {
            String name = left ? "arrow_left" : "arrow_right";
            write(zip, "assets/hypergravel/textures/item/" + name + ".png", MenuArt.arrow(left));
            write(zip, "assets/hypergravel/models/ui/" + name + ".json",
                    ("{\"parent\":\"minecraft:item/generated\","
                            + "\"textures\":{\"layer0\":\"hypergravel:item/" + name + "\"}}")
                            .getBytes(StandardCharsets.UTF_8));
            write(zip, "assets/hypergravel/items/" + name + ".json",
                    modelItem("hypergravel:ui/" + name));
        }
    }

    private static byte[] readResource(String path) {
        try (java.io.InputStream in = PackBuilder.class.getResourceAsStream(path)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            LOGGER.warn("pack: could not read {}", path, e);
            return null;
        }
    }

    







    











    private static void writeWorldsIcons(ZipOutputStream zip) throws IOException {
        for (String icon : new String[] {"icon_survival", "icon_arena", "icon_creative",
                "icon_leaderboard", "icon_stats", "icon_spectate", "icon_settings"}) {
            for (String suffix : new String[] {"", "_big"}) {
                String name = icon + suffix;
                byte[] art = readResource("/pack/" + name + ".png");
                if (art == null) {
                    LOGGER.warn("pack: {} is missing, its card will be empty", name);
                    continue;
                }
                write(zip, "assets/hypergravel/textures/item/" + name + ".png", art);
                
                
                write(zip, "assets/hypergravel/textures/item/" + name + ".png.mcmeta",
                        "{\"animation\":{\"frametime\":3}}".getBytes(StandardCharsets.UTF_8));
                String model = "{\"parent\":\"minecraft:item/generated\","
                        + "\"textures\":{\"layer0\":\"hypergravel:item/" + name + "\"}"
                        + (suffix.isEmpty() ? "}"
                                : ",\"display\":{\"gui\":{\"rotation\":[0,0,0],"
                                        + "\"translation\":[0,1,0],\"scale\":[2,2,2]}}}");
                write(zip, "assets/hypergravel/models/ui/" + name + ".json",
                        model.getBytes(StandardCharsets.UTF_8));
                write(zip, "assets/hypergravel/items/" + name + ".json",
                        modelItem("hypergravel:ui/" + name));
            }
        }
    }

    
    private static void writeBlip(ZipOutputStream zip) throws IOException {
        write(zip, "assets/hypergravel/textures/item/radar_blip.png", MenuArt.radarBlip());
        write(zip, "assets/hypergravel/textures/item/radar_blip.png.mcmeta",
                "{\"animation\":{\"frametime\":2}}".getBytes(StandardCharsets.UTF_8));
        write(zip, "assets/hypergravel/models/ui/radar_blip.json",
                ("{\"parent\":\"minecraft:item/generated\","
                        + "\"textures\":{\"layer0\":\"hypergravel:item/radar_blip\"}}")
                        .getBytes(StandardCharsets.UTF_8));
        write(zip, "assets/hypergravel/items/radar_blip.json", modelItem("hypergravel:ui/radar_blip"));
    }

    private static void writeEarth(ZipOutputStream zip) throws IOException {
        byte[] strip = MenuArt.earthFrames(16);
        write(zip, "assets/hypergravel/textures/item/icon_earth.png", strip);
        write(zip, "assets/hypergravel/textures/item/icon_earth.png.mcmeta",
                "{\"animation\":{\"frametime\":3}}".getBytes(StandardCharsets.UTF_8));
        write(zip, "assets/hypergravel/models/ui/icon_earth.json",
                ("{\"parent\":\"minecraft:item/generated\","
                        + "\"textures\":{\"layer0\":\"hypergravel:item/icon_earth\"}}")
                        .getBytes(StandardCharsets.UTF_8));
        write(zip, "assets/hypergravel/items/icon_earth.json", modelItem("hypergravel:ui/icon_earth"));
        write(zip, "assets/hypergravel/models/ui/icon_earth_big.json",
                ("{\"parent\":\"minecraft:item/generated\","
                        + "\"textures\":{\"layer0\":\"hypergravel:item/icon_earth\"},"
                        + "\"display\":{\"gui\":{\"rotation\":[0,0,0],"
                        + "\"translation\":[0,1,0],\"scale\":[2,2,2]}}}")
                        .getBytes(StandardCharsets.UTF_8));
        write(zip, "assets/hypergravel/items/icon_earth_big.json",
                modelItem("hypergravel:ui/icon_earth_big"));
    }

    private static void writePortal(ZipOutputStream zip) throws IOException {
        byte[] strip = readResource("/pack/nether_portal.png");
        if (strip == null) {
            return;
        }
        write(zip, "assets/hypergravel/textures/item/icon_portal.png", strip);
        write(zip, "assets/hypergravel/textures/item/icon_portal.png.mcmeta",
                "{\"animation\":{\"frametime\":1}}".getBytes(StandardCharsets.UTF_8));
        write(zip, "assets/hypergravel/models/ui/icon_portal.json",
                ("{\"parent\":\"minecraft:item/generated\","
                        + "\"textures\":{\"layer0\":\"hypergravel:item/icon_portal\"}}")
                        .getBytes(StandardCharsets.UTF_8));
        write(zip, "assets/hypergravel/items/icon_portal.json",
                modelItem("hypergravel:ui/icon_portal"));
    }

    private static byte[] modelItem(String model) {
        JsonObject root = new JsonObject();
        root.add("model", plainModel(model));
        root.addProperty("oversized_in_gui", true);
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] tintedModelItem(String model) {
        JsonObject tint = new JsonObject();
        tint.addProperty("type", "minecraft:grass");
        tint.addProperty("temperature", 0.5);
        tint.addProperty("downfall", 1.0);
        JsonArray tints = new JsonArray();
        tints.add(tint);

        JsonObject inner = plainModel(model);
        inner.add("tints", tints);

        JsonObject root = new JsonObject();
        root.add("model", inner);
        root.addProperty("oversized_in_gui", true);
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] specialItem(String base) {
        JsonObject model = specialHead(base);

        JsonObject transformation = new JsonObject();
        transformation.add("left_rotation", numbers(1, 0, 0, 0));
        transformation.add("right_rotation", numbers(0, 0, 0, 1));
        transformation.add("scale", numbers(1, 1, 1));
        transformation.add("translation", numbers(0.5, 0, 0.5));
        model.add("transformation", transformation);

        JsonObject root = new JsonObject();
        root.add("model", model);
        root.addProperty("oversized_in_gui", true);
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static JsonArray numbers(double... values) {
        JsonArray array = new JsonArray();
        for (double value : values) {
            array.add(value);
        }
        return array;
    }

    private static JsonObject spaceProvider() {
        JsonObject provider = new JsonObject();
        provider.addProperty("type", "space");
        JsonObject advances = new JsonObject();
        advances.addProperty(" ", 4);
        int shift = 1;
        for (int i = 0; i < 8; i++) {
            advances.addProperty(String.valueOf((char) (SHIFT_BASE + i)), -shift);
            shift *= 2;
        }
        provider.add("advances", advances);
        return provider;
    }

    private static JsonObject specialHead(String base) {
        JsonObject special = new JsonObject();
        special.addProperty("type", "minecraft:special");
        JsonObject kind = new JsonObject();
        kind.addProperty("type", "minecraft:player_head");
        special.add("model", kind);
        special.addProperty("base", base);
        return special;
    }

    private static JsonObject plainModel(String model) {
        JsonObject object = new JsonObject();
        object.addProperty("type", "minecraft:model");
        object.addProperty("model", model);
        return object;
    }

    private static final int[][] GATE_BLOCKS = {
        {12, 3, 4, 0},
        {9, 3, 1, 1}, {18, 3, 1, 1}, {27, 3, 1, 1},
        {15, 3, 1, 1}, {24, 3, 1, 1}, {33, 3, 1, 1},
        {46, 8, 1, 1},
    };

    private static final String[] OVERSIZED_BLOCKS = {
        "grass_block", "beacon", "crafting_table", "anvil", "target",
        
        "jukebox", "note_block",
    };

    private static String bigBlockModel(String block) {
        return "{\"parent\":\"minecraft:block/" + block + "\","
                + "\"display\":{\"gui\":{\"rotation\":[30,225,0],"
                + "\"translation\":[0,0,0],\"scale\":[1.85,1.85,1.85]}}}";
    }

    











    private static void writeFruitTek(ZipOutputStream zip) throws IOException {
        for (String id : new String[] {"seed", "fiber"}) {
            String name = "ftk_" + id;
            byte[] art = readResource("/pack/" + name + ".png");
            if (art == null) {
                LOGGER.warn("pack: {} is missing, that item keeps its vanilla look", name);
                continue;
            }
            write(zip, "assets/hypergravel/textures/item/" + name + ".png", art);
            write(zip, "assets/hypergravel/textures/item/" + name + ".png.mcmeta",
                    "{\"animation\":{\"frametime\":3}}".getBytes(StandardCharsets.UTF_8));
            write(zip, "assets/hypergravel/models/ui/" + name + ".json",
                    ("{\"parent\":\"minecraft:item/generated\","
                            + "\"textures\":{\"layer0\":\"hypergravel:item/" + name + "\"}}")
                            .getBytes(StandardCharsets.UTF_8));
            JsonObject item = new JsonObject();
            item.add("model", plainModel("hypergravel:ui/" + name));
            write(zip, "assets/hypergravel/items/" + name + ".json",
                    item.toString().getBytes(StandardCharsets.UTF_8));
        }
    }


    















    







    private record Bank(String instrument, String prefix, String[] ids, boolean[] animated) { }

    private static final Bank[] BANKS = {
        new Bank("creeper", "ftkb_",
                new String[] {"rack", "blank", "switch", "patch", "crac", "ups"},
                new boolean[] {true, false, true, true, true, true}),
        new Bank("dragon", "ftkb_",
                new String[] {"pdu", "floor", "tray", "console", "cage", "door"},
                new boolean[] {true, false, false, true, false, true}),
        new Bank("zombie", "city_",
                new String[] {"generator", "solar", "battery", "transformer", "pole", "lamp"},
                new boolean[] {true, true, true, true, true, false}),
        new Bank("skeleton", "city_",
                new String[] {"pump", "pipe", "antenna", "junction", "traffic", "kiosk"},
                new boolean[] {true, true, true, true, true, true}),
        
        
        new Bank("wither_skeleton", "grid_",
                new String[] {"wire", "wirebend", "wiretee", "wirecross", "relay", "uplink"},
                new boolean[] {false, false, false, false, true, true}),
    };

    
    private static final int[] TURNS = {0, 90, 180, 270};

    private static void writeFruitTekBlocks(ZipOutputStream zip) throws IOException {
        JsonArray multipart = new JsonArray();

        
        
        
        
        
        
        JsonArray keepVanilla = new JsonArray();
        keepVanilla.add(condition("note", "0"));
        JsonArray noBank = new JsonArray();
        for (Bank bank : BANKS) {
            noBank.add(condition("instrument", "!" + bank.instrument()));
        }
        JsonObject noneOfOurs = new JsonObject();
        noneOfOurs.add("AND", noBank);
        keepVanilla.add(noneOfOurs);
        JsonObject either = new JsonObject();
        either.add("OR", keepVanilla);
        multipart.add(selector(either, "minecraft:block/note_block"));

        for (Bank bank : BANKS) {
            writeBank(zip, multipart, bank);
        }

        JsonObject blockstate = new JsonObject();
        blockstate.add("multipart", multipart);
        write(zip, "assets/minecraft/blockstates/note_block.json",
                blockstate.toString().getBytes(StandardCharsets.UTF_8));
    }

    
    private static void writeBank(ZipOutputStream zip, JsonArray multipart, Bank bank) throws IOException {
        for (int i = 0; i < bank.ids().length; i++) {
            String name = bank.prefix() + bank.ids()[i];
            boolean wrote = true;
            for (String face : new String[] {"front", "side", "top"}) {
                byte[] art = readResource("/pack/" + name + "_" + face + ".png");
                if (art == null) {
                    LOGGER.warn("pack: {} is missing, that block keeps the note block look", name);
                    wrote = false;
                    break;
                }
                write(zip, "assets/hypergravel/textures/block/" + name + "_" + face + ".png", art);
                if (bank.animated()[i]) {
                    write(zip, "assets/hypergravel/textures/block/" + name + "_" + face + ".png.mcmeta",
                            "{\"animation\":{\"frametime\":3}}".getBytes(StandardCharsets.UTF_8));
                }
            }
            if (!wrote) {
                continue;
            }
            
            String model = "{\"parent\":\"minecraft:block/orientable\",\"textures\":{"
                    + "\"front\":\"hypergravel:block/" + name + "_front\","
                    + "\"side\":\"hypergravel:block/" + name + "_side\","
                    + "\"top\":\"hypergravel:block/" + name + "_top\"}}";
            write(zip, "assets/hypergravel/models/block/" + name + ".json", model.getBytes(StandardCharsets.UTF_8));
            write(zip, "assets/hypergravel/models/ui/" + name + ".json", model.getBytes(StandardCharsets.UTF_8));
            JsonObject item = new JsonObject();
            item.add("model", plainModel("hypergravel:ui/" + name));
            write(zip, "assets/hypergravel/items/" + name + ".json", item.toString().getBytes(StandardCharsets.UTF_8));

            int base = 1 + i * 4;
            for (int facing = 0; facing < 4; facing++) {
                JsonObject when = condition("instrument", bank.instrument());
                when.addProperty("note", String.valueOf(base + facing));
                JsonObject apply = new JsonObject();
                apply.addProperty("model", "hypergravel:block/" + name);
                if (TURNS[facing] != 0) apply.addProperty("y", TURNS[facing]);
                JsonObject part = new JsonObject();
                part.add("when", when);
                part.add("apply", apply);
                multipart.add(part);
            }
        }
    }

    private static JsonObject condition(String property, String value) {
        JsonObject when = new JsonObject();
        when.addProperty(property, value);
        return when;
    }

    private static JsonObject selector(JsonObject when, String model) {
        JsonObject apply = new JsonObject();
        apply.addProperty("model", model);
        JsonObject part = new JsonObject();
        part.add("when", when);
        part.add("apply", apply);
        return part;
    }

    








    private static void writePodium(ZipOutputStream zip) throws IOException {
        for (String metal : new String[] {"diamond", "gold_ingot", "iron_ingot"}) {
            write(zip, "assets/hypergravel/models/ui/podium_" + metal + ".json",
                    ("{\"parent\":\"minecraft:item/" + metal + "\","
                            + "\"display\":{\"gui\":{\"rotation\":[0,0,0],"
                            + "\"translation\":[0,1,0],\"scale\":[2.15,2.15,2.15]}}}")
                            .getBytes(StandardCharsets.UTF_8));
            write(zip, "assets/hypergravel/items/podium_" + metal + ".json",
                    modelItem("hypergravel:ui/podium_" + metal));
        }
    }

    private static String bigItemModel(String item) {
        return "{\"parent\":\"minecraft:item/" + item + "\","
                + "\"display\":{\"gui\":{\"rotation\":[0,0,0],"
                + "\"translation\":[0,1,0],\"scale\":[2.6,2.6,2.6]}}}";
    }

    private static String entityModel(String light, double scale, int lift, String rotation) {
        return "{\"parent\":\"builtin/entity\","
                + "\"textures\":{\"particle\":\"block/soul_sand\"},"
                + "\"gui_light\":\"" + light + "\","
                + "\"display\":{\"gui\":{"
                + "\"rotation\":" + rotation + ","
                + "\"translation\":[0," + lift + ",0],"
                + "\"scale\":[" + scale + "," + scale + "," + scale + "]}}}";
    }

    private static JsonObject reference(String id) {
        JsonObject provider = new JsonObject();
        provider.addProperty("type", "reference");
        provider.addProperty("id", id);
        return provider;
    }

    private static void write(ZipOutputStream zip, String name, byte[] content) throws IOException {
        
        
        byte[] override = OVERRIDES.get(name);
        if (override != null) {
            content = override;
            USED.get().add(name);
        }
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(FIXED_TIME);
        CRC32 crc = new CRC32();
        crc.update(content);
        entry.setCrc(crc.getValue());
        entry.setSize(content.length);
        zip.putNextEntry(entry);
        zip.write(content);
        zip.closeEntry();
    }

    private static String sha1(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(content);
            StringBuilder hex = new StringBuilder(40);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-1 is required by the JLS", e);
        }
    }
}
