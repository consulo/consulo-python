package consulo.python.regexp.impl.internal;

import com.jetbrains.python.psi.PyStringLiteralExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;


/**
 * @author VISTALL
 * @since 27/04/2023
 */
@ExtensionImpl
public class PyStringLiteralExpressionRegexpInjector extends PythonRegexpInjector {
    @Override
    public Class<? extends PsiElement> getElementClass() {
        return PyStringLiteralExpression.class;
    }
}
