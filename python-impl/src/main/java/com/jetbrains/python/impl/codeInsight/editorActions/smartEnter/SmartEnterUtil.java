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

package com.jetbrains.python.impl.codeInsight.editorActions.smartEnter;

import jakarta.annotation.Nonnull;

import consulo.ui.ex.action.IdeActions;
import consulo.codeEditor.Editor;
import consulo.codeEditor.action.EditorActionHandler;
import consulo.codeEditor.action.EditorActionManager;
import consulo.codeEditor.EditorEx;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   15.04.2010
 * Time:   19:48:16
 */
public class SmartEnterUtil {
  private static EditorActionHandler getEnterHandler() {
    return EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_START_NEW_LINE);
  }

  public static void plainEnter(@Nonnull final Editor editor) {
    getEnterHandler().execute(editor, ((EditorEx) editor).getDataContext());
  }

  private SmartEnterUtil() {
  }
}
