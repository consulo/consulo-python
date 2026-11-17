// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.debugger;

import com.jetbrains.python.debugger.PyStackFrameInfo;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import consulo.application.Application;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorCustomElementRenderer;
import consulo.codeEditor.Inlay;
import consulo.codeEditor.InlayModel;
import consulo.codeEditor.markup.GutterIconRenderer;
import consulo.colorScheme.TextAttributes;
import consulo.fileEditor.FileEditor;
import consulo.fileEditor.FileEditorManager;
import consulo.navigation.OpenFileDescriptor;
import consulo.navigation.OpenFileDescriptorFactory;
import consulo.fileEditor.TextEditor;
import consulo.project.Project;
import consulo.disposer.Disposer;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.project.ui.util.AppUIUtil;
import consulo.ui.ex.Gray;
import consulo.ui.ex.JBColor;
import consulo.util.collection.ContainerUtil;
import consulo.ui.ex.awt.UIUtil;
import consulo.execution.debug.XDebugSession;
import consulo.execution.debug.event.XDebugSessionListener;
import consulo.execution.debug.XSourcePosition;
import consulo.execution.debug.icon.ExecutionDebugIconGroup;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.image.Image;
import consulo.ui.style.StyleManager;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;

@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@Singleton
public final class PyUnitTestsDebuggingService {
    private static final Map<XDebugSession, List<Inlay<FailedTestInlayRenderer>>> ourActiveInlays = new WeakHashMap<>();
    private static final Map<Inlay<?>, ComponentListener> ourEditorListeners = Maps.newHashMap();

    private static final Set<String> PYTEST_SET_UP_AND_TEAR_DOWN_FUNCTION_NAMES = ImmutableSet.of(
      "call_fixture_func", "_eval_scope_callable", "_teardown_yield_fixture");
    private static final Set<String> UNITTEST_SET_UP_AND_TEAR_DOWN_FUNCTION_NAMES = ImmutableSet.of("_callSetUp", "_callTearDown");
    private static final Map<String, Set<String>> SET_UP_AND_TEAR_DOWN_FUNCTIONS_BY_FRAMEWORK = ImmutableMap.of(
      "pytest", PYTEST_SET_UP_AND_TEAR_DOWN_FUNCTION_NAMES, "unittest", UNITTEST_SET_UP_AND_TEAR_DOWN_FUNCTION_NAMES);

    /**
     * Show an inlay with the fail information at the place the test has failed.
     *
     * @param debugSession session from which process the service was called from
     * @param frame frame of the test function where an error has occurred
     * @param exceptionType the type name of an exception that made test fail
     * @param errorMessage  the message describing the reason of fail
     * @param isTestSetUpFail if the error happened in a test set up function or method
     */
    public void showFailedTestInlay(XDebugSession debugSession, PyStackFrame frame,
                                    String exceptionType, String errorMessage, boolean isTestSetUpFail) {
        showFailedTestInlay(debugSession, frame.getPosition(), exceptionType, errorMessage, isTestSetUpFail);
    }

    public void showFailedTestInlay(XDebugSession debugSession, @Nullable XSourcePosition position,
                                    String exceptionType, String errorMessage, boolean isTestSetUpFail) {
        if (position == null) {
            return;
        }
        AppUIUtil.invokeLaterIfProjectAlive(debugSession.getProject(), () -> {
            Consumer<Editor> addInlayToEditor = makeAddInlayToEditorFunction(debugSession, position, exceptionType, errorMessage, isTestSetUpFail);
            Arrays.stream(getAllEditorsForFile(debugSession.getProject(), position.getFile())).forEach(
              editor -> addInlayToEditor.accept(editor));
        });
    }

    private Consumer<Editor> makeAddInlayToEditorFunction(XDebugSession debugSession, XSourcePosition position,
                                                          String exceptionType, String errorMessage,
                                                          boolean isTestSetUpFail) {
        return editor -> {
            InlayModel inlayModel = editor.getInlayModel();

            Inlay<FailedTestInlayRenderer> inlay = inlayModel.addBlockElement(
              position.getOffset(), true, false, 0,
              new FailedTestInlayRenderer(exceptionType, errorMessage, isTestSetUpFail));

            if (inlay == null) {
                return;
            }

            ourActiveInlays.putIfAbsent(debugSession, new ArrayList<>());
            ourActiveInlays.get(debugSession).add(inlay);

            ComponentListener listener = new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    Rectangle inlayBounds = inlay.getBounds();
                    if (inlayBounds != null && e.getComponent().getWidth() != inlay.getBounds().width) {
                        inlay.update();
                    }
                }
            };
            editor.getComponent().addComponentListener(listener);
            ourEditorListeners.put(inlay, listener);

            if (isTestSetUpFail) {
                // Errors in test set up should be deleted after the session resumes since set up is called
                // multiple times and we don't want to stack up the inlays below the erroneous line.
                debugSession.addSessionListener(new XDebugSessionListener() {
                    @Override
                    public void sessionResumed() {
                        disposeInlay(inlay);
                        debugSession.removeSessionListener(this);
                    }
                });
            }
        };
    }

    public static void removeInlaysAssociatedWithSession(XDebugSession session) {
        List<Inlay<FailedTestInlayRenderer>> inlays = ourActiveInlays.getOrDefault(session, null);
        if (inlays != null) {
            inlays.forEach((inlay) -> Application.get().invokeLater(() -> disposeInlay(inlay)));
            ourActiveInlays.remove(session);
        }
    }

    /**
     * Dispose the inlay and forget its listener.
     * {@code ourEditorListeners} is static, so an entry that stays keeps the editor and the
     * project alive after the session ends.
     */
    private static void disposeInlay(Inlay<?> inlay) {
        ComponentListener listener = ourEditorListeners.remove(inlay);
        if (listener != null) {
            inlay.getEditor().getComponent().removeComponentListener(listener);
        }
        Disposer.dispose(inlay);
    }

    /**
     * Check if the error has happened while set up or tear down.
     *
     * @param frames of the thread where an exception has occured
     */
    public static boolean isErrorInTestSetUpOrTearDown(List<PyStackFrameInfo> frames) {
        return isErrorInTestSetUpOrTearDown(ContainerUtil.map(frames, PyStackFrameInfo::getName));
    }

    /**
     * Check if the error has happened while set up or tear down.
     *
     * @param frameNames the function names of the thread where an exception has occurred
     */
    public static boolean isErrorInTestSetUpOrTearDown(Collection<String> frameNames) {
        for (String frameName : frameNames) {
            if (ContainerUtil.exists(SET_UP_AND_TEAR_DOWN_FUNCTIONS_BY_FRAMEWORK.values(), names -> names.contains(frameName)))
              return true;
        }
        return false;
    }

    private static final class FailedTestInlayRenderer implements EditorCustomElementRenderer {

        private static final float HEIGHT_FACTOR = .5f;
        private static final short RIGHT_BAR_THICKNESS = 2;
        private static final short INLAY_TEXT_INDENT = 10;

        private final String myExceptionType;
        private final String myErrorMessage;
        private final boolean myIsTestSetUpFail;

        private int myCurrentLineNumber = 1;

        private FailedTestInlayRenderer(String exceptionType, String errorMessage, boolean isTestSetUpFail) {
            myExceptionType = exceptionType;
            myErrorMessage = StringUtil.trimTrailing(errorMessage);
            myIsTestSetUpFail = isTestSetUpFail;
        }

        @Override
        public int calcWidthInPixels(Inlay inlay) {
            return inlay.getEditor().getComponent().getWidth();
        }

        @Override
        public int calcHeightInPixels(Inlay inlay) {
            int lineHeight = inlay.getEditor().getLineHeight();
            return lineHeight + lineHeight * (StringUtil.countNewLines(myErrorMessage) + 1) + Math.round(lineHeight * HEIGHT_FACTOR);
        }

        @Override
        public GutterIconRenderer calcGutterIconRenderer(Inlay inlay) {
            return new PyUnitTestsDebuggingService.FailedTestGutterIconRenderer();
        }

        @Override
        public void paint(Inlay inlay,
                          Graphics g,
                          Rectangle targetRegion,
                          TextAttributes textAttributes) {
            resetCurrentLineNumber();

            g.setColor(getInlayBackgroundColor());
            g.fillRect(targetRegion.x, targetRegion.y, targetRegion.width, targetRegion.height);

            g.setColor(UIUtil.getErrorForeground());
            g.fillRect(targetRegion.x, targetRegion.y, targetRegion.x + RIGHT_BAR_THICKNESS, calcHeightInPixels(inlay));

            g.setFont(getFont(inlay.getEditor()));

            g.setColor(UIUtil.getErrorForeground());
            drawStringToInlayBox((myIsTestSetUpFail ? getErrorInTestSetUpCaption() : getFailedTestCaption()) + ":", inlay, g, targetRegion);

            g.setColor(UIUtil.getToolTipForeground());

            // An exception can carry no message. Then the type alone must appear, without a colon.
            String[] lines = StringUtil.splitByLines(myErrorMessage);
            String firstLine = myErrorMessage.isEmpty() ? myExceptionType : myExceptionType + ": " + lines[0];
            drawStringToInlayBox(firstLine, inlay, g, targetRegion);

            for (int k = 1; k < lines.length; k++) {
                drawStringToInlayBox(lines[k], inlay, g, targetRegion);
            }
        }

        private static Font getFont(Editor editor) {
            String fontName = UIUtil.getToolTipFont().getFontName();
            int fontSize = editor.getColorsScheme().getEditorFontSize();
            return UIUtil.getFontWithFallback(fontName, Font.PLAIN, fontSize);
        }

        private void drawStringToInlayBox(String str, Inlay<?> inlay, Graphics g, Rectangle targetRegion) {
            g.drawString(str, targetRegion.x + INLAY_TEXT_INDENT, targetRegion.y + inlay.getEditor().getLineHeight() * myCurrentLineNumber);
            myCurrentLineNumber++;
        }

        private void resetCurrentLineNumber() {
            myCurrentLineNumber = 1;
        }
    }

    private static Editor [] getAllEditorsForFile(Project project, VirtualFile file) {
        FileEditor[] fileEditors = FileEditorManager.getInstance(project).getAllEditors(file);

        if (fileEditors.length == 0) {
            Editor editor = openEditorForFile(project, file);
            return editor == null ? Editor.EMPTY_ARRAY : new Editor[]{openEditorForFile(project, file)};
        }

        return Arrays.stream(fileEditors)
          .map(fileEditor -> (TextEditor)fileEditor)
          .map(textEditor -> textEditor.getEditor())
          .toArray(Editor[]::new);
    }

    private static @Nullable Editor openEditorForFile(Project project, VirtualFile file) {
        OpenFileDescriptor descriptor = OpenFileDescriptorFactory.getInstance(project).builder(file).offset(0).build();
        return FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
    }

    private static Color getInlayBackgroundColor() {
        return !StyleManager.get().getCurrentStyle().isDark() ? new JBColor(Gray._240, Gray._192) : JBColor.WHITE;
    }

    private static String getFailedTestCaption() {
        return PyLocalize.debuggerTestFailedCaption().get();
    }

    private static String getErrorInTestSetUpCaption() {
        return PyLocalize.debuggerErrorInTestSetupOrTeardownCaption().get();
    }

    private static final class FailedTestGutterIconRenderer extends GutterIconRenderer {
        private static final FailedTestGutterIconRenderer INSTANCE = new FailedTestGutterIconRenderer();

        private FailedTestGutterIconRenderer() {}

        public static PyUnitTestsDebuggingService.FailedTestGutterIconRenderer getInstance() {
            return INSTANCE;
        }

        @Override
        public Image getIcon() {
            return ExecutionDebugIconGroup.breakpointBreakpointexception();
        }

        @Override
        public Alignment getAlignment() {
            return Alignment.RIGHT;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof FailedTestGutterIconRenderer;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }
    }
}
