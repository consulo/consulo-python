// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions.convertToFString;

import com.jetbrains.python.impl.PyStringFormatParser.NewStyleSubstitutionChunk;
import com.jetbrains.python.impl.PyStringFormatParser.PercentSubstitutionChunk;
import com.jetbrains.python.impl.PyStringFormatParser.SubstitutionChunk;
import com.jetbrains.python.impl.PyStringFormatParser;
import com.jetbrains.python.impl.codeInsight.PySubstitutionChunkReference;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static com.jetbrains.python.impl.codeInsight.intentions.ConvertFormatOperatorToMethodIntention.convertFormatSpec;
import static com.jetbrains.python.impl.psi.PyUtil.as;

/**
 * @author Mikhail Golubev
 */
public class OldStyleConvertToFStringProcessor extends BaseConvertToFStringProcessor<SubstitutionChunk> {
    public OldStyleConvertToFStringProcessor(PyStringLiteralExpression pyString) {
        super(pyString);
    }

    @Override
    protected List<SubstitutionChunk> extractAllSubstitutionChunks() {
        return PyStringFormatParser.filterSubstitutions(PyStringFormatParser.parsePercentFormat(myPyString.getText()));
    }

    @Override
    protected PySubstitutionChunkReference createReference(SubstitutionChunk chunk) {
        return new PySubstitutionChunkReference(myPyString, chunk);
    }

    @Override
    protected boolean checkChunk(SubstitutionChunk chunk) {
        // TODO handle dynamic width and precision in old-style/"percent" formatting
        return !("*".equals(chunk.getPrecision()) || "*".equals(chunk.getWidth()));
    }

    @Override
    protected boolean checkReferencedExpression(List<SubstitutionChunk> chunks,
            SubstitutionChunk chunk,
            PsiElement valueSource,
            PyExpression expression) {
        if ((chunk.getMappingKey() != null || chunks.size() > 1) && expression == valueSource) {
            return false;
        }
        return super.checkReferencedExpression(chunks, chunk, valueSource, expression);
    }

    @Override
    public PyExpression getWholeExpressionToReplace() {
        //noinspection ConstantConditions
        return PsiTreeUtil.getParentOfType(myPyString, PyBinaryExpression.class);
    }

    @Override
    protected @Nullable PsiElement getValuesSource() {
        PyBinaryExpression binaryExpression = as(myPyString.getParent(), PyBinaryExpression.class);
        assert binaryExpression != null;
        return binaryExpression.getRightExpression();
    }

    @Override
    protected boolean processSubstitutionChunk(SubstitutionChunk subsChunk, StringBuilder fStringText) {
        char conversionChar = subsChunk.getConversionType();

        String widthAndPrecision = StringUtil.notNullize(subsChunk.getWidth());
        if (StringUtil.isNotEmpty(subsChunk.getPrecision())) {
            widthAndPrecision += "." + subsChunk.getPrecision();
        }

        String conversionFlags = subsChunk instanceof PercentSubstitutionChunk ?
                ((PercentSubstitutionChunk)subsChunk).getConversionFlags() :
                ((NewStyleSubstitutionChunk)subsChunk).getConversion();

        fStringText.append("{");
        PySubstitutionChunkReference reference = createReference(subsChunk);
        PyExpression resolveResult = adjustResolveResult(reference.resolve());
        assert resolveResult != null;

        PsiElement adjusted = prepareExpressionToInject(resolveResult, subsChunk);
        if (adjusted == null) {
            return false;
        }
        fStringText.append(adjusted.getText());

        // TODO mostly duplicates the logic of ConvertFormatOperatorToMethodIntention
        if (conversionChar == 'r') {
            fStringText.append("!r");
        }

        if ((conversionChar != 'r' && conversionChar != 's')
                || StringUtil.isNotEmpty(conversionFlags)
                || StringUtil.isNotEmpty(widthAndPrecision)) {
            fStringText.append(":");
        }

        fStringText.append(convertFormatSpec(StringUtil.notNullize(conversionFlags), widthAndPrecision, String.valueOf(conversionChar)));

        if (StringUtil.isNotEmpty(widthAndPrecision)) {
            fStringText.append(widthAndPrecision);
        }

        if ('i' == conversionChar || 'u' == conversionChar) {
            fStringText.append("d");
        }
        else if ('s' != conversionChar && 'r' != conversionChar) {
            fStringText.append(conversionChar);
        }
        fStringText.append("}");
        return true;
    }

    @Override
    protected void processLiteralChunk(String chunk, StringBuilder fStringText) {
        fStringText.append(chunk.replace("%%", "%").replace("{", "{{").replace("}", "}}"));
    }
}

