/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstAssignmentStatement;
import com.jetbrains.python.ast.PyAstDelStatement;
import com.jetbrains.python.ast.PyAstElement;
import com.jetbrains.python.ast.PyAstExpression;
import com.jetbrains.python.ast.PyAstTargetExpression;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiElement;

/** How we refer to a name */
public enum AccessDirection {

    /** Reference */
    READ,

    /** Target of assignment */
    WRITE,

    /** Target of del statement */
    DELETE;

    /**
     * @return the access direction of element, judging from surrounding statements.
     */
    @RequiredReadAction
    public static AccessDirection of(PyAstElement element) {
        PsiElement parent = element.getParent();
        if (element instanceof PyAstTargetExpression) {
            return WRITE;
        }
        else if (parent instanceof PyAstAssignmentStatement) {
            for (PyAstExpression target : ((PyAstAssignmentStatement)parent).getTargets()) {
                if (target == element) {
                    return WRITE;
                }
            }
        }
        else if (parent instanceof PyAstDelStatement) {
            return DELETE;
        }
        return READ;
    }
}
