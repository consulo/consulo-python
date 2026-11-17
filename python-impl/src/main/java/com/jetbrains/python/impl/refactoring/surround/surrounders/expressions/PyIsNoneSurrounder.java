/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.impl.refactoring.surround.surrounders.expressions;

import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;

public class PyIsNoneSurrounder extends PyBinaryConditionSurrounder {
    private static final LocalizeValue TEMPLATE_DESCRIPTION = PyLocalize.surroundWithIsNoneTemplate();

    public PyIsNoneSurrounder() {
        super("if expr is None:\n pass");
    }

    @Override
    public LocalizeValue getTemplateDescription() {
        //noinspection DialogTitleCapitalization
        return TEMPLATE_DESCRIPTION;
    }
}
