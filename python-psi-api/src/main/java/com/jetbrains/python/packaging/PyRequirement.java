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

import org.jspecify.annotations.Nullable;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.util.io.FileUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import com.jetbrains.python.packaging.requirement.PyRequirementRelation;
import com.jetbrains.python.packaging.requirement.PyRequirementVersionNormalizer;
import com.jetbrains.python.packaging.requirement.PyRequirementVersionSpec;

/**
 * @author vlan
 */
public class PyRequirement
{

	// common regular expressions

	private static final String LINE_WS_REGEXP = "[ \t]";

	private static final String COMMENT_GROUP = "comment";

	private static final String COMMENT_REGEXP = "(?<" + COMMENT_GROUP + ">" + LINE_WS_REGEXP + "+#.*)?";

	private static final String NAME_GROUP = "name";

	// archive-related regular expressions

	private static final Pattern GITHUB_ARCHIVE_URL = Pattern.compile("https?://github\\.com/[^/\\s]+/(?<" + NAME_GROUP + ">[^/\\s]+)/archive/\\S+" + COMMENT_REGEXP);

	private static final Pattern ARCHIVE_URL = Pattern.compile("https?://\\S+/" +
			"(?<" + NAME_GROUP + ">\\S+)" +
			"(\\.tar\\.gz|\\.zip)(#(sha1|sha224|sha256|sha384|sha512|md5)=\\w+)?" + COMMENT_REGEXP);

	// vcs-related regular expressions
	// don't forget to update calculateVcsInstallOptions(Matcher) after this section changing

	private static final String VCS_EDITABLE_GROUP = "editable";

	private static final String VCS_EDITABLE_REGEXP = "((?<" + VCS_EDITABLE_GROUP + ">-e|--editable)" + LINE_WS_REGEXP + "+)?";

	private static final String VCS_SRC_BEFORE_GROUP = "srcb";

	private static final String VCS_SRC_AFTER_GROUP = "srca";

	private static final String VCS_SRC_BEFORE_REGEXP = "(?<" + VCS_SRC_BEFORE_GROUP + ">--src" + LINE_WS_REGEXP + "+\\S+" + LINE_WS_REGEXP + "+)?";

	private static final String VCS_SRC_AFTER_REGEXP = "(?<" + VCS_SRC_AFTER_GROUP + ">" + LINE_WS_REGEXP + "+--src" + LINE_WS_REGEXP + "+\\S+)?";

	private static final String PATH_IN_VCS_GROUP = "path";

	private static final String PATH_IN_VCS_REGEXP = "(?<" + PATH_IN_VCS_GROUP + ">[^@#\\s]+)";

	private static final String VCS_REVISION_REGEXP = "(@[^#\\s]+)?";

	private static final String VCS_EGG_BEFORE_SUBDIR_GROUP = "eggb";

	private static final String VCS_EGG_AFTER_SUBDIR_GROUP = "egga";

	private static final String VCS_PARAMS_REGEXP = "(" +
			"(#egg=(?<" + VCS_EGG_BEFORE_SUBDIR_GROUP + ">[^&\\s]+)(&subdirectory=\\S+)?)" +
			"|" +
			"(#subdirectory=[^&\\s]+&egg=(?<" + VCS_EGG_AFTER_SUBDIR_GROUP + ">\\S+))" +
			")?";

	private static final String VCS_GROUP = "vcs";

	private static final String VCS_URL_PREFIX = VCS_SRC_BEFORE_REGEXP + VCS_EDITABLE_REGEXP + "(?<" + VCS_GROUP + ">";

	private static final String VCS_URL_SUFFIX = PATH_IN_VCS_REGEXP + VCS_REVISION_REGEXP + VCS_PARAMS_REGEXP + ")" + VCS_SRC_AFTER_REGEXP + COMMENT_REGEXP;

	private static final String GIT_USER_AT_REGEXP = "[\\w-]+@";

	// supports: git+user@...
	private static final Pattern GIT_PROJECT_URL = Pattern.compile(VCS_URL_PREFIX + "git\\+" + GIT_USER_AT_REGEXP + "[^:\\s]+:" + VCS_URL_SUFFIX);

	// supports: bzr+lp:...
	private static final Pattern BZR_PROJECT_URL = Pattern.compile(VCS_URL_PREFIX + "bzr\\+lp:" + VCS_URL_SUFFIX);

	// supports: (bzr|git|hg|svn)(+smth)?://...
	private static final Pattern VCS_PROJECT_URL = Pattern.compile(VCS_URL_PREFIX + "(bzr|git|hg|svn)(\\+[A-Za-z]+)?://?[^/]+/" + VCS_URL_SUFFIX);

	// requirement-related regular expressions
	// don't forget to update calculateRequirementInstallOptions(Matcher) after this section changing

	// PEP-508 + PEP-440
	// https://www.python.org/dev/peps/pep-0508/
	// https://www.python.org/dev/peps/pep-0440/
	private static final String IDENTIFIER_REGEXP = "[A-Za-z0-9]([-_\\.]?[A-Za-z0-9])*";

	private static final String REQUIREMENT_NAME_REGEXP = "(?<" + NAME_GROUP + ">" + IDENTIFIER_REGEXP + ")";

	private static final String REQUIREMENT_EXTRAS_GROUP = "extras";

	private static final String REQUIREMENT_EXTRAS_REGEXP = "(?<" + REQUIREMENT_EXTRAS_GROUP + ">" +
			"\\[" +
			IDENTIFIER_REGEXP + "(" + LINE_WS_REGEXP + "*," + LINE_WS_REGEXP + "*" + IDENTIFIER_REGEXP + ")*" +
			"\\]" +
			")?";

	private static final String REQUIREMENT_VERSIONS_SPECS_GROUP = "versionspecs";

	private static final String REQUIREMENT_VERSION_SPEC_REGEXP = "(<=?|!=|===?|>=?|~=)" + LINE_WS_REGEXP + "*[\\.\\*\\+!\\w-]+";

	private static final String REQUIREMENT_VERSIONS_SPECS_REGEXP = "(?<" + REQUIREMENT_VERSIONS_SPECS_GROUP + ">" + REQUIREMENT_VERSION_SPEC_REGEXP +
			"(" + LINE_WS_REGEXP + "*," + LINE_WS_REGEXP + "*" + REQUIREMENT_VERSION_SPEC_REGEXP + ")*)?";

	private static final String REQUIREMENT_OPTIONS_GROUP = "options";

	private static final String REQUIREMENT_OPTIONS_REGEXP = "(?<" + REQUIREMENT_OPTIONS_GROUP + ">(" + LINE_WS_REGEXP + "+(--global-option|--install-option)=\"[^\"]*\")+)?";

	private static final String REQUIREMENT_GROUP = "requirement";

	private static final Pattern REQUIREMENT = Pattern.compile("(?<" + REQUIREMENT_GROUP + ">" +
			REQUIREMENT_NAME_REGEXP +
			LINE_WS_REGEXP + "*" +
			REQUIREMENT_EXTRAS_REGEXP +
			LINE_WS_REGEXP + "*" +
			REQUIREMENT_VERSIONS_SPECS_REGEXP +
			")" +
			REQUIREMENT_OPTIONS_REGEXP +
			COMMENT_REGEXP);

	private final String myName;

	private final List<PyRequirementVersionSpec> myVersionSpecs;

	private final List<String> myInstallOptions;

	private final String myExtras;

	public PyRequirement(String name)
	{
		this(name, Collections.emptyList());
	}

	public PyRequirement(String name, String version)
	{
		this(name, Collections.singletonList(calculateVersionSpec(version, PyRequirementRelation.EQ)));
	}

	public PyRequirement(String name, String version, List<String> installOptions)
	{
		this(name, Collections.singletonList(calculateVersionSpec(version, PyRequirementRelation.EQ)), installOptions);
	}

	public PyRequirement(String name, String version, List<String> installOptions, String extras)
	{
		this(name, Collections.singletonList(calculateVersionSpec(version, PyRequirementRelation.EQ)), installOptions, extras);
	}

	public PyRequirement(String name, List<PyRequirementVersionSpec> versionSpecs)
	{
		myName = name;
		myVersionSpecs = versionSpecs;
		myExtras = "";
		myInstallOptions = Collections.singletonList(toString());
	}

	public PyRequirement(String name, List<PyRequirementVersionSpec> versionSpecs, List<String> installOptions)
	{
		myName = name;
		myVersionSpecs = versionSpecs;
		myInstallOptions = Collections.unmodifiableList(installOptions);
		myExtras = "";
	}

	public PyRequirement(String name, List<PyRequirementVersionSpec> versionSpecs, List<String> installOptions, String extras)
	{
		myName = name;
		myVersionSpecs = versionSpecs;
		myInstallOptions = Collections.unmodifiableList(installOptions);
		myExtras = extras;
	}

	public String getName()
	{
		return myName;
	}

	public String getFullName()
	{
		return myName + myExtras;
	}

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
	public PyPackage match(List<PyPackage> packages)
	{
		String normalizedName = normalizeName(myName);

		return packages.stream().filter(pkg -> normalizedName.equalsIgnoreCase(pkg.getName())).findAny().filter(pkg -> myVersionSpecs.stream().allMatch(spec -> spec.matches(pkg.getVersion())))
				.orElse(null);
	}

	@Nullable
	public static PyRequirement fromLine(String line)
	{
		PyRequirement githubArchiveUrl = parseGithubArchiveUrl(line);
		if(githubArchiveUrl != null)
		{
			return githubArchiveUrl;
		}

		PyRequirement archiveUrl = parseArchiveUrl(line);
		if(archiveUrl != null)
		{
			return archiveUrl;
		}

		PyRequirement vcsProjectUrl = parseVcsProjectUrl(line);
		if(vcsProjectUrl != null)
		{
			return vcsProjectUrl;
		}

		return parseRequirement(line);
	}

	public static List<PyRequirement> fromText(String text)
	{
		return fromText(text, null, new HashSet<>());
	}

	public static List<PyRequirement> fromFile(VirtualFile file)
	{
		return fromText(loadText(file), file, new HashSet<>());
	}

	public static PyRequirementVersionSpec calculateVersionSpec(String version, PyRequirementRelation expectedRelation)
	{
		String normalizedVersion = PyRequirementVersionNormalizer.normalize(version);

		return normalizedVersion == null ? new PyRequirementVersionSpec(PyRequirementRelation.STR_EQ, version) : new PyRequirementVersionSpec(expectedRelation, normalizedVersion);
	}

	@Nullable
	private static PyRequirement parseGithubArchiveUrl(String line)
	{
		Matcher matcher = GITHUB_ARCHIVE_URL.matcher(line);

		if(matcher.matches())
		{
			return new PyRequirement(matcher.group(NAME_GROUP), Collections.emptyList(), Collections.singletonList(dropComments(line, matcher)));
		}

		return null;
	}

	@Nullable
	private static PyRequirement parseArchiveUrl(String line)
	{
		Matcher matcher = ARCHIVE_URL.matcher(line);

		if(matcher.matches())
		{
			return createVcsOrArchiveRequirement(Collections.singletonList(dropComments(line, matcher)), parseNameAndVersionFromVcsOrArchive(matcher.group(NAME_GROUP)));
		}

		return null;
	}

	@Nullable
	private static PyRequirement parseVcsProjectUrl(String line)
	{
		Matcher vcsMatcher = VCS_PROJECT_URL.matcher(line);
		if(vcsMatcher.matches())
		{
			return createVcsRequirement(vcsMatcher);
		}

		Matcher gitMatcher = GIT_PROJECT_URL.matcher(line);
		if(gitMatcher.matches())
		{
			return createVcsRequirement(gitMatcher);
		}

		Matcher bzrMatcher = BZR_PROJECT_URL.matcher(line);
		if(bzrMatcher.matches())
		{
			return createVcsRequirement(bzrMatcher);
		}

		return null;
	}

	@Nullable
	private static PyRequirement parseRequirement(String line)
	{
		Matcher matcher = REQUIREMENT.matcher(line);
		if(matcher.matches())
		{
			String name = matcher.group(NAME_GROUP);
			List<PyRequirementVersionSpec> versionSpecs = parseVersionSpecs(matcher.group(REQUIREMENT_VERSIONS_SPECS_GROUP));
			List<String> installOptions = calculateRequirementInstallOptions(matcher);
			String extras = matcher.group(REQUIREMENT_EXTRAS_GROUP);

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

	private static List<PyRequirement> fromText(String text, @Nullable VirtualFile containingFile, Set<VirtualFile> visitedFiles)
	{
		if(containingFile != null)
		{
			visitedFiles.add(containingFile);
		}

		return splitByLinesAndCollapse(text).stream().map(line -> parseLine(line, containingFile, visitedFiles)).flatMap(Collection::stream).filter(req -> req != null).collect(Collectors
				.toCollection(LinkedHashSet::new)).stream().collect(Collectors.toList());
	}

	private static String loadText(VirtualFile file)
	{
		Document document = FileDocumentManager.getInstance().getDocument(file);

		return document == null ? "" : document.getText();
	}

	private static String dropComments(String line, Matcher matcher)
	{
		int commentIndex = matcher.start(COMMENT_GROUP);

		if(commentIndex == -1)
		{
			return line;
		}

		return line.substring(0, findFirstNotWhiteSpaceBefore(line, commentIndex) + 1);
	}

	private static Pair<String, String> parseNameAndVersionFromVcsOrArchive(String name)
	{
		boolean isName = true;
		List<String> nameParts = new ArrayList<>();
		List<String> versionParts = new ArrayList<>();

		for(String part : StringUtil.split(name, "-"))
		{
			boolean partStartsWithDigit = !part.isEmpty() && Character.isDigit(part.charAt(0));

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

	private static PyRequirement createVcsOrArchiveRequirement(List<String> installOptions, Pair<String, String> nameAndVersion)
	{
		String name = nameAndVersion.getFirst();
		String version = nameAndVersion.getSecond();

		if(version == null)
		{
			return new PyRequirement(name, Collections.emptyList(), installOptions);
		}

		return new PyRequirement(name, Collections.singletonList(calculateVersionSpec(version, PyRequirementRelation.EQ)), installOptions);
	}

	@Nullable
	private static PyRequirement createVcsRequirement(Matcher matcher)
	{
		String path = matcher.group(PATH_IN_VCS_GROUP);
		String egg = getEgg(matcher);

		String project = extractProject(dropTrunk(dropRevision(path)));
		Pair<String, String> nameAndVersion = parseNameAndVersionFromVcsOrArchive(egg == null ? StringUtil.trimEnd(project, ".git") : egg);

		return createVcsOrArchiveRequirement(calculateVcsInstallOptions(matcher), nameAndVersion);
	}

	private static List<PyRequirementVersionSpec> parseVersionSpecs(@Nullable String versionSpecs)
	{
		if(versionSpecs == null)
		{
			return Collections.emptyList();
		}

		return StreamSupport.stream(StringUtil.tokenize(versionSpecs, ",").spliterator(), false).map(String::trim).map(PyRequirement::parseVersionSpec).filter(req -> req != null).collect(Collectors
				.toList());
	}

	private static List<String> calculateRequirementInstallOptions(Matcher matcher)
	{
		List<String> result = new ArrayList<>();
		result.add(matcher.group(REQUIREMENT_GROUP));

		String requirementOptions = matcher.group(REQUIREMENT_OPTIONS_GROUP);
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

	private static List<String> splitByLinesAndCollapse(String text)
	{
		List<String> result = new ArrayList<>();
		StringBuilder sb = new StringBuilder();

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

	private static List<PyRequirement> parseLine(String line, @Nullable VirtualFile containingFile, Set<VirtualFile> visitedFiles)
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

	private static String normalizeVcsOrArchiveNameParts(List<String> nameParts)
	{
		return normalizeName(StringUtil.join(nameParts, "-"));
	}

	@Nullable
	private static String normalizeVcsOrArchiveVersionParts(List<String> versionParts)
	{
		return versionParts.isEmpty() ? null : normalizeVersion(StringUtil.join(versionParts, "-"));
	}

	private static List<String> calculateVcsInstallOptions(Matcher matcher)
	{
		List<String> result = new ArrayList<>();

		String srcBefore = matcher.group(VCS_SRC_BEFORE_GROUP);
		if(srcBefore != null)
		{
			result.addAll(Arrays.asList(srcBefore.split("\\s+")));
		}

		String editable = matcher.group(VCS_EDITABLE_GROUP);
		if(editable != null)
		{
			result.add(editable);
		}

		result.add(matcher.group(VCS_GROUP));

		String srcAfter = matcher.group(VCS_SRC_AFTER_GROUP);
		if(srcAfter != null)
		{
			result.addAll(Arrays.asList(srcAfter.split("\\s+")).subList(1, 3)); // skip spaces before --src and get only two values
		}

		return result;
	}

	@Nullable
	private static String getEgg(Matcher matcher)
	{
		String beforeSubdir = matcher.group(VCS_EGG_BEFORE_SUBDIR_GROUP);

		return beforeSubdir == null ? matcher.group(VCS_EGG_AFTER_SUBDIR_GROUP) : beforeSubdir;
	}

	private static String extractProject(String path)
	{
		int end = path.endsWith("/") ? path.length() - 1 : path.length();
		int slashIndex = path.lastIndexOf("/", end - 1);

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

	private static String dropTrunk(String path)
	{
		String slashTrunk = "/trunk";

		if(path.endsWith(slashTrunk))
		{
			return path.substring(0, path.length() - slashTrunk.length());
		}

		String slashTrunkSlash = "/trunk/";

		if(path.endsWith(slashTrunkSlash))
		{
			return path.substring(0, path.length() - slashTrunkSlash.length());
		}

		return path;
	}

	private static String dropRevision(String path)
	{
		int atIndex = path.lastIndexOf("@");

		if(atIndex != -1)
		{
			return path.substring(0, atIndex);
		}

		return path;
	}

	@Nullable
	private static PyRequirementVersionSpec parseVersionSpec(String versionSpec)
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
			int versionIndex = findFirstNotWhiteSpaceAfter(versionSpec, relation.toString().length());
			String version = versionSpec.substring(versionIndex);

			if(relation == PyRequirementRelation.STR_EQ)
			{
				return new PyRequirementVersionSpec(relation, version);
			}

			return calculateVersionSpec(version, relation);
		}

		return null;
	}

	private static List<PyRequirement> parseRecursiveLine(String line, @Nullable VirtualFile containingFile, Set<VirtualFile> visitedFiles, int flagLength)
	{
		if(containingFile == null)
		{
			return Collections.emptyList();
		}

		int pathIndex = findFirstNotWhiteSpaceAfter(line, flagLength);
		if(pathIndex == line.length())
		{
			return Collections.emptyList();
		}

		String path = FileUtil.toSystemIndependentName(line.substring(pathIndex));
		VirtualFile file = findRecursiveFile(containingFile, path);

		if(file != null && !visitedFiles.contains(file))
		{
			return fromText(loadText(file), file, visitedFiles);
		}

		return Collections.emptyList();
	}

	private static String normalizeName(String s)
	{
		return s.replace("_", "-");
	}

	private static String normalizeVersion(String s)
	{
		return s.replace("_", "-").replaceAll("-?py[\\d\\.]+", "");
	}

	private static int findFirstNotWhiteSpaceAfter(String line, int beginIndex)
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

	private static int findFirstNotWhiteSpaceBefore(String line, int beginIndex)
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
	private static VirtualFile findRecursiveFile(VirtualFile containingFile, String path)
	{
		VirtualFile dir = containingFile.getParent();
		if(dir == null)
		{
			return null;
		}

		VirtualFile file = dir.findFileByRelativePath(path);
		if(file != null)
		{
			return file;
		}

		return LocalFileSystem.getInstance().findFileByPath(path);
	}
}
