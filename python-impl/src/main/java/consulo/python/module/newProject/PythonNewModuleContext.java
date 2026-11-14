/*
 * Copyright 2013-2026 consulo.io
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
package consulo.python.module.newProject;

import consulo.content.bundle.Sdk;
import consulo.module.creation.NewModuleWizardContextBase;
import org.jspecify.annotations.Nullable;

/**
 * @author VISTALL
 * @since 2026-11-14
 */
public class PythonNewModuleContext extends NewModuleWizardContextBase {
    private @Nullable Sdk mySdk;

    public PythonNewModuleContext(boolean isNewProject) {
        super(isNewProject);
    }

    public @Nullable Sdk getSdk() {
        return mySdk;
    }

    public void setSdk(@Nullable Sdk sdk) {
        mySdk = sdk;
    }
}
