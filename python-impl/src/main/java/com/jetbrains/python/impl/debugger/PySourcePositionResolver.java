// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger;

import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.impl.psi.types.PyLegacyDocstringTypeParser;
import com.jetbrains.python.impl.psi.types.PyModuleType;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyPsiFacade;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.execution.debug.XDebuggerUtil;
import consulo.execution.debug.XSourcePosition;
import consulo.execution.debug.frame.XStackFrame;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class PySourcePositionResolver {
    private final XStackFrame myCurrentFrame;
    private final Project myProject;

    public PySourcePositionResolver(XStackFrame currentFrame, Project project) {
        myCurrentFrame = currentFrame;
        myProject = project;
    }

    public XStackFrame getCurrentFrame() {
        return myCurrentFrame;
    }

    public Project getProject() {
        return myProject;
    }

    public @Nullable XSourcePosition getSourcePositionForName(@Nullable String name, @Nullable String parentType) {
        if (name == null) {
            return null;
        }
        XSourcePosition currentPosition = myCurrentFrame.getSourcePosition();
        if (currentPosition == null) {
            return null;
        }

        PsiFile file = getPsiFile(currentPosition);
        if (file == null) {
            return null;
        }

        if (StringUtil.isEmpty(parentType)) {
            Ref<@Nullable PsiElement> elementRef = resolveInCurrentFrame(name, currentPosition, file);
            return elementRef.isNull() ? null : XDebuggerUtil.getInstance().createPositionByElement(elementRef.get());
        }
        else {
            PyType parentDef = resolveTypeFromString(parentType, file);
            if (parentDef == null) {
                return null;
            }
            TypeEvalContext context = TypeEvalContext.codeInsightFallback(file.getProject());
            List<? extends RatedResolveResult> results =
                parentDef.resolveMember(name, null, AccessDirection.READ, PyResolveContext.defaultContext(context));
            if (results != null && !results.isEmpty()) {
                return XDebuggerUtil.getInstance().createPositionByElement(results.get(0).getElement());
            }
            else {
                return typeToPosition(parentDef); // at least try to return parent
            }
        }
    }

    public @Nullable XSourcePosition getSourcePositionForType(@Nullable String typeName) {
        XSourcePosition currentPosition = myCurrentFrame.getSourcePosition();

        PsiFile file = getPsiFile(currentPosition);

        if (typeName == null || !(file instanceof PyFile)) {
            return null;
        }


        PyType pyType = resolveTypeFromString(typeName, file);
        return pyType == null ? null : typeToPosition(pyType);
    }

    private Ref<@Nullable PsiElement> resolveInCurrentFrame(String name, XSourcePosition currentPosition, PsiFile file) {
        Ref<@Nullable PsiElement> elementRef = Ref.create();
        PsiElement currentElement = file.findElementAt(currentPosition.getOffset());

        if (currentElement == null) {
            return elementRef;
        }


        PyResolveUtil.scopeCrawlUp(new PsiScopeProcessor() {
            @Override
            public boolean execute(PsiElement element, ResolveState state) {
                if (element instanceof PyImportElement importElement) {
                    if (name.equals(importElement.getVisibleName())) {
                        if (elementRef.isNull()) {
                            elementRef.set(element);
                        }
                        return false;
                    }
                    return true;
                }
                else {
                    if (elementRef.isNull()) {
                        elementRef.set(element);
                    }
                    return false;
                }
            }
        }, currentElement, name, null);
        return elementRef;
    }

    private @Nullable PyType resolveTypeFromString(String typeName, PsiFile file) {
        String name = typeName.replace("__builtin__.", "");
        PyType pyType = null;
        if (!name.contains(".")) {
            pyType = PyLegacyDocstringTypeParser.getTypeByName(file, name);
        }
        if (pyType == null) {
            PyElementGenerator generator = PyElementGenerator.getInstance(myProject);
            PyPsiFacade psiFacade = PyPsiFacade.getInstance(myProject);
            PsiFile dummyFile = generator.createDummyFile(LanguageLevel.forElement(file), "");
            Module moduleForFile = ModuleUtilCore.findModuleForPsiElement(file);
            dummyFile.putUserData(ModuleUtilCore.KEY_MODULE, moduleForFile);

            pyType = psiFacade.parseTypeAnnotation(name, dummyFile);
        }
        return pyType;
    }

    private @Nullable XSourcePosition typeToPosition(@Nullable PyType pyType) {
        PyClassType classType = PyUtil.as(pyType, PyClassType.class);

        if (classType != null) {
            return XDebuggerUtil.getInstance().createPositionByElement(classType.getPyClass());
        }

        PyModuleType moduleType = PyUtil.as(pyType, PyModuleType.class);
        if (moduleType != null) {
            return XDebuggerUtil.getInstance().createPositionByElement(moduleType.getModule());
        }
        return null;
    }

    private @Nullable PsiFile getPsiFile(@Nullable XSourcePosition currentPosition) {
        if (currentPosition == null) {
            return null;
        }

        return PsiManager.getInstance(myProject).findFile(currentPosition.getFile());
    }
}
