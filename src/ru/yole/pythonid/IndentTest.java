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

import com.intellij.ide.DataManager;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.testFramework.LightCodeInsightTestCase;

public class IndentTest extends LightCodeInsightTestCase {
	private PythonSupportLoader _pythonSupportLoader;

	protected void setUp()
			throws Exception {
		super.setUp();
		this._pythonSupportLoader = new PythonSupportLoader();
		this._pythonSupportLoader.initComponent();
	}

	protected void tearDown() throws Exception {
		this._pythonSupportLoader.disposeComponent();
		this._pythonSupportLoader = null;
		super.tearDown();
	}

	private void doTest(String before, String after) throws Exception {
		String name = getTestName(false);

		configureFromFileText(name + ".py", before);

		EditorActionManager actionManager = EditorActionManager.getInstance();
		EditorActionHandler actionHandler = actionManager.getActionHandler("EditorEnter");

		actionHandler.execute(getEditor(), DataManager.getInstance().getDataContext());

		String s = this.myFile.getText();
		checkResultByText(null, after, false);
	}

	public void testSimpleIndent() throws Exception {
		doTest("a=1<caret>", "a=1\n<caret>");
	}

	public void testIndentColon() throws Exception {
		doTest("if a:<caret>", "if a:\n    <caret>");
	}

	public void testIndentStatementList() throws Exception {
		doTest("if a:<caret>\n    print a", "if a:\n    <caret>\n    print a");
	}

	public void testIndentStatementList2() throws Exception {
		doTest("while a:\n    print a<caret>", "while a:\n    print a\n    <caret>");
	}

	public void testIndentStatementList3() throws Exception {
		doTest("if a:\n    print a<caret>\n\nprint b", "if a:\n    print a\n    <caret>\n\nprint b");
	}

	public void testIndentOneLineStatementList() throws Exception {
		doTest("if a:\n    if b: print c<caret>\n    print d", "if a:\n    if b: print c\n    <caret>\n    print d");
	}

	public void testIndentOneLineStatementListBreak() throws Exception {
		doTest("if a:\n    if b:<caret> print c\n    print d", "if a:\n    if b:\n        <caret>print c\n    print d");
	}

	public void testAlignInList() throws Exception {
		doTest("__all__ = [a,<caret>", "__all__ = [a,\n           <caret>");
	}

	public void testAlignInListMiddle()
			throws Exception {
		doTest("__all__ = [a,<caret>\n           c]", "__all__ = [a,\n           <caret>\n           c]");
	}

	public void testAlignInListMiddle2()
			throws Exception {
		doTest("__all__ = [a,\n           b,<caret>\n           c]", "__all__ = [a,\n           b,\n           <caret>\n           c]");
	}

	public void testAlignInListComp()
			throws Exception {
		doTest("__all__ = [a for<caret>", "__all__ = [a for\n           <caret>");
	}

	public void testClass()
			throws Exception {
		doTest("class A:\n    print a<caret>", "class A:\n    print a\n    <caret>");
	}

	public void testClass2()
			throws Exception {
		doTest("class CombatExpertiseFeat(Ability):\n    if a: print b\n    def getAvailableActions(self):<caret>", "class CombatExpertiseFeat(Ability):\n    if a: print b\n    def getAvailableActions(self):\n        <caret>");
	}

	public void testClass2_1()
			throws Exception {
		doTest("class CombatExpertiseFeat(Ability):\n    if a: print b\n    def getAvailableActions(self):<caret>\nclass C2: pass", "class CombatExpertiseFeat(Ability):\n    if a: print b\n    def getAvailableActions(self):\n        <caret>\nclass C2: pass");
	}

	public void testMultiDedent()
			throws Exception {
		doTest("class CombatExpertiseFeat(Ability):\n    def getAvailableActions(self):\n        result = ArrayList()<caret>", "class CombatExpertiseFeat(Ability):\n    def getAvailableActions(self):\n        result = ArrayList()\n        <caret>");
	}

	public void testMultiDedent1()
			throws Exception {
		doTest("class CombatExpertiseFeat(Ability):\n    def getAvailableActions(self):\n        if a:<caret>", "class CombatExpertiseFeat(Ability):\n    def getAvailableActions(self):\n        if a:\n            <caret>");
	}

	public void testMultiDedent2()
			throws Exception {
		doTest("class CombatExpertiseFeat(Ability):\n    def getAvailableActions(self): result = ArrayList()<caret>", "class CombatExpertiseFeat(Ability):\n    def getAvailableActions(self): result = ArrayList()\n    <caret>");
	}

	public void testIfElse()
			throws Exception {
		doTest("if a:<caret>\n    b\nelse:\n    c", "if a:\n    <caret>\n    b\nelse:\n    c");
	}

	public void testIfElse2()
			throws Exception {
		doTest("if a:\n    b\nelse:<caret>\n    c", "if a:\n    b\nelse:\n    <caret>\n    c");
	}
}