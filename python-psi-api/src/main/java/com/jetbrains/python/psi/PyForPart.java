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
package com.jetbrains.python.psi;

import jakarta.annotation.Nullable;

/**
 * Main part of a 'for' statement
 * User: dcheryasov
 * Date: Mar 15, 2009 8:55:22 PM
 */
public interface PyForPart extends PyStatementPart {
	/**
	 * @return target: the "x" in "<code>for x in (1, 2, 3)</code>".
	 */
	@Nullable
	PyExpression getTarget();

	/**
	 * @return source of iteration: the "(1, 2, 3)" in "<code>for x in (1, 2, 3)</code>".
	 */
	@Nullable
	PyExpression getSource();

}
