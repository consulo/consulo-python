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
package com.jetbrains.python.impl.packaging;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.jetbrains.python.impl.PythonHelpersLocator;
import com.jetbrains.python.impl.packaging.pip.PypiPackageCache;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.http.HttpRequests;
import consulo.logging.Logger;
import consulo.repository.ui.PackageVersionComparator;
import consulo.repository.ui.RepoPackage;
import consulo.util.collection.ContainerUtil;
import consulo.util.concurrent.AsyncResult;
import consulo.util.io.FileUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * User: catherine
 */
public class PyPIPackageUtil
{
	private static final Logger LOG = Logger.getInstance(PyPIPackageUtil.class);
	private static final Gson GSON = new GsonBuilder().create();

	private static final String PYPI_HOST = "https://pypi.python.org";
	public static final String PYPI_URL = PYPI_HOST + "/pypi";
	public static final String PYPI_LIST_URL = PYPI_HOST + "/simple";

	/**
	 * Contains mapping "importable top-level package" -> "package name on PyPI".
	 */
	public static final Map<String, String> PACKAGES_TOPLEVEL = loadPackageAliases();

	public static final PyPIPackageUtil INSTANCE = new PyPIPackageUtil();

	/**
	 * Contains cached versions of packages from additional repositories.
	 *
	 * @see #getPackageVersionsFromAdditionalRepositories(String)
	 */
	private final LoadingCache<String, List<String>> myAdditionalPackagesReleases =
			CacheBuilder.newBuilder().build(new CacheLoader<String, List<String>>()
			{
				@Override
				public List<String> load(@Nonnull String key) throws Exception
				{
					LOG.debug("Searching for versions of package '" + key + "' in additional repositories");
					final List<String> repositories = PyPackageService.getInstance().additionalRepositories;
					for(String repository : repositories)
					{
						final List<String> versions = parsePackageVersionsFromArchives(composeSimpleUrl(key, repository));
						if(!versions.isEmpty())
						{
							LOG.debug("Found versions " + versions + " in " + repository);
							return Collections.unmodifiableList(versions);
						}
					}
					return Collections.emptyList();
				}
			});

	/**
	 * Contains cached packages taken from additional repositories.
	 *
	 * @see #getAdditionalPackages()
	 */
	private volatile Set<RepoPackage> myAdditionalPackages = null;

	/**
	 * Contains cached package information retrieved through PyPI's JSON API.
	 *
	 * @see #refreshAndGetPackageDetailsFromPyPI(String, boolean)
	 */
	private final LoadingCache<String, PackageDetails> myPackageToDetails =
			CacheBuilder.newBuilder().build(new CacheLoader<String, PackageDetails>()
			{
				@Override
				public PackageDetails load(@Nonnull String key) throws Exception
				{
					LOG.debug("Fetching details for the package '" + key + "' on PyPI");
					return HttpRequests.request(PYPI_URL + "/" + key + "/json")
							.userAgent(getUserAgent())
							.connect(request -> GSON.fromJson(request.getReader(), PackageDetails.class));
				}
			});

	/**
	 * Lowercased package names for fast check that some package is available in PyPI.
	 * TODO find the way to get rid of it, it's not a good idea to store 85k+ entries in memory twice
	 */
	@Nullable
	private volatile Set<String> myPackageNames = null;


	/**
	 * Prevents simultaneous updates of {@link PypiPackageCache}
	 * because the corresponding response contains tons of data and multiple
	 * queries at the same time can cause memory issues.
	 */
	private final Object myPyPIPackageCacheUpdateLock = new Object();

	/**
	 * Value for "User Agent" HTTP header in form: PyCharm/2016.2 EAP
	 */
	@Nonnull
	private static String getUserAgent()
	{
		Application application = Application.get();
		return application.getName() + "/" + application.getVersion();
	}

	@Nonnull
	private static ImmutableMap<String, String> loadPackageAliases()
	{
		final ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
		try (FileReader reader = new FileReader(PythonHelpersLocator.getHelperPath("/tools/packages")))
		{
			final String text = FileUtil.loadTextAndClose(reader);
			final List<String> lines = StringUtil.split(text, "\n");
			for(String line : lines)
			{
				final List<String> split = StringUtil.split(line, " ");
				builder.put(split.get(0), split.get(1));
			}
		}
		catch(IOException e)
		{
			LOG.error("Cannot find \"packages\". " + e.getMessage());
		}
		return builder.build();
	}

	@Nonnull
	private static Pair<String, String> splitNameVersion(@Nonnull String pyPackage)
	{
		final int dashInd = pyPackage.lastIndexOf("-");
		if(dashInd >= 0 && dashInd + 1 < pyPackage.length())
		{
			final String name = pyPackage.substring(0, dashInd);
			final String version = pyPackage.substring(dashInd + 1);
			if(StringUtil.containsAlphaCharacters(version))
			{
				return Pair.create(pyPackage, null);
			}
			return Pair.create(name, version);
		}
		return Pair.create(pyPackage, null);
	}

	public static boolean isPyPIRepository(@Nullable String repository)
	{
		return repository != null && repository.startsWith(PYPI_HOST);
	}

	@Nonnull
	public Set<RepoPackage> getAdditionalPackages() throws IOException
	{
		if(myAdditionalPackages == null)
		{
			final Set<RepoPackage> packages = new TreeSet<>();
			for(String url : PyPackageService.getInstance().additionalRepositories)
			{
				packages.addAll(getPackagesFromAdditionalRepository(url));
			}
			myAdditionalPackages = packages;
		}
		return Collections.unmodifiableSet(myAdditionalPackages);
	}

	@Nonnull
	private static List<RepoPackage> getPackagesFromAdditionalRepository(@Nonnull String url) throws IOException
	{
		final List<RepoPackage> result = new ArrayList<>();
		final boolean simpleIndex = url.endsWith("simple/");
		final List<String> packagesList = parsePyPIListFromWeb(url, simpleIndex);

		for(String pyPackage : packagesList)
		{
			if(simpleIndex)
			{
				final Pair<String, String> nameVersion = splitNameVersion(StringUtil.trimTrailing(pyPackage, '/'));
				result.add(new RepoPackage(nameVersion.getFirst(), url, nameVersion.getSecond()));
			}
			else
			{
				try
				{
					final Pattern repositoryPattern = Pattern.compile(url + "([^/]*)/([^/]*)$");
					final Matcher matcher = repositoryPattern.matcher(URLDecoder.decode(pyPackage, "UTF-8"));
					if(matcher.find())
					{
						final String packageName = matcher.group(1);
						final String packageVersion = matcher.group(2);
						if(!packageName.contains(" "))
						{
							result.add(new RepoPackage(packageName, url, packageVersion));
						}
					}
				}
				catch(UnsupportedEncodingException e)
				{
					LOG.warn(e.getMessage());
				}
			}
		}
		return result;
	}

	public void clearPackagesCache()
	{
		PypiPackageCache.getInstance().dropCache();
		myAdditionalPackages = null;
	}

	public <T> AsyncResult<T> fillPackageDetails(@Nonnull String packageName, @Nonnull Function<PackageDetails.Info, T> converter)
	{
		AsyncResult<T> result = AsyncResult.undefined();
		ApplicationManager.getApplication().executeOnPooledThread(() -> {
			try
			{
				final PackageDetails packageDetails = refreshAndGetPackageDetailsFromPyPI(packageName, false);
				result.setDone(converter.apply(packageDetails.getInfo()));
			}
			catch(IOException e)
			{
				result.rejectWithThrowable(e);
			}
		});
		return result;
	}

	@Nonnull
	private PackageDetails refreshAndGetPackageDetailsFromPyPI(@Nonnull String packageName, boolean alwaysRefresh) throws IOException
	{
		if(alwaysRefresh)
		{
			myPackageToDetails.invalidate(packageName);
		}
		return getCachedValueOrRethrowIO(myPackageToDetails, packageName);
	}

	public AsyncResult<List<String>> usePackageReleases(@Nonnull String packageName)
	{
		AsyncResult<List<String>> result = AsyncResult.undefined();
		ApplicationManager.getApplication().executeOnPooledThread(() -> {
			try
			{
				final List<String> releasesFromSimpleIndex = getPackageVersionsFromAdditionalRepositories(packageName);
				if(releasesFromSimpleIndex.isEmpty())
				{
					final List<String> releasesFromPyPI = getPackageVersionsFromPyPI(packageName, true);
					result.setDone(releasesFromPyPI);
				}
				else
				{
					result.setDone(releasesFromSimpleIndex);
				}
			}
			catch(Exception e)
			{
				result.rejectWithThrowable(e);
			}
		});
		return result;
	}

	/**
	 * Fetches available package versions using JSON API of PyPI.
	 */
	@Nonnull
	private List<String> getPackageVersionsFromPyPI(@Nonnull String packageName, boolean force) throws IOException
	{
		final PackageDetails details = refreshAndGetPackageDetailsFromPyPI(packageName, force);
		final List<String> result = details.getReleases();
		result.sort(PackageVersionComparator.VERSION_COMPARATOR.reversed());
		return Collections.unmodifiableList(result);
	}

	@Nullable
	private String getLatestPackageVersionFromPyPI(@Nonnull String packageName) throws IOException
	{
		LOG.debug("Requesting the latest PyPI version for the package " + packageName);
		final List<String> versions = getPackageVersionsFromPyPI(packageName, true);
		final String latest = ContainerUtil.getFirstItem(versions);
		getPyPIPackages().put(packageName, StringUtil.notNullize(latest));
		return latest;
	}

	/**
	 * Fetches available package versions by scrapping the page containing package archives.
	 * It's primarily used for additional repositories since, e.g. devpi doesn't provide another way to get this information.
	 */
	@Nonnull
	private List<String> getPackageVersionsFromAdditionalRepositories(@Nonnull String packageName) throws IOException
	{
		return getCachedValueOrRethrowIO(myAdditionalPackagesReleases, packageName);
	}

	@Nonnull
	private static <T> T getCachedValueOrRethrowIO(@Nonnull LoadingCache<String, ? extends T> cache, @Nonnull String key) throws IOException
	{
		try
		{
			return cache.get(key);
		}
		catch(ExecutionException e)
		{
			final Throwable cause = e.getCause();
			throw (cause instanceof IOException ? (IOException) cause : new IOException("Unexpected non-IO error", cause));
		}
	}

	@Nullable
	private String getLatestPackageVersionFromAdditionalRepositories(@Nonnull String packageName) throws IOException
	{
		final List<String> versions = getPackageVersionsFromAdditionalRepositories(packageName);
		return ContainerUtil.getFirstItem(versions);
	}

	@Nullable
	public String fetchLatestPackageVersion(@Nonnull String packageName) throws IOException
	{
		String version = getPyPIPackages().get(packageName);
		// Package is on PyPI but it's version is unknown
		if(version != null && version.isEmpty())
		{
			version = getLatestPackageVersionFromPyPI(packageName);
		}
		if(!PyPackageService.getInstance().additionalRepositories.isEmpty())
		{
			final String extraVersion = getLatestPackageVersionFromAdditionalRepositories(packageName);
			if(extraVersion != null)
			{
				version = extraVersion;
			}
		}
		return version;
	}

	@Nonnull
	private static List<String> parsePackageVersionsFromArchives(@Nonnull String archivesUrl) throws IOException
	{
		return HttpRequests.request(archivesUrl).userAgent(getUserAgent()).connect(request -> {
			final List<String> versions = new ArrayList<>();
			final Reader reader = request.getReader();
			new ParserDelegator().parse(reader, new HTMLEditorKit.ParserCallback()
			{
				HTML.Tag myTag;

				@Override
				public void handleStartTag(HTML.Tag tag, MutableAttributeSet set, int i)
				{
					myTag = tag;
				}

				@Override
				public void handleText(@Nonnull char[] data, int pos)
				{
					if(myTag != null && "a".equals(myTag.toString()))
					{
						String packageVersion = String.valueOf(data);
						final String suffix = ".tar.gz";
						if(!packageVersion.endsWith(suffix))
						{
							return;
						}
						packageVersion = StringUtil.trimEnd(packageVersion, suffix);
						versions.add(splitNameVersion(packageVersion).second);
					}
				}
			}, true);
			versions.sort(PackageVersionComparator.VERSION_COMPARATOR.reversed());
			return versions;
		});
	}

	@Nonnull
	private static String composeSimpleUrl(@NonNls @Nonnull String packageName, @Nonnull String rep)
	{
		String suffix = "";
		final String repository = StringUtil.trimEnd(rep, "/");
		if(!repository.endsWith("+simple") && !repository.endsWith("/simple"))
		{
			suffix = "/+simple";
		}
		suffix += "/" + packageName;
		return repository + suffix;
	}

	public void updatePyPICache(@Nonnull PyPackageService service) throws IOException
	{
		service.LAST_TIME_CHECKED = System.currentTimeMillis();

		PypiPackageCache.getInstance().dropCache();

		if(service.PYPI_REMOVED)
		{
			return;
		}

		parsePyPIList(parsePyPIListFromWeb(PYPI_LIST_URL, true), service);
	}

	private void parsePyPIList(@Nonnull List<String> packages, @Nonnull PyPackageService service)
	{
		myPackageNames = null;

		List<String> toWritePackages = new ArrayList<>(packages.size());
		for(String pyPackage : packages)
		{
			try
			{
				final String packageName = URLDecoder.decode(pyPackage, "UTF-8");
				if(!packageName.isBlank())
				{
					toWritePackages.add(packageName);
				}
			}
			catch(UnsupportedEncodingException e)
			{
				LOG.warn(e);
			}
		}

		PypiPackageCache.getInstance().updateCache(toWritePackages);
	}

	@Nonnull
	private static List<String> parsePyPIListFromWeb(@Nonnull String url, boolean isSimpleIndex) throws IOException
	{
		LOG.debug("Fetching index of all packages available on " + url);
		return HttpRequests.request(url).userAgent(getUserAgent()).connect(request -> {
			final List<String> packages = new ArrayList<>();
			final Reader reader = request.getReader();
			new ParserDelegator().parse(reader, new HTMLEditorKit.ParserCallback()
			{
				boolean inTable = false;
				HTML.Tag myTag;

				@Override
				public void handleStartTag(@Nonnull HTML.Tag tag, @Nonnull MutableAttributeSet set, int i)
				{
					myTag = tag;
					if(!isSimpleIndex)
					{
						if("table".equals(tag.toString()))
						{
							inTable = !inTable;
						}

						if(inTable && "a".equals(tag.toString()))
						{
							packages.add(String.valueOf(set.getAttribute(HTML.Attribute.HREF)));
						}
					}
				}

				@Override
				public void handleText(@Nonnull char[] data, int pos)
				{
					if(isSimpleIndex)
					{
						if(myTag != null && "a".equals(myTag.toString()))
						{
							packages.add(String.valueOf(data));
						}
					}
				}

				@Override
				public void handleEndTag(@Nonnull HTML.Tag tag, int i)
				{
					if(!isSimpleIndex)
					{
						if("table".equals(tag.toString()))
						{
							inTable = !inTable;
						}
					}
				}
			}, true);
			return packages;
		});
	}

	@Nonnull
	public Collection<String> getPackageNames()
	{
		final Map<String, String> pyPIPackages = getPyPIPackages();
		final ArrayList<String> list = Lists.newArrayList(pyPIPackages.keySet());
		Collections.sort(list);
		return list;
	}

	@Nonnull
	public Map<String, String> loadAndGetPackages() throws IOException
	{
		Map<String, String> pyPIPackages = getPyPIPackages();
		synchronized(myPyPIPackageCacheUpdateLock)
		{
			if(pyPIPackages.isEmpty())
			{
				updatePyPICache(PyPackageService.getInstance());
				pyPIPackages = getPyPIPackages();
			}
		}
		return pyPIPackages;
	}

	@Nonnull
	public static Map<String, String> getPyPIPackages()
	{
		List<String> cache = PypiPackageCache.getInstance().getCache();
		return cache.stream().collect(Collectors.toMap(s -> s, s -> ""));
	}

	public boolean isInPyPI(@Nonnull String packageName)
	{
		if(myPackageNames == null)
		{
			myPackageNames = getPyPIPackages().keySet().stream().map(name -> name.toLowerCase(Locale.ENGLISH)).collect(Collectors.toSet());
		}
		return myPackageNames != null && myPackageNames.contains(packageName.toLowerCase(Locale.ENGLISH));
	}

	@SuppressWarnings("FieldMayBeFinal")
	public static final class PackageDetails
	{
		public static final class Info
		{
			// We have to explicitly name each of the fields instead of just using
			// GsonBuilder#setFieldNamingStrategy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES),
			// since otherwise GSON wouldn't be able to deserialize server responses
			// in the professional edition of PyCharm where the names of private fields
			// are obfuscated.
			@SerializedName("version")
			private String version = "";
			@SerializedName("author")
			private String author = "";
			@SerializedName("author_email")
			private String authorEmail = "";
			@SerializedName("home_page")
			private String homePage = "";
			@SerializedName("summary")
			private String summary = "";


			@Nonnull
			public String getVersion()
			{
				return version;
			}

			@Nonnull
			public String getAuthor()
			{
				return author;
			}

			@Nonnull
			public String getAuthorEmail()
			{
				return authorEmail;
			}

			@Nonnull
			public String getHomePage()
			{
				return homePage;
			}

			@Nonnull
			public String getSummary()
			{
				return summary;
			}
		}

		@SerializedName("info")
		private Info info = new Info();
		@SerializedName("releases")
		private Map<String, Object> releases = Collections.emptyMap();

		@Nonnull
		public Info getInfo()
		{
			return info;
		}

		@Nonnull
		public List<String> getReleases()
		{
			return new ArrayList<>(releases.keySet());
		}
	}
}
