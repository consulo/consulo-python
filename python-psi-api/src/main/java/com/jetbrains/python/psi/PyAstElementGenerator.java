package com.jetbrains.python.psi;

import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.ast.PyAstExpression;
import com.jetbrains.python.ast.PyAstExpressionStatement;
import com.jetbrains.python.ast.PyAstFunction;
import com.jetbrains.python.ast.PyAstPassStatement;
import com.jetbrains.python.ast.PyAstStatementList;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileFactory;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;

import java.util.Arrays;

@ServiceAPI(ComponentScope.PROJECT)
public class PyAstElementGenerator {
    public static PyAstElementGenerator getInstance(Project project) {
        return project.getInstance(PyAstElementGenerator.class);
    }

    private final Project myProject;

    protected PyAstElementGenerator(Project project) {
        myProject = project;
    }

    static final int[] FROM_ROOT = new int[]{0};

    public <T> T createFromText(LanguageLevel langLevel, Class<T> aClass, String text) {
        return createFromText(langLevel, aClass, text, FROM_ROOT);
    }

    public <T> T createPhysicalFromText(LanguageLevel langLevel, Class<T> aClass, String text) {
        return createFromText(langLevel, aClass, text, FROM_ROOT, true);
    }

    /**
     * Creates an arbitrary PSI element from text, by creating a bigger construction and then cutting the proper subelement.
     * Will produce all kinds of exceptions if the path or class would not match the PSI tree.
     *
     * @param langLevel the language level to use for parsing the text
     * @param aClass    class of the PSI element; may be an interface not descending from PsiElement, as long as target node can be cast to it
     * @param text      text to parse
     * @param path      a sequence of numbers, each telling which child to select at current tree level; 0 means first child, etc.
     * @return the newly created PSI element
     */
    public <T> T createFromText(LanguageLevel langLevel, Class<T> aClass, String text, int[] path) {
        return createFromText(langLevel, aClass, text, path, false);
    }

    private <T> T createFromText(LanguageLevel langLevel, Class<T> aClass, String text, int[] path, boolean physical) {
        PsiElement ret = createDummyFile(langLevel, text, physical);
        for (int skip : path) {
            if (ret != null) {
                ret = ret.getFirstChild();
                for (int i = 0; i < skip; i += 1) {
                    if (ret != null) {
                        ret = ret.getNextSibling();
                    }
                    else {
                        ret = null;
                        break;
                    }
                }
            }
            else {
                break;
            }
        }
        if (ret == null) {
            throw new IllegalArgumentException("Can't find element matching path " + Arrays.toString(path) + " in text '" + text + "'");
        }
        if (aClass.isInstance(ret)) {
            //noinspection unchecked
            return (T)ret;
        }
        else {
            throw new IllegalArgumentException("Can't create an element of type " + aClass + " from text '" + text + "', got " + ret.getClass() + " instead");
        }
    }

    public PsiFile createDummyFile(LanguageLevel langLevel, String contents) {
        return createDummyFile(langLevel, contents, false);
    }

    /**
     * @return name used for {@link #createDummyFile(LanguageLevel, String)}
     */
    public static String getDummyFileName() {
        return "dummy." + PythonFileType.INSTANCE.getDefaultExtension();
    }

    /**
     * TODO: Use {@link PsiFileFactory} instead?
     */
    private PsiFile createDummyFile(LanguageLevel langLevel, String contents, boolean physical) {
        PsiFileFactory factory = PsiFileFactory.getInstance(myProject);
        String name = getDummyFileName();
        // TODO consulo: PsiFileFactoryImpl#trySetupPsiForFile(LightVirtualFile, Language, boolean, boolean) missing (internal), so the
        //  language level is passed as LanguageVersion instead of PyLanguageFacade#setEffectiveLanguageLevel on a LightVirtualFile
        PsiFile psiFile =
            factory.createFileFromText(name, PythonLanguage.getInstance().getVersion(langLevel), contents, physical, true, true);
        assert psiFile != null;
        return psiFile;
    }

    public ASTNode createComma() {
        PsiFile dummyFile = createDummyFile(LanguageLevel.getDefault(), "[0,]");
        PyAstExpressionStatement expressionStatement = (PyAstExpressionStatement)dummyFile.getFirstChild();
        ASTNode zero = expressionStatement.getFirstChild().getNode().getFirstChildNode().getTreeNext();
        return zero.getTreeNext().copyElement();
    }

    public PyAstExpressionStatement createDocstring(String content) {
        return createFromText(LanguageLevel.getDefault(),
            PyAstExpressionStatement.class, content + "\n");
    }

    public PyAstPassStatement createPassStatement() {
        PyAstFunction function = createFromText(LanguageLevel.getDefault(), PyAstFunction.class, "def foo():\n\tpass");
        PyAstStatementList statementList = function.getStatementList();
        return (PyAstPassStatement)statementList.getStatements()[0];
    }

    public PyAstExpression createExpressionFromText(LanguageLevel languageLevel, String text)
        throws IncorrectOperationException {
        PsiFile dummyFile = createDummyFile(languageLevel, text);
        PsiElement element = dummyFile.getFirstChild();
        if (element instanceof PyAstExpressionStatement expressionStatement) {
            return expressionStatement.getExpression();
        }
        throw new IncorrectOperationException("could not parse text as expression: " + text);
    }
}
