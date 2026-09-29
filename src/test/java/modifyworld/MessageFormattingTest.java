package modifyworld;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MessageFormattingTest {
    @Test
    void substitutionsAreSinglePassAndPreserveUnknownTokens() {
        var config = new YamlConfiguration();
        config.options().pathSeparator('/');
        config.set("messages/modifyworld", "$1 / $10 / $permission / $99");
        var informer = new PlayerInformer(config);
        assertEquals("$2\\text / ten / modifyworld.example / $99",
                informer.formatMessage("modifyworld.example", "$2\\text", "two", "3", "4", "5", "6", "7", "8", "9", "ten"));
    }

    @Test
    void emptyMessageRemainsSilentAndLoginSupportsPermissionPlaceholder() {
        var config = new YamlConfiguration();
        config.options().pathSeparator('/');
        config.set("messages/modifyworld.login", "Missing $permission");
        var informer = new PlayerInformer(config);
        assertEquals("Missing modifyworld.login", informer.formatMessage("modifyworld.login"));
        informer.setMessage("modifyworld.login", "");
        assertEquals("", informer.formatMessage("modifyworld.login"));
    }
}
