/*
 * Copyright 2006 Dmitry Jemerov (yole)
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

package ru.yole.pythonid;

import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import ru.yole.pythonid.psi.PyElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class PythonReferenceProviderRegistryImpl
		implements PythonReferenceProviderRegistry {
	private final Set<PsiReferenceProvider> providers = new CopyOnWriteArraySet();

	@Override
	public PsiReference[] getPythonReferences(PyElement element) {
		List list = new ArrayList();
		for (PsiReferenceProvider provider : this.providers) {
			Collections.addAll(list, provider.getReferencesByElement(element));
		}
		return (PsiReference[]) list.toArray(new PsiReference[list.size()]);
	}

	@Override
	public void registerReferenceProvider(PsiReferenceProvider provider) {
		this.providers.add(provider);
	}

	@Override
	public void unregisterReferenceProvider(PsiReferenceProvider provider) {
		this.providers.remove(provider);
	}
}