/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.rest.quickfixes;

import com.jetbrains.python.rest.inspections.RestRoleInspection;
import com.jetbrains.python.rest.inspections.RestRoleInspectionState;
import com.jetbrains.rest.RestBundle;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.scheme.InspectionProfile;
import consulo.language.editor.inspection.scheme.InspectionProjectProfileManager;
import consulo.language.editor.intention.LowPriorityAction;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

/**
 * @author catherine
 */
public class AddIgnoredRoleFix implements LocalQuickFix, LowPriorityAction {
    private final String myRole;

    public AddIgnoredRoleFix(String role) {
        myRole = role;
    }

    @Nonnull
    @Override
    public LocalizeValue getName() {
        return LocalizeValue.localizeTODO(RestBundle.message("QFIX.ignore.role", myRole));
    }

    @Override
    public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
        final InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
        profile.<RestRoleInspection, RestRoleInspectionState>modifyToolSettings(
            RestRoleInspection.class.getSimpleName(),
            descriptor.getPsiElement(),
            (t, s) -> {
                if (!s.ignoredRoles.contains(myRole)) {
                    s.ignoredRoles.add(myRole);
                }
            }
        );
    }
}
