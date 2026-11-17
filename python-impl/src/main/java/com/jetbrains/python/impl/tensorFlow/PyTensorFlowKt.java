// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.tensorFlow;

import com.jetbrains.python.impl.psi.resolve.MainPythonSdkKt;
import com.jetbrains.python.impl.psi.resolve.PackageAvailabilityService;
import com.jetbrains.python.impl.psi.resolve.PyQualifiedNameResolveContextImpl;
import com.jetbrains.python.impl.psi.resolve.PyResolveImportUtil;
import com.jetbrains.python.packaging.PyRequirement;
import com.jetbrains.python.packaging.requirement.PyRequirementRelation;
import com.jetbrains.python.packaging.requirement.PyRequirementVersionSpec;
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext;
import consulo.content.bundle.Sdk;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.QualifiedName;
import consulo.module.Module;
import consulo.util.lang.Pair;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

public final class PyTensorFlowKt {
    private PyTensorFlowKt() {
    }

    private static final List<Pair<VersionRange, Map<String, String>>> LAYOUT_PER_VERSION = List.of(
        Pair.create(new VersionRange("2.16.1", null), mapOf(
            // See https://github.com/tensorflow/tensorflow/commit/4be8c8d2d20c7297fd3fc6a036a21098b830c9bc
            "keras", "keras._tf_keras.keras",
            // losses, metrics, optimizers, initializers should be covered by the content of
            // typeshed/stubs/tensorflow/tensorflow/keras
            "initializers", "keras._tf_keras.keras.initializers",
            "losses", "keras._tf_keras.keras.losses",
            "metrics", "keras._tf_keras.keras.metrics",
            "optimizers", "keras._tf_keras.keras.optimizers",
            "security", "tensorflow.security",
            "*", "tensorflow._api.v2"
        )),
        Pair.create(new VersionRange("2.6.0rc0", "2.16.1"), mapOf(
            "keras", "keras.api._v2.keras",
            // losses, metrics, optimizers, initializers are not available as tensorflow submodules, only as its attributes
            // i.e. "from tensorflow import losses" is possible, but not "import tensorflow.losses".
            "estimator", "tensorflow_estimator.python.estimator.api._v2.estimator",
            "summary", "tensorboard.summary._tf.summary",
            "*", "tensorflow._api.v2"
        )),
        Pair.create(new VersionRange("2.0.0a0", "2.6.0rc0"), mapOf(
            "compiler", "tensorflow_core.compiler",
            "core", "tensorflow_core.core",
            "tools", "tensorflow_core.tools",
            "python", "tensorflow_core.python",
            "keras", "tensorflow_core.python.keras.api._v2.keras",
            "estimator", "tensorflow_estimator.python.estimator.api._v2.estimator",
            "initializers", "tensorflow_core.python.keras.api._v2.keras.initializers",
            "losses", "tensorflow_core.python.keras.api._v2.keras.losses",
            "metrics", "tensorflow_core.python.keras.api._v2.keras.metrics",
            "optimizers", "tensorflow_core.python.keras.api._v2.keras.optimizers",
            "summary", "tensorboard.summary._tf.summary",
            "*", "tensorflow_core._api.v2"
        )),
        Pair.create(new VersionRange("1.15.0rc0", "2.0.0a0"), mapOf(
            "compiler", "tensorflow_core.compiler",
            "core", "tensorflow_core.core",
            "tools", "tensorflow_core.tools",
            "python", "tensorflow_core.python",
            "keras", "tensorflow_core.python.keras.api._v1.keras",
            "estimator", "tensorflow_estimator.python.estimator.api._v1.estimator",
            "*", "tensorflow_core._api.v1"
        )),
        Pair.create(new VersionRange(null, "1.15.0rc0"), mapOf(
            "keras", "tensorflow.python.keras.api._v1.keras",
            "estimator", "tensorflow_estimator.python.estimator.api._v1.estimator",
            "*", "tensorflow._api.v1"
        ))
    );

    /**
     * Kotlin {@code mapOf(k to v, ...)}: an insertion-ordered map
     */
    private static Map<String, String> mapOf(String... keysAndValues) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            result.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return result;
    }

    /**
     * The TensorFlow layout of the interpreter that {@code context} resolves in: the one of its module, or its own.
     */
    static Map<String, String> getTensorFlowPathConfig(PyQualifiedNameResolveContext context) {
        Module module = context.getModule();
        Sdk interpreterOfModule = module != null ? MainPythonSdkKt.pythonSdkIfReady(module) : null;
        Sdk interpreter = interpreterOfModule != null ? interpreterOfModule : PyQualifiedNameResolveContextImpl.getStoredInterpreter(context);
        String version = interpreter != null
            ? PackageAvailabilityService.getInstance().installedPackageVersion(context.getProject(), interpreter, "tensorflow")
            : null;
        if (version == null) {
            return LAYOUT_PER_VERSION.get(0).getSecond();
        }
        for (Pair<VersionRange, Map<String, String>> pair : LAYOUT_PER_VERSION) {
            if (pair.getFirst().contains(version)) {
                return pair.getSecond();
            }
        }
        throw new NoSuchElementException("Collection contains no element matching the predicate.");
    }

    static @Nullable PsiElement takeFirstResolvedInTensorFlow(String qualifiedName, PyQualifiedNameResolveContext context) {
        List<PsiElement> resolved = PyResolveImportUtil.resolveQualifiedName(QualifiedName.fromDottedString(qualifiedName), context);
        return resolved.isEmpty() ? null : resolved.get(0);
    }

    private static final class VersionRange {
        private final @Nullable String myMinIncluded;
        private final @Nullable String myMaxExcluded;
        private final List<PyRequirementVersionSpec> myVersionSpecs;

        private VersionRange(@Nullable String minIncluded, @Nullable String maxExcluded) {
            myMinIncluded = minIncluded;
            myMaxExcluded = maxExcluded;
            List<PyRequirementVersionSpec> versionSpecs = new ArrayList<>();
            if (minIncluded != null) {
                // TODO consulo: com.intellij.python.requirements.pyRequirementVersionSpec missing - PyRequirement.calculateVersionSpec is its analog
                versionSpecs.add(PyRequirement.calculateVersionSpec(minIncluded, PyRequirementRelation.GTE));
            }
            if (maxExcluded != null) {
                versionSpecs.add(PyRequirement.calculateVersionSpec(maxExcluded, PyRequirementRelation.LT));
            }
            myVersionSpecs = versionSpecs;
        }

        boolean contains(String version) {
            for (PyRequirementVersionSpec spec : myVersionSpecs) {
                if (!spec.matches(version)) {
                    return false;
                }
            }
            return true;
        }
    }
}
