// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions.convertToFString;

import com.jetbrains.python.impl.PyNewStyleStringFormatParser.Field;
import com.jetbrains.python.impl.PyNewStyleStringFormatParser;
import com.jetbrains.python.impl.codeInsight.PySubstitutionChunkReference;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyNumericLiteralExpression;
import com.jetbrains.python.psi.PyPrefixExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyStringLiteralUtil;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class NewStyleConvertToFStringProcessor extends BaseConvertToFStringProcessor<Field> {
    public NewStyleConvertToFStringProcessor(PyStringLiteralExpression pyString) {
        super(pyString);
    }

    @Override
    protected List<Field> extractAllSubstitutionChunks() {
        return PyNewStyleStringFormatParser.parse(myPyString.getText()).getAllFields();
    }

    @Override
    protected List<Field> extractTopLevelSubstitutionChunks() {
        return PyNewStyleStringFormatParser.parse(myPyString.getText()).getFields();
    }

    @Override
    protected PySubstitutionChunkReference createReference(Field field) {
        return new PySubstitutionChunkReference(myPyString, field);
    }

    @Override
    protected boolean checkChunk(Field chunk) {
        return true;
    }

    @Override
    public PyExpression getWholeExpressionToReplace() {
        //noinspection ConstantConditions
        return PsiTreeUtil.getParentOfType(myPyString, PyCallExpression.class);
    }

    @Override
    protected @Nullable PsiElement getValuesSource() {
        PyCallExpression callExpression = PsiTreeUtil.getParentOfType(myPyString, PyCallExpression.class);
        assert callExpression != null;
        return callExpression.getArgumentList();
    }

    @Override
    protected @Nullable PsiElement prepareExpressionToInject(PyExpression expression, Field field) {
        PsiElement prepared = super.prepareExpressionToInject(expression, field);
        if (prepared == null) {
            return null;
        }
        // You cannot access attributes on numeric literals without wrapping them in parentheses
        if (!field.getAttributesAndLookups().isEmpty() && (prepared instanceof PyBinaryExpression ||
                prepared instanceof PyPrefixExpression ||
                prepared instanceof PyNumericLiteralExpression)) {
            return wrapExpressionInParentheses(prepared);
        }
        return prepared;
    }

    @Override
    protected boolean processSubstitutionChunk(Field field, StringBuilder fStringText) {

        String stringText = myPyString.getText();

        // Actual format field
        fStringText.append("{");
        PySubstitutionChunkReference reference = createReference(field);
        PyExpression resolveResult = adjustResolveResult(reference.resolve());
        if (resolveResult == null) {
            return false;
        }
        PsiElement adjusted = prepareExpressionToInject(resolveResult, field);
        if (adjusted == null) {
            return false;
        }
        fStringText.append(adjusted.getText());
        String quotedAttrsAndItems = quoteItemsInFragments(field);
        if (quotedAttrsAndItems == null) {
            return false;
        }
        fStringText.append(quotedAttrsAndItems);

        // Conversion is copied as is if it's present
        String conversion = field.getConversion();
        if (conversion != null) {
            fStringText.append(conversion);
        }

        // Format spec is copied if present handling nested fields
        TextRange specRange = field.getFormatSpecRange();
        if (specRange != null) {
            int specOffset = specRange.getStartOffset();
            // Do not proceed too nested fields
            if (field.getDepth() == 1) {
                for (Field nestedField : field.getNestedFields()) {
                    // Copy text of the format spec between nested fragments
                    fStringText.append(stringText, specOffset, nestedField.getLeftBraceOffset());
                    specOffset = nestedField.getFieldEnd();

                    // recursively format nested field
                    if (!processSubstitutionChunk(nestedField, fStringText)) {
                        return false;
                    }
                }
            }
            if (specOffset < specRange.getEndOffset()) {
                fStringText.append(stringText, specOffset, specRange.getEndOffset());
            }
        }

        fStringText.append("}");
        return true;
    }

    @Override
    protected void processLiteralChunk(String chunk, StringBuilder fStringText) {
        fStringText.append(chunk);
    }

    private @Nullable String quoteItemsInFragments(Field field) {
        List<String> escaped = new ArrayList<>();
        for (String part : field.getAttributesAndLookups()) {
            if (part.startsWith(".")) {
                escaped.add(part);
            }
            else if (part.startsWith("[")) {
                if (part.contains("\\")) {
                    return null;
                }
                String indexText = part.substring(1, part.length() - 1);
                if (indexText.matches("\\d+")) {
                    escaped.add(part);
                    continue;
                }
                char originalQuote = myNodeInfo.getSingleQuote();
                char targetQuote = PyStringLiteralUtil.flipQuote(originalQuote);
                // there are no escapes inside the fragment, so the lookup key cannot contain 
                // the host string quote unless it's a multiline string literal
                if (indexText.indexOf(targetQuote) >= 0) {
                    if (!myNodeInfo.isTripleQuoted() || indexText.indexOf(originalQuote) >= 0) {
                        return null;
                    }
                    targetQuote = originalQuote;
                }
                escaped.add("[" + targetQuote + indexText + targetQuote + "]");
            }
        }
        return StringUtil.join(escaped, "");
    }
}

