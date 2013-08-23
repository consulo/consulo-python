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

package org.consulo.python.psi;

import java.util.List;

public abstract interface PyListCompExpression extends PyExpression {
	public abstract PyExpression getResultExpression();

	public abstract List<ListCompComponent> getComponents();

	public static abstract interface ForComponent extends PyListCompExpression.ListCompComponent {
		public abstract PyExpression getIteratorVariable();

		public abstract PyExpression getIteratedList();
	}

	public static abstract interface IfComponent extends PyListCompExpression.ListCompComponent {
		public abstract PyExpression getTest();
	}

	public static abstract interface ListCompComponent {
	}
}