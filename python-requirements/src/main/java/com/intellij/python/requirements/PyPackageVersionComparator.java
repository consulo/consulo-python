// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.python.requirements;

import com.jetbrains.python.packaging.requirement.PyRequirementRelation;
import consulo.repository.ui.PackageVersionComparator;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Compares {@link PyPackageVersion PyPackageVersions}.
 * <p>
 * Based on <a href="https://www.python.org/dev/peps/pep-0440/#summary-of-permitted-suffixes-and-relative-ordering">PEP-440</a>.
 */
public final class PyPackageVersionComparator implements Comparator<PyPackageVersion> {
    public static final PyPackageVersionComparator INSTANCE = new PyPackageVersionComparator();

    /**
     * Compares versions by normalizing them or using {@link PackageVersionComparator#VERSION_COMPARATOR} as a fallback
     */
    public static final Comparator<String> STR_COMPARATOR = (o1, o2) -> {
        PyPackageVersion normalized1 = PyPackageVersionNormalizer.normalize(o1);
        if (normalized1 == null) {
            return PackageVersionComparator.VERSION_COMPARATOR.compare(o1, o2);
        }

        PyPackageVersion normalized2 = PyPackageVersionNormalizer.normalize(o2);
        if (normalized2 == null) {
            return PackageVersionComparator.VERSION_COMPARATOR.compare(o1, o2);
        }

        return INSTANCE.compare(normalized1, normalized2);
    };

    private PyPackageVersionComparator() {
    }

    @Override
    public int compare(PyPackageVersion o1, PyPackageVersion o2) {
        int epochs = compareEpochs(o1, o2);
        if (epochs != 0) {
            return epochs;
        }

        PyRequirementRelation releases = compareReleases(o1, o2);
        if (releases != PyRequirementRelation.EQ) {
            if (releases == PyRequirementRelation.LT) {
                return -1;
            }
            if (releases == PyRequirementRelation.GT) {
                return 1;
            }
            return 0;
        }

        int pres = comparePres(o1, o2);
        if (pres != 0) {
            return pres;
        }

        int posts = comparePosts(o1, o2);
        if (posts != 0) {
            return posts;
        }

        int devs = compareDevs(o1, o2);
        if (devs != 0) {
            return devs;
        }

        return compareLocals(o1, o2);
    }

    /**
     * @see PyPackageVersionNormalizer#normalizeEpoch
     */
    private static int compareEpochs(PyPackageVersion o1, PyPackageVersion o2) {
        String epoch1 = o1.getEpoch();
        String epoch2 = o2.getEpoch();
        return compareAsInts(epoch1 != null ? epoch1 : "0", epoch2 != null ? epoch2 : "0");
    }

    /**
     * @see PyPackageVersionNormalizer#normalizeRelease
     */
    private static PyRequirementRelation compareReleases(PyPackageVersion o1, PyPackageVersion o2) {
        Iterator<Map.Entry<String, String>> iterator =
            zipLongest(Arrays.asList(o1.getRelease().split("\\.", -1)), Arrays.asList(o2.getRelease().split("\\.", -1)), "0").iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            String releasePart1 = entry.getKey();
            String releasePart2 = entry.getValue();

            if (releasePart1.equals("*") || releasePart2.equals("*")) {
                return PyRequirementRelation.COMPATIBLE;
            }

            int releaseParts = compareAsInts(releasePart1, releasePart2);
            if (releaseParts < 0) {
                return PyRequirementRelation.LT;
            }
            if (releaseParts > 0) {
                return PyRequirementRelation.GT;
            }
        }

        boolean devOnly1 = o1.getDev() != null && o1.getPre() == null && o1.getPost() == null;
        boolean devOnly2 = o2.getDev() != null && o2.getPre() == null && o2.getPost() == null;

        if (devOnly1 || devOnly2) {
            if (!devOnly2) {
                return PyRequirementRelation.LT;
            }
            if (!devOnly1) {
                return PyRequirementRelation.GT;
            }

            int devs = compareDevs(o1, o2);
            if (devs < 0) {
                return PyRequirementRelation.LT;
            }
            if (devs > 0) {
                return PyRequirementRelation.GT;
            }
        }

        return PyRequirementRelation.EQ;
    }

    /**
     * @see PyPackageVersionNormalizer#normalizePost
     */
    private static int comparePosts(PyPackageVersion o1, PyPackageVersion o2) {
        String post1 = o1.getPost();
        String post2 = o2.getPost();
        return compareAsInts(post1 != null ? post1.substring(4) : "-1", post2 != null ? post2.substring(4) : "-1");
    }

    /**
     * @see PyPackageVersionNormalizer#normalizePre
     */
    private static int comparePres(PyPackageVersion o1, PyPackageVersion o2) {
        String pre1 = o1.getPre();
        String pre2 = o2.getPre();

        if (pre1 == null && pre2 == null) {
            return 0;
        }
        if (pre2 == null) {
            return -1;
        }
        if (pre1 == null) {
            return 1;
        }

        String preType1 = filterLetters(pre1);
        String preType2 = filterLetters(pre2);

        int preTypes = preType1.compareTo(preType2);
        if (preTypes != 0) {
            return preTypes;
        }

        return compareAsInts(pre1.substring(preType1.length()), pre2.substring(preType2.length()));
    }

    /**
     * @see PyPackageVersionNormalizer#normalizeDev
     */
    private static int compareDevs(PyPackageVersion o1, PyPackageVersion o2) {
        String dev1 = o1.getDev();
        String dev2 = o2.getDev();

        if (dev1 == null && dev2 == null) {
            return 0;
        }
        if (dev2 == null) {
            return -1;
        }
        if (dev1 == null) {
            return 1;
        }

        return dev1.substring(3).compareTo(dev2.substring(3));
    }

    /**
     * @see PyPackageVersionNormalizer#normalizeLocal
     */
    private static int compareLocals(PyPackageVersion o1, PyPackageVersion o2) {
        String local1 = o1.getLocal();
        String local2 = o2.getLocal();
        return (local1 != null ? local1 : "").compareTo(local2 != null ? local2 : "");
    }

    private static int compareAsInts(String o1, String o2) {
        return new BigInteger(o1).compareTo(new BigInteger(o2));
    }

    private static String filterLetters(String s) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c)) {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private static <E> EntryStream<E, E> zipLongest(Collection<E> c1, Collection<E> c2, E fillValue) {
        long maxSize = Math.max(c1.size(), c2.size());

        return StreamEx
            .of(fillToLength(c1, fillValue, maxSize))
            .zipWith(fillToLength(c2, fillValue, maxSize));
    }

    private static <E> Stream<E> fillToLength(Collection<E> c, E fillValue, long length) {
        return StreamEx
            .of(c)
            .append(StreamEx.constant(fillValue, length))
            .limit(length);
    }
}
