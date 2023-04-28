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

package com.jetbrains.python;

import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.module.Module;
import consulo.ide.impl.idea.openapi.module.ModuleServiceManager;
import consulo.util.xml.serializer.XmlSerializerUtil;
import javax.annotation.Nonnull;

/**
 * User: catherine
 */
@State(name = "ReSTService",
       storages = {@Storage(file = "$MODULE_FILE$")}
)
public class ReSTService implements PersistentStateComponent<ReSTService> {
  public String DOC_DIR = "";
  public boolean TXT_IS_RST = false;

  public ReSTService() {
  }

  @Override
  public ReSTService getState() {
    return this;
  }

  @Override
  public void loadState(ReSTService state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public void setWorkdir(String workDir) {
    DOC_DIR = workDir;
  }

  public static ReSTService getInstance(@Nonnull Module module) {
    return module.getInstance(ReSTService.class);
  }

  public String getWorkdir() {
    return DOC_DIR;
  }

  public boolean txtIsRst() {
    return TXT_IS_RST;
  }

  public void setTxtIsRst(boolean isRst) {
    TXT_IS_RST = isRst;
  }
}
