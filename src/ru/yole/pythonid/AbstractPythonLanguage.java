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

import com.intellij.lang.Commenter;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import org.jetbrains.annotations.NotNull;
import ru.yole.pythonid.psi.PyElementGenerator;
import ru.yole.pythonid.psi.impl.PyElementGeneratorImpl;
import ru.yole.pythonid.psi.impl.PyFileImpl;
import ru.yole.pythonid.validation.*;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public abstract class AbstractPythonLanguage extends PythonLanguage {
	private final PyElementGenerator elementGenerator = new PyElementGeneratorImpl(this);
	private PythonReferenceProviderRegistry refProviders = new PythonReferenceProviderRegistryImpl();
	private PyTokenTypes tokenTypes = new PyTokenTypesImpl(this);
	private PyElementTypes elementTypes = new PyElementTypesImpl(this);
	protected Set<Class<? extends PyAnnotator>> _annotators = new CopyOnWriteArraySet();
	private ParserDefinition myParserDefinition;

	public AbstractPythonLanguage(String id) {
		super(id);

		this._annotators.add(AssignTargetAnnotator.class);
		this._annotators.add(ParameterListAnnotator.class);
		this._annotators.add(ArgumentListAnnotator.class);
		this._annotators.add(ReturnAnnotator.class);
		this._annotators.add(TryExceptAnnotator.class);
		this._annotators.add(BreakContinueAnnotator.class);
		this._annotators.add(GlobalAnnotator.class);
		this._annotators.add(DocStringAnnotator.class);
		this._annotators.add(ImportAnnotator.class);
		this._annotators.add(UnresolvedReferenceAnnotator.class);
	}

	public ParserDefinition getParserDefinition() {
		if (this.myParserDefinition == null) {
			this.myParserDefinition = new PythonParserDefinition(this, getFileType());
		}
		return this.myParserDefinition;
	}

	@NotNull
	public SyntaxHighlighter getSyntaxHighlighter(Project project) {
		void tmp8_5 = new PyHighligher(this);
		if (tmp8_5 == null) throw new IllegalStateException("@NotNull method must not return null");
		return tmp8_5;
	}

	public PairedBraceMatcher getPairedBraceMatcher() {
		return new PyBraceMatcher(this);
	}

	@Override
	@NotNull
	public PyAnnotatingVisitor getAnnotator() {
		void tmp11_8 = new PyAnnotatingVisitor(this._annotators);
		if (tmp11_8 == null) throw new IllegalStateException("@NotNull method must not return null");
		return tmp11_8;
	}

	@Override
	public PythonReferenceProviderRegistry getReferenceProviderRegistry() {
		return this.refProviders;
	}

	@NotNull
	public FindUsagesProvider getFindUsagesProvider() {
		void tmp8_5 = new PythonFindUsagesProvider(this);
		if (tmp8_5 == null) throw new IllegalStateException("@NotNull method must not return null");
		return tmp8_5;
	}

	public Commenter getCommenter() {
		return new PythonCommenter();
	}

	public abstract IFileElementType getFileElementType();

	@Override
	public PyElementGenerator getElementGenerator() {
		return this.elementGenerator;
	}

	@Override
	public PyTokenTypes getTokenTypes() {
		return this.tokenTypes;
	}

	@Override
	public PyElementTypes getElementTypes() {
		return this.elementTypes;
	}

	@Override
	public FileCreator getFileCreator() {
		return new FileCreator() {
			@Override
			public PsiFile createFile(Project project, VirtualFile file) {
				return new PyFileImpl(project, file, AbstractPythonLanguage.this, AbstractPythonLanguage.this.getFileType());
			}

			@Override
			public PsiFile createFile(Project project, String name, CharSequence text) {
				return new PyFileImpl(project, name, text, AbstractPythonLanguage.this, AbstractPythonLanguage.this.getFileType());
			}

			@Override
			public PsiFile createDummyFile(Project project, String contents) {
				ParserDefinition def = AbstractPythonLanguage.this.getParserDefinition();
				assert (def != null);
				return def.createFile(project, "dummy." + AbstractPythonLanguage.this.getFileType().getDefaultExtension(), contents);
			}
		};
	}

	public void registerAnnotator(Class<? extends PyAnnotator> annotator) {
		this._annotators.add(annotator);
	}
}