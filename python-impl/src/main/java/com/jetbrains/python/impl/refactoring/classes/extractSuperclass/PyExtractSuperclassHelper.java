// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.classes.extractSuperclass;

import com.google.common.base.Predicate;
import consulo.application.ApplicationManager;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.lang.Comparing;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.editor.refactoring.event.RefactoringEventData;
import consulo.language.editor.refactoring.event.RefactoringEventListener;
import java.util.function.Function;
import consulo.util.io.PathUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.JBIterable;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.impl.refactoring.PyPsiRefactoringUtil;
import com.jetbrains.python.impl.refactoring.classes.PyClassRefactoringUtil;
import com.jetbrains.python.impl.refactoring.classes.membersManager.MembersManager;
import com.jetbrains.python.impl.refactoring.classes.membersManager.PyMemberInfo;
import com.jetbrains.python.impl.refactoring.classes.membersManager.PyMembersUtil;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static com.jetbrains.python.impl.psi.resolve.PyNamespacePackageUtil.isInNamespacePackage;
import static com.jetbrains.python.impl.refactoring.classes.PyClassRefactoringUtil.placeFile;

/**
 * @author Dennis.Ushakov
 */
public final class PyExtractSuperclassHelper {
    private static final Logger LOG = Logger.getInstance(PyExtractSuperclassHelper.class);
    /**
     * Accepts only those members whose element is PyClass object (new classes)
     */
    private static final Predicate<PyMemberInfo<PyElement>> ALLOW_OBJECT = new PyMembersUtil.ObjectPredicate(true);

    private PyExtractSuperclassHelper() {
    }

    public static void extractSuperclass(PyClass clazz,
                                         Collection<PyMemberInfo<PyElement>> selectedMemberInfos,
                                         String superBaseName,
                                         String targetFile) {
        Project project = clazz.getProject();

        //We will need to change it probably while param may be read-only
        //noinspection AssignmentToMethodParameter
        selectedMemberInfos = new ArrayList<>(selectedMemberInfos);

        RefactoringEventData beforeData = new RefactoringEventData();
        beforeData.addElements(JBIterable.from(selectedMemberInfos).transform(
            (Function<PyMemberInfo<PyElement>, PsiElement>)info -> info.getMember()).toList());

        project.getMessageBus().syncPublisher(RefactoringEventListener.class)
            .refactoringStarted(getRefactoringId(), beforeData);

        // PY-12171
        PyMemberInfo<PyElement> objectMember = MembersManager.findMember(selectedMemberInfos, ALLOW_OBJECT);
        if (LanguageLevel.forElement(clazz).isPy3K() && !isObjectParentDeclaredExplicitly(clazz)) {
            // Remove object from list if Py3
            if (objectMember != null) {
                selectedMemberInfos.remove(objectMember);
            }
        }
        else {
            // Always add object if < Py3
            if (objectMember == null) {
                PyMemberInfo<PyElement> object = MembersManager.findMember(clazz, ALLOW_OBJECT);
                if (object != null) {
                    selectedMemberInfos.add(object);
                }
            }
        }

        String text = "class " + superBaseName + ":\n  pass" + "\n";
        PyClass newClass = PyElementGenerator.getInstance(project).createFromText(LanguageLevel.getDefault(), PyClass.class, text);

        newClass = placeNewClass(project, newClass, clazz, targetFile);
        MembersManager.moveAllMembers(selectedMemberInfos, clazz, newClass);
        if (!newClass.getContainingFile().equals(clazz.getContainingFile())) {
            PyClassRefactoringUtil.optimizeImports(clazz.getContainingFile()); // To remove unneeded imports only if user used different file
        }
        PyPsiRefactoringUtil.addSuperclasses(project, clazz, null, newClass);

        RefactoringEventData afterData = new RefactoringEventData();
        afterData.addElement(newClass);
        project.getMessageBus().syncPublisher(RefactoringEventListener.class)
            .refactoringDone(getRefactoringId(), afterData);
    }

    /**
     * If class explicitly extends object we shall move it even in Py3K
     */
    private static boolean isObjectParentDeclaredExplicitly(PyClass clazz) {
        return ContainerUtil.exists(clazz.getSuperClassExpressions(), o -> PyNames.OBJECT.equals(o.getName()));
    }

    private static boolean isRefactoredClassInBackedFile(@Nullable VirtualFile targetFile, PyClass pyClass) {
        // TODO consulo: com.intellij.notebook.editor.BackedVirtualFile missing, JB logic:
        //  VirtualFile file = pyClass.getContainingFile().getVirtualFile();
        //  return file instanceof BackedVirtualFile &&
        //         Comparing.equal(((BackedVirtualFile)file).getOriginFile(), targetFile);
        return false;
    }

    private static PyClass placeNewClass(Project project,
                                         PyClass newClass,
                                         PyClass clazz,
                                         String targetFile) {
        VirtualFile file = VirtualFileManager.getInstance()
            .findFileByUrl(ApplicationManager.getApplication().isUnitTestMode() ? targetFile : VirtualFileUtil.pathToUrl(targetFile));
        // file is the same as the source
        if (Comparing.equal(file, clazz.getContainingFile().getVirtualFile()) || isRefactoredClassInBackedFile(file, clazz)) {
            return (PyClass)clazz.getParent().addBefore(newClass, clazz);
        }
        boolean isNamespace = isInNamespacePackage(clazz);
        PsiFile psiFile = null;
        try {
            if (file == null) {
                // file does not exist
                String filename;
                String path;
                if (targetFile.endsWith(PythonFileType.INSTANCE.getDefaultExtension())) {
                    path = PathUtil.getParentPath(targetFile);
                    filename = PathUtil.getFileName(targetFile);
                }
                else {
                    path = targetFile;
                    filename = PyNames.INIT_DOT_PY; // user requested putting the class into this package directly
                }
                psiFile = placeFile(project, path, filename, isNamespace);
            }
            else if (file.isDirectory()) { // existing directory
                psiFile = placeFile(project, file.getPath(), PyNames.INIT_DOT_PY, isNamespace);
            }
            else { // existing file
                psiFile = PsiManager.getInstance(project).findFile(file);
            }
        }
        catch (IOException e) {
            LOG.error(e);
        }

        LOG.assertTrue(psiFile != null);
        if (psiFile.getLastChild() != null) {
            // TODO: make the number of newlines depend on style setting
            psiFile.add(PyElementGenerator.getInstance(project).createFromText(LanguageLevel.PYTHON27, PsiWhiteSpace.class, "\n\n"));
        }
        newClass = (PyClass)psiFile.add(newClass);
        PyPsiRefactoringUtil.insertImport(clazz, Collections.singleton(newClass));
        return newClass;
    }


    public static String getRefactoringId() {
        return "refactoring.python.extract.superclass";
    }
}
