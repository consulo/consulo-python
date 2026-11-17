// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import consulo.language.ast.ASTNode;
import consulo.document.Document;
import consulo.project.Project;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.PsiNamedElement;
import consulo.language.impl.psi.LightElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.editor.util.PsiUtilBase;
import consulo.language.psi.util.QualifiedName;
import consulo.util.collection.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.ast.impl.PyUtilCore;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.impl.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyIndentUtil;
import com.jetbrains.python.impl.psi.PyKnownDecoratorUtil;
import com.jetbrains.python.psi.PyPassStatement;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyStatementListContainer;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.impl.pyi.PyiUtil;
import com.jetbrains.python.impl.refactoring.classes.PyDependenciesComparator;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class PyPsiRefactoringUtil {
    /**
     * Adds element to statement list to the correct place according to its dependencies.
     *
     * @param element       to insert
     * @param statementList where element should be inserted
     * @return inserted element
     */
    public static <T extends PyElement> T addElementToStatementList(T element,
                                                                    PyStatementList statementList) {
        PsiElement before = null;
        PsiElement after = null;
        for (PyStatement statement : statementList.getStatements()) {
            if (PyDependenciesComparator.depends(element, statement)) {
                after = statement;
            }
            else if (PyDependenciesComparator.depends(statement, element)) {
                before = statement;
            }
        }
        PsiElement result;
        if (after != null) {

            result = statementList.addAfter(element, after);
        }
        else if (before != null) {
            result = statementList.addBefore(element, before);
        }
        else {
            result = addElementToStatementList(element, statementList, true);
        }
        @SuppressWarnings("unchecked") // Inserted element can't have different type
        T resultCasted = (T)result;
        return resultCasted;
    }

    /**
     * Inserts specified element into the statement list either at the beginning or at its end. If new element is going to be
     * inserted at the beginning, any preceding docstrings and/or calls to super methods will be skipped.
     * Moreover if statement list previously didn't contain any statements, explicit new line and indentation will be inserted in
     * front of it.
     *
     * @param element        element to insert
     * @param statementList  statement list
     * @param toTheBeginning whether to insert element at the beginning or at the end of the statement list
     * @return actually inserted element as for {@link PsiElement#add(PsiElement)}
     */
    public static PsiElement addElementToStatementList(PsiElement element,
                                                                PyStatementList statementList,
                                                                boolean toTheBeginning) {
        PsiElement prevElem = PyPsiUtils.getPrevNonWhitespaceSibling(statementList);
        // If statement list is on the same line as previous element (supposedly colon), move its only statement on the next line
        if (prevElem != null && PyUtilCore.onSameLine(statementList, prevElem)) {
            PsiDocumentManager manager = PsiDocumentManager.getInstance(statementList.getProject());
            Document document = statementList.getContainingFile().getFileDocument();
            PyStatementListContainer container = (PyStatementListContainer)statementList.getParent();
            manager.doPostponedOperationsAndUnblockDocument(document);
            String indentation = "\n" + PyIndentUtil.getElementIndent(statementList);
            // If statement list was empty initially, we need to add some anchor statement ("pass"), so that preceding new line was not
            // parsed as following entire StatementListContainer (e.g. function). It's going to be replaced anyway.
            String text = statementList.getStatements().length == 0 ? indentation + PyNames.PASS : indentation;
            document.insertString(statementList.getTextRange().getStartOffset(), text);
            manager.commitDocument(document);
            statementList = container.getStatementList();
        }
        PsiElement firstChild = statementList.getFirstChild();
        if (firstChild == statementList.getLastChild() && firstChild instanceof PyPassStatement) {
            element = firstChild.replace(element);
        }
        else {
            PyStatement[] statements = statementList.getStatements();
            if (toTheBeginning && statements.length > 0) {
                PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(statementList, PyDocStringOwner.class);
                PyStatement anchor = statements[0];
                if (docStringOwner != null && anchor instanceof PyExpressionStatement &&
                    ((PyExpressionStatement)anchor).getExpression() == docStringOwner.getDocStringExpression()) {
                    PyStatement next = PsiTreeUtil.getNextSiblingOfType(anchor, PyStatement.class);
                    if (next == null) {
                        return statementList.addAfter(element, anchor);
                    }
                    anchor = next;
                }
                while (anchor instanceof PyExpressionStatement) {
                    PyExpression expression = ((PyExpressionStatement)anchor).getExpression();
                    if (expression instanceof PyCallExpression) {
                        PyExpression callee = ((PyCallExpression)expression).getCallee();
                        if ((PyUtil.isSuperCall((PyCallExpression)expression) || (callee != null && PyNames.INIT.equals(callee.getName())))) {
                            PyStatement next = PsiTreeUtil.getNextSiblingOfType(anchor, PyStatement.class);
                            if (next == null) {
                                return statementList.addAfter(element, anchor);
                            }
                            anchor = next;
                            continue;
                        }
                    }
                    break;
                }
                element = statementList.addBefore(element, anchor);
            }
            else {
                element = statementList.add(element);
            }
        }
        return element;
    }

    public static List<PyFunction> getAllSuperAbstractMethods(PyClass cls, TypeEvalContext context) {
        return ContainerUtil.filter(getAllSuperMethods(cls, context), method -> isAbstractMethodForClass(method, cls, context));
    }

    private static boolean isAbstractMethodForClass(PyFunction method, PyClass cls, TypeEvalContext context) {
        String methodName = method.getName();
        if (methodName == null ||
            cls.findMethodByName(methodName, false, context) != null ||
            cls.findClassAttribute(methodName, false, context) != null) {
            return false;
        }
        PyClass methodClass = method.getContainingClass();
        if (methodClass != null) {
            for (PyClass ancestor : cls.getAncestorClasses(context)) {
                if (ancestor.equals(methodClass)) {
                    break;
                }
                if (ancestor.findClassAttribute(methodName, false, context) != null) {
                    return false;
                }
            }
        }
        return method.onlyRaisesNotImplementedError() || PyKnownDecoratorUtil.hasAbstractDecorator(method, context);
    }

    /**
     * Returns all super functions available through MRO.
     */
    public static List<PyFunction> getAllSuperMethods(PyClass pyClass, TypeEvalContext context) {
        Map<String, PyFunction> functions = Maps.newLinkedHashMap();
        for (PyClassLikeType type : pyClass.getAncestorTypes(context)) {
            if (type != null) {
                for (PyFunction function : PyTypeUtil.getMembersOfType(type, PyFunction.class, false, context)) {
                    String name = function.getName();
                    if (name != null) {
                        if (!functions.containsKey(name) ||
                            PyiUtil.isOverload(functions.get(name), context) && !PyiUtil.isOverload(function, context)) {
                            functions.put(name, function);
                        }
                    }
                }
            }
        }
        return Lists.newArrayList(functions.values());
    }

    public static boolean isValidQualifiedName(QualifiedName name) {
        if (name == null) {
            return false;
        }
        Collection<String> components = name.getComponents();
        if (components.isEmpty()) {
            return false;
        }
        for (String s : components) {
            if (!PyNames.isIdentifier(s) || PyNames.isReserved(s)) {
                return false;
            }
        }
        return true;
    }

    public static void insertImport(PsiElement anchor, Collection<? extends PsiNamedElement> elements) {
        for (PsiNamedElement newClass : elements) {
            insertImport(anchor, newClass);
        }
    }

    public static boolean insertImport(PsiElement anchor, PsiNamedElement element) {
        return insertImport(anchor, element, null);
    }

    public static boolean insertImport(PsiElement anchor, PsiNamedElement element, @Nullable String asName) {
        return insertImport(anchor, element, asName, PyCodeInsightSettings.getInstance().PREFER_FROM_IMPORT);
    }

    public static boolean insertImport(PsiElement anchor,
                                       PsiNamedElement element,
                                       @Nullable String asName,
                                       boolean preferFromImport) {
        if (PyBuiltinCache.getInstance(element).isBuiltin(element)) {
            return false;
        }
        PsiFileSystemItem elementSource = element instanceof PsiDirectory ? (PsiFileSystemItem)element : element.getContainingFile();
        PsiFile file = anchor.getContainingFile();
        if (elementSource == file || elementSource == file.getOriginalFile()) {
            return false;
        }
        QualifiedName qname = QualifiedNameFinder.findCanonicalImportPath(element, anchor);
        if (qname == null || !isValidQualifiedName(qname)) {
            return false;
        }
        QualifiedName containingQName;
        String importedName;
        boolean importingModuleOrPackage = element instanceof PyFile || element instanceof PsiDirectory;
        if (importingModuleOrPackage) {
            containingQName = qname.removeLastComponent();
            importedName = qname.getLastComponent();
        }
        // See PyClassRefactoringUtil.DynamicNamedElement
        else if (PyUtil.isTopLevel(element) || element instanceof LightElement) {
            containingQName = qname;
            importedName = getOriginalName(element);
        }
        else {
            return false;
        }
        AddImportHelper.ImportPriority priority = AddImportHelper.getImportPriority(anchor, elementSource);
        if (preferFromImport && !containingQName.getComponents().isEmpty() || !importingModuleOrPackage) {
            return AddImportHelper.addOrUpdateFromImportStatement(file, containingQName.toString(), importedName, asName, priority, anchor);
        }
        else {
            return AddImportHelper.addImportStatement(file, containingQName.append(importedName).toString(), asName, priority, anchor);
        }
    }

    public static @Nullable String getOriginalName(PsiNamedElement element) {
        if (element instanceof PyFile) {
            VirtualFile virtualFile = PsiUtilBase.asVirtualFile(PyUtil.turnInitIntoDir(element));
            if (virtualFile != null) {
                return virtualFile.getNameWithoutExtension();
            }
            return null;
        }
        return element.getName();
    }

    public static @Nullable String getOriginalName(PyImportElement element) {
        QualifiedName qname = element.getImportedQName();
        if (qname != null && qname.getComponentCount() > 0) {
            return qname.getComponents().get(0);
        }
        return null;
    }

    /**
     * Adds expressions to superclass list
     *
     * @param project          project
     * @param clazz            class to add expressions to superclass list
     * @param paramExpressions param expressions. Like "object" or "MySuperClass". Will not add any param exp. if null.
     * @param keywordArguments keyword args like "metaclass=ABCMeta". key-value pairs.  Will not add any keyword arg. if null.
     */
    public static void addSuperClassExpressions(Project project,
                                                PyClass clazz,
                                                @Nullable Collection<String> paramExpressions,
                                                @Nullable Collection<Pair<String, String>> keywordArguments) {
        PyElementGenerator generator = PyElementGenerator.getInstance(project);
        LanguageLevel languageLevel = LanguageLevel.forElement(clazz);

        PyArgumentList superClassExpressionList = clazz.getSuperClassExpressionList();
        boolean addExpression = false;
        if (superClassExpressionList == null) {
            superClassExpressionList = generator.createFromText(languageLevel, PyClass.class, "class foo():pass").getSuperClassExpressionList();
            assert superClassExpressionList != null : "expression not created";
            addExpression = true;
        }


        generator.createFromText(LanguageLevel.PYTHON34, PyClass.class, "class foo(object, metaclass=Foo): pass").getSuperClassExpressionList();
        if (paramExpressions != null) {
            for (String paramExpression : paramExpressions) {
                superClassExpressionList.addArgument(generator.createExpressionFromText(languageLevel, paramExpression));
            }
        }

        if (keywordArguments != null) {
            for (Pair<String, String> keywordArgument : keywordArguments) {
                superClassExpressionList.addArgument(generator.createKeywordArgument(languageLevel, keywordArgument.first, keywordArgument.second));
            }
        }

        // If class has no expression list, then we need to add it manually.
        if (addExpression) {
            ASTNode classNameNode = clazz.getNameNode(); // For nameless classes we simply add expression list directly to them
            PsiElement elementToAddAfter = (classNameNode == null) ? clazz.getFirstChild() : classNameNode.getPsi();
            clazz.addAfter(superClassExpressionList, elementToAddAfter);
        }
    }

    /**
     * Adds class attributeName (field) if it does not exist. like __metaclass__ = ABCMeta. Or CLASS_FIELD = 42.
     *
     * @param aClass        where to add
     * @param attributeName attribute's name. Like __metaclass__ or CLASS_FIELD
     * @param value         it's value. Like ABCMeta or 42.
     * @return newly inserted attribute
     */
    public static @Nullable PsiElement addClassAttributeIfNotExist(
        PyClass aClass,
        String attributeName,
        String value) {
        if (aClass.findClassAttribute(attributeName, false, null) != null) {
            return null; //Do not add any if exist already
        }
        PyElementGenerator generator = PyElementGenerator.getInstance(aClass.getProject());
        String text = String.format("%s = %s", attributeName, value);
        LanguageLevel level = LanguageLevel.forElement(aClass);

        PyAssignmentStatement assignmentStatement = generator.createFromText(level, PyAssignmentStatement.class, text);
        //TODO: Add metaclass to the top. Add others between last attributeName and first method
        return addElementToStatementList(assignmentStatement, aClass.getStatementList(), true);
    }

    public static boolean addMetaClassIfNotExist(PyClass cls, PyClass metaClass, TypeEvalContext context) {
        String metaClassName = metaClass.getName();
        if (metaClassName == null) {
            return false;
        }

        PyType metaClassType = cls.getMetaClassType(false, context);
        if (metaClassType != null) {
            return false;
        }

        insertImport(cls, metaClass);

        LanguageLevel languageLevel = LanguageLevel.forElement(cls);
        if (languageLevel.isPython2()) {
            addClassAttributeIfNotExist(cls, PyNames.DUNDER_METACLASS, metaClassName);
        }
        else {
            List<Pair<String, String>> keywordArguments = Collections.singletonList(Pair.create(PyNames.METACLASS, metaClassName));
            addSuperClassExpressions(cls.getProject(), cls, null, keywordArguments);
        }

        return true;
    }

    /**
     * Adds super classes to certain class.
     *
     * @param project      project where refactoring takes place
     * @param clazz        destination
     * @param superClasses classes to add
     */
    public static void addSuperclasses(Project project,
                                       PyClass clazz,
                                       PyClass ... superClasses) {

        Collection<String> superClassExpressions = new ArrayList<>();

        for (PyClass superClass : superClasses) {
            if (superClass == null) {
                continue;
            }
            String superClassName = superClass.getName();
            if (superClassName == null) {
                continue;
            }

            String existingReference = findReferenceThroughExistingImport(clazz, superClass, superClassName);
            if (existingReference != null) {
                superClassExpressions.add(existingReference);
            }
            else {
                superClassExpressions.add(superClassName);
                insertImport(clazz, superClass);
            }
        }

        addSuperClassExpressions(project, clazz, superClassExpressions, null);
    }

    /**
     * Finds a reference to the top-level class {@code superClass} through an existing top-level import in the file of {@code anchor}.
     * <p>
     * For {@code from abc import ABC as Base}, the reference is {@code Base}.
     * For {@code import abc as a}, the reference is {@code a.ABC}.
     *
     * @return the reference text, or {@code null} if the file has no such import
     */
    private static @Nullable String findReferenceThroughExistingImport(PsiElement anchor,
                                                                       PyClass superClass,
                                                                       String superClassName) {
        if (!(anchor.getContainingFile() instanceof PyFile file) || superClass.getContainingFile() == file || !PyUtil.isTopLevel(superClass)) {
            return null;
        }
        QualifiedName moduleQName = QualifiedNameFinder.findCanonicalImportPath(superClass, anchor);
        if (moduleQName == null || !isValidQualifiedName(moduleQName)) {
            return null;
        }

        QualifiedName superClassQName = QualifiedName.fromComponents(superClassName);
        for (PyFromImportStatement fromImport : file.getFromImports()) {
            if (fromImport.getRelativeLevel() != 0 || !moduleQName.equals(fromImport.getImportSourceQName())) {
                continue;
            }
            for (PyImportElement importElement : fromImport.getImportElements()) {
                if (superClassQName.equals(importElement.getImportedQName())) {
                    String asName = importElement.getAsName();
                    return asName != null ? asName : superClassName;
                }
            }
        }
        for (PyImportElement importElement : file.getImportTargets()) {
            if (moduleQName.equals(importElement.getImportedQName())) {
                String asName = importElement.getAsName();
                return (asName != null ? asName : moduleQName.toString()) + "." + superClassName;
            }
        }
        return null;
    }

    public static boolean shouldCopyAnnotations(PsiElement copiedElement, PsiFile destFile) {
        if (LanguageLevel.forElement(copiedElement).isPython2()) {
            return false;
        }
        if (PyiUtil.isInsideStub(destFile)) {
            return true;
        }
        if (PyiUtil.isInsideStub(copiedElement)) {
            return PyCodeInsightSettings.getInstance().COPY_TYPE_ANNOTATIONS_FROM_STUBS;
        }
        return true;
    }
}
