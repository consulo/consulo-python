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
package org.consulo.python.run;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PythonTracebackFilter implements Filter {
	private Project myProject;
	private Pattern _pattern = Pattern.compile("File \"([^\"]+)\"\\, line (\\d+)\\, in");

	public PythonTracebackFilter(Project myProject) {
		this.myProject = myProject;
	}

	@Override
	public Filter.Result applyFilter(String line, int entireLength) {
		Matcher matcher = this._pattern.matcher(line);
		if (matcher.find()) {
			String fileName = matcher.group(1).replace('\\', '/');
			int lineNumber = Integer.parseInt(matcher.group(2));
			VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(fileName);
			if (vFile != null) {
				OpenFileHyperlinkInfo hyperlink = new OpenFileHyperlinkInfo(this.myProject, vFile, lineNumber - 1);
				int textStartOffset = entireLength - line.length();
				int startPos = line.indexOf('"') + 1;
				int endPos = line.indexOf('"', startPos);
				return new Filter.Result(startPos + textStartOffset, endPos + textStartOffset, hyperlink);
			}
		}
		return null;
	}
}