// SPDX-License-Identifier: GPL-2.0-or-later
// Added on 2026-10-07: cover numeric release and beta ordering.
package modifyworld.bukkit;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ReleaseVersionTest {
    @ParameterizedTest
    @CsvSource({
        "2.0.0-BETA.6, 2.0.0-BETA.6, 0",
        "2.0.0-BETA.9, 2.0.0-BETA.10, -1",
        "2.0.0-BETA.999, 2.0.0, -1",
        "2.0.0, 2.0.1-BETA.0, -1",
        "2.9.99, 2.10.0-BETA.1, -1",
        "9.99.99, 10.0.0-BETA.1, -1",
        "2.0.0, 2.0.0, 0",
        "02.00.00-BETA.06, 2.0.0-BETA.6, 0",
        "2.0.0-BETA.999999999999999999999999, 2.0.0, -1"
    })
    void comparesEveryComponentNumericallyAndFinalAfterBeta(String left, String right, int order) {
        var first = ReleaseVersion.parse(left);
        var second = ReleaseVersion.parse(right);
        assertEquals(order, Integer.signum(first.compareTo(second)));
        assertEquals(-order, Integer.signum(second.compareTo(first)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "2", "2.0", "2.0.0.1", "-2.0.0", "2.0.0-BETA", "2.0.0-beta.1",
        "2.0.0-BETA.-1", "2.0.0-RC.1", "2.0.0-BETA.1-extra", " 2.0.0", "2.0.0\n"})
    void rejectsMalformedVersionStrings(String version) {
        assertThrows(IllegalArgumentException.class, () -> ReleaseVersion.parse(version));
    }
}
