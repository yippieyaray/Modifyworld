// SPDX-License-Identifier: GPL-2.0-or-later
// Added on 2026-10-08: bounded, cancellable GitHub release notification.
// Modified on 2026-10-08: direct update downloads to Hangar.
package modifyworld.bukkit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.math.BigInteger;
import java.util.OptionalInt;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/** Runs on an asynchronous task; reports updates without downloading release assets. */
final class UpdateChecker implements Runnable, AutoCloseable {
    static final int MAX_PAGES = 3;
    static final int MAX_BODY_BYTES = 1024 * 1024;
    private static final String API = "https://api.github.com/repos/yippieyaray/Modifyworld/releases";
    private static final String DOWNLOADS = "https://hangar.papermc.io/Yippie/Modifyworld-Reloaded";

    record Response(int status, String body, boolean hasNext) { }
    record Update(ReleaseVersion version, String url) { }

    interface Transport extends AutoCloseable {
        Response get(URI uri) throws IOException;
        @Override default void close() { }
    }

    private final String installed;
    private final String serverVersion;
    private final OptionalInt serverBuild;
    private final Transport transport;
    private final Consumer<String> log;
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile boolean closed;

    UpdateChecker(String installed, String serverVersion, OptionalInt serverBuild, Consumer<String> log) {
        this(installed, serverVersion, serverBuild, new GitHubTransport(installed), log);
    }

    UpdateChecker(String installed, String serverVersion, OptionalInt serverBuild, Transport transport, Consumer<String> log) {
        this.installed = displayVersion(installed);
        this.serverVersion = serverVersion;
        this.serverBuild = serverBuild;
        this.transport = transport;
        this.log = log;
    }

    @Override
    public void run() {
        if (closed || !started.compareAndSet(false, true)) return;
        try {
            ReleaseVersion current = ReleaseVersion.parse(displayVersion(installed));
            Update newest = null;
            for (int page = 1; page <= MAX_PAGES && !closed; page++) {
                Response response = transport.get(URI.create(API + "?per_page=100&page=" + page));
                if (closed) return;
                if (response.status() != 200) {
                    report("Update check unavailable (GitHub HTTP " + response.status() + ").");
                    return;
                }
                var candidate = newestUpdate(response.body(), current, serverVersion, serverBuild);
                if (candidate.isPresent() && (newest == null
                        || candidate.get().version().compareTo(newest.version()) > 0)) {
                    newest = candidate.get();
                }
                if (!response.hasNext()) {
                    if (newest != null) {
                        report("Update available: " + installed + " -> " + newest.version()
                                + " | " + newest.url());
                    }
                    return;
                }
            }
            // Do not claim the highest release when a bounded scan is incomplete.
            report("Update check incomplete: GitHub release page limit reached.");
        } catch (IOException | RuntimeException failure) {
            report("Update check unavailable (" + failure.getClass().getSimpleName() + ").");
        } finally {
            transport.close();
        }
    }

    static Optional<Update> newestUpdate(String json, ReleaseVersion installed, String serverVersion, OptionalInt serverBuild) {
        JsonElement parsed = JsonParser.parseString(json);
        if (!parsed.isJsonArray()) throw new IllegalArgumentException("Expected a release list");
        Update newest = null;
        for (JsonElement entry : parsed.getAsJsonArray()) {
            if (!entry.isJsonObject()) continue;
            JsonObject release = entry.getAsJsonObject();
            Boolean draft = booleanField(release, "draft");
            Boolean preview = booleanField(release, "prerelease");
            if (!Boolean.FALSE.equals(draft) || preview == null) continue;
            String published = stringField(release, "published_at");
            String tag = stringField(release, "tag_name");
            if (published == null || published.isBlank() || tag == null || tag.length() > 128) continue;
            if (!supportsServer(tag, serverVersion, serverBuild)) continue;
            String cleanTag = displayVersion(tag);
            String versionText = cleanTag.startsWith("v") ? cleanTag.substring(1) : cleanTag;
            ReleaseVersion version;
            try { version = ReleaseVersion.parse(versionText); }
            catch (IllegalArgumentException unsupported) { continue; }
            boolean prerelease = preview || version.beta() != null;
            if (installed.beta() == null && prerelease) continue;
            if (version.compareTo(installed) <= 0) continue;
            if (newest == null || version.compareTo(newest.version()) > 0) {
                // Direct downloads to the fixed Hangar project, not arbitrary response URLs.
                newest = new Update(version, DOWNLOADS);
            }
        }
        return Optional.ofNullable(newest);
    }

    private static String displayVersion(String version) {
        int plus = version.indexOf('+');
        return plus < 0 ? version : version.substring(0, plus);
    }

    private static boolean supportsServer(String tag, String serverVersion, OptionalInt serverBuild) {
        int plus = tag.indexOf('+');
        if (plus < 0) return true; // Legacy tags always retain the fallback behavior.
        String metadata = tag.substring(plus + 1);
        String prefix = "paper.min.";
        if (!metadata.startsWith(prefix)) return false;
        String requirement = metadata.substring(prefix.length());
        int separator = requirement.indexOf(".build.");
        String minimum = separator < 0 ? requirement : requirement.substring(0, separator);
        String minimumBuild = separator < 0 ? "" : requirement.substring(separator + ".build.".length());
        if (separator >= 0 && !minimumBuild.matches("[0-9]+")) return false;
        if (!minimum.matches("[0-9]+(?:\\.[0-9]+)+")
                || serverVersion == null || serverVersion.length() > 128
                || !serverVersion.matches("[0-9]+(?:\\.[0-9]+)+")) return false;
        String[] required = minimum.split("\\.");
        String[] actual = serverVersion.split("\\.");
        for (int i = 0; i < Math.max(required.length, actual.length); i++) {
            BigInteger left = i < actual.length ? new BigInteger(actual[i]) : BigInteger.ZERO;
            BigInteger right = i < required.length ? new BigInteger(required[i]) : BigInteger.ZERO;
            int comparison = left.compareTo(right);
            if (comparison != 0) return comparison > 0;
        }
        // Build numbers are comparable only within the same Minecraft version.
        return separator < 0 || (serverBuild.isPresent() && serverBuild.getAsInt() >= 0
                && BigInteger.valueOf(serverBuild.getAsInt()).compareTo(new BigInteger(minimumBuild)) >= 0);
    }

    private static @Nullable Boolean booleanField(JsonObject object, String key) {
        JsonElement field = object.get(key);
        return field != null && field.isJsonPrimitive() && field.getAsJsonPrimitive().isBoolean()
                ? field.getAsBoolean() : null;
    }

    private static @Nullable String stringField(JsonObject object, String key) {
        JsonElement field = object.get(key);
        return field != null && field.isJsonPrimitive() && field.getAsJsonPrimitive().isString()
                ? field.getAsString() : null;
    }

    private synchronized void report(String message) {
        if (!closed) log.accept(message);
    }

    @Override
    public synchronized void close() {
        closed = true;
        transport.close();
    }

    private static final class GitHubTransport implements Transport {
        private final String userAgent;
        private @Nullable HttpURLConnection active;
        private boolean closed;

        GitHubTransport(String version) { userAgent = "Modifyworld/" + version; }

        @Override
        public Response get(URI uri) throws IOException {
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            synchronized (this) {
                if (closed) {
                    connection.disconnect();
                    throw new InterruptedIOException("Update check stopped");
                }
                active = connection;
            }
            try {
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10");
                connection.setRequestProperty("User-Agent", userAgent);
                int status = connection.getResponseCode();
                if (status != 200) return new Response(status, "", false);
                String link = connection.getHeaderField("Link");
                boolean next = link != null && link.contains("rel=\"next\"");
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
                try (var input = connection.getInputStream(); var body = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    while (true) {
                        long remaining = deadline - System.nanoTime();
                        if (remaining <= 0) throw new java.net.SocketTimeoutException("Release response timed out");
                        connection.setReadTimeout((int) Math.max(1, Math.min(5000,
                                java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(remaining))));
                        int read = input.read(buffer);
                        if (read < 0) break;
                        if (body.size() + read > MAX_BODY_BYTES) throw new IOException("Release response too large");
                        body.write(buffer, 0, read);
                    }
                    return new Response(status, body.toString(StandardCharsets.UTF_8), next);
                }
            } finally {
                synchronized (this) { if (active == connection) active = null; }
                connection.disconnect();
            }
        }

        @Override
        public synchronized void close() {
            closed = true;
            if (active != null) active.disconnect();
        }
    }
}
