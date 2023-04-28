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

package com.jetbrains.python.debugger;

import com.google.common.collect.Maps;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import consulo.application.AllIcons;
import consulo.application.ApplicationManager;
import consulo.application.WriteAction;
import consulo.application.util.function.Computable;
import consulo.execution.debug.XDebuggerManager;
import consulo.execution.debug.breakpoint.XBreakpoint;
import consulo.execution.debug.breakpoint.XBreakpointType;
import consulo.execution.debug.breakpoint.ui.XBreakpointCustomPropertiesPanel;
import consulo.language.editor.ui.TreeChooser;
import consulo.language.editor.ui.TreeClassChooserFactory;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.ex.awt.IdeBorderFactory;
import consulo.ui.image.Image;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;

import javax.annotation.Nonnull;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.function.Predicate;


public class PyExceptionBreakpointType
		extends XBreakpointType<XBreakpoint<PyExceptionBreakpointProperties>, PyExceptionBreakpointProperties>
{

	private static final String BASE_EXCEPTION = "BaseException";

	public PyExceptionBreakpointType()
	{
		super("python-exception", "Python Exception Breakpoint");
	}

	@Nonnull
	@Override
	public Image getEnabledIcon()
	{
		return AllIcons.Debugger.Db_exception_breakpoint;
	}

	@Nonnull
	@Override
	public Image getDisabledIcon()
	{
		return AllIcons.Debugger.Db_disabled_exception_breakpoint;
	}

	@Override
	public PyExceptionBreakpointProperties createProperties()
	{
		return new PyExceptionBreakpointProperties(BASE_EXCEPTION);
	}

	@Override
	public boolean isAddBreakpointButtonVisible()
	{
		return true;
	}

	@Override
	public XBreakpoint<PyExceptionBreakpointProperties> addBreakpoint(final Project project, JComponent parentComponent)
	{
		TreeClassChooserFactory.Builder<PyClass> builder = TreeClassChooserFactory.getInstance(project).newChooser(PyClass.class);
		builder.withTitle(LocalizeValue.localizeTODO("Select Exception Class"));
		builder.withSearchScope(GlobalSearchScope.allScope(project));
		builder.withClassProvider((proj, name, searchInLibraries, pattern, searchScope) ->
		{
			PyExceptionCachingFilter filter = new PyExceptionCachingFilter();
			final Collection<PyClass> classes = PyClassNameIndex.find(name, proj, searchScope.isSearchInLibraries());
			final List<PyClass> result = new ArrayList<>();
			for(PyClass c : classes)
			{
				if(filter.test(c))
				{
					result.add(c);
				}
			}

			return result;
		});

		TreeChooser<PyClass> treeChooser = builder.build();


		treeChooser.showDialog();

		// on ok
		final PyClass pyClass = treeChooser.getSelected();
		if(pyClass != null)
		{
			final String qualifiedName = pyClass.getQualifiedName();
			assert qualifiedName != null : "Qualified name of the class shouldn't be null";
			return WriteAction.compute(() -> XDebuggerManager.getInstance(project).getBreakpointManager().addBreakpoint(PyExceptionBreakpointType.this, new PyExceptionBreakpointProperties
					(qualifiedName)));
		}
		return null;
	}

	private static class PyExceptionCachingFilter implements Predicate<PyClass>
	{
		private final HashMap<Integer, Pair<WeakReference<PyClass>, Boolean>> processedElements = Maps.newHashMap();

		@Override
		public boolean test(@Nonnull final PyClass pyClass)
		{
			final VirtualFile virtualFile = pyClass.getContainingFile().getVirtualFile();
			if(virtualFile == null)
			{
				return false;
			}

			final int key = pyClass.hashCode();
			final Pair<WeakReference<PyClass>, Boolean> pair = processedElements.get(key);
			boolean isException;
			if(pair == null || pair.first.get() != pyClass)
			{
				isException = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>()
				{
					@Override
					public Boolean compute()
					{
						return PyUtil.isExceptionClass(pyClass);
					}
				});
				processedElements.put(key, Pair.create(new WeakReference<PyClass>(pyClass), isException));
			}
			else
			{
				isException = pair.second;
			}
			return isException;
		}
	}

	@Override
	public String getBreakpointsDialogHelpTopic()
	{
		return "reference.dialogs.breakpoints";
	}

	@Override
	public String getDisplayText(XBreakpoint<PyExceptionBreakpointProperties> breakpoint)
	{
		PyExceptionBreakpointProperties properties = breakpoint.getProperties();
		if(properties != null)
		{
			String exception = properties.getException();
			if(BASE_EXCEPTION.equals(exception))
			{
				return "All exceptions";
			}
			return exception;
		}
		return "";
	}

	@Override
	public XBreakpoint<PyExceptionBreakpointProperties> createDefaultBreakpoint(@Nonnull XBreakpointCreator<PyExceptionBreakpointProperties> creator)
	{
		final XBreakpoint<PyExceptionBreakpointProperties> breakpoint = creator.createBreakpoint(createDefaultBreakpointProperties());
		breakpoint.setEnabled(true);
		return breakpoint;
	}

	private static PyExceptionBreakpointProperties createDefaultBreakpointProperties()
	{
		PyExceptionBreakpointProperties p = new PyExceptionBreakpointProperties(BASE_EXCEPTION);
		p.setNotifyOnTerminate(true);
		p.setNotifyAlways(false);
		p.setNotifyAlways(false);
		return p;
	}

	@Override
	public XBreakpointCustomPropertiesPanel<XBreakpoint<PyExceptionBreakpointProperties>> createCustomPropertiesPanel()
	{
		return new PyExceptionBreakpointPropertiesPanel();
	}


	private static class PyExceptionBreakpointPropertiesPanel
			extends XBreakpointCustomPropertiesPanel<XBreakpoint<PyExceptionBreakpointProperties>>
	{
		private JCheckBox myNotifyOnTerminateCheckBox;
		private JCheckBox myNotifyOnRaiseCheckBox;
		private JRadioButton myAlwaysRadio;
		private JRadioButton myOnlyOnFirstRadio;

		@Nonnull
		@Override
		public JComponent getComponent()
		{
			myNotifyOnTerminateCheckBox = new JCheckBox("On termination");
			myNotifyOnRaiseCheckBox = new JCheckBox("On raise");
			myAlwaysRadio = new JRadioButton("At each level of call chain");
			myOnlyOnFirstRadio = new JRadioButton("At top of call chain");

			ButtonGroup group = new ButtonGroup();
			group.add(myAlwaysRadio);
			group.add(myOnlyOnFirstRadio);

			Box notificationsBox = Box.createVerticalBox();
			JPanel panel = new JPanel(new BorderLayout());
			panel.add(myNotifyOnTerminateCheckBox, BorderLayout.NORTH);
			notificationsBox.add(panel);
			panel = new JPanel(new BorderLayout());
			panel.add(myNotifyOnRaiseCheckBox, BorderLayout.NORTH);
			notificationsBox.add(panel);
			panel = new JPanel(new BorderLayout());
			EmptyBorder border = new EmptyBorder(0, 20, 0, 0);
			panel.setBorder(border);
			panel.add(myAlwaysRadio, BorderLayout.NORTH);
			panel.add(myOnlyOnFirstRadio, BorderLayout.CENTER);


			notificationsBox.add(panel);


			panel = new JPanel(new BorderLayout());
			JPanel innerPanel = new JPanel(new BorderLayout());
			innerPanel.add(notificationsBox, BorderLayout.CENTER);
			innerPanel.add(Box.createHorizontalStrut(3), BorderLayout.WEST);
			innerPanel.add(Box.createHorizontalStrut(3), BorderLayout.EAST);
			panel.add(innerPanel, BorderLayout.NORTH);
			panel.setBorder(IdeBorderFactory.createTitledBorder("Activation policy", true));

			ActionListener listener = new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					setRadioButtonsEnabled();
				}
			};

			myNotifyOnRaiseCheckBox.addActionListener(listener);

			setRadioButtonsEnabled();

			return panel;
		}

		private void setRadioButtonsEnabled()
		{
			setRadioButtonsEnabled(myNotifyOnRaiseCheckBox.isSelected());
		}

		private void setNotifyOnRaiseSelected(boolean selected)
		{
			myNotifyOnRaiseCheckBox.setSelected(selected);
			setRadioButtonsEnabled(selected);
		}

		private void setRadioButtonsEnabled(boolean selected)
		{
			myAlwaysRadio.setEnabled(selected);
			myOnlyOnFirstRadio.setEnabled(selected);
			if(selected && !(myAlwaysRadio.isSelected() || myOnlyOnFirstRadio.isSelected()))
			{
				myAlwaysRadio.setSelected(true);
			}
		}

		@Override
		public void saveTo(@Nonnull XBreakpoint<PyExceptionBreakpointProperties> breakpoint)
		{
			breakpoint.getProperties().setNotifyOnTerminate(myNotifyOnTerminateCheckBox.isSelected());

			breakpoint.getProperties().setNotifyAlways(myNotifyOnRaiseCheckBox.isSelected() && myAlwaysRadio.isSelected());
			breakpoint.getProperties().setNotifyOnlyOnFirst(myNotifyOnRaiseCheckBox.isSelected() && myOnlyOnFirstRadio.isSelected());
		}

		@Override
		public void loadFrom(@Nonnull XBreakpoint<PyExceptionBreakpointProperties> breakpoint)
		{
			myNotifyOnTerminateCheckBox.setSelected(breakpoint.getProperties().isNotifyOnTerminate());

			boolean always = breakpoint.getProperties().isNotifyAlways();
			boolean onFirst = breakpoint.getProperties().isNotifyOnlyOnFirst();

			setNotifyOnRaiseSelected(always || onFirst);

			myAlwaysRadio.setSelected(always);
			myOnlyOnFirstRadio.setSelected(onFirst);
		}
	}
}


