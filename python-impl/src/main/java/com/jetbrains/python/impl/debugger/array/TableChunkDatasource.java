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
package com.jetbrains.python.impl.debugger.array;

import jakarta.annotation.Nonnull;
import com.jetbrains.python.debugger.ArrayChunk;
import com.jetbrains.python.debugger.PyDebuggerException;

/**
 * Created by Alla on 4/23/2016.
 */
public interface TableChunkDatasource
{

	String correctStringValue(@Nonnull Object value);

	void showError(String message);


	ArrayChunk getChunk(int rowOffset, int colOffset, int rows, int cols) throws PyDebuggerException;

}
