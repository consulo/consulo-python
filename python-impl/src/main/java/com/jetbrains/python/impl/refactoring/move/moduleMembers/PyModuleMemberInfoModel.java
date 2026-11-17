/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.impl.refactoring.move.moduleMembers;

import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import consulo.language.editor.refactoring.classMember.AbstractUsesDependencyMemberInfoModel;

/**
 * @author Mikhail Golubev
 */
class PyModuleMemberInfoModel extends AbstractUsesDependencyMemberInfoModel<PyElement, PyFile, PyModuleMemberInfo> {
    final PyFile myPyFile;

    PyModuleMemberInfoModel(PyFile file) {
        super(file, null, false);
        myPyFile = file;
    }

    @Override
    public boolean isAbstractEnabled(PyModuleMemberInfo member) {
        return false;
    }

    @Override
    protected int doCheck(PyModuleMemberInfo memberInfo, int problem) {
        return problem == ERROR ? WARNING : problem;
    }
}
