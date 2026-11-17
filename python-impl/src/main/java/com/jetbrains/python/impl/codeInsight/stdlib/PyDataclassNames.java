// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.stdlib;

import com.google.common.collect.ImmutableSet;
import com.jetbrains.python.PyNames;

import java.util.List;
import java.util.Set;

public final class PyDataclassNames {
    private PyDataclassNames() {
    }

    public static final class Dataclasses {
        private Dataclasses() {
        }

        public static final String DATACLASSES_MISSING = "dataclasses.MISSING";
        public static final String DATACLASSES_INITVAR = "dataclasses.InitVar";
        public static final String DATACLASSES_FIELDS = "dataclasses.fields";
        public static final String DATACLASSES_ASDICT = "dataclasses.asdict";
        public static final String DATACLASSES_FIELD = "dataclasses.field";
        public static final String DATACLASSES_REPLACE = "dataclasses.replace";
        public static final String DATACLASSES_KW_ONLY = "dataclasses.KW_ONLY";
        public static final String DUNDER_POST_INIT = "__post_init__";
        public static final String DUNDER_SLOTS = PyNames.SLOTS;
        public static final String DUNDER_MATCH_ARGS = PyNames.MATCH_ARGS;

        /**
         * @see PyDataclassResolver#getOmittedDefaultQualifiedNames
         */
        public static final Set<String> OMITTED_DEFAULTS = ImmutableSet.of(DATACLASSES_MISSING);
        public static final List<String> DECORATOR_PARAMETERS =
            List.of("init", "repr", "eq", "order", "unsafe_hash", "frozen", "match_args", "kw_only", "slots");
        public static final Set<String> HELPER_FUNCTIONS = ImmutableSet.of(DATACLASSES_FIELDS, DATACLASSES_ASDICT, "dataclasses.astuple", DATACLASSES_REPLACE);
    }

    public static final class Attrs {
        private Attrs() {
        }

        public static final Set<String> ATTRS_NOTHING = ImmutableSet.of("attr.NOTHING", "attrs.NOTHING");
        public static final Set<String> ATTRS_FACTORY = ImmutableSet.of("attr.Factory", "attrs.Factory");
        public static final Set<String> ATTRS_ASSOC = ImmutableSet.of("attr.assoc", "attrs.assoc");
        public static final Set<String> ATTRS_EVOLVE = ImmutableSet.of("attr.evolve", "attrs.evolve");
        public static final Set<String> ATTRS_FROZEN = ImmutableSet.of("attr.frozen", "attrs.frozen");
        public static final String DUNDER_POST_INIT = "__attrs_post_init__";
        public static final String DUNDER_ATTRS = "__attrs_attrs__";
        public static final List<String> DECORATOR_PARAMETERS = List.of(
            "these",
            "repr_ns",
            "repr",
            "cmp",
            "hash",
            "init",
            "slots",
            "frozen",
            "weakref_slot",
            "str",
            "auto_attribs",
            "kw_only",
            "cache_hash",
            "auto_exc",
            "eq",
            "order",
            "match_args"
        );
        public static final Set<String> FIELD_FUNCTIONS = ImmutableSet.of(
            "attr.ib",
            "attr.attr",
            "attr.attrib",
            "attr.field",
            "attrs.field"
        );
        public static final Set<String> INSTANCE_HELPER_FUNCTIONS = ImmutableSet.of(
            "attr.asdict",
            "attr.astuple",
            "attr.assoc",
            "attr.evolve",
            "attrs.asdict",
            "attrs.astuple",
            "attrs.assoc",
            "attrs.evolve"
        );
        public static final Set<String> CLASS_HELPERS_FUNCTIONS = ImmutableSet.of(
            "attr.fields",
            "attr.fields_dict",
            "attrs.fields",
            "attrs.fields_dict"
        );
    }

    public static final class DataclassTransform {
        private DataclassTransform() {
        }

        public static final String DATACLASS_TRANSFORM_NAME = "dataclass_transform";

        public static final Set<String> DECORATOR_OR_CLASS_PARAMETERS = ImmutableSet.of(
            "init",
            "eq",
            "order",
            "unsafe_hash",
            "frozen",
            "match_args",
            "kw_only",
            "slots"
        );

        public static final Set<String> FIELD_SPECIFIER_PARAMETERS = ImmutableSet.of(
            "init",
            "default",
            "default_factory",
            "factory",
            "kw_only",
            "alias"
        );
    }
}
