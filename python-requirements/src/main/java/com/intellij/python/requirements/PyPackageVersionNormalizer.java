// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.python.requirements;

import consulo.util.lang.StringUtil;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes requirement version.
 * <p>
 * Based on
 * <a href="https://www.python.org/dev/peps/pep-0440/#normalization">https://www.python.org/dev/peps/pep-0440/#normalization</a>
 * and
 * <a href="https://www.python.org/dev/peps/pep-0440/#summary-of-permitted-suffixes-and-relative-ordering">https://www.python.org/dev/peps/pep-0440/#summary-of-permitted-suffixes-and-relative-ordering</a>.
 */
public final class PyPackageVersionNormalizer {

    private static final String EPOCH_GROUP = "epoch";

    private static final String RELEASE_GROUP = "release";

    private static final String PRE_RELEASE_TYPE_GROUP = "pretype";

    private static final String PRE_RELEASE_NUMBER_GROUP = "prenumber";

    private static final String POST_RELEASE_TYPE_GROUP = "posttype";

    private static final String POST_RELEASE_NUMBER_GROUP = "postnumber";

    private static final String IMPLICIT_POST_RELEASE_NUMBER_GROUP = "implicitpostnumber";

    private static final String DEV_RELEASE_TYPE_GROUP = "devtype";

    private static final String DEV_RELEASE_NUMBER_GROUP = "devnumber";

    private static final String LOCAL_VERSION_GROUP = "local";

    private static final String SEP_REGEXP = "([\\.\\-_])?";

    private static final String EPOCH_REGEXP = "(?<" + EPOCH_GROUP + ">\\d+!)?";

    private static final String RELEASE_REGEXP = "(?<" + RELEASE_GROUP + ">(\\d+(\\.\\d+)*)|(\\d+\\.(\\d+\\.)*\\*))";

    private static final String PRE_RELEASE_REGEXP =
        "(" +
        SEP_REGEXP +
        "(?<" + PRE_RELEASE_TYPE_GROUP + ">a|alpha|b|beta|rc|c|pre|preview)" +
        "(" + SEP_REGEXP + "(?<" + PRE_RELEASE_NUMBER_GROUP + ">\\d+))?" +
        ")?";

    private static final String POST_RELEASE_REGEXP =
        "(" +
        "(" + SEP_REGEXP + "(?<" + POST_RELEASE_TYPE_GROUP + ">post|rev|r)(" + SEP_REGEXP + "(?<" + POST_RELEASE_NUMBER_GROUP + ">\\d+))?)" +
        "|" +
        "(-(?<" + IMPLICIT_POST_RELEASE_NUMBER_GROUP + ">\\d+))" +
        ")?";

    private static final String DEV_RELEASE_REGEXP =
        "(" +
        SEP_REGEXP + "(?<" + DEV_RELEASE_TYPE_GROUP + ">dev)(?<" + DEV_RELEASE_NUMBER_GROUP + ">\\d+)?" +
        ")?";

    private static final String LOCAL_VERSION_REGEXP = "(?<" + LOCAL_VERSION_GROUP + ">\\+[a-z0-9]([a-z0-9\\._-]*[a-z0-9])?)?";

    private static final Pattern VERSION = Pattern.compile(
        "^" +
        "v?" +
        EPOCH_REGEXP +
        RELEASE_REGEXP +
        PRE_RELEASE_REGEXP +
        POST_RELEASE_REGEXP +
        DEV_RELEASE_REGEXP +
        LOCAL_VERSION_REGEXP +
        "$",
        Pattern.CASE_INSENSITIVE);

    public static @Nullable PyPackageVersion normalize(String version) {
        Matcher matcher = VERSION.matcher(version);
        if (matcher.matches()) {
            return new PyPackageVersion(
                normalizeEpoch(matcher),
                normalizeRelease(matcher),
                normalizePre(matcher),
                normalizePost(matcher),
                normalizeDev(matcher),
                normalizeLocal(matcher)
            );
        }

        return null;
    }

    private static @Nullable String normalizeEpoch(Matcher matcher) {
        String epoch = matcher.group(EPOCH_GROUP);
        if (epoch != null) {
            return normalizeNumber(epoch.substring(0, epoch.length() - 1));
        }

        return null;
    }

    private static String normalizeRelease(Matcher matcher) {
        return StreamEx
            .of(StringUtil.tokenize(matcher.group(RELEASE_GROUP), ".").iterator())
            .map(releasePart -> releasePart.equals("*") ? "*" : normalizeNumber(releasePart))
            .joining(".");
    }

    private static @Nullable String normalizePre(Matcher matcher) {
        String preReleaseType = matcher.group(PRE_RELEASE_TYPE_GROUP);
        if (preReleaseType != null) {
            String preReleaseNumber = matcher.group(PRE_RELEASE_NUMBER_GROUP);
            String normalizedPreReleaseNumber = preReleaseNumber == null ? "0" : normalizeNumber(preReleaseNumber);

            return normalizePreReleaseType(preReleaseType) + normalizedPreReleaseNumber;
        }

        return null;
    }

    private static @Nullable String normalizePost(Matcher matcher) {
        String postReleaseType = matcher.group(POST_RELEASE_TYPE_GROUP);
        if (postReleaseType != null) {
            String postReleaseNumber = matcher.group(POST_RELEASE_NUMBER_GROUP);
            String normalizedPostReleaseNumber = postReleaseNumber == null ? "0" : normalizeNumber(postReleaseNumber);

            return "post" + normalizeNumber(normalizedPostReleaseNumber);
        }

        String implicitPostReleaseNumber = matcher.group(IMPLICIT_POST_RELEASE_NUMBER_GROUP);
        if (implicitPostReleaseNumber != null) {
            return "post" + normalizeNumber(implicitPostReleaseNumber);
        }

        return null;
    }

    private static @Nullable String normalizeDev(Matcher matcher) {
        if (matcher.group(DEV_RELEASE_TYPE_GROUP) != null) {
            String devReleaseNumber = matcher.group(DEV_RELEASE_NUMBER_GROUP);
            String normalizedDevReleaseNumber = devReleaseNumber == null ? "0" : normalizeNumber(devReleaseNumber);

            return "dev" + normalizedDevReleaseNumber;
        }

        return null;
    }

    private static @Nullable String normalizeLocal(Matcher matcher) {
        String localVersion = matcher.group(LOCAL_VERSION_GROUP);
        if (localVersion != null) {
            return localVersion.substring(1).replaceAll("[-_]", ".");
        }

        return null;
    }

    private static String normalizeNumber(String number) {
        return new BigInteger(number).toString();
    }

    private static String normalizePreReleaseType(String preReleaseType) {
        if (preReleaseType.equalsIgnoreCase("a") || preReleaseType.equalsIgnoreCase("alpha")) {
            return "a";
        }
        else if (preReleaseType.equalsIgnoreCase("b") || preReleaseType.equalsIgnoreCase("beta")) {
            return "b";
        }
        else {
            return "rc";
        }
    }
}
