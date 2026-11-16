// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.impl.psi.impl.stubs;

import com.google.common.collect.BoundType;
import com.google.common.collect.ImmutableRangeSet;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.psi.impl.PyEvaluator;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyElsePart;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyIfPart;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.psi.PyStatementPart;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.stubs.PyVersionSpecificStub;
import consulo.application.util.CachedValue;
import consulo.application.util.CachedValueProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.util.dataholder.Key;
import consulo.util.lang.Version;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Port of the top-level functions of JB {@code PyVersionSpecificStubBase.kt}.
 */
public final class PyVersionSpecificStubBaseKt {
    // Consulo: JB caches the value via StubBuildCachedValuesManager.StubBuildCachedValueProvider("python.versionsForElement"),
    // which is missing in Consulo; the regular cached value (the JB fallback outside of stub building) is used instead
    private static final Key<CachedValue<ImmutableRangeSet<Version>>> EVAL_VERSIONS_KEY = Key.create("python.versionsForElement");

    private PyVersionSpecificStubBaseKt() {
    }

    public static Iterable<StubElement> getChildrenStubs(StubElement<?> stub, LanguageLevel languageLevel) {
        Version version = new Version(languageLevel.getMajorVersion(), languageLevel.getMinorVersion(), 0);
        List<StubElement> result = new ArrayList<>();
        for (StubElement it : stub.getChildrenStubs()) {
            if (!(it instanceof PyVersionSpecificStub versionSpecificStub) || versionSpecificStub.getVersions().contains(version)) {
                result.add(it);
            }
        }
        return result;
    }

    public static ImmutableRangeSet<Version> evaluateVersionsForElement(PsiElement element) {
        return LanguageCachedValueUtil.getCachedValue(element, EVAL_VERSIONS_KEY, () -> {
            PsiElement parent = element.getParent();
            ImmutableRangeSet<Version> result;
            if (parent == null) {
                result = ImmutableRangeSet.of(Range.<Version>all());
            }
            else {
                result = evaluateVersionsForElement(parent);
                if (parent instanceof PyIfPart || parent instanceof PyElsePart) {
                    PsiElement grandParent = parent.getParent();
                    if (grandParent instanceof PyIfStatement ifStatement
                        && element == ((PyStatementPart) parent).getStatementList()) {
                        RangeSet<Version> versions = evaluateVersionRangeForIfStatementPart(ifStatement, parent);
                        if (versions != null) {
                            result = result.intersection(versions);
                        }
                    }
                }
            }
            return CachedValueProvider.Result.create(result, element);
        });
    }

    private static @Nullable RangeSet<Version> evaluateVersionRangeForIfStatementPart(PyIfStatement ifStatement,
                                                                                      PsiElement ifStatementPart) {
        assert ifStatementPart instanceof PyIfPart || ifStatementPart instanceof PyElsePart;
        RangeSet<Version> result;
        if (ifStatementPart instanceof PyIfPart ifPart) {
            ImmutableRangeSet<Version> versionRanges = convertToVersionRanges(ifPart);
            if (versionRanges == null) {
                return null;
            }
            result = TreeRangeSet.create(versionRanges);
        }
        else {
            result = TreeRangeSet.create(List.of(Range.<Version>all()));
        }
        List<PyIfPart> ifParts = new ArrayList<>();
        ifParts.add(ifStatement.getIfPart());
        ifParts.addAll(List.of(ifStatement.getElifParts()));
        for (PyIfPart ifPart : ifParts) {
            if (ifPart == ifStatementPart) {
                break;
            }
            ImmutableRangeSet<Version> versionRanges = convertToVersionRanges(ifPart);
            if (versionRanges == null) {
                return null;
            }
            result.removeAll(versionRanges);
        }
        return result;
    }

    /**
     * @return Version ranges if {@code expression} is a version check, {@code null} otherwise
     * @see <a href="https://peps.python.org/pep-0484/#version-and-platform-checking">Version and Platform Checks</a>
     */
    private static @Nullable ImmutableRangeSet<Version> convertToVersionRanges(PyIfPart ifPart) {
        PyExpression condition = ifPart.getCondition();
        return condition != null ? convertToVersionRanges(condition) : null;
    }

    private static @Nullable ImmutableRangeSet<Version> convertToVersionRanges(PyExpression expression) {
        if (!(PyPsiUtils.flattenParens(expression) instanceof PyBinaryExpression binaryExpr)) {
            return null;
        }
        PyElementType operator = binaryExpr.getOperator();
        if (operator == PyTokenTypes.AND_KEYWORD || operator == PyTokenTypes.OR_KEYWORD) {
            PyExpression rhs = binaryExpr.getRightExpression();
            if (rhs == null) {
                return null;
            }
            ImmutableRangeSet<Version> ranges1 = convertToVersionRanges(binaryExpr.getLeftExpression());
            if (ranges1 == null) {
                return null;
            }
            ImmutableRangeSet<Version> ranges2 = convertToVersionRanges(rhs);
            if (ranges2 == null) {
                return null;
            }
            return operator == PyTokenTypes.AND_KEYWORD
                ? ranges1.intersection(ranges2)
                : ranges1.union(ranges2);
        }
        else if (operator == PyTokenTypes.LT || operator == PyTokenTypes.GT || operator == PyTokenTypes.LE || operator == PyTokenTypes.GE) {
            if (!PyEvaluator.isSysVersionInfoExpression(binaryExpr.getLeftExpression())) {
                return null;
            }

            int @Nullable [] versionArray = PyEvaluator.evaluateAsVersion(binaryExpr.getRightExpression());
            if (versionArray == null || versionArray.length == 0) {
                return null;
            }
            int major = versionArray[0];
            int minor = versionArray.length > 1 ? versionArray[1] : 0;
            Version version = new Version(major, minor, 0);

            Range<Version> range;
            if (operator == PyTokenTypes.LT) {
                range = Range.lessThan(version);
            }
            else if (operator == PyTokenTypes.GT) {
                range = Range.greaterThan(version);
            }
            else if (operator == PyTokenTypes.LE) {
                range = Range.atMost(version);
            }
            else if (operator == PyTokenTypes.GE) {
                range = Range.atLeast(version);
            }
            else {
                throw new IllegalStateException();
            }
            return ImmutableRangeSet.of(range);
        }
        else {
            return null;
        }
    }

    public static void serializeVersions(RangeSet<Version> versions, StubOutputStream outputStream) throws IOException {
        Set<Range<Version>> ranges = versions.asRanges();
        outputStream.writeVarInt(ranges.size());
        for (Range<Version> range : ranges) {
            serializeRange(range, outputStream);
        }
    }

    private static void serializeRange(Range<Version> range, StubOutputStream outputStream) throws IOException {
        Endpoint low = range.hasLowerBound() ? new Endpoint(range.lowerEndpoint(), range.lowerBoundType()) : null;
        Endpoint high = range.hasUpperBound() ? new Endpoint(range.upperEndpoint(), range.upperBoundType()) : null;
        serializeEndpoint(low, outputStream);
        serializeEndpoint(high, outputStream);
    }

    private static void serializeEndpoint(@Nullable Endpoint endpoint, StubOutputStream outputStream) throws IOException {
        if (endpoint == null) {
            outputStream.writeByte(EndpointType.UNBOUND);
        }
        else {
            int endpointType = switch (endpoint.boundType()) {
                case OPEN -> EndpointType.OPEN;
                case CLOSED -> EndpointType.CLOSED;
            };
            outputStream.writeByte(endpointType);
            outputStream.writeVarInt(endpoint.version().major);
            outputStream.writeVarInt(endpoint.version().minor);
        }
    }

    public static RangeSet<Version> deserializeVersions(StubInputStream stream) throws IOException {
        int size = stream.readVarInt();
        ImmutableRangeSet.Builder<Version> builder = ImmutableRangeSet.builder();
        for (int i = 0; i < size; i++) {
            builder.add(deserializeRange(stream));
        }
        return builder.build();
    }

    private static Range<Version> deserializeRange(StubInputStream stream) throws IOException {
        Endpoint low = deserializeEndpoint(stream);
        Endpoint high = deserializeEndpoint(stream);
        if (low != null && high != null) {
            return Range.range(low.version(), low.boundType(), high.version(), high.boundType());
        }
        else if (high != null) {
            return Range.upTo(high.version(), high.boundType());
        }
        else if (low != null) {
            return Range.downTo(low.version(), low.boundType());
        }
        else {
            return Range.all();
        }
    }

    private static @Nullable Endpoint deserializeEndpoint(StubInputStream stream) throws IOException {
        int endpointType = stream.readByte();
        BoundType boundType = switch (endpointType) {
            case EndpointType.UNBOUND -> null;
            case EndpointType.OPEN -> BoundType.OPEN;
            case EndpointType.CLOSED -> BoundType.CLOSED;
            default -> throw new IllegalArgumentException();
        };
        if (boundType == null) {
            return null;
        }
        int major = stream.readVarInt();
        int minor = stream.readVarInt();
        return new Endpoint(new Version(major, minor, 0), boundType);
    }

    private record Endpoint(Version version, BoundType boundType) {
    }

    private static final class EndpointType {
        static final int UNBOUND = 0;
        static final int OPEN = 1;
        static final int CLOSED = 2;
    }
}
