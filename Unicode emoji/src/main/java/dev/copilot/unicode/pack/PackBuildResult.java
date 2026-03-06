package dev.copilot.unicode.pack;

import java.nio.file.Path;

public record PackBuildResult(Path zipPath, byte[] sha1) {
}
