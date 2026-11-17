// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.python.requirements;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Presents normalized version of python package or requirement as described in <a href="https://www.python.org/dev/peps/pep-0440/#normalization">PEP-440</a>.
 * <p>
 * Instances of this class MUST be obtained from {@link PyPackageVersionNormalizer#normalize}.
 */
public final class PyPackageVersion {
    private final @Nullable String myEpoch;
    private final String myRelease;
    private final @Nullable String myPre;
    private final @Nullable String myPost;
    private final @Nullable String myDev;
    private final @Nullable String myLocal;

    PyPackageVersion(
        @Nullable String epoch,
        String release,
        @Nullable String pre,
        @Nullable String post,
        @Nullable String dev,
        @Nullable String local
    ) {
        myEpoch = epoch;
        myRelease = release;
        myPre = pre;
        myPost = post;
        myDev = dev;
        myLocal = local;
    }

    public @Nullable String getEpoch() {
        return myEpoch;
    }

    public String getRelease() {
        return myRelease;
    }

    public @Nullable String getPre() {
        return myPre;
    }

    public @Nullable String getPost() {
        return myPost;
    }

    public @Nullable String getDev() {
        return myDev;
    }

    public @Nullable String getLocal() {
        return myLocal;
    }

    /**
     * String representation that follows spelling described in <a href="https://www.python.org/dev/peps/pep-0440/#normalization">PEP-440</a>
     */
    public String getPresentableText() {
        return Stream.of(epochPresentable(), myRelease, myPre, postPresentable(), devPresentable(), localPresentable())
            .filter(Objects::nonNull)
            .collect(Collectors.joining(""));
    }

    private @Nullable String epochPresentable() {
        return myEpoch == null ? null : myEpoch + "!";
    }

    private @Nullable String postPresentable() {
        return myPost == null ? null : "." + myPost;
    }

    private @Nullable String devPresentable() {
        return myDev == null ? null : "." + myDev;
    }

    private @Nullable String localPresentable() {
        return myLocal == null ? null : "+" + myLocal;
    }

    public PyPackageVersion withRelease(String newRelease) {
        return new PyPackageVersion(myEpoch, newRelease, myPre, myPost, myDev, myLocal);
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PyPackageVersion that)) {
            return false;
        }
        return Objects.equals(myEpoch, that.myEpoch) &&
            myRelease.equals(that.myRelease) &&
            Objects.equals(myPre, that.myPre) &&
            Objects.equals(myPost, that.myPost) &&
            Objects.equals(myDev, that.myDev) &&
            Objects.equals(myLocal, that.myLocal);
    }

    @Override
    public int hashCode() {
        return Objects.hash(myEpoch, myRelease, myPre, myPost, myDev, myLocal);
    }

    @Override
    public String toString() {
        return "PyPackageVersion(epoch=" + myEpoch + ", release=" + myRelease + ", pre=" + myPre + ", post=" + myPost +
            ", dev=" + myDev + ", local=" + myLocal + ")";
    }
}
