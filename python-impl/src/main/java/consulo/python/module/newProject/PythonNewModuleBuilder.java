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

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.AllIcons;
import consulo.content.bundle.Sdk;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.module.content.layer.ContentEntry;
import consulo.module.content.layer.ModifiableRootModel;
import consulo.module.creation.scratch.NewModuleBuilder;
import consulo.module.creation.scratch.NewModuleBuilderProcessor;
import consulo.module.creation.scratch.NewModuleContext;
import consulo.module.creation.scratch.NewModuleContextGroup;
import consulo.python.module.extension.BasePyMutableModuleExtension;
import consulo.python.psi.icon.PythonPsiIconGroup;
import consulo.ui.ex.wizard.WizardStep;
import consulo.virtualFileSystem.VirtualFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * New Python modules - on the machine chosen in the wizard, with an interpreter of that machine.
 *
 * @author VISTALL
 * @since 2026-11-14
 */
@ExtensionImpl
public class PythonNewModuleBuilder implements NewModuleBuilder {
    private static final Logger LOG = Logger.getInstance(PythonNewModuleBuilder.class);

    @Override
    public void setupContext(NewModuleContext context) {
        NewModuleContextGroup group = context.addGroup("python", LocalizeValue.localizeTODO("Python"), PythonPsiIconGroup.python());

        group.add(LocalizeValue.localizeTODO("Console Application"), AllIcons.RunConfigurations.Application, new NewModuleBuilderProcessor<PythonNewModuleContext>() {
            @Override
            public PythonNewModuleContext createContext(boolean isNewProject) {
                return new PythonNewModuleContext(isNewProject);
            }

            @Override
            public void buildSteps(Consumer<WizardStep<PythonNewModuleContext>> consumer, PythonNewModuleContext context) {
                consumer.accept(new PythonNewModuleSetupStep(context));
            }

            @RequiredReadAction
            @Override
            public void process(PythonNewModuleContext context, ContentEntry contentEntry, ModifiableRootModel modifiableRootModel) {
                BasePyMutableModuleExtension extension = modifiableRootModel.getExtensionWithoutCheck(BasePyMutableModuleExtension.class);
                assert extension != null;
                extension.setEnabled(true);

                Sdk sdk = context.getSdk();
                if (sdk != null) {
                    extension.getInheritableSdk().set(null, sdk);
                    modifiableRootModel.addModuleExtensionSdkEntry(extension);
                }

                VirtualFile directory = contentEntry.getFile();
                if (directory != null) {
                    // written on the machine of the module
                    Path main = directory.toNioPath().resolve("main.py");
                    try {
                        if (!Files.exists(main)) {
                            Files.writeString(main, "print(\"Hello, World!\")\n");
                        }
                    }
                    catch (IOException e) {
                        LOG.warn("Cannot create " + main, e);
                    }
                    directory.refresh(true, false);
                }
            }
        });
    }
}
