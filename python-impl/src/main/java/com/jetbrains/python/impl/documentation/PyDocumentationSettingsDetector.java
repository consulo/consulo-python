// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.documentation;

import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.documentation.docstrings.DocStringParser;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import consulo.application.ReadAction;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.function.Function;

public final class PyDocumentationSettingsDetector
    implements Function<Pair<Module, Collection<VirtualFile>>, PyDocumentationSettings.@Nullable ServiceState> {

    private static final Logger LOG = Logger.getInstance(PyDocumentationSettingsDetector.class);

    @Override
    public PyDocumentationSettings.@Nullable ServiceState apply(Pair<Module, Collection<VirtualFile>> pair) {
        Module module = pair.first;
        if (module.isDisposed()) {
            return null;
        }
        // TODO consulo: Application.assertIsNonDispatchThread() missing

        for (VirtualFile file : pair.second) {
            DocStringFormat docFormat = ReadAction.compute(() -> checkDocString(file, module));    // detect docstring type
            if (docFormat != PyDocumentationSettings.DEFAULT_DOC_STRING_FORMAT) {
                LOG.debug("Docstring format '" + docFormat + "' was detected from content of the file '" + file.getPath() + "'");
                return new PyDocumentationSettings.ServiceState(docFormat);
            }
        }
        return null;
    }

    private static DocStringFormat checkDocString(VirtualFile file, Module module) {
        final PsiFile psiFile = PsiManager.getInstance(module.getProject()).findFile(file);
        if (psiFile instanceof PyFile) {
            final DocStringFormat perFileFormat = PyDocumentationSettings.getFormatFromDocformatAttribute(psiFile);
            if (perFileFormat != null) {
                return perFileFormat;
            }
            // Why toplevel docstring owners only
            final PyDocStringOwner[] children = PsiTreeUtil.getChildrenOfType(psiFile, PyDocStringOwner.class);
            if (children != null) {
                for (PyDocStringOwner owner : children) {
                    final PyStringLiteralExpression docStringExpression = owner.getDocStringExpression();
                    if (docStringExpression != null) {
                        final DocStringFormat guessed = DocStringParser.guessDocStringFormat(docStringExpression.getStringValue());
                        if (guessed != PyDocumentationSettings.DEFAULT_DOC_STRING_FORMAT) {
                            return guessed;
                        }
                    }
                }
            }
        }
        return PyDocumentationSettings.DEFAULT_DOC_STRING_FORMAT;
    }
}
