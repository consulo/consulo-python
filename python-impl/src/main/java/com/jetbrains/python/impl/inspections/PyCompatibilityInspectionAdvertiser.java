// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.sdk.PythonSdkUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFile;
import consulo.ide.impl.idea.codeInspection.ex.EditInspectionToolsSettingsAction;
import consulo.language.content.FileIndexFacade;
import consulo.language.editor.annotation.AnnotationHolder;
import consulo.language.editor.annotation.Annotator;
import consulo.language.editor.inspection.scheme.InspectionProfile;
import consulo.language.editor.inspection.scheme.InspectionProjectProfileManager;
import consulo.language.editor.inspection.scheme.InspectionToolWrapper;
import consulo.language.editor.rawHighlight.HighlightDisplayKey;
import consulo.language.psi.PsiElement;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.project.Project;
import consulo.project.ui.notification.NotificationType;
import consulo.project.ui.notification.event.NotificationListener;
import consulo.python.impl.localize.PyCodeInsightLocalize;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.jetbrains.python.impl.statistics.PythonCompatibilityInspectionAdvertiserIdsHolder.STALE_PYTHON_VERSION;

/**
 * @author Mikhail Golubev
 */
public final class PyCompatibilityInspectionAdvertiser implements Annotator {

    private static final Key<Boolean> DONT_SHOW_BALLOON = Key.create("showingPyCompatibilityAdvertiserBalloon");

    // Allow to show declined suggestion multiple times to ease debugging
    private static final boolean SHOW_ONCE_FOR_VERSION = true;

    @Override
    public void annotate(PsiElement element, AnnotationHolder holder) {
        if (element instanceof PyFile pyFile) {

            final Project project = element.getProject();

            final VirtualFile vFile = pyFile.getVirtualFile();
            if (vFile != null && FileIndexFacade.getInstance(project).isInLibraryClasses(vFile)) {
                return;
            }

            final Boolean showingFlag = project.getUserData(DONT_SHOW_BALLOON);
            if (showingFlag != null && showingFlag.booleanValue()) {
                return;
            }

            if (!moduleUsesPythonSdk(pyFile)) {
                return;
            }

            final int inspectionVersion = getSettings(project).version;
            if (inspectionVersion < PyCompatibilityInspection.LATEST_INSPECTION_VERSION) {
                if (isCompatibilityInspectionEnabled(element)) {
                    final LanguageLevel pyVersion = getLatestConfiguredCompatiblePython3Version(element);
                    if (pyVersion != null && pyVersion.isOlderThan(LanguageLevel.getLatest())) {
                        showStalePython3VersionWarning(pyFile, project, pyVersion);
                    }
                }
            }
        }
    }

    private static boolean moduleUsesPythonSdk(PyFile file) {
        final Module module = ModuleUtilCore.findModuleForFile(file.getVirtualFile(), file.getProject());
        if (module != null) {
            // TODO consulo: EvoPyProject (com.intellij.python.sdk.backend.evolution) missing, JB:
            //  EvoPyProject pyProject = EvoPyProjectModelKt.findEvoPyProjectIfReady(module);
            //  return pyProject != null && pyProject.getInterpreter() != null;
            return PythonSdkUtil.findPythonSdk(module) != null;
        }
        return false;
    }

    private static @Nullable LanguageLevel getLatestConfiguredCompatiblePython3Version(PsiElement element) {
        final LanguageLevel latestVersion = getLatestConfiguredCompatiblePythonVersion(element);
        return latestVersion != null && !latestVersion.isPython2() ? latestVersion : null;
    }

    private static void showStalePython3VersionWarning(PyFile file,
                                                       Project project,
                                                       LanguageLevel latestConfiguredVersion) {
        final List<LanguageLevel> versionsToEnable = getVersionsNewerThan(latestConfiguredVersion);
        final String versionsList = StringUtil.join(versionsToEnable, LanguageLevel::toString, ",&nbsp;");
        final String message =
            PyCodeInsightLocalize.pythonCompatibilityInspectionAdvertiserVersionStalePython3VersionWarningMessage(
                latestConfiguredVersion,
                versionsList
            ).get();
        showSingletonNotification(
            project,
            PyCodeInsightLocalize.pythonCompatibilityInspectionAdvertiserNotificationsTitle().get(),
            message,
            STALE_PYTHON_VERSION,
            (notification, event) -> {
                final boolean enabled = "#yes".equals(event.getDescription());
                if (enabled) {
                    enableVersions(project, file, versionsToEnable);
                }
                if (enabled || SHOW_ONCE_FOR_VERSION) {
                    getSettings(project).version = PyCompatibilityInspection.LATEST_INSPECTION_VERSION;
                }
            }
        );
    }

    private static List<LanguageLevel> getVersionsNewerThan(LanguageLevel version) {
        final List<LanguageLevel> result = new ArrayList<>();
        final LanguageLevel latest = LanguageLevel.getLatest();
        for (LanguageLevel level : PyCompatibilityInspection.SUPPORTED_LEVELS) {
            if (version.isOlderThan(level) && latest.isAtLeast(level)) {
                result.add(level);
            }
        }
        return result;
    }

    private static void enableVersions(Project project, PsiElement file, List<LanguageLevel> versions) {
        final InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
        final String shortName = getCompatibilityInspectionShortName();
        final InspectionToolWrapper tool = profile.getInspectionTool(shortName, project);
        if (tool != null) {
            // Consulo: JB keeps the versions in PyCompatibilityInspection.ourVersions, Consulo keeps them in PyCompatibilityInspectionState
            profile.<PyCompatibilityInspection, PyCompatibilityInspectionState>modifyToolSettings(shortName, file, (inspection, state) -> {
                List<String> newVersions = new ArrayList<>(ContainerUtil.map(versions, LanguageLevel::toString));
                newVersions.removeAll(state.versions);
                state.versions.addAll(newVersions);
            });
            EditInspectionToolsSettingsAction.editToolSettings(project, profile, true, shortName);
        }
    }

    private static void showSingletonNotification(Project project, String msg, String displayId) {
        showSingletonNotification(
            project,
            PyCodeInsightLocalize.pythonCompatibilityInspectionAdvertiserNotificationsTitle().get(),
            msg,
            displayId,
            (notification, event) -> {
                final boolean enabled = "#yes".equals(event.getDescription());
                if (enabled) {
                    enableCompatibilityInspection(project);
                }
                if (enabled || SHOW_ONCE_FOR_VERSION) {
                    getSettings(project).version = PyCompatibilityInspection.LATEST_INSPECTION_VERSION;
                }
            }
        );
    }

    private static void showSingletonNotification(Project project,
                                                  String title,
                                                  String htmlContent,
                                                  String displayId,
                                                  NotificationListener listener) {
        project.putUserData(DONT_SHOW_BALLOON, true);
        PyCompatibilityInspectionAdvertiserNotificationGroupContributor.PYTHON_COMPATIBILITY_INSPECTION_ADVERTISER
            .createNotification(title, htmlContent, NotificationType.INFORMATION, (notification, event) -> {
                try {
                    listener.hyperlinkActivated(notification, event);
                }
                finally {
                    notification.expire();
                }
            })
            .setDisplayId(displayId)
            // TODO consulo: Notification.setSuggestionType(boolean) missing, JB: .setSuggestionType(true)
            .notify(project);
    }

    private static boolean isCompatibilityInspectionEnabled(PsiElement anchor) {
        final InspectionProfile profile = InspectionProjectProfileManager.getInstance(anchor.getProject()).getCurrentProfile();
        final InspectionToolWrapper tool = profile.getInspectionTool(getCompatibilityInspectionShortName(), anchor.getProject());
        // TODO consulo: InspectionToolWrapper.getID() missing, JB: HighlightDisplayKey.findById(tool.getID())
        return tool != null && profile.isToolEnabled(HighlightDisplayKey.find(tool.getShortName()), anchor);
    }

    private static void enableCompatibilityInspection(Project project) {
        final InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
        final InspectionToolWrapper tool = profile.getInspectionTool(getCompatibilityInspectionShortName(), project);
        if (tool != null) {
            // TODO consulo: InspectionProfileImpl.setToolEnabled(String, boolean) is internal, JB: profile.setToolEnabled(tool.getShortName(), true)
            profile.modifyProfile(model -> model.enableTool(tool.getShortName(), null, project));
            EditInspectionToolsSettingsAction.editToolSettings(project, profile, true, getCompatibilityInspectionShortName());
        }
    }

    private static @Nullable LanguageLevel getLatestConfiguredCompatiblePythonVersion(PsiElement anchor) {
        final InspectionProfile profile = InspectionProjectProfileManager.getInstance(anchor.getProject()).getCurrentProfile();
        // Consulo: JB reads PyCompatibilityInspection.ourVersions, Consulo keeps them in PyCompatibilityInspectionState
        final PyCompatibilityInspectionState state = profile.getToolState(getCompatibilityInspectionShortName(), anchor);
        assert state != null;
        final List<String> versions = state.versions;
        if (versions.isEmpty()) {
            return null;
        }
        return StreamEx.of(versions)
            .map(LanguageLevel::fromPythonVersion)
            .max(Comparator.naturalOrder())
            .orElse(null);
    }

    private static String getCompatibilityInspectionShortName() {
        return PyCompatibilityInspection.class.getSimpleName();
    }

    private static PyCompatibilityInspectionAdvertiserSettings getSettings(Project project) {
        return PyCompatibilityInspectionAdvertiserSettings.getInstance(project);
    }
}
