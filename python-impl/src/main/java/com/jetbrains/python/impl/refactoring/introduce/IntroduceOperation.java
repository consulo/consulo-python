// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.introduce;

import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.util.collection.ContainerUtil;
import com.jetbrains.python.psi.PyExpression;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;


public class IntroduceOperation {
    private final Project myProject;
    private final Editor myEditor;
    private final PsiFile myFile;
    private String myName;
    private final EnumSet<IntroduceHandler.InitPlace> myAvailableInitPlaces = EnumSet.of(IntroduceHandler.InitPlace.SAME_METHOD);
    private IntroduceHandler.InitPlace myInitPlace = IntroduceHandler.InitPlace.SAME_METHOD;
    private IntroduceHandler.InitPlace myInplaceInitPlace = IntroduceHandler.InitPlace.SAME_METHOD;
    private Boolean myReplaceAll;
    private PsiElement myElement;
    private PyExpression myInitializer;
    private List<SmartPsiElementPointer<PsiElement>> myOccurrences = Collections.emptyList();
    private Collection<String> mySuggestedNames;

    public IntroduceOperation(Project project,
                                                        Editor editor,
                                                        PsiFile file,
                                                        String name) {
        myProject = project;
        myEditor = editor;
        myFile = file;
        myName = name;
    }

    public void addAvailableInitPlace(IntroduceHandler.InitPlace initPlace) {
        myAvailableInitPlaces.add(initPlace);
    }

    public void removeAvailableInitPlace(IntroduceHandler.InitPlace initPlace) {
        myAvailableInitPlaces.remove(initPlace);
    }

    public EnumSet<IntroduceHandler.InitPlace> getAvailableInitPlaces() {
        return myAvailableInitPlaces;
    }

    public String getName() {
        return myName;
    }

    public void setName(@Nullable String name) {
        myName = name;
    }

    public Project getProject() {
        return myProject;
    }

    public Editor getEditor() {
        return myEditor;
    }

    public PsiFile getFile() {
        return myFile;
    }

    public PsiElement getElement() {
        return myElement;
    }

    public void setElement(PsiElement element) {
        myElement = element;
    }

    public IntroduceHandler.InitPlace getInitPlace() {
        return myInitPlace;
    }

    public void setInitPlace(IntroduceHandler.InitPlace initPlace) {
        myInitPlace = initPlace;
    }

    public Boolean isReplaceAll() {
        return myReplaceAll;
    }

    public void setReplaceAll(boolean replaceAll) {
        myReplaceAll = replaceAll;
    }

    public PyExpression getInitializer() {
        return myInitializer;
    }

    public void setInitializer(PyExpression initializer) {
        myInitializer = initializer;
    }

    public List<PsiElement> getOccurrences() {
        return ContainerUtil.mapNotNull(myOccurrences, SmartPsiElementPointer::getElement);
    }

    public void setOccurrences(List<PsiElement> occurrences) {
        myOccurrences = ContainerUtil.map(occurrences, SmartPointerManager::createPointer);
    }

    public Collection<String> getSuggestedNames() {
        return mySuggestedNames;
    }

    public void setSuggestedNames(Collection<String> suggestedNames) {
        mySuggestedNames = suggestedNames;
    }

    public IntroduceHandler.InitPlace getInplaceInitPlace() {
        return myInplaceInitPlace;
    }

    public void setInplaceInitPlace(IntroduceHandler.InitPlace inplaceInitPlace) {
        myInplaceInitPlace = inplaceInitPlace;
    }
}
