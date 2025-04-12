/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.jetbrains.python.impl.psi.search;

import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.search.DefinitionsScopedSearch;
import consulo.language.psi.search.DefinitionsScopedSearchExecutor;
import jakarta.annotation.Nonnull;

import java.util.function.Predicate;

/**
 * @author yole
 */
@ExtensionImpl
public class PyDefinitionsSearch implements DefinitionsScopedSearchExecutor {
    @Override
    public boolean execute(
        @Nonnull DefinitionsScopedSearch.SearchParameters parameters,
        @Nonnull Predicate<? super PsiElement> consumer
    ) {
        PsiElement element = parameters.getElement();
        if (element instanceof PyClass pyClass) {
            return PyClassInheritorsSearch.search(pyClass, true).forEach(consumer::test);
        }
        else if (element instanceof PyFunction function) {
            return PyOverridingMethodsSearch.search(function, true).forEach(consumer::test);
        }
        else if (element instanceof PyTargetExpression && element.getParent() instanceof PyAssignmentStatement assignment) {
            return consumer.test(assignment); // PY-237
        }
        return true;
    }
}
