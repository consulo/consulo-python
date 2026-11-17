// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.references;

import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.editor.completion.CompletionUtilCore;
import consulo.language.editor.completion.lookup.InsertHandler;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.ast.ASTNode;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.document.Document;
import consulo.codeEditor.Editor;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiErrorElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.ResolveResult;
import consulo.language.ast.IElementType;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.util.collection.ArrayUtil;
import consulo.language.util.ProcessingContext;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.psi.PyImportStatementBase;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.impl.psi.impl.PyReferenceExpressionImpl;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.impl.psi.resolve.PyResolveImportUtil;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.impl.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.impl.psi.types.PyModuleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isUnknown;

/**
 * Reference in an import statement:<br/>
 * <code>import <u>foo.name</u></code>
 */
public class PyImportReference extends PyReferenceImpl {
    protected final PyReferenceExpressionImpl myElement;

    public PyImportReference(PyReferenceExpressionImpl element, PyResolveContext context) {
        super(element, context);
        myElement = element;
    }

    public static PyImportReference forElement(PyReferenceExpressionImpl expression, PsiElement importParent, PyResolveContext context) {
        if (importParent instanceof PyImportElement) {
            PyImportStatementBase importStatement = PsiTreeUtil.getParentOfType(importParent, PyImportStatementBase.class);
            if (importStatement instanceof PyFromImportStatement) {
                return new PyFromImportNameReference(expression, context);
            }
            return new PyImportReference(expression, context);
        }
        return new PyFromImportSourceReference(expression, context);
    }

    @Override
    public @Nullable String getUnresolvedDescription() {
        PyImportStatement importStatement = PsiTreeUtil.getParentOfType(myElement, PyImportStatement.class);
        if (importStatement != null) {
            // The template marks the module name with backticks; render them to plain quotes for the description.
            return PyLocalize.unresolvedImportReference(myElement.getReferencedName()).get();
        }
        return super.getUnresolvedDescription();
    }

    @Override
    protected List<RatedResolveResult> resolveInner() {
        PyImportElement parent = PsiTreeUtil.getParentOfType(myElement, PyImportElement.class); //importRef.getParent();
        QualifiedName qname = myElement.asQualifiedName();
        return qname == null ? Collections.emptyList() : ResolveImportUtil.resolveNameInImportStatement(parent, qname);
    }

    @Override
    public Object[] getVariants() {
        // no completion in invalid import statements
        PyImportElement importElement = PsiTreeUtil.getParentOfType(myElement, PyImportElement.class);
        if (importElement != null) {
            PsiErrorElement prevError = PsiTreeUtil.getPrevSiblingOfType(importElement, PsiErrorElement.class);
            if (prevError != null) {
                return ArrayUtil.EMPTY_OBJECT_ARRAY;
            }
        }

        PyExpression qualifier = myElement.getQualifier();
        TypeEvalContext context = TypeEvalContext.codeCompletion(myElement.getProject(),
                                                                       CompletionUtilCore.getOriginalOrSelf(myElement).getContainingFile());
        if (qualifier != null) {
            // qualifier's type must be module, it should know how to complete
            PyType type = context.getType(qualifier);
            if (type != null) {
                Object[] variants = type.getCompletionVariants(myElement.getName(), myElement, new ProcessingContext());
                if (!alreadyHasImportKeyword()) {
                    replaceInsertHandler(variants, ImportKeywordHandler.INSTANCE);
                }
                return variants;
            }
            else {
                return ArrayUtil.EMPTY_OBJECT_ARRAY;
            }
        }
        else {
            // complete to possible modules
            return new ImportVariantCollector(context).execute();
        }
    }

    private static void replaceInsertHandler(Object[] variants, InsertHandler<LookupElement> insertHandler) {
        for (int i = 0; i < variants.length; i += 1) {
            Object item = variants[i];
            if (hasChildPackages(item)) {
                continue;
            }
            if (item instanceof LookupElementBuilder) {
                variants[i] = ((LookupElementBuilder)item).withInsertHandler(insertHandler);
            }
            else if (item instanceof PsiNamedElement element) {
                String name = element.getName();
                assert name != null; // it can't really have null name
                variants[i] = LookupElementBuilder
                    .create(name)
                    .withIcon(IconDescriptorUpdaters.getIcon(element, 0))
                    .withInsertHandler(insertHandler);
            }
        }
    }

    private static boolean hasChildPackages(Object item) {
        PsiElement itemElement = null;
        if (item instanceof PsiElement) {
            itemElement = (PsiElement)item;
        }
        else if (item instanceof LookupElement lookupElement) {
            PsiElement element = lookupElement.getPsiElement();
            if (element != null) {
                itemElement = element;
            }
        }
        return !(itemElement instanceof PsiFile);  // TODO deeper check?
    }

    private boolean alreadyHasImportKeyword() {
        if (PsiTreeUtil.getParentOfType(myElement, PyImportStatement.class) != null) {
            return true;
        }
        ASTNode node = myElement.getNode();
        while (node != null) {
            IElementType nodeType = node.getElementType();
            if (nodeType == PyTokenTypes.IMPORT_KEYWORD) {
                return true;
            }
            node = node.getTreeNext();
        }
        return false;
    }

    class ImportVariantCollector {
        private final PsiFile myCurrentFile;
        private final Set<String> myNamesAlready;
        private final List<Object> myObjects;
        private final TypeEvalContext myContext;

        ImportVariantCollector(TypeEvalContext context) {
            myContext = context;
            PsiFile currentFile = myElement.getContainingFile();
            currentFile = currentFile.getOriginalFile();
            myCurrentFile = currentFile;
            myNamesAlready = new HashSet<>();
            myObjects = new ArrayList<>();
        }

        public Object[] execute() {
            int relativeLevel = -1;
            InsertHandler<LookupElement> insertHandler = null;

            // NOTE: could use getPointInImport()
            // are we in "import _" or "from foo import _"?
            PyFromImportStatement fromImport = PsiTreeUtil.getParentOfType(myElement, PyFromImportStatement.class);
            if (fromImport != null && myElement.getParent() != fromImport) { // in "from foo import _"
                PyReferenceExpression src = fromImport.getImportSource();
                if (src != null) {
                    ResolveResult[] resolved = src.getReference().multiResolve(false);
                    for (ResolveResult result : resolved) {
                        PsiElement modCandidate = result.getElement();
                        if (modCandidate instanceof PyTypedElement module) {
                            addImportedNames(fromImport.getImportElements()); // don't propose already imported items
                            // try to collect submodules
                            PyType qualifierType = myContext.getType(module);
                            if (qualifierType != null) {
                                ProcessingContext ctx = new ProcessingContext();
                                ctx.put(PyType.CTX_NAMES, myNamesAlready);
                                Collections.addAll(myObjects, qualifierType.getCompletionVariants(myElement.getName(), myElement, ctx));
                            }
                        }
                        else if (modCandidate instanceof PsiDirectory) {
                            fillFromDir((PsiDirectory)modCandidate, ImportKeywordHandler.INSTANCE);
                        }
                    }
                    if (!myObjects.isEmpty()) {
                        return myObjects.toArray();
                    }
                }
                else { // null source, must be a "from ... import"
                    relativeLevel = fromImport.getRelativeLevel();
                    if (relativeLevel > 0) {
                        PsiDirectory relativeDir = ResolveImportUtil.stepBackFrom(myCurrentFile, relativeLevel);
                        if (relativeDir != null) {
                            addImportedNames(fromImport.getImportElements());
                            fillFromDir(relativeDir, null);
                        }
                    }
                }
                fillFromRootsIfNotRelative(relativeLevel, insertHandler);
            }
            else { // in "import _" or "from _ import"
                PsiElement prevElem = PyPsiUtils.getPrevNonWhitespaceSibling(myElement);
                while (prevElem != null && prevElem.getNode().getElementType() == PyTokenTypes.DOT) {
                    relativeLevel += 1;
                    prevElem = PyPsiUtils.getPrevNonWhitespaceSibling(prevElem);
                }
                if (fromImport != null) {
                    addImportedNames(fromImport.getImportElements());
                    if (!alreadyHasImportKeyword()) {
                        insertHandler = ImportKeywordHandler.INSTANCE;
                    }
                }
                else {
                    myNamesAlready.add(PyNames.FUTURE_MODULE); // never add it to "import ..."
                    PyImportStatement importStatement = PsiTreeUtil.getParentOfType(myElement, PyImportStatement.class);
                    if (importStatement != null) {
                        addImportedNames(importStatement.getImportElements());
                    }
                }
                PsiDirectory containingDirectory = myCurrentFile.getContainingDirectory();
                // look at dir by level
                if (LanguageLevel.forElement(myCurrentFile).isPy3K() && containingDirectory != null &&
                    PyUtil.isExplicitPackage(containingDirectory)) {
                    fillFromRootsIfNotRelative(relativeLevel, insertHandler);
                    fillFromSameDirectoryOrRelative(relativeLevel, insertHandler);
                }
                else {
                    fillFromSameDirectoryOrRelative(relativeLevel, insertHandler);
                    fillFromRootsIfNotRelative(relativeLevel, insertHandler);
                }
            }

            return ArrayUtil.toObjectArray(myObjects);
        }

        private void fillFromRootsIfNotRelative(int relativeLevel, @Nullable InsertHandler<LookupElement> insertHandler) {
            if (relativeLevel == -1) {
                fillFromQName(QualifiedName.fromComponents(), insertHandler);
            }
        }

        private void fillFromSameDirectoryOrRelative(int relativeLevel, @Nullable InsertHandler<LookupElement> insertHandler) {
            if (relativeLevel < 0 && ResolveImportUtil.isAbsoluteImportEnabledFor(myCurrentFile)) {
                return;
            }
            PsiDirectory containingDirectory = myCurrentFile.getContainingDirectory();
            if (containingDirectory != null) {
                QualifiedName thisQName = QualifiedNameFinder.findShortestImportableQName(containingDirectory);
                if (thisQName == null || thisQName.getComponentCount() == relativeLevel) {
                    fillFromDir(ResolveImportUtil.stepBackFrom(myCurrentFile, relativeLevel), insertHandler);
                }
                else if (thisQName.getComponentCount() > relativeLevel) {
                    thisQName = thisQName.removeTail(relativeLevel);
                    fillFromQName(thisQName, insertHandler);
                }
            }
        }

        private void fillFromQName(QualifiedName thisQName, InsertHandler<LookupElement> insertHandler) {
            PyResolveImportUtil.resolveQualifiedName(thisQName, PyResolveImportUtil.fromFoothold(myCurrentFile)).stream()
                .filter(PsiDirectory.class::isInstance)
                .map(PsiDirectory.class::cast)
                .forEach(directory -> fillFromDir(directory, insertHandler));
        }

        private void addImportedNames(PyImportElement[] importElements) {
            for (PyImportElement element : importElements) {
                PyReferenceExpression ref = element.getImportReferenceExpression();
                if (ref != null) {
                    String s = ref.getReferencedName();
                    if (s != null) {
                        myNamesAlready.add(s);
                    }
                }
            }
        }

        /**
         * Adds variants found under given dir.
         */
        private void fillFromDir(PsiDirectory targetDir, @Nullable InsertHandler<LookupElement> insertHandler) {
            if (targetDir != null) {
                PsiFile initPy = targetDir.findFile(PyNames.INIT_DOT_PY);
                if (initPy instanceof PyFile) {
                    PyModuleType moduleType = new PyModuleType((PyFile)initPy);
                    ProcessingContext context = new ProcessingContext();
                    context.put(PyType.CTX_NAMES, myNamesAlready);
                    Object[] completionVariants = moduleType.getCompletionVariants("", getElement(), context);
                    if (insertHandler != null) {
                        replaceInsertHandler(completionVariants, insertHandler);
                    }
                    myObjects.addAll(Arrays.asList(completionVariants));
                }
                else {
                    myObjects.addAll(PyModuleType.getSubModuleVariants(targetDir, myElement, myNamesAlready));
                }
            }
        }
    }

    @Override
    public @Nullable HighlightSeverity getUnresolvedHighlightSeverity(TypeEvalContext context) {
        PyExpression qualifier = myElement.getQualifier();
        if (qualifier != null && isUnknown(context.getType(qualifier))) {
            /* in case the element qualifier can not be resolved, the qualifier need to be highlighted instead of the element */
            return null;
        }
        return HighlightSeverity.ERROR;
    }

    /**
     * Adds ' import ' text after the item.
     */
    private static class ImportKeywordHandler implements InsertHandler<LookupElement> {
        public static final InsertHandler<LookupElement> INSTANCE = new ImportKeywordHandler();

        private static final String IMPORT_KWD = " import ";

        @Override
        public void handleInsert(InsertionContext context, LookupElement item) {
            Editor editor = context.getEditor();
            Document document = editor.getDocument();
            int tailOffset = context.getTailOffset();
            document.insertString(tailOffset, IMPORT_KWD);
            editor.getCaretModel().moveToOffset(tailOffset + IMPORT_KWD.length());
        }
    }
}
