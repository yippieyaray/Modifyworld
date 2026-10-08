// SPDX-License-Identifier: GPL-2.0-or-later
// Added on 2026-10-07: numeric release ordering for migration thresholds.
package modifyworld.bukkit;

import java.math.BigInteger;
import java.util.regex.Pattern;

/** Final releases sort after every beta of the same major/minor/patch version. */
record ReleaseVersion(BigInteger major, BigInteger minor, BigInteger patch, BigInteger beta)
        implements Comparable<ReleaseVersion> {
    private static final Pattern FORMAT = Pattern.compile("([0-9]+)\\.([0-9]+)\\.([0-9]+)(?:-BETA\\.([0-9]+))?");

    static ReleaseVersion parse(String text) {
        if (text == null) throw new IllegalArgumentException("Missing release version");
        var match = FORMAT.matcher(text);
        if (!match.matches()) {
            throw new IllegalArgumentException("Release version must match <major>.<minor>.<patch>[-BETA.<beta>]");
        }
        return new ReleaseVersion(new BigInteger(match.group(1)), new BigInteger(match.group(2)),
                new BigInteger(match.group(3)), match.group(4) == null ? null : new BigInteger(match.group(4)));
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch + (beta == null ? "" : "-BETA." + beta);
    }

    @Override
    public int compareTo(ReleaseVersion other) {
        int result = major.compareTo(other.major);
        if (result == 0) result = minor.compareTo(other.minor);
        if (result == 0) result = patch.compareTo(other.patch);
        if (result != 0) return result;
        if (beta == null) return other.beta == null ? 0 : 1;
        return other.beta == null ? -1 : beta.compareTo(other.beta);
    }
}
