// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.documentation;

import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.impl.pyi.PyiFile;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.application.ReadAction;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.language.editor.documentation.ExternalDocumentationProvider;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.util.QualifiedName;
import consulo.language.util.ModuleUtilCore;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.python.impl.localize.PyCodeInsightLocalize;
import consulo.ui.ex.awt.Messages;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.List;

@ExtensionImpl(id = "pythonExternalDocumentationProvider", order = "before pythonDocumentationProvider")
public final class PythonExternalDocumentationProvider extends PythonDocumentationProvider implements ExternalDocumentationProvider {
    private static final Logger LOG = Logger.getInstance(PythonExternalDocumentationProvider.class);

    @Override
    public @Nullable String fetchExternalDocumentation(Project project, PsiElement element, List<String> docUrls) {
        // TODO consulo: ExternalDocumentationProvider#fetchExternalDocumentation(..., boolean onHover) missing
        PsiNamedElement namedElement = ReadAction.compute(() -> {
            PsiElement moduleAnchor = element instanceof PsiDirectory ? element : element.getContainingFile();
            Module module = ModuleUtilCore.findModuleForPsiElement(moduleAnchor);
            if (module != null && !PyDocumentationSettings.getInstance(module).isRenderExternalDocumentation()) {
                return null;
            }

            PsiFileSystemItem file = PythonDocumentationProvider.getFile(element);

            if (file == null) {
                return null;
            }

            if (file instanceof PyiFile) {
                return null;
            }

            return PythonDocumentationProvider.getNamedElement(element);
        });


        if (namedElement != null) {
            // TODO consulo: org.jsoup library missing, PythonDocumentationLinkProvider#quickDocExtractor missing - JB logic:
            //  PythonDocumentationMap documentationMap = PythonDocumentationMap.getInstance();
            //  for (String url : docUrls) {
            //    Supplier<Document> documentSupplier = Suppliers.memoize(() -> {
            //      try {
            //        return Jsoup.parse(new URL(url), 1000);
            //      }
            //      catch (IOException e) {
            //        String message = "Can't read external doc URL: " + url;
            //        // Report as errors to EA only URL-induced problems with built-in documentation endpoints
            //        if ((e instanceof HttpStatusException || e instanceof UnknownHostException) && !documentationMap.isUserDefinedUrl(url)) {
            //          LOG.error(message, e);
            //        }
            //        else {
            //          LOG.warn(message, e);
            //        }
            //        return null;
            //      }
            //    });
            //
            //    for (PythonDocumentationLinkProvider documentationLinkProvider : PythonDocumentationLinkProvider.EP_NAME.getExtensionList()) {
            //      String providedUrl = ReadAction.compute(() -> documentationLinkProvider.getExternalDocumentationUrl(namedElement, namedElement));
            //      if (!url.equals(providedUrl)) {
            //        continue;
            //      }
            //
            //      Function<Document, String> quickDocExtractor = documentationLinkProvider.quickDocExtractor(namedElement);
            //
            //      if (quickDocExtractor != null) {
            //        Document document = documentSupplier.get();
            //        if (document != null) {
            //          String quickDoc = ReadAction.compute(() -> quickDocExtractor.apply(document));
            //          if (StringUtil.isNotEmpty(quickDoc)) {
            //            return quickDoc;
            //          }
            //        }
            //      }
            //    }
            //  }
        }

        return null;
    }

    @Override
    public boolean hasDocumentationFor(PsiElement element, PsiElement originalElement) {
        return PythonDocumentationProvider.getOnlyUrlFor(element, originalElement) != null;
    }

    @Override
    public boolean canPromptToConfigureDocumentation(PsiElement element) {
        PsiFile containingFile = element.getContainingFile();
        if (containingFile instanceof PyFile) {
            Project project = element.getProject();
            VirtualFile vFile = containingFile.getVirtualFile();
            if (vFile != null && ProjectRootManager.getInstance(project).getFileIndex().isInLibraryClasses(vFile)) {
                QualifiedName qName = QualifiedNameFinder.findCanonicalImportPath(element, element);
                if (qName != null && qName.getComponentCount() > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void promptToConfigureDocumentation(PsiElement element) {
        Project project = element.getProject();
        QualifiedName qName = QualifiedNameFinder.findCanonicalImportPath(element, element);
        if (qName != null && qName.getComponentCount() > 0) {
            showNoExternalDocumentationDialog(project, qName);
        }
    }

    private static void showNoExternalDocumentationDialog(Project project, QualifiedName qName) {
        Application.get().invokeLater(() -> {
            int rc = Messages.showOkCancelDialog(project,
                PyCodeInsightLocalize.externalDocumentationConfigureDescription(qName.getComponents().get(0)).get(),
                PyCodeInsightLocalize.externalDocumentationTitle().get(),
                Messages.getQuestionIcon());
            if (rc == Messages.OK) {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, DOCUMENTATION_CONFIGURABLE_ID, "");
            }
        });
    }
}
