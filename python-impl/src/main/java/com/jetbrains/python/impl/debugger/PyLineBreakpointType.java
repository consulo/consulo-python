// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger;

import com.google.common.collect.Sets;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.scratch.ScratchUtil;
import consulo.language.Language;
import consulo.language.util.LanguageUtil;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.virtualFileSystem.fileType.FileTypeRegistry;
import consulo.project.Project;
import consulo.util.lang.ref.Ref;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.ast.IElementType;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.execution.debug.XDebuggerUtil;
import consulo.execution.debug.breakpoint.SuspendPolicy;
import consulo.execution.debug.breakpoint.XLineBreakpointTypeBase;
import consulo.execution.debug.evaluation.XDebuggerEditorsProvider;
import consulo.python.impl.localize.PyLocalize;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import org.jspecify.annotations.Nullable;

import java.util.Set;


@ExtensionImpl
public class PyLineBreakpointType extends XLineBreakpointTypeBase {
    public static final String ID = "python-line";

    private static final Set<IElementType> UNSTOPPABLE_ELEMENT_TYPES = Sets.newHashSet(PyTokenTypes.TRIPLE_QUOTED_STRING,
                                                                                       PyTokenTypes.SINGLE_QUOTED_STRING,
                                                                                       PyTokenTypes.SINGLE_QUOTED_UNICODE,
                                                                                       PyTokenTypes.DOCSTRING);

    @SuppressWarnings("unchecked") private static final Class<? extends PsiElement>[] UNSTOPPABLE_ELEMENTS = new Class[]{PsiWhiteSpace.class, PsiComment.class};

    public static PyLineBreakpointType getInstance() {
        return EXTENSION_POINT_NAME.findExtensionOrFail(PyLineBreakpointType.class);
    }

    public PyLineBreakpointType() {
        super(ID, PyLocalize.debuggerLineBreakpointType().get(), new PyDebuggerEditorsProvider());
    }

    public PyLineBreakpointType(String id, String title, @Nullable XDebuggerEditorsProvider editorsProvider) {
        super(id, title, editorsProvider);
    }

    // Consulo: XLineBreakpointType has no canPutAt, PyLineBreakpointTypeResolver calls it
    public boolean canPutAt(VirtualFile file, int line, Project project) {
        Ref<Boolean> stoppable = Ref.create(false);
        Document document = FileDocumentManager.getInstance().getDocument(file);
        if (document != null && isSuitableFileType(project, file)) {
            lineHasStoppablePsi(project, line, document, getUnstoppableElements(), getUnstoppableElementTypes(), stoppable);
        }

        return stoppable.get();
    }

    protected boolean isSuitableFileType(Project project, VirtualFile file) {
        return FileTypeRegistry.getInstance().isFileOfType(file, getFileType()) ||
               (ScratchUtil.isScratch(file) && LanguageUtil.getLanguageForPsi(project, file) == getFileLanguage());
    }

    protected FileType getFileType() {
        return PythonFileType.INSTANCE;
    }

    protected Language getFileLanguage() {
        return PythonLanguage.INSTANCE;
    }

    protected Set<IElementType> getUnstoppableElementTypes() {
        return UNSTOPPABLE_ELEMENT_TYPES;
    }

    protected Class<? extends PsiElement>[] getUnstoppableElements() {
        return UNSTOPPABLE_ELEMENTS;
    }

    /**
     * We can't rely only on file type, because there are Cython files which contain
     * Python & Cython elements and there are Jupyter files which contain Python & Markdown elements
     * That's why we should check that there is at least one stoppable psiElement at the line
     *
     * @param psiElement to check
     * @return true if psiElement is compatible with breakpoint type and false otherwise
     */
    protected boolean isPsiElementStoppable(PsiElement psiElement) {
        return psiElement.getLanguage() == PythonLanguage.INSTANCE;
    }

    protected void lineHasStoppablePsi(Project project,
                                       int line,
                                       Document document,
                                       Class<? extends PsiElement>[] unstoppablePsiElements,
                                       Set<IElementType> unstoppableElementTypes,
                                       Ref<? super Boolean> stoppable) {
        XDebuggerUtil.getInstance().iterateLine(project, document, line, psiElement -> {
            if (PsiTreeUtil.getNonStrictParentOfType(psiElement, unstoppablePsiElements) != null) {
                return true;
            }
            if (psiElement.getNode() != null && unstoppableElementTypes.contains(psiElement.getNode().getElementType())) {
                return true;
            }
            if (isPsiElementStoppable(psiElement)) {
                stoppable.set(true);
            }
            return false;
        });

        if (PyDebugSupportUtils.isContinuationLine(document, line - 1)) {
            stoppable.set(false);
        }
    }

    @Override
    public boolean isSuspendThreadSupported() {
        return true;
    }

    @Override
    public SuspendPolicy getDefaultSuspendPolicy() {
        return SuspendPolicy.THREAD;
    }

    // TODO consulo: XLineBreakpointType.supportsInterLinePlacement missing
    public boolean supportsInterLinePlacement() {
        return true;
    }

    @Override
    public String getBreakpointsDialogHelpTopic() {
        return "reference.dialogs.breakpoints";
    }
}
