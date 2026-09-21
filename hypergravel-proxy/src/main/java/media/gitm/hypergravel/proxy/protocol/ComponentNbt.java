package media.gitm.hypergravel.proxy.protocol;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

public final class ComponentNbt {

    private static final byte TAG_END = 0;
    private static final byte TAG_BYTE = 1;
    private static final byte TAG_STRING = 8;
    private static final byte TAG_LIST = 9;
    private static final byte TAG_COMPOUND = 10;

    private ComponentNbt() {
    }

    public static void write(ByteBuf buf, Component component) {
        buf.writeByte(TAG_COMPOUND);
        writeCompoundBody(buf, component);
    }

    private static void writeCompoundBody(ByteBuf buf, Component component) {
        String text = component instanceof TextComponent tc ? tc.content() : "";
        namedString(buf, "text", text);

        TextColor color = component.color();
        if (color != null) {
            namedString(buf, "color", color.asHexString());
        }
        for (Map.Entry<TextDecoration, TextDecoration.State> e
                : component.decorations().entrySet()) {
            if (e.getValue() != TextDecoration.State.NOT_SET) {
                buf.writeByte(TAG_BYTE);
                name(buf, decorationKey(e.getKey()));
                buf.writeByte(e.getValue() == TextDecoration.State.TRUE ? 1 : 0);
            }
        }

        
        
        
        ClickEvent click = component.clickEvent();
        if (click != null) {
            buf.writeByte(TAG_COMPOUND);
            name(buf, "click_event");
            namedString(buf, "action", clickAction(click.action()));
            namedString(buf, payloadField(click.action()), click.value());
            buf.writeByte(TAG_END);
        }

        HoverEvent<?> hover = component.hoverEvent();
        if (hover != null && hover.action() == HoverEvent.Action.SHOW_TEXT
                && hover.value() instanceof Component shown) {
            buf.writeByte(TAG_COMPOUND);
            name(buf, "hover_event");
            namedString(buf, "action", "show_text");
            buf.writeByte(TAG_COMPOUND);
            name(buf, "value");
            writeCompoundBody(buf, shown);
            buf.writeByte(TAG_END);
        }

        List<Component> children = component.children();
        if (!children.isEmpty()) {
            buf.writeByte(TAG_LIST);
            name(buf, "extra");
            buf.writeByte(TAG_COMPOUND);
            buf.writeInt(children.size());
            for (Component child : children) {
                writeCompoundBody(buf, child);
            }
        }
        buf.writeByte(TAG_END);
    }

    private static String clickAction(ClickEvent.Action action) {
        return switch (action) {
            case OPEN_URL -> "open_url";
            case OPEN_FILE -> "open_file";
            case RUN_COMMAND -> "run_command";
            case SUGGEST_COMMAND -> "suggest_command";
            case CHANGE_PAGE -> "change_page";
            case COPY_TO_CLIPBOARD -> "copy_to_clipboard";
            
            
            default -> action.toString().toLowerCase(java.util.Locale.ROOT);
        };
    }

    
    private static String payloadField(ClickEvent.Action action) {
        return switch (action) {
            case OPEN_URL -> "url";
            case OPEN_FILE -> "path";
            case RUN_COMMAND, SUGGEST_COMMAND -> "command";
            case CHANGE_PAGE -> "page";
            case COPY_TO_CLIPBOARD -> "value";
            default -> "value";
        };
    }

    private static String decorationKey(TextDecoration decoration) {
        return switch (decoration) {
            case BOLD -> "bold";
            case ITALIC -> "italic";
            case UNDERLINED -> "underlined";
            case STRIKETHROUGH -> "strikethrough";
            case OBFUSCATED -> "obfuscated";
        };
    }

    private static void namedString(ByteBuf buf, String tagName, String value) {
        buf.writeByte(TAG_STRING);
        name(buf, tagName);
        nbtString(buf, value);
    }

    private static void name(ByteBuf buf, String tagName) {
        nbtString(buf, tagName);
    }

    private static void nbtString(ByteBuf buf, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }
}
