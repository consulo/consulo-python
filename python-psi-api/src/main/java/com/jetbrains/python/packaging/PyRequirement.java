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
package com.jetbrains.python.packaging;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.packaging.requirement.PyRequirementRelation;
import com.jetbrains.python.packaging.requirement.PyRequirementVersionNormalizer;
import com.jetbrains.python.packaging.requirement.PyRequirementVersionSpec;

/**
 * @author vlan
 */
public class PyRequirement
{

	// common regular expressions

	@Nonnull
	private static final String LINE_WS_REGEXP = "[ \t]";

	@Nonnull
	private static final String COMMENT_GROUP = "comment";

	@Nonnull
	private static final String COMMENT_REGEXP = "(?<" + COMMENT_GROUP + ">" + LINE_WS_REGEXP + "+#.*)?";

	@Nonnull
	private static final String NAME_GROUP = "name";

	// archive-related regular expressions

	@Nonnull
	private static final Pattern GITHUB_ARCHIVE_URL = Pattern.compile("https?://github\\.com/[^/\\s]+/(?<" + NAME_GROUP + ">[^/\\s]+)/archive/\\S+" + COMMENT_REGEXP);

	@Nonnull
	private static final Pattern ARCHIVE_URL = Pattern.compile("https?://\\S+/" +
			"(?<" + NAME_GROUP + ">\\S+)" +
			"(\\.tar\\.gz|\\.zip)(#(sha1|sha224|sha256|sha384|sha512|md5)=\\w+)?" + COMMENT_REGEXP);

	// vcs-related regular expressions
	// don't forget to update calculateVcsInstallOptions(Matcher) after this section changing

	@Nonnull
	private static final String VCS_EDITABLE_GROUP = "editable";

	@Nonnull
	private static final String VCS_EDITABLE_REGEXP = "((?<" + VCS_EDITABLE_GROUP + ">-e|--editable)" + LINE_WS_REGEXP + "+)?";

	@Nonnull
	private static final String VCS_SRC_BEFORE_GROUP = "srcb";

	@Nonnull
	private static final String VCS_SRC_AFTER_GROUP = "srca";

	@Nonnull
	private static final String VCS_SRC_BEFORE_REGEXP = "(?<" + VCS_SRC_BEFORE_GROUP + ">--src" + LINE_WS_REGEXP + "+\\S+" + LINE_WS_REGEXP + "+)?";

	@Nonnull
	private static final String VCS_SRC_AFTER_REGEXP = "(?<" + VCS_SRC_AFTER_GROUP + ">" + LINE_WS_REGEXP + "+--src" + LINE_WS_REGEXP + "+\\S+)?";

	@Nonnull
	private static final String PATH_IN_VCS_GROUP = "path";

	@Nonnull
	private static final String PATH_IN_VCS_REGEXP = "(?<" + PATH_IN_VCS_GROUP + ">[^@#\\s]+)";

	@Nonnull
	private static final String VCS_REVISION_REGEXP = "(@[^#\\s]+)?";

	@Nonnull
	private static final String VCS_EGG_BEFORE_SUBDIR_GROUP = "eggb";

	@Nonnull
	private static final String VCS_EGG_AFTER_SUBDIR_GROUP = "egga";

	@Nonnull
	private static final String VCS_PARAMS_REGEXP = "(" +
			"(#egg=(?<" + VCS_EGG_BEFORE_SUBDIR_GROUP + ">[^&\\s]+)(&subdirectory=\\S+)?)" +
			"|" +
			"(#subdirectory=[^&\\s]+&egg=(?<" + VCS_EGG_AFTER_SUBDIR_GROUP + ">\\S+))" +
			")?";

	@Nonnull
	private static final String VCS_GROUP = "vcs";

	@Nonnull
	private static final String VCS_URL_PREFIX = VCS_SRC_BEFORE_REGEXP + VCS_EDITABLE_REGEXP + "(?<" + VCS_GROUP + ">";

	@Nonnull
	private static final String VCS_URL_SUFFIX = PATH_IN_VCS_REGEXP + VCS_REVISION_REGEXP + VCS_PARAMS_REGEXP + ")" + VCS_SRC_AFTER_REGEXP + COMMENT_REGEXP;

	@Nonnull
	private static final String GIT_USER_AT_REGEXP = "[\\w-]+@";

	// supports: git+user@...
	@Nonnull
	private static final Pattern GIT_PROJECT_URL = Pattern.compile(VCS_URL_PREFIX + "git\\+" + GIT_USER_AT_REGEXP + "[^:\\s]+:" + VCS_URL_SUFFIX);

	// supports: bzr+lp:...
	@Nonnull
	private static final Pattern BZR_PROJECT_URL = Pattern.compile(VCS_URL_PREFIX + "bzr\\+lp:" + VCS_URL_SUFFIX);

	// supports: (bzr|git|hg|svn)(+smth)?://...
	@Nonnull
	private static final Pattern VCS_PROJECT_URL = Pattern.compile(VCS_URL_PREFIX + "(bzr|git|hg|svn)(\\+[A-Za-z]+)?://?[^/]+/" + VCS_URL_SUFFIX);

	// requirement-related regular expressions
	// don't forget to update calculateRequirementInstallOptions(Matcher) after this section changing

	// PEP-508 + PEP-440
	// https://www.python.org/dev/peps/pep-0508/
	// https://www.python.org/dev/peps/pep-0440/
	@Nonnull
	private static final String IDENTIFIER_REGEXP = "[A-Za-z0-9]([-_\\.]?[A-Za-z0-9])*";

	@Nonnull
	private static final String REQUIREMENT_NAME_REGEXP = "(?<" + NAME_GROUP + ">" + IDENTIFIER_REGEXP + ")";

	@Nonnull
	private static final String REQUIREMENT_EXTRAS_GROUP = "extras";

	@Nonnull
	private static final String REQUIREMENT_EXTRAS_REGEXP = "(?<" + REQUIREMENT_EXTRAS_GROUP + ">" +
			"\\[" +
			IDENTIFIER_REGEXP + "(" + LINE_WS_REGEXP + "*," + LINE_WS_REGEXP + "*" + IDENTIFIER_REGEXP + ")*" +
			"\\]" +
			")?";

	@Nonnull
	private static final String REQUIREMENT_VERSIONS_SPECS_GROUP = "versionspecs";

	@Nonnull
	private static final String REQUIREMENT_VERSION_SPEC_REGEXP = "(<=?|!=|===?|>=?|~=)" + LINE_WS_REGEXP + "*[\\.\\*\\+!\\w-]+";

	@Nonnull
	private static final String REQUIREMENT_VERSIONS_SPECS_REGEXP = "(?<" + REQUIREMENT_VERSIONS_SPECS_GROUP + ">" + REQUIREMENT_VERSION_SPEC_REGEXP +
			"(" + LINE_WS_REGEXP + "*," + LINE_WS_REGEXP + "*" + REQUIREMENT_VERSION_SPEC_REGEXP + ")*)?";

	@Nonnull
	private static final String REQUIREMENT_OPTIONS_GROUP = "options";

	@Nonnull
	private static final String REQUIREMENT_OPTIONS_REGEXP = "(?<" + REQUIREMENT_OPTIONS_GROUP + ">(" + LINE_WS_REGEXP + "+(--global-option|--install-option)=\"[^\"]*\")+)?";

	@Nonnull
	private static final String REQUIREMENT_GROUP = "requirement";

	@Nonnull
	private static final Pattern REQUIREMENT = Pattern.compile("(?<" + REQUIREMENT_GROUP + ">" +
			REQUIREMENT_NAME_REGEXP +
			LINE_WS_REGEXP + "*" +
			REQUIREMENT_EXTRAS_REGEXP +
			LINE_WS_REGEXP + "*" +
			REQUIREMENT_VERSIONS_SPECS_REGEXP +
			")" +
			REQUIREMENT_OPTIONS_REGEXP +
			COMMENT_REGEXP);

	@Nonnull
	private final String myName;

	@Nonnull
	private final List<PyRequirementVersionSpec> myVersionSpecs;

	@Nonnull
	private final List<String> myInstallOptions;

	@Nonnull
	private final String myExtras;

	public PyRequirement(@Nonnull String name)
	{
		this(name, Collections.emptyList());
	}

	public PyRequirement(@Nonnull String name, @Nonnull String version)
	{
		this(name, Collections.singletonList(calculateVersionSpec(version, PyRequirementRelation.EQ)));
	}

	public PyRequirement(@Nonnull String name, @Nonnull String version, @Nonnull List<String> installOptions)
	{
		this(name, Collections.singletonList(calculateVersionSpec(version, PyRequirementRelation.EQ)), installOptions);
	}

	public PyRequirement(@Nonnull String name, @Nonnull String version, @Nonnull List<String> installOptions, @Nonnull String extras)
	{
		this(name, Collections.singletonList(calculateVersionSpec(version, PyRequirementRelation.EQ)), installOptions, extras);
	}

	public PyRequirement(@Nonnull String name, @Nonnull List<PyRequirementVersionSpec> versionSpecs)
	{
		myName = name;
		myVersionSpecs = versionSpecs;
		myExtras = "";
		myInstallOptions = Collections.singletonList(toString());
	}

	public PyRequirement(@Nonnull String name, @Nonnull List<PyRequirementVersionSpec> versionSpecs, @Nonnull List<String> installOptions)
	{
		myName = name;
		myVersionSpecs = versionSpecs;
		myInstallOptions = Collections.unmodifiableList(installOptions);
		myExtras = "";
	}

	public PyRequirement(@Nonnull String name, @Nonnull List<PyRequirementVersionSpec> versionSpecs, @Nonnull List<String> installOptions, @Nonnull String extras)
	{
		myName = name;
		myVersionSpecs = versionSpecs;
		myInstallOptions = Collections.unmodifiableList(installOptions);
		myExtras = extras;
	}

	@Nonnull
	public String getName()
	{
		return myName;
	}

	@Nonnull
	public String getFullName()
	{
		return myName + myExtras;
	}

	@Nonnull
	public List<String> getInstallOptions()
	{
		return myInstallOptions;
	}

	@Override
	public String toString()
	{
		return myName + myExtras + StringUtil.join(myVersionSpecs, ",");
	}

	@Override
	public boolean equals(Object o)
	{
		if(o == this)
		{
			return true;
		}
		if(o == null || getClass() != o.getClass())
		{
			return false;
		}

		PyRequirement that = (PyRequirement) o;

		if(!myName.equals(that.myName))
		{
			return false;
		}
		if(!myVersionSpecs.equals(that.myVersionSpecs))
		{
			return false;
		}
		if(!myInstallOptions.equals(that.myInstallOptions))
		{
			return false;
		}
		if(!myExtras.equals(that.myExtras))
		{
			return false;
		}

		return true;
	}

	@Override
	public int hashCode()
	{
		int result = myName.hashCode();
		result = 31 * result + myVersionSpecs.hashCode();
		result = 31 * result + myInstallOptions.hashCode();
		result = 31 * result + myExtras.hashCode();
		return result;
	}

	@Nullable
	public PyPackage match(@Nonnull List<PyPackage> packages)
	{
		final String normalizedName = normalizeName(myName);

		return packages.stream().filter(pkg -> normalizedName.equalsIgnoreCase(pkg.getName())).findAny().filter(pkg -> myVersionSpecs.stream().allMatch(spec -> spec.matches(pkg.getVersion())))
				.orElse(null);
	}

	@Nullable
	public static PyRequirement fromLine(@Nonnull String line)
	{
		final PyRequirement githubArchiveUrl = parseGithubArchiveUrl(line);
		if(githubArchiveUrl != null)
		{
			return githubArchiveUrl;
		}

		final PyRequirement archiveUrl = parseArchiveUrl(line);
		if(archiveUrl != null)
		{
			return archiveUrl;
		}

		final PyRequirement vcsProjectUrl = parseVcsProjectUrl(line);
		if(vcsProjectUrl != null)
		{
			return vcsProjectUrl;
		}

		return parseRequirement(line);
	}

	@Nonnull
	public static List<PyRequirement> fromText(@Nonnull String text)
	{
		return fromText(text, null, new HashSet<>());
	}

	@Nonnull
	public static List<PyRequirement> fromFile(@Nonnull VirtualFile file)
	{
		return fromText(loadText(file), file, new HashSet<>());
	}

	@Nonnull
	public static PyRequirementVersionSpec calculateVersionSpec(@Nonnull String version, @Nonnull PyRequirementRelation expectedRelation)
	{
		final String normalizedVersion = PyRequirementVersionNormalizer.normalize(version);

		return normalizedVersion == null ? new PyRequirementVersionSpec(PyRequirementRelation.STR_EQ, version) : new PyRequirementVersionSpec(expectedRelation, normalizedVersion);
	}

	@Nullable
	private static PyRequirement parseGithubArchiveUrl(@Nonnull String line)
	{
		final Matcher matcher = GITHUB_ARCHIVE_URL.matcher(line);

		if(matcher.matches())
		{
			return new PyRequirement(matcher.group(NAME_GROUP), Collections.emptyList(), Collections.singletonList(dropComments(line, matcher)));
		}

		return null;
	}

	@Nullable
	private static PyRequirement parseArchiveUrl(@Nonnull String line)
	{
		final Matcher matcher = ARCHIVE_URL.matcher(line);

		if(matcher.matches())
		{
			return createVcsOrArchiveRequirement(Collections.singletonList(dropComments(line, matcher)), parseNameAndVersionFromVcsOrArchive(matcher.group(NAME_GROUP)));
		}

		return null;
	}

	@Nullable
	private static PyRequirement parseVcsProjectUrl(@Nonnull String line)
	{
		final Matcher vcsMatcher = VCS_PROJECT_URL.matcher(line);
		if(vcsMatcher.matches())
		{
			return createVcsRequirement(vcsMatcher);
		}

		final Matcher gitMatcher = GIT_PROJECT_URL.matcher(line);
		if(gitMatcher.matches())
		{
			return createVcsRequirement(gitMatcher);
		}

		final Matcher bzrMatcher = BZR_PROJECT_URL.matcher(line);
		if(bzrMatcher.matches())
		{
			return createVcsRequirement(bzrMatcher);
		}

		return null;
	}

	@Nullable
	private static PyRequirement parseRequirement(@Nonnull String line)
	{
		final Matcher matcher = REQUIREMENT.matcher(line);
		if(matcher.matches())
		{
			final String name = matcher.group(NAME_GROUP);
			final List<PyRequirementVersionSpec> versionSpecs = parseVersionSpecs(matcher.group(REQUIREMENT_VERSIONS_SPECS_GROUP));
			final List<String> installOptions = calculateRequirementInstallOptions(matcher);
			final String extras = matcher.group(REQUIREMENT_EXTRAS_GROUP);

			if(extras == null)
			{
				return new PyRequirement(name, versionSpecs, installOptions);
			}
			else
			{
				return new PyRequirement(name, versionSpecs, installOptions, extras);
			}
		}

		return null;
	}

	@Nonnull
	private static List<PyRequirement> fromText(@Nonnull String text, @Nullable VirtualFile containingFile, @Nonnull Set<VirtualFile> visitedFiles)
	{
		if(containingFile != null)
		{
			visitedFiles.add(containingFile);
		}

		return splitByLinesAndCollapse(text).stream().map(line -> parseLine(line, containingFile, visitedFiles)).flatMap(Collection::stream).filter(req -> req != null).collect(Collectors
				.toCollection(LinkedHashSet::new)).stream().collect(Collectors.toList());
	}

	@Nonnull
	private static String loadText(@Nonnull VirtualFile file)
	{
		final Document document = FileDocumentManager.getInstance().getDocument(file);

		return document == null ? "" : document.getText();
	}

	@Nonnull
	private static String dropComments(@Nonnull String line, @Nonnull Matcher matcher)
	{
		final int commentIndex = matcher.start(COMMENT_GROUP);

		if(commentIndex == -1)
		{
			return line;
		}

		return line.substring(0, findFirstNotWhiteSpaceBefore(line, commentIndex) + 1);
	}

	@Nonnull
	private static Pair<String, String> parseNameAndVersionFromVcsOrArchive(@Nonnull String name)
	{
		boolean isName = true;
		final List<String> nameParts = new ArrayList<>();
		final List<String> versionParts = new ArrayList<>();

		for(String part : StringUtil.split(name, "-"))
		{
			final boolean partStartsWithDigit = !part.isEmpty() && Character.isDigit(part.charAt(0));

			if(partStartsWithDigit || "dev".equals(part))
			{
				isName = false;
			}

			if(isName)
			{
				nameParts.add(part);
			}
			else
			{
				versionParts.add(part);
			}
		}

		return Pair.create(normalizeVcsOrArchiveNameParts(nameParts), normalizeVcsOrArchiveVersionParts(versionParts));
	}

	@Nonnull
	private static PyRequirement createVcsOrArchiveRequirement(@Nonnull List<String> installOptions, @Nonnull Pair<String, String> nameAndVersion)
	{
		final String name = nameAndVersion.getFirst();
		final String version = nameAndVersion.getSecond();

		if(version == null)
		{
			return new PyRequirement(name, Collections.emptyList(), installOptions);
		}

		return new PyRequirement(name, Collections.singletonList(calculateVersionSpec(version, PyRequirementRelation.EQ)), installOptions);
	}

	@Nullable
	private static PyRequirement createVcsRequirement(@Nonnull Matcher matcher)
	{
		final String path = matcher.group(PATH_IN_VCS_GROUP);
		final String egg = getEgg(matcher);

		final String project = extractProject(dropTrunk(dropRevision(path)));
		final Pair<String, String> nameAndVersion = parseNameAndVersionFromVcsOrArchive(egg == null ? StringUtil.trimEnd(project, ".git") : egg);

		return createVcsOrArchiveRequirement(calculateVcsInstallOptions(matcher), nameAndVersion);
	}

	@Nonnull
	private static List<PyRequirementVersionSpec> parseVersionSpecs(@Nullable String versionSpecs)
	{
		if(versionSpecs == null)
		{
			return Collections.emptyList();
		}

		return StreamSupport.stream(StringUtil.tokenize(versionSpecs, ",").spliterator(), false).map(String::trim).map(PyRequirement::parseVersionSpec).filter(req -> req != null).collect(Collectors
				.toList());
	}

	@Nonnull
	private static List<String> calculateRequirementInstallOptions(@Nonnull Matcher matcher)
	{
		final List<String> result = new ArrayList<>();
		result.add(matcher.group(REQUIREMENT_GROUP));

		final String requirementOptions = matcher.group(REQUIREMENT_OPTIONS_GROUP);
		if(requirementOptions != null)
		{
			boolean isKey = true;
			for(String token : StringUtil.tokenize(requirementOptions, "\""))
			{
				result.add(isKey ? token.substring(findFirstNotWhiteSpaceAfter(token, 0), token.length() - 1) : token);
				isKey = !isKey;
			}
		}

		return result;
	}

	@Nonnull
	private static List<String> splitByLinesAndCollapse(@Nonnull String text)
	{
		final List<String> result = new ArrayList<>();
		final StringBuilder sb = new StringBuilder();

		for(String line : StringUtil.splitByLines(text))
		{
			if(line.endsWith("\\") && !line.endsWith("\\\\"))
			{
				sb.append(line.substring(0, line.length() - 1));
			}
			else
			{
				if(sb.length() == 0)
				{
					result.add(line);
				}
				else
				{
					sb.append(line);

					result.add(sb.toString());

					sb.setLength(0);
				}
			}
		}

		return result;
	}

	@Nonnull
	private static List<PyRequirement> parseLine(@Nonnull String line, @Nullable VirtualFile containingFile, @Nonnull Set<VirtualFile> visitedFiles)
	{
		if(line.startsWith("-r"))
		{
			return parseRecursiveLine(line, containingFile, visitedFiles, "-r".length());
		}

		if(line.startsWith("--requirement "))
		{
			return parseRecursiveLine(line, containingFile, visitedFiles, "--requirement ".length());
		}

		return Collections.singletonList(fromLine(line));
	}

	@Nonnull
	private static String normalizeVcsOrArchiveNameParts(@Nonnull List<String> nameParts)
	{
		return normalizeName(StringUtil.join(nameParts, "-"));
	}

	@Nullable
	private static String normalizeVcsOrArchiveVersionParts(@Nonnull List<String> versionParts)
	{
		return versionParts.isEmpty() ? null : normalizeVersion(StringUtil.join(versionParts, "-"));
	}

	@Nonnull
	private static List<String> calculateVcsInstallOptions(@Nonnull Matcher matcher)
	{
		final List<String> result = new ArrayList<>();

		final String srcBefore = matcher.group(VCS_SRC_BEFORE_GROUP);
		if(srcBefore != null)
		{
			result.addAll(Arrays.asList(srcBefore.split("\\s+")));
		}

		final String editable = matcher.group(VCS_EDITABLE_GROUP);
		if(editable != null)
		{
			result.add(editable);
		}

		result.add(matcher.group(VCS_GROUP));

		final String srcAfter = matcher.group(VCS_SRC_AFTER_GROUP);
		if(srcAfter != null)
		{
			result.addAll(Arrays.asList(srcAfter.split("\\s+")).subList(1, 3)); // skip spaces before --src and get only two values
		}

		return result;
	}

	@Nullable
	private static String getEgg(@Nonnull Matcher matcher)
	{
		final String beforeSubdir = matcher.group(VCS_EGG_BEFORE_SUBDIR_GROUP);

		return beforeSubdir == null ? matcher.group(VCS_EGG_AFTER_SUBDIR_GROUP) : beforeSubdir;
	}

	@Nonnull
	private static String extractProject(@Nonnull String path)
	{
		final int end = path.endsWith("/") ? path.length() - 1 : path.length();
		final int slashIndex = path.lastIndexOf("/", end - 1);

		if(slashIndex != -1)
		{
			return path.substring(slashIndex + 1, end);
		}

		if(end != path.length())
		{
			return path.substring(0, end);
		}

		return path;
	}

	@Nonnull
	private static String dropTrunk(@Nonnull String path)
	{
		final String slashTrunk = "/trunk";

		if(path.endsWith(slashTrunk))
		{
			return path.substring(0, path.length() - slashTrunk.length());
		}

		final String slashTrunkSlash = "/trunk/";

		if(path.endsWith(slashTrunkSlash))
		{
			return path.substring(0, path.length() - slashTrunkSlash.length());
		}

		return path;
	}

	@Nonnull
	private static String dropRevision(@Nonnull String path)
	{
		final int atIndex = path.lastIndexOf("@");

		if(atIndex != -1)
		{
			return path.substring(0, atIndex);
		}

		return path;
	}

	@Nullable
	private static PyRequirementVersionSpec parseVersionSpec(@Nonnull String versionSpec)
	{
		PyRequirementRelation relation = null;

		if(versionSpec.startsWith("==="))
		{
			relation = PyRequirementRelation.STR_EQ;
		}
		else if(versionSpec.startsWith("=="))
		{
			relation = PyRequirementRelation.EQ;
		}
		else if(versionSpec.startsWith("<="))
		{
			relation = PyRequirementRelation.LTE;
		}
		else if(versionSpec.startsWith(">="))
		{
			relation = PyRequirementRelation.GTE;
		}
		else if(versionSpec.startsWith("<"))
		{
			relation = PyRequirementRelation.LT;
		}
		else if(versionSpec.startsWith(">"))
		{
			relation = PyRequirementRelation.GT;
		}
		else if(versionSpec.startsWith("~="))
		{
			relation = PyRequirementRelation.COMPATIBLE;
		}
		else if(versionSpec.startsWith("!="))
		{
			relation = PyRequirementRelation.NE;
		}

		if(relation != null)
		{
			final int versionIndex = findFirstNotWhiteSpaceAfter(versionSpec, relation.toString().length());
			final String version = versionSpec.substring(versionIndex);

			if(relation == PyRequirementRelation.STR_EQ)
			{
				return new PyRequirementVersionSpec(relation, version);
			}

			return calculateVersionSpec(version, relation);
		}

		return null;
	}

	@Nonnull
	private static List<PyRequirement> parseRecursiveLine(@Nonnull String line, @Nullable VirtualFile containingFile, @Nonnull Set<VirtualFile> visitedFiles, int flagLength)
	{
		if(containingFile == null)
		{
			return Collections.emptyList();
		}

		final int pathIndex = findFirstNotWhiteSpaceAfter(line, flagLength);
		if(pathIndex == line.length())
		{
			return Collections.emptyList();
		}

		final String path = FileUtil.toSystemIndependentName(line.substring(pathIndex));
		final VirtualFile file = findRecursiveFile(containingFile, path);

		if(file != null && !visitedFiles.contains(file))
		{
			return fromText(loadText(file), file, visitedFiles);
		}

		return Collections.emptyList();
	}

	@Nonnull
	private static String normalizeName(@Nonnull String s)
	{
		return s.replace("_", "-");
	}

	@Nonnull
	private static String normalizeVersion(@Nonnull String s)
	{
		return s.replace("_", "-").replaceAll("-?py[\\d\\.]+", "");
	}

	private static int findFirstNotWhiteSpaceAfter(@Nonnull String line, int beginIndex)
	{
		for(int i = beginIndex; i < line.length(); i++)
		{
			if(!StringUtil.isWhiteSpace(line.charAt(i)))
			{
				return i;
			}
		}

		return line.length();
	}

	private static int findFirstNotWhiteSpaceBefore(@Nonnull String line, int beginIndex)
	{
		for(int i = beginIndex; i >= 0; i--)
		{
			if(!StringUtil.isWhiteSpace(line.charAt(i)))
			{
				return i;
			}
		}

		return -1;
	}

	@Nullable
	private static VirtualFile findRecursiveFile(@Nonnull VirtualFile containingFile, @Nonnull String path)
	{
		final VirtualFile dir = containingFile.getParent();
		if(dir == null)
		{
			return null;
		}

		final VirtualFile file = dir.findFileByRelativePath(path);
		if(file != null)
		{
			return file;
		}

		return LocalFileSystem.getInstance().findFileByPath(path);
	}
}
