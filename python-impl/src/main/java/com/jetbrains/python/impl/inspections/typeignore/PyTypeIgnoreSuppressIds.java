// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.typeignore;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.inspections.PySuppressionUtil;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.component.extension.ExtensionPointCacheKey;
import consulo.language.editor.inspection.InspectionTool;
import consulo.language.editor.inspection.LocalInspectionTool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Caches, for every registered inspection, its suppress id (the tool id passed to
 * {@link consulo.language.editor.inspection.InspectionSuppressor}) and the reverse mapping from its industry-standard
 * kebab-case alias (e.g. {@code unresolved-references}) back to that id. It also keeps the list of Python inspections
 * for {@link PyIgnoreCodeCompletionContributor}. {@link TypeIgnoreInspectionSuppressor} uses this
 * to tell a PyCharm inspection code such as {@code PyTypeChecker} / {@code unresolved-references} apart from a
 * foreign/mypy code such as {@code attr-defined} inside {@code # type: ignore[...]} / {@code # pycharm: ignore[...]}.
 * <p>
 * Consulo: JB keeps a clearable lazy index dropped on inspection EP changes; here the index is an
 * extension point cache of {@link InspectionTool}, which is rebuilt when the extensions change.
 */
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
@Singleton
public final class PyTypeIgnoreSuppressIds {
    private static final ExtensionPointCacheKey<InspectionTool, SuppressIdIndex> INDEX_KEY =
        ExtensionPointCacheKey.create("PyTypeIgnoreSuppressIds", walker -> {
            List<InspectionTool> tools = new ArrayList<>();
            walker.walk(tools::add);
            return computeIndex(tools);
        });

    private final Application myApplication;

    @Inject
    public PyTypeIgnoreSuppressIds(Application application) {
        myApplication = application;
    }

    static PyTypeIgnoreSuppressIds getInstance() {
        return Application.get().getInstance(PyTypeIgnoreSuppressIds.class);
    }

    private SuppressIdIndex index() {
        return myApplication.getExtensionPoint(InspectionTool.class).getOrBuildCache(INDEX_KEY);
    }

    boolean isKnownSuppressId(String suppressId) {
        return index().suppressIds.contains(suppressId);
    }

    /**
     * Maps a kebab-case alias (e.g. {@code unresolved-references}) back to its suppress id, or {@code null} if unknown.
     */
    @Nullable String resolveKebabAlias(String code) {
        return index().kebabAliasToId.get(code);
    }

    /**
     * The Python inspections, one entry for each suppress id, in registration order.
     */
    List<PythonInspection> pythonInspections() {
        return index().pythonInspections;
    }

    /**
     * A Python inspection. {@code kebabAlias} is {@code null} when the suppress id has no alias.
     */
    record PythonInspection(String suppressId, @Nullable String kebabAlias, String displayName) {
    }

    private record SuppressIdIndex(Set<String> suppressIds,
                                   Map<String, String> kebabAliasToId,
                                   List<PythonInspection> pythonInspections) {
    }

    private static SuppressIdIndex computeIndex(List<InspectionTool> tools) {
        Set<String> ids = new HashSet<>();
        Map<String, String> kebabAliasToId = new HashMap<>();
        Map<String, PythonInspection> pythonInspections = new LinkedHashMap<>();
        for (InspectionTool tool : tools) {
            // JB: local inspections use "ep.id ?: ep.shortName", global inspections use "ep.shortName"
            String id = tool instanceof LocalInspectionTool localTool ? localTool.getID() : tool.getShortName();
            ids.add(id);
            String alias = PySuppressionUtil.toSuppressionCode(id);
            if (alias != null) {
                kebabAliasToId.put(alias, id);
            }
            if (tool instanceof LocalInspectionTool && tool.getLanguage() == PythonLanguage.INSTANCE && !pythonInspections.containsKey(id)) {
                String displayName = tool.getDisplayName().get();
                pythonInspections.put(id, new PythonInspection(id, alias, displayName.isEmpty() ? id : displayName));
            }
        }
        return new SuppressIdIndex(ids, kebabAliasToId, List.copyOf(pythonInspections.values()));
    }
}
