// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import consulo.util.collection.ContainerUtil;
import com.jetbrains.python.impl.codeInsight.PyCodeInsightCounters;
import com.jetbrains.python.impl.codeInsight.PyCodeInsightCounters.Counter;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableParameterListType;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyUnpackedTupleType;
import com.jetbrains.python.psi.types.TypeEvalContext;

/**
 * Recursively traverses {@link PyType} trees in DFS order.
 * <p>
 * Should be used as follows:
 * <pre>{@code
 *     PyRecursiveTypeVisitor.traverse(type, new PyRecursiveTypeVisitor.PyTypeTraverser() {
 *       @Override
 *       public Traversal visitPyType(PyType type) {
 *         if (isTheRightOne(type)) {
 *           return Traversal.TERMINATE;
 *         }
 *         return Traversal.CONTINUE;
 *       }
 *
 *       @Override
 *       public Traversal visitPyClassType(PyClassType classType) {
 *         // Irrelevant, don't go deeper
 *         return Traversal.PRUNE;
 *       }
 *     });
 * }</pre>
 */
public final class PyRecursiveTypeVisitor extends PyTypeVisitorExt<PyRecursiveTypeVisitor.Traversal> {
    private final PyTypeTraverser myTraverser;
    private final PyTypeComponentsStrategy myComponentsStrategy;
    private final Set<PyType> myVisited;

    private PyRecursiveTypeVisitor(PyTypeTraverser traverser, TypeEvalContext context) {
        myComponentsStrategy = new TypeHintComponents(context);
        myTraverser = traverser;
        myVisited = new HashSet<>();
    }

    @Override
    public Traversal visitPyType(PyType type) {
        if (!myVisited.add(type)) {
            return Traversal.CONTINUE;
        }
        PyCodeInsightCounters.inc(Counter.TYPE_TRAVERSAL_VISITS);
        try {
            Traversal next = visit(type, myTraverser);
            if (next == Traversal.TERMINATE) {
                return Traversal.TERMINATE;
            }
            if (next != Traversal.PRUNE) {
                for (@Nullable PyType childType : visit(type, myComponentsStrategy)) {
                    if (visit(childType, this) == Traversal.TERMINATE) {
                        return Traversal.TERMINATE;
                    }
                }
            }
            return Traversal.CONTINUE;
        }
        finally {
            myVisited.remove(type);
        }
    }

    @Override
    public Traversal visitUnknownType() {
        Traversal traversal = myTraverser.visitUnknownType();
        return traversal == Traversal.TERMINATE ? Traversal.TERMINATE : Traversal.CONTINUE;
    }

    public enum Traversal {
        /**
         * Continue the depth-first traversal normally.
         */
        CONTINUE,
        /**
         * Don't traverse the current subtree, but continue otherwise.
         */
        PRUNE,
        /**
         * Completely stop the traversal.
         */
        TERMINATE,
    }

    public static abstract class PyTypeTraverser extends PyTypeVisitorExt<Traversal> {
        @Override
        public Traversal visitPyType(PyType type) {
            return Traversal.CONTINUE;
        }

        @Override
        public Traversal visitUnknownType() {
            return Traversal.CONTINUE;
        }
    }

    private static abstract class PyTypeComponentsStrategy extends PyTypeVisitorExt<List<@Nullable PyType>> {
        @Override
        public List<@Nullable PyType> visitPyType(PyType type) {
            return Collections.emptyList();
        }

        @Override
        public List<@Nullable PyType> visitUnknownType() {
            return Collections.emptyList();
        }
    }

    public static void traverse(@Nullable PyType type, TypeEvalContext context, PyTypeTraverser delegate) {
        visit(type, new PyRecursiveTypeVisitor(delegate, context));
    }

    /**
     * Visits only those component types that appear inside type expressions.
     * For instance, it skips bounds and constraints of {@link PyTypeVarType}, defaults of any
     * {@link PyTypeParameterType}, as well as fields of {@link PyNamedTupleType}.
     */
    private static class TypeHintComponents extends PyTypeComponentsStrategy {
        private final TypeEvalContext myTypeEvalContext;

        protected TypeHintComponents(TypeEvalContext context) {
            myTypeEvalContext = context;
        }

        @Override
        public List<@Nullable PyType> visitPyCallableType(PyCallableType callableType) {
            List<PyType> result = new ArrayList<>();
            List<PyCallableParameter> parameters = callableType.getParameters(myTypeEvalContext);
            if (parameters != null) {
                for (PyCallableParameter parameter : parameters) {
                    result.add(parameter.getType(myTypeEvalContext));
                }
            }
            result.add(callableType.getReturnType(myTypeEvalContext));
            return result;
        }

        @Override
        public List<@Nullable PyType> visitPyOverloadType(PyOverloadType overloadType) {
            return Collections.unmodifiableList(new ArrayList<>(overloadType.getItems()));
        }

        @Override
        public List<@Nullable PyType> visitPyClassType(PyClassType classType) {
            return classType.getTypeArguments();
        }

        @Override
        public List<@Nullable PyType> visitPyConcatenateType(PyConcatenateType concatenateType) {
            // TODO consulo: ContainerUtil.append(List, T...) missing
            return ContainerUtil.concat(concatenateType.getFirstTypes(), Collections.singletonList(concatenateType.getParamSpec()));
        }

        @Override
        public List<@Nullable PyType> visitPyTypedDictType(PyTypedDictType typedDictType) {
            // Read from the declaration, so a recursive TypedDict does not have to evaluate the item it is in the middle of.
            List<PyType> typeArguments = typedDictType.getTypeArgumentsOrDeclaredParameters();
            if (typeArguments != null) {
                return typeArguments;
            }
            // TODO this is not entirely correct. Field types of a non-generic typed dict are not used in its notation.
            return ContainerUtil.map(typedDictType.fields(myTypeEvalContext).values(), field -> field.getType());
        }

        @Override
        public List<@Nullable PyType> visitPyCallableParameterListType(PyCallableParameterListType callableParameterListType) {
            return ContainerUtil.map(callableParameterListType.getParameters(), param -> param.getType(myTypeEvalContext));
        }

        @Override
        public List<@Nullable PyType> visitPyTupleType(PyTupleType tupleType) {
            return tupleType.getElementTypes();
        }

        @Override
        public List<@Nullable PyType> visitPyNamedTupleType(PyNamedTupleType namedTupleType) {
            // Types of named tuple fields don't belong to its notation.
            // Unlike regular tuple, it's a named type, i.e. the type of
            //  
            // class Person(NamedTuple):
            //     name: str
            //     age: int
            //
            // is just Person, not Person[str, int]. The type arguments of a generic named tuple class, as in Base[str], belong to its notation.
            return namedTupleType.getTypeArguments();
        }

        @Override
        public List<@Nullable PyType> visitPyCompositeType(PyCompositeType compositeType) {
            return Collections.unmodifiableList(new ArrayList<>(compositeType.getMembers()));
        }

        @Override
        public List<@Nullable PyType> visitPyUnpackedTupleType(PyUnpackedTupleType unpackedTupleType) {
            return unpackedTupleType.getElementTypes();
        }

        @Override
        public List<@Nullable PyType> visitPyNarrowedType(PyNarrowedType narrowedType) {
            return Collections.singletonList(narrowedType.getNarrowedType());
        }

        @Override
        public List<@Nullable PyType> visitPyTypeFormType(PyTypeFormType typeFormType) {
            return Collections.singletonList(typeFormType.getRepresentedType());
        }
    }
}
