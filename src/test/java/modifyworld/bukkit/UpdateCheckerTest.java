// SPDX-License-Identifier: GPL-2.0-or-later
// Added on 2026-10-08: release channel, pagination, failure and shutdown regression tests.
package modifyworld.bukkit;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class UpdateCheckerTest {
    private static String release(String tag, boolean prerelease) {
        return "{\"tag_name\":\"" + tag + "\",\"draft\":false,\"prerelease\":"
                + prerelease + ",\"published_at\":\"2026-10-08T12:00:00Z\"}";
    }

    private static String releases(String... entries) { return "[" + String.join(",", entries) + "]"; }

    private static java.util.Optional<UpdateChecker.Update> newestUpdate(String json, ReleaseVersion installed) {
        return UpdateChecker.newestUpdate(json, installed, "26.2", OptionalInt.of(130));
    }

    @ParameterizedTest
    @ValueSource(strings = {"26.1", "26.2", "26.2.0"})
    void supportedMinimumVersionsAreDisplayedWithoutMetadata(String minimum) {
        var update = newestUpdate(releases(release("v2.0.1+paper.min." + minimum, false)),
                ReleaseVersion.parse("2.0.0")).orElseThrow();
        assertEquals("2.0.1", update.version().toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"paper.min.26.3", "paper.min.26.2.1", "paper.min.27.0",
            "paper.min.26.10", "paper.min.invalid", "", "unknown.26.2"})
    void incompatibleOrInvalidMetadataIsSkippedWithLegacyFallback(String metadata) {
        var update = newestUpdate(releases(release("v3.0.0+" + metadata, false),
                release("v2.0.1", false)), ReleaseVersion.parse("2.0.0")).orElseThrow();
        assertEquals("2.0.1", update.version().toString());
    }

    @Test
    void unknownServerVersionOnlyAllowsLegacyFallback() {
        assertTrue(UpdateChecker.newestUpdate(releases(release("v2.0.1+paper.min.26.2", false)),
                ReleaseVersion.parse("2.0.0"), "unknown", OptionalInt.empty()).isEmpty());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "26.2,130,false", "26.2,131,false", "26.2,132,true", "26.2,133,true",
        "26.2.0,132,true", "26.1,999,false", "26.3,1,true", "26.10,1,true"
    })
    void minimumBuildAppliesOnlyToSameMinecraftVersion(String server, int build, boolean allowed) {
        var update = UpdateChecker.newestUpdate(releases(release("v2.0.1+paper.min.26.2.build.132", false)),
                ReleaseVersion.parse("2.0.0"), server, OptionalInt.of(build));
        assertEquals(allowed, update.isPresent());
    }

    @Test
    void missingBuildSkipsBuildRequirementButKeepsVersionAndLegacyFallback() {
        assertTrue(UpdateChecker.newestUpdate(releases(release("v2.0.1+paper.min.26.2.build.132", false)),
                ReleaseVersion.parse("2.0.0"), "26.2", OptionalInt.empty()).isEmpty());
        assertTrue(UpdateChecker.newestUpdate(releases(release("v2.0.1+paper.min.26.2.build.132", false)),
                ReleaseVersion.parse("2.0.0"), "26.3", OptionalInt.empty()).isPresent());
        var update = UpdateChecker.newestUpdate(releases(release("v3.0.0+paper.min.26.2.build.132", false),
                release("v2.0.2+paper.min.26.2", false), release("v2.0.1", false)),
                ReleaseVersion.parse("2.0.0"), "26.2", OptionalInt.empty()).orElseThrow();
        assertEquals("2.0.2", update.version().toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"26.2.build.", "26.2.build.abc", "26.2.build.-132", "26.2.build.132-extra", "26.2-132"})
    void malformedBuildRequirementsAreSkipped(String minimum) {
        assertTrue(newestUpdate(releases(release("v2.0.1+paper.min." + minimum, false)),
                ReleaseVersion.parse("2.0.0")).isEmpty());
    }

    @Test
    void installedAndAvailableMetadataNeverAppearsInUpdateNotice() {
        var messages = new ArrayList<String>();
        var transport = new FakeTransport(new UpdateChecker.Response(200,
                releases(release("v2.0.1+paper.min.26.2.build.130", false)), false));
        try (var checker = new UpdateChecker("2.0.0+paper.min.26.2", "26.2", OptionalInt.of(130), transport, messages::add)) {
            checker.run();
            assertEquals(List.of("Update available: 2.0.0 -> 2.0.1 | https://hangar.papermc.io/Yippie/Modifyworld-Reloaded"), messages);
        }
    }

    @Test
    void betaReceivesNewerBetasNumericallyRatherThanByPublicationOrder() {
        var update = newestUpdate(releases(release("v2.0.0-BETA.9", true),
                release("v2.0.0-BETA.10", true), release("v2.0.0-BETA.8", true)),
                ReleaseVersion.parse("2.0.0-BETA.7")).orElseThrow();
        assertEquals("2.0.0-BETA.10", update.version().toString());
        assertEquals("https://hangar.papermc.io/Yippie/Modifyworld-Reloaded", update.url());
    }

    @Test
    void finalReleaseSupersedesBetaOfSameVersion() {
        var update = newestUpdate(releases(release("v2.0.0-BETA.99", true),
                release("v2.0.0", false)), ReleaseVersion.parse("2.0.0-BETA.7")).orElseThrow();
        assertEquals("2.0.0", update.version().toString());
    }

    @Test
    void stableInstallationIgnoresAllPrereleasesIncludingMislabelledBetas() {
        var update = newestUpdate(releases(release("v3.0.0-BETA.1", true),
                release("v4.0.0-BETA.1", false), release("v5.0.0", true), release("v2.0.1", false)),
                ReleaseVersion.parse("2.0.0")).orElseThrow();
        assertEquals("2.0.1", update.version().toString());
    }

    @Test
    void equalAndOlderVersionsAreNotUpdates() {
        assertTrue(newestUpdate(releases(release("v2.0.0-BETA.7", true),
                release("v2.0.0-BETA.6", true), release("v1.9.0", false)),
                ReleaseVersion.parse("2.0.0-BETA.7")).isEmpty());
    }

    @Test
    void draftsUnpublishedAndMalformedEntriesAreSkipped() {
        String valid = release("v2.0.1", false);
        var update = newestUpdate(releases(
                release("v9.0.0", false).replace("\"draft\":false", "\"draft\":true"),
                release("v8.0.0", false).replace("\"2026-10-08T12:00:00Z\"", "null"),
                release("v7.0.0", false).replace("\"prerelease\":false", "\"prerelease\":\"false\""),
                release("unsupported", false), "null", "{}", valid), ReleaseVersion.parse("2.0.0"));
        assertEquals("2.0.1", update.orElseThrow().version().toString());
    }

    @Test
    void tagsWithoutVPrefixAreSupportedAndLinksCannotBeInjected() {
        var update = newestUpdate(releases(release("2.0.1", false)
                .replace("}", ",\"html_url\":\"https://example.invalid/evil\"}")),
                ReleaseVersion.parse("2.0.0")).orElseThrow();
        assertEquals("https://hangar.papermc.io/Yippie/Modifyworld-Reloaded", update.url());
    }

    @ParameterizedTest
    @ValueSource(strings = {"broken", "{}", "null"})
    void malformedResponsesProduceOneShortNotice(String body) {
        var messages = new ArrayList<String>();
        var transport = new FakeTransport(new UpdateChecker.Response(200, body, false));
        try (var checker = new UpdateChecker("2.0.0", "26.2", OptionalInt.of(130), transport, messages::add)) {
            checker.run();
            assertEquals(1, messages.size());
            assertTrue(messages.getFirst().startsWith("Update check unavailable"));
            assertTrue(transport.closed);
        }
    }

    @Test
    void highestVersionAcrossPagesIsSelectedAndCheckRunsOnlyOnce() {
        var messages = new ArrayList<String>();
        var transport = new FakeTransport(
                new UpdateChecker.Response(200, releases(release("v2.0.0-BETA.9", true)), true),
                new UpdateChecker.Response(200, releases(release("v2.0.0-BETA.10", true)), false));
        try (var checker = new UpdateChecker("2.0.0-BETA.7", "26.2", OptionalInt.of(130), transport, messages::add)) {
            checker.run();
            checker.run();
            assertEquals(2, transport.requests.size());
            assertTrue(transport.requests.getFirst().toString().endsWith("per_page=100&page=1"));
            assertTrue(transport.requests.getLast().toString().endsWith("per_page=100&page=2"));
            assertEquals(1, messages.size());
            assertTrue(messages.getFirst().startsWith("Update available: 2.0.0-BETA.7 -> 2.0.0-BETA.10"));
            assertTrue(transport.closed);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 404, 429, 500})
    void httpFailuresDoNotRetryOrClaimAnUpdate(int status) {
        var messages = new ArrayList<String>();
        var transport = new FakeTransport(new UpdateChecker.Response(status, "", false));
        try (var checker = new UpdateChecker("2.0.0", "26.2", OptionalInt.of(130), transport, messages::add)) {
            checker.run();
            assertEquals(List.of("Update check unavailable (GitHub HTTP " + status + ")."), messages);
            assertEquals(1, transport.requests.size());
            assertTrue(transport.closed);
        }
    }

    @Test
    void networkFailureIsContainedAndTransportClosed() {
        var messages = new ArrayList<String>();
        var transport = new FakeTransport();
        transport.failure = new java.net.SocketTimeoutException("timeout");
        try (var checker = new UpdateChecker("2.0.0", "26.2", OptionalInt.of(130), transport, messages::add)) {
            checker.run();
            assertEquals(List.of("Update check unavailable (SocketTimeoutException)."), messages);
            assertTrue(transport.closed);
        }
    }

    @Test
    void pageLimitPreventsUnboundedRequestsAndPartialUpdateClaims() {
        var messages = new ArrayList<String>();
        var response = new UpdateChecker.Response(200, releases(release("v3.0.0", false)), true);
        var transport = new FakeTransport(response, response, response);
        try (var checker = new UpdateChecker("2.0.0", "26.2", OptionalInt.of(130), transport, messages::add)) {
            checker.run();
            assertEquals(UpdateChecker.MAX_PAGES, transport.requests.size());
            assertEquals(List.of("Update check incomplete: GitHub release page limit reached."), messages);
        }
    }

    @Test
    void noUpdateProducesNoConsoleNoise() {
        var messages = new ArrayList<String>();
        var transport = new FakeTransport(new UpdateChecker.Response(200, "[]", false));
        try (var checker = new UpdateChecker("2.0.0", "26.2", OptionalInt.of(130), transport, messages::add)) {
            checker.run();
            assertTrue(messages.isEmpty());
        }
    }

    @Test
    void shutdownBeforeStartPreventsRequestsAndNotifications() {
        var messages = new ArrayList<String>();
        var transport = new FakeTransport();
        var checker = new UpdateChecker("2.0.0", "26.2", OptionalInt.of(130), transport, messages::add);
        checker.close();
        checker.run();
        assertTrue(transport.requests.isEmpty());
        assertTrue(messages.isEmpty());
        assertTrue(transport.closed);
    }

    @Test
    void shutdownDuringRequestPreventsLateNotifications() {
        var messages = new ArrayList<String>();
        var transport = new FakeTransport(new UpdateChecker.Response(200, releases(release("v3.0.0", false)), false));
        var checker = new UpdateChecker("2.0.0", "26.2", OptionalInt.of(130), transport, messages::add);
        transport.beforeResponse = checker::close;
        checker.run();
        assertTrue(messages.isEmpty());
        assertTrue(transport.closed);
    }

    private static final class FakeTransport implements UpdateChecker.Transport {
        private final List<UpdateChecker.Response> responses;
        private final List<URI> requests = new ArrayList<>();
        private IOException failure;
        private Runnable beforeResponse = () -> { };
        private boolean closed;

        FakeTransport(UpdateChecker.Response... responses) { this.responses = List.of(responses); }

        @Override
        public UpdateChecker.Response get(URI uri) throws IOException {
            requests.add(uri);
            if (failure != null) throw failure;
            beforeResponse.run();
            return responses.get(requests.size() - 1);
        }

        @Override public void close() { closed = true; }
    }
}
