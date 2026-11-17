// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package consulo.python.regexp.impl.internal;

import consulo.language.file.FileViewProvider;
import consulo.language.psi.ContributedReferenceHost;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiReferenceService;
import org.intellij.lang.regexp.RegExpFile;

public class DjangoRegexpFile extends RegExpFile implements ContributedReferenceHost {
    public DjangoRegexpFile(FileViewProvider viewProvider) {
        super(viewProvider, PythonRegexpLanguage.INSTANCE);
    }

    @Override
    public PsiReference[] getReferences() {
        return PsiReferenceService.getService().getContributedReferences(this);
    }
}
