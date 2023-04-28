/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.inspections;

import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.inspections.quickfix.AddSelfQuickFix;
import com.jetbrains.python.inspections.quickfix.RenameParameterQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.ASTNode;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.QualifiedName;
import consulo.util.lang.ref.Ref;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Looks for the 'self' or its equivalents.
 *
 * @author dcheryasov
 */
@ExtensionImpl
public class PyMethodParametersInspection extends PyInspection
{
	@Nonnull
	@Override
	public InspectionToolState<?> createStateProvider()
	{
		return new PyMethodParametersInspectionState();
	}

	@Nls
	@Nonnull
	public String getDisplayName()
	{
		return PyBundle.message("INSP.NAME.problematic.first.parameter");
	}

	@Nonnull
	public HighlightDisplayLevel getDefaultLevel()
	{
		return HighlightDisplayLevel.WEAK_WARNING;
	}

	@Nonnull
	@Override
	public PsiElementVisitor buildVisitor(@Nonnull ProblemsHolder holder,
										  boolean isOnTheFly,
										  @Nonnull LocalInspectionToolSession session,
										  Object state)
	{
		return new Visitor(holder, session, (PyMethodParametersInspectionState) state);
	}

	public class Visitor extends PyInspectionVisitor
	{
		private Ref<PsiElement> myPossibleZopeRef = null;
		private final PyMethodParametersInspectionState myState;

		public Visitor(@Nullable ProblemsHolder holder, @Nonnull LocalInspectionToolSession session, PyMethodParametersInspectionState state)
		{
			super(holder, session);
			myState = state;
		}

		@Nullable
		private PsiElement findZopeInterface(PsiElement foothold)
		{
			PsiElement ret;
			synchronized(this)
			{ // other threads would wait as long in resolveInRoots() anyway
				if(myPossibleZopeRef == null)
				{
					myPossibleZopeRef = new Ref<>();
					ret = ResolveImportUtil.resolveModuleInRoots(QualifiedName.fromComponents("zope.interface.Interface"), foothold);
					myPossibleZopeRef.set(ret); // null is OK
				}
				else
				{
					ret = myPossibleZopeRef.get();
				}
			}
			return ret;
		}

		@Override
		public void visitPyFunction(final PyFunction node)
		{
			for(PyInspectionExtension extension : PyInspectionExtension.EP_NAME.getExtensionList())
			{
				if(extension.ignoreMethodParameters(node))
				{
					return;
				}
			}
			// maybe it's a zope interface?
			PsiElement zope_interface = findZopeInterface(node);
			final PyClass cls = node.getContainingClass();
			if(zope_interface instanceof PyClass)
			{
				if(cls != null && cls.isSubclass((PyClass) zope_interface, myTypeEvalContext))
				{
					return; // it can have any params
				}
			}
			// analyze function itself
			PyUtil.MethodFlags flags = PyUtil.MethodFlags.of(node);
			if(flags != null)
			{
				PyParameterList plist = node.getParameterList();
				PyParameter[] params = plist.getParameters();
				final String methodName = node.getName();
				final String CLS = "cls"; // TODO: move to style settings
				if(params.length == 0)
				{ // fix: add
					// check for "staticmetod"
					if(flags.isStaticMethod())
					{
						return; // no params may be fine
					}
					// check actual param list
					ASTNode name_node = node.getNameNode();
					if(name_node != null)
					{
						PsiElement open_paren = plist.getFirstChild();
						PsiElement close_paren = plist.getLastChild();
						if(open_paren != null && close_paren != null &&
								"(".equals(open_paren.getText()) && ")".equals(close_paren.getText()))
						{
							String paramName;
							if(flags.isMetaclassMethod())
							{
								if(flags.isClassMethod())
								{
									paramName = myState.MCS;
								}
								else
								{
									paramName = CLS;
								}
							}
							else if(flags.isClassMethod())
							{
								paramName = CLS;
							}
							else
							{
								paramName = PyNames.CANONICAL_SELF;
							}
							registerProblem(plist,
									PyBundle.message("INSP.must.have.first.parameter", paramName),
									ProblemHighlightType.GENERIC_ERROR,
									null,
									new AddSelfQuickFix(paramName));
						}
					}
				}
				else
				{ // fix: rename
					PyNamedParameter first_param = params[0].getAsNamed();
					if(first_param != null)
					{
						String pname = first_param.getName();
						if(pname == null)
						{
							return;
						}
						// every dup, swap, drop, or dup+drop of "self"
						@NonNls String[] mangled = {
								"eslf",
								"sself",
								"elf",
								"felf",
								"slef",
								"seelf",
								"slf",
								"sslf",
								"sefl",
								"sellf",
								"sef",
								"seef"
						};
						if(PyUtil.among(pname, mangled))
						{
							registerProblem(PyUtil.sure(params[0].getNode()).getPsi(),
									PyBundle.message("INSP.probably.mistyped.self"),
									new RenameParameterQuickFix(PyNames.CANONICAL_SELF));
							return;
						}
						if(flags.isMetaclassMethod())
						{
							if(flags.isStaticMethod() && !PyNames.NEW.equals(methodName))
							{
								return;
							}
							String expectedName;
							String alternativeName = null;
							if(PyNames.NEW.equals(methodName) || flags.isClassMethod())
							{
								expectedName = myState.MCS;
							}
							else if(flags.isSpecialMetaclassMethod())
							{
								expectedName = CLS;
							}
							else
							{
								expectedName = PyNames.CANONICAL_SELF;
								alternativeName = CLS;
							}
							if(!expectedName.equals(pname) && (alternativeName == null || !alternativeName.equals(pname)))
							{
								registerProblem(PyUtil.sure(params[0].getNode()).getPsi(),
										PyBundle.message("INSP.usually.named.$0", expectedName),
										new RenameParameterQuickFix(expectedName));
							}
						}
						else if(flags.isClassMethod() ||
								PyNames.NEW.equals(methodName) ||
								PyNames.INIT_SUBCLASS.equals(methodName) && LanguageLevel.forElement(node).isAtLeast(LanguageLevel.PYTHON36))
						{
							if(!CLS.equals(pname))
							{
								registerProblem(PyUtil.sure(params[0].getNode()).getPsi(),
										PyBundle.message("INSP.usually.named.$0", CLS),
										new RenameParameterQuickFix(CLS));
							}
						}
						else if(!flags.isStaticMethod() && !first_param.isPositionalContainer() && !PyNames.CANONICAL_SELF.equals(pname))
						{
							if(flags.isMetaclassMethod() && CLS.equals(pname))
							{
								return;   // accept either 'self' or 'cls' for all methods in metaclass
							}
							registerProblem(PyUtil.sure(params[0].getNode()).getPsi(),
									PyBundle.message("INSP.usually.named.self"),
									new RenameParameterQuickFix(PyNames.CANONICAL_SELF));
						}
					}
					else
					{ // the unusual case of a method with first tuple param
						if(!flags.isStaticMethod())
						{
							registerProblem(plist, PyBundle.message("INSP.first.param.must.not.be.tuple"));
						}
					}
				}
			}
		}
	}

}
