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
package com.jetbrains.python.sdk;

import com.jetbrains.python.remote.PyCredentialsContribution;
import consulo.content.bundle.Sdk;
import consulo.ide.impl.idea.remote.RemoteSdkAdditionalData;
import consulo.ide.impl.idea.remote.VagrantBasedCredentialsHolder;
import consulo.ide.impl.idea.remote.ext.LanguageCaseCollector;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.ref.Ref;

import javax.annotation.Nullable;

public abstract class CredentialsTypeExChecker {
  private boolean mySshContribution;
  private boolean myVagrantContribution;
  private boolean myWebDeploymentContribution;

  public CredentialsTypeExChecker withSshContribution(boolean sshContribution) {
    mySshContribution = sshContribution;
    return this;
  }

  public CredentialsTypeExChecker withVagrantContribution(boolean vagrantContribution) {
    myVagrantContribution = vagrantContribution;
    return this;
  }

  public CredentialsTypeExChecker withWebDeploymentContribution(boolean webDeploymentContribution) {
    myWebDeploymentContribution = webDeploymentContribution;
    return this;
  }

  public boolean check(@Nullable final Sdk sdk) {
    if (sdk == null) {
      return false;
    }
    RemoteSdkAdditionalData data = ObjectUtil.tryCast(sdk.getSdkAdditionalData(), RemoteSdkAdditionalData.class);
    if (data == null) {
      return false;
    }
    return check(data);
  }

  public boolean check(consulo.ide.impl.idea.remote.RemoteSdkAdditionalData data) {
    final Ref<Boolean> result = Ref.create(mySshContribution);
    data.switchOnConnectionType(new LanguageCaseCollector<PyCredentialsContribution>() {

      @Override
      protected void processLanguageContribution(PyCredentialsContribution languageContribution, Object credentials) {
        result.set(checkLanguageContribution(languageContribution));
      }
    }.collectCases(PyCredentialsContribution.class, new consulo.ide.impl.idea.remote.ext.CredentialsCase.Ssh() {
                     @Override
                     public void process(consulo.ide.impl.idea.remote.RemoteCredentialsHolder credentials) {
                       result.set(mySshContribution);
                     }
                   }, new consulo.ide.impl.idea.remote.ext.CredentialsCase.Vagrant() {
                     @Override
                     public void process(VagrantBasedCredentialsHolder credentials) {
                       result.set(myVagrantContribution);
                     }
                   }, new consulo.ide.impl.idea.remote.ext.CredentialsCase.WebDeployment() {
                     @Override
                     public void process(consulo.ide.impl.idea.remote.WebDeploymentCredentialsHolder credentials) {
                       result.set(myWebDeploymentContribution);
                     }
                   }
    ));
    return result.get();
  }

  protected abstract boolean checkLanguageContribution(PyCredentialsContribution languageContribution);
}
