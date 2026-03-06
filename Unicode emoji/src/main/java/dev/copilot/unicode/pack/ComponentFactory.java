package dev.copilot.unicode.pack;

import net.kyori.adventure.text.Component;

final class ComponentFactory {

    private ComponentFactory() {
    }

    static Component prompt(final ResourcePackConfig config) {
        final String prompt = config.prompt();
        if (prompt == null || prompt.isBlank()) {
            return Component.text("This server offers a custom emoji font.");
        }
        return Component.text(prompt);
    }
}
