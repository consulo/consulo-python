// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.validation;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.documentation.docstrings.DocStringParser;
import com.jetbrains.python.formatter.PyCodeStyleSettings;
import com.jetbrains.python.impl.HelperPackage;
import com.jetbrains.python.impl.PythonHelper;
import com.jetbrains.python.impl.codeInsight.imports.OptimizeImportsQuickFix;
import com.jetbrains.python.impl.inspections.PyPep8Inspection;
import com.jetbrains.python.impl.inspections.PyPep8InspectionState;
import com.jetbrains.python.impl.inspections.quickfix.PyFillParagraphFix;
import com.jetbrains.python.impl.inspections.quickfix.ReformatFix;
import com.jetbrains.python.impl.inspections.quickfix.RemoveTrailingBlankLinesFix;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyFileImpl;
import com.jetbrains.python.impl.sdk.PreferredSdkComparator;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import com.jetbrains.python.impl.sdk.PythonSdkUtil;
import com.jetbrains.python.impl.sdk.flavors.PythonSdkFlavor;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyKeywordPattern;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ApplicationProperties;
import consulo.codeEditor.Editor;
import consulo.codeEditor.PersistentEditorSettings;
import consulo.content.bundle.Sdk;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.ide.impl.idea.codeInspection.ex.CustomEditInspectionToolsSettingsAction;
import consulo.language.Language;
import consulo.language.codeStyle.CodeStyle;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.CommonCodeStyleSettings;
import consulo.language.editor.annotation.AnnotationBuilder;
import consulo.language.editor.annotation.AnnotationHolder;
import consulo.language.editor.annotation.ExternalAnnotator;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.editor.inspection.scheme.InspectionProfile;
import consulo.language.editor.inspection.scheme.InspectionProjectProfileManager;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.editor.rawHighlight.HighlightDisplayKey;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.jetbrains.python.impl.validation.PyPep8HelperBridgeKt.execPep8Helper;

@ExtensionImpl
public final class Pep8ExternalAnnotator extends ExternalAnnotator<Pep8ExternalAnnotator.State, Pep8ExternalAnnotator.Results> {
    // Consulo: JB selects one of the bundled pycodestyle-2.8.0.py / pycodestyle-2.10.0.py / pycodestyle.py copies by
    // the interpreter version (selectPycodestyleHelper); Consulo bundles only one pycodestyle.py helper
    // TODO consulo: pycodestyle-2.8.0.py and pycodestyle-2.10.0.py helpers missing

    // Taken directly from the sources of pycodestyle.py
    private static final String DEFAULT_IGNORED_ERRORS = "E121,E123,E126,E226,E24,E704,W503,W504";
    private static final Logger LOG = Logger.getInstance(Pep8ExternalAnnotator.class);
    private static final Pattern E303_LINE_COUNT_PATTERN = Pattern.compile(".*\\((\\d+)\\)$");

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }

    public static class Problem {
        private final int myLine;
        private final int myColumn;
        private final String myCode;
        private final String myDescription;

        public Problem(int line, int column, String code, String description) {
            myLine = line;
            myColumn = column;
            myCode = code;
            myDescription = description;
        }

        public int getLine() {
            return myLine;
        }

        public int getColumn() {
            return myColumn;
        }

        public String getCode() {
            return myCode;
        }

        public String getDescription() {
            return myDescription;
        }
    }

    @Override
    public @Nullable State collectInformation(PsiFile psiFile) {
        VirtualFile vFile = psiFile.getVirtualFile();
        if (vFile == null || vFile.getFileType() != PythonFileType.INSTANCE) {
            return null;
        }
        Sdk sdk = PythonSdkType.findLocalCPython(DocStringParser.getModuleForElement(psiFile));
        if (sdk == null) {
            if (!myReportedMissingInterpreter) {
                myReportedMissingInterpreter = true;
                reportMissingInterpreter();
            }
            return null;
        }
        final String homePath = sdk.getHomePath();
        if (homePath == null) {
            if (!myReportedMissingInterpreter) {
                myReportedMissingInterpreter = true;
                LOG.info("Could not find home path for interpreter " + homePath);
            }
            return null;
        }
        final InspectionProfile profile = InspectionProjectProfileManager.getInstance(psiFile.getProject()).getInspectionProfile();
        final HighlightDisplayKey key = HighlightDisplayKey.find(PyPep8Inspection.INSPECTION_SHORT_NAME);
        if (!profile.isToolEnabled(key, psiFile)) {
            return null;
        }
        if (psiFile instanceof PyFileImpl file && !file.isAcceptedFor(PyPep8Inspection.class)) {
            return null;
        }
        // Consulo: the inspection options live in the tool state
        final PyPep8InspectionState toolState = profile.getToolState(PyPep8Inspection.INSPECTION_SHORT_NAME, psiFile);
        final CodeStyleSettings commonSettings = CodeStyle.getSettings(psiFile);
        final PyCodeStyleSettings customSettings = CodeStyle.getCustomSettings(psiFile, PyCodeStyleSettings.class);

        final List<String> ignoredErrors = new ArrayList<>(toolState.ignoredErrors);
        if (!customSettings.SPACE_AFTER_NUMBER_SIGN) {
            ignoredErrors.add("E262"); // Block comment should start with a space
            ignoredErrors.add("E265"); // Inline comment should start with a space
        }

        if (!customSettings.SPACE_BEFORE_NUMBER_SIGN) {
            ignoredErrors.add("E261"); // At least two spaces before inline comment
        }

        return new State(Path.of(homePath), // homePath is always valid!
            // The local SDK used to launch the pycodestyle.py script is not necessarily of the same version as the project SDK,
            // so LanguageLevel.forElement(file) might lead to launching the script incompatible with the "runner" interpreter.
            PythonSdkType.getLanguageLevelForSdk(sdk),
            psiFile.getText(),
            profile.getErrorLevel(key, psiFile),
            ignoredErrors,
            commonSettings.getRightMargin(PythonLanguage.getInstance()),
            customSettings.HANG_CLOSING_BRACKETS);
    }

    public static class Results {
        public final List<Problem> problems = new ArrayList<>();
        private final HighlightDisplayLevel level;

        public Results(HighlightDisplayLevel level) {
            this.level = level;
        }
    }

    private boolean myReportedMissingInterpreter;

    // TODO consulo: ExternalAnnotator.getPairedBatchInspectionShortName missing - JB returns PyPep8Inspection.INSPECTION_SHORT_NAME

    @Override
    public @Nullable Results doAnnotate(State collectedInfo) {
        if (collectedInfo == null) {
            return null;
        }
        ArrayList<String> options = new ArrayList<>();

        if (!collectedInfo.ignoredErrors.isEmpty()) {
            options.add("--ignore=" + DEFAULT_IGNORED_ERRORS + "," + StringUtil.join(collectedInfo.ignoredErrors, ","));
        }
        if (collectedInfo.hangClosingBrackets) {
            options.add("--hang-closing");
        }
        options.add("--max-line-length=" + collectedInfo.margin);
        options.add("-");

        HelperPackage pycodestyleScript = PythonHelper.PYCODESTYLE;
        byte[] stdin = collectedInfo.fileText.getBytes(StandardCharsets.UTF_8);
        String output = execPep8Helper(pycodestyleScript, collectedInfo.interpreterPath, stdin, options);


        Results results = new Results(collectedInfo.level);
        if (output == null) {
            LOG.info("Timeout running pycodestyle.py");
            return results;
        }
        for (String line : StringUtil.split(output, "\n")) {
            ContainerUtil.addIfNotNull(results.problems, parseProblem(line));
        }
        return results;
    }

    private static void reportMissingInterpreter() {
        LOG.info("Found no suitable interpreter to run pycodestyle.py. Available interpreters are: [");
        List<Sdk> allSdks = ContainerUtil.sorted(PythonSdkUtil.getAllSdks(), PreferredSdkComparator.INSTANCE);
        for (Sdk sdk : allSdks) {
            LOG.info("  Path: " + sdk.getHomePath() + "; Flavor: " + PythonSdkFlavor.getFlavor(sdk) + "; Remote: " + PythonSdkUtil.isRemote(sdk));
        }
        LOG.info("]");
    }

    @Override
    public void apply(PsiFile psiFile, Results annotationResult, AnnotationHolder holder) {
        if (annotationResult == null || !psiFile.isValid()) {
            return;
        }
        final String text = psiFile.getText();
        Project project = psiFile.getProject();
        final Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);

        final InspectionProfile profile = InspectionProjectProfileManager.getInstance(psiFile.getProject()).getInspectionProfile();
        final PyPep8Inspection inspection = profile.getUnwrappedTool(PyPep8Inspection.INSPECTION_SHORT_NAME, psiFile);

        for (Problem problem : annotationResult.problems) {
            final int line = problem.myLine - 1;
            final int column = problem.myColumn - 1;
            int offset;
            if (document != null) {
                offset = line >= document.getLineCount() ? document.getTextLength() - 1 : document.getLineStartOffset(line) + column;
            }
            else {
                offset = StringUtil.lineColToOffset(text, line, column);
            }
            PsiElement problemElement = psiFile.findElementAt(offset);
            // E3xx - blank lines warnings
            if (!(problemElement instanceof PsiWhiteSpace) && problem.myCode.startsWith("E3")) {
                final PsiElement elementBefore = psiFile.findElementAt(Math.max(0, offset - 1));
                if (elementBefore instanceof PsiWhiteSpace) {
                    problemElement = elementBefore;
                }
            }
            // W292 no newline at end of file
            if (problemElement == null && document != null && offset == document.getTextLength() && problem.myCode.equals("W292")) {
                problemElement = psiFile.findElementAt(Math.max(0, offset - 1));
            }

            // TODO consulo: ignoredDueToNoqaComment missing - it needs com.jetbrains.python.inspections.flake8.Flake8InspectionSuppressor
            if (ignoreDueToSettings(psiFile, problem, problemElement) ||
                ignoredDueToProblemSuppressors(problem, psiFile, problemElement)) {
                continue;
            }

            if (problemElement != null) {
                // Consulo: a workaround until the bundled pycodestyle.py supports Python 3.6 variable annotations by itself
                if (problem.myCode.equals("E701") &&
                    problemElement.getNode().getElementType() == PyTokenTypes.COLON &&
                    problemElement.getParent() instanceof PyAnnotation) {
                    continue;
                }

                if (inspection != null && inspection.isSuppressedFor(problemElement)) {
                    continue;
                }

                TextRange problemRange = problemElement.getTextRange();
                // Multi-line warnings are shown only in the gutter and it's not the desired behavior from the usability point of view.
                // So we register it only on that line where pycodestyle.py found the problem originally.
                if (crossesLineBoundary(document, text, problemRange)) {
                    final int lineEndOffset;
                    if (document != null) {
                        lineEndOffset = line >= document.getLineCount() ? document.getTextLength() - 1 : document.getLineEndOffset(line);
                    }
                    else {
                        lineEndOffset = StringUtil.lineColToOffset(text, line + 1, 0) - 1;
                    }
                    if (offset > lineEndOffset) {
                        // PSI/document don't match, don't try to highlight random places
                        continue;
                    }
                    problemRange = new TextRange(offset, lineEndOffset);
                }

                final String message = "PEP 8: " + problem.myCode + " " + problem.myDescription;
                HighlightSeverity severity = annotationResult.level.getSeverity();
                IntentionAction fix;
                boolean universal;
                if (problem.myCode.equals("E401")) {
                    fix = new OptimizeImportsQuickFix();
                    universal = true;
                }
                else if (problem.myCode.equals("W391")) {
                    fix = new RemoveTrailingBlankLinesFix();
                    universal = true;
                }
                else if (problem.myCode.equals("E501")) {
                    fix = new PyFillParagraphFix();
                    universal = false;
                }
                else {
                    fix = new ReformatFix();
                    universal = true;
                }
                AnnotationBuilder builder = holder.newAnnotation(severity, message).range(problemRange);
                if (universal) {
                    builder = builder.newFix(fix).universal().registerFix();
                }
                else {
                    builder = builder.withFix(fix);
                }
                builder
                    .withFix(new IgnoreErrorFix(problem.myCode))
                    .withFix(new CustomEditInspectionToolsSettingsAction(HighlightDisplayKey.find(PyPep8Inspection.INSPECTION_SHORT_NAME),
                        PyLocalize.qfixPep8EditInspectionProfileSetting()))
                    .create();
            }
        }
    }

    static class State {
        private final Path interpreterPath;
        private final LanguageLevel interpreterVersion;
        private final String fileText;
        private final HighlightDisplayLevel level;
        private final List<String> ignoredErrors;
        private final int margin;
        private final boolean hangClosingBrackets;

        State(Path interpreterPath,
              LanguageLevel interpreterVersion,
              String fileText,
              HighlightDisplayLevel level,
              List<String> ignoredErrors,
              int margin,
              boolean hangClosingBrackets) {
            this.interpreterPath = interpreterPath;
            this.interpreterVersion = interpreterVersion;
            this.fileText = fileText;
            this.level = level;
            this.ignoredErrors = ignoredErrors;
            this.margin = margin;
            this.hangClosingBrackets = hangClosingBrackets;
        }
    }

    private static boolean ignoredDueToProblemSuppressors(Problem problem,
                                                          PsiFile file,
                                                          @Nullable PsiElement element) {
        final Pep8ProblemSuppressor[] suppressors = Pep8ProblemSuppressor.EP_NAME.getExtensions();
        return Arrays.stream(suppressors).anyMatch(p -> p.isProblemSuppressed(problem, file, element));
    }

    private static boolean crossesLineBoundary(@Nullable Document document, String text, TextRange problemRange) {
        int start = problemRange.getStartOffset();
        int end = problemRange.getEndOffset();
        if (document != null) {
            return document.getLineNumber(start) != document.getLineNumber(end);
        }
        return StringUtil.offsetToLineNumber(text, start) != StringUtil.offsetToLineNumber(text, end);
    }

    private static boolean ignoreDueToSettings(PsiFile file, Problem problem, @Nullable PsiElement element) {
        final PersistentEditorSettings editorSettings = PersistentEditorSettings.getInstance();
        if (!editorSettings.getStripTrailingSpaces().equals("None")) {
            // ignore trailing spaces errors if they're going to disappear after save
            if (problem.myCode.equals("W291") || problem.myCode.equals("W293")) {
                return true;
            }
        }

        final CommonCodeStyleSettings commonSettings = CodeStyle.getLanguageSettings(file, PythonLanguage.getInstance());
        final PyCodeStyleSettings pySettings = CodeStyle.getCustomSettings(file, PyCodeStyleSettings.class);

        if (element instanceof PsiWhiteSpace) {
            // E303 too many blank lines (num)
            if (problem.myCode.equals("E303")) {
                final Matcher matcher = E303_LINE_COUNT_PATTERN.matcher(problem.myDescription);
                if (matcher.matches()) {
                    final int reportedBlanks = Integer.parseInt(matcher.group(1));
                    final PsiElement nonWhitespaceAfter = PyPsiUtils.getNextNonWhitespaceSibling(element);
                    final PsiElement nonWhitespaceBefore = PyPsiUtils.getPrevNonWhitespaceSibling(element);
                    final boolean classNearby = nonWhitespaceBefore instanceof PyClass || nonWhitespaceAfter instanceof PyClass;
                    final boolean functionNearby = nonWhitespaceBefore instanceof PyFunction || nonWhitespaceAfter instanceof PyFunction;
                    if (functionNearby || classNearby) {
                        if (PyUtil.isTopLevel(element)) {
                            if (reportedBlanks <= pySettings.BLANK_LINES_AROUND_TOP_LEVEL_CLASSES_FUNCTIONS) {
                                return true;
                            }
                        }
                        else {
                            // Blanks around classes have priority over blanks around functions as defined in Python spacing builder
                            if (classNearby && reportedBlanks <= commonSettings.BLANK_LINES_AROUND_CLASS ||
                                functionNearby && reportedBlanks <= commonSettings.BLANK_LINES_AROUND_METHOD) {
                                return true;
                            }
                        }
                    }
                }
            }

            // E251 unexpected spaces around keyword / parameter equals
            // Note that E222 (multiple spaces after operator) is not suppressed, though.
            if (problem.myCode.equals("E251") &&
                (element.getParent() instanceof PyParameter && pySettings.SPACE_AROUND_EQ_IN_NAMED_PARAMETER ||
                    element.getParent() instanceof PyKeywordArgument && pySettings.SPACE_AROUND_EQ_IN_KEYWORD_ARGUMENT ||
                    element.getParent() instanceof PyKeywordPattern && pySettings.SPACE_AROUND_EQ_IN_KEYWORD_ARGUMENT)) {
                return true;
            }
        }
        // W191 (indentation contains tabs) is reported also for indents inside multiline string literals,
        // thus underlying PSI element is not necessarily a whitespace
        if (problem.myCode.equals("W191") && CodeStyle.getIndentOptions(file).USE_TAB_CHARACTER) {
            return true;
        }
        return false;
    }

    private static final Pattern PROBLEM_PATTERN = Pattern.compile(".+:(\\d+):(\\d+): ([EW]\\d{3}) (.+)");

    private static @Nullable Problem parseProblem(String s) {
        Matcher m = PROBLEM_PATTERN.matcher(s);
        if (m.matches()) {
            int line = Integer.parseInt(m.group(1));
            int column = Integer.parseInt(m.group(2));
            return new Problem(line, column, m.group(3), m.group(4));
        }
        // Consulo: JB checks ApplicationInfo.isEAP()
        if (ApplicationProperties.isInSandbox()) {
            LOG.info("Failed to parse problem line from pycodestyle.py: " + s);
        }
        return null;
    }

    private static class IgnoreErrorFix implements SyntheticIntentionAction {
        private final String myCode;

        IgnoreErrorFix(String code) {
            myCode = code;
        }

        @Override
        public LocalizeValue getText() {
            return PyLocalize.annIgnoreErrorsLikeThis();
        }

        @Override
        public boolean isAvailable(Project project, Editor editor, PsiFile psiFile) {
            return true;
        }

        @Override
        public void invoke(Project project, Editor editor, final PsiFile psiFile) throws IncorrectOperationException {
            // Consulo: the inspection options live in the tool state
            InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
            profile.<PyPep8Inspection, PyPep8InspectionState>modifyToolSettings(PyPep8Inspection.INSPECTION_SHORT_NAME, psiFile, (tool, s) -> {
                if (!s.ignoredErrors.contains(myCode)) {
                    s.ignoredErrors.add(myCode);
                }
            });
        }

        @Override
        public boolean startInWriteAction() {
            return false;
        }
    }
}
