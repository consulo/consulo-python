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

import org.jspecify.annotations.Nullable;
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
				public List<String> load(String key) throws Exception
				{
					LOG.debug("Searching for versions of package '" + key + "' in additional repositories");
					List<String> repositories = PyPackageService.getInstance().additionalRepositories;
					for(String repository : repositories)
					{
						List<String> versions = parsePackageVersionsFromArchives(composeSimpleUrl(key, repository));
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
				public PackageDetails load(String key) throws Exception
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
	private static String getUserAgent()
	{
		Application application = Application.get();
		return application.getName() + "/" + application.getVersion();
	}

	private static ImmutableMap<String, String> loadPackageAliases()
	{
		ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
		try (FileReader reader = new FileReader(PythonHelpersLocator.getHelperPath("/tools/packages")))
		{
			String text = FileUtil.loadTextAndClose(reader);
			List<String> lines = StringUtil.split(text, "\n");
			for(String line : lines)
			{
				List<String> split = StringUtil.split(line, " ");
				builder.put(split.get(0), split.get(1));
			}
		}
		catch(IOException e)
		{
			LOG.error("Cannot find \"packages\". " + e.getMessage());
		}
		return builder.build();
	}

	private static Pair<String, String> splitNameVersion(String pyPackage)
	{
		int dashInd = pyPackage.lastIndexOf("-");
		if(dashInd >= 0 && dashInd + 1 < pyPackage.length())
		{
			String name = pyPackage.substring(0, dashInd);
			String version = pyPackage.substring(dashInd + 1);
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

	public Set<RepoPackage> getAdditionalPackages() throws IOException
	{
		if(myAdditionalPackages == null)
		{
			Set<RepoPackage> packages = new TreeSet<>();
			for(String url : PyPackageService.getInstance().additionalRepositories)
			{
				packages.addAll(getPackagesFromAdditionalRepository(url));
			}
			myAdditionalPackages = packages;
		}
		return Collections.unmodifiableSet(myAdditionalPackages);
	}

	private static List<RepoPackage> getPackagesFromAdditionalRepository(String url) throws IOException
	{
		List<RepoPackage> result = new ArrayList<>();
		boolean simpleIndex = url.endsWith("simple/");
		List<String> packagesList = parsePyPIListFromWeb(url, simpleIndex);

		for(String pyPackage : packagesList)
		{
			if(simpleIndex)
			{
				Pair<String, String> nameVersion = splitNameVersion(StringUtil.trimTrailing(pyPackage, '/'));
				result.add(new RepoPackage(nameVersion.getFirst(), url, nameVersion.getSecond()));
			}
			else
			{
				try
				{
					Pattern repositoryPattern = Pattern.compile(url + "([^/]*)/([^/]*)$");
					Matcher matcher = repositoryPattern.matcher(URLDecoder.decode(pyPackage, "UTF-8"));
					if(matcher.find())
					{
						String packageName = matcher.group(1);
						String packageVersion = matcher.group(2);
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

	public <T> AsyncResult<T> fillPackageDetails(String packageName, Function<PackageDetails.Info, T> converter)
	{
		AsyncResult<T> result = AsyncResult.undefined();
		ApplicationManager.getApplication().executeOnPooledThread(() -> {
			try
			{
				PackageDetails packageDetails = refreshAndGetPackageDetailsFromPyPI(packageName, false);
				result.setDone(converter.apply(packageDetails.getInfo()));
			}
			catch(IOException e)
			{
				result.rejectWithThrowable(e);
			}
		});
		return result;
	}

	private PackageDetails refreshAndGetPackageDetailsFromPyPI(String packageName, boolean alwaysRefresh) throws IOException
	{
		if(alwaysRefresh)
		{
			myPackageToDetails.invalidate(packageName);
		}
		return getCachedValueOrRethrowIO(myPackageToDetails, packageName);
	}

	public AsyncResult<List<String>> usePackageReleases(String packageName)
	{
		AsyncResult<List<String>> result = AsyncResult.undefined();
		ApplicationManager.getApplication().executeOnPooledThread(() -> {
			try
			{
				List<String> releasesFromSimpleIndex = getPackageVersionsFromAdditionalRepositories(packageName);
				if(releasesFromSimpleIndex.isEmpty())
				{
					List<String> releasesFromPyPI = getPackageVersionsFromPyPI(packageName, true);
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
	private List<String> getPackageVersionsFromPyPI(String packageName, boolean force) throws IOException
	{
		PackageDetails details = refreshAndGetPackageDetailsFromPyPI(packageName, force);
		List<String> result = details.getReleases();
		result.sort(PackageVersionComparator.VERSION_COMPARATOR.reversed());
		return Collections.unmodifiableList(result);
	}

	@Nullable
	private String getLatestPackageVersionFromPyPI(String packageName) throws IOException
	{
		LOG.debug("Requesting the latest PyPI version for the package " + packageName);
		List<String> versions = getPackageVersionsFromPyPI(packageName, true);
		String latest = ContainerUtil.getFirstItem(versions);
		getPyPIPackages().put(packageName, StringUtil.notNullize(latest));
		return latest;
	}

	/**
	 * Fetches available package versions by scrapping the page containing package archives.
	 * It's primarily used for additional repositories since, e.g. devpi doesn't provide another way to get this information.
	 */
	private List<String> getPackageVersionsFromAdditionalRepositories(String packageName) throws IOException
	{
		return getCachedValueOrRethrowIO(myAdditionalPackagesReleases, packageName);
	}

	private static <T> T getCachedValueOrRethrowIO(LoadingCache<String, ? extends T> cache, String key) throws IOException
	{
		try
		{
			return cache.get(key);
		}
		catch(ExecutionException e)
		{
			Throwable cause = e.getCause();
			throw (cause instanceof IOException ? (IOException) cause : new IOException("Unexpected non-IO error", cause));
		}
	}

	@Nullable
	private String getLatestPackageVersionFromAdditionalRepositories(String packageName) throws IOException
	{
		List<String> versions = getPackageVersionsFromAdditionalRepositories(packageName);
		return ContainerUtil.getFirstItem(versions);
	}

	@Nullable
	public String fetchLatestPackageVersion(String packageName) throws IOException
	{
		String version = getPyPIPackages().get(packageName);
		// Package is on PyPI but it's version is unknown
		if(version != null && version.isEmpty())
		{
			version = getLatestPackageVersionFromPyPI(packageName);
		}
		if(!PyPackageService.getInstance().additionalRepositories.isEmpty())
		{
			String extraVersion = getLatestPackageVersionFromAdditionalRepositories(packageName);
			if(extraVersion != null)
			{
				version = extraVersion;
			}
		}
		return version;
	}

	private static List<String> parsePackageVersionsFromArchives(String archivesUrl) throws IOException
	{
		return HttpRequests.request(archivesUrl).userAgent(getUserAgent()).connect(request -> {
			final List<String> versions = new ArrayList<>();
			Reader reader = request.getReader();
			new ParserDelegator().parse(reader, new HTMLEditorKit.ParserCallback()
			{
				HTML.Tag myTag;

				@Override
				public void handleStartTag(HTML.Tag tag, MutableAttributeSet set, int i)
				{
					myTag = tag;
				}

				@Override
				public void handleText(char[] data, int pos)
				{
					if(myTag != null && "a".equals(myTag.toString()))
					{
						String packageVersion = String.valueOf(data);
						String suffix = ".tar.gz";
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

	private static String composeSimpleUrl(String packageName, String rep)
	{
		String suffix = "";
		String repository = StringUtil.trimEnd(rep, "/");
		if(!repository.endsWith("+simple") && !repository.endsWith("/simple"))
		{
			suffix = "/+simple";
		}
		suffix += "/" + packageName;
		return repository + suffix;
	}

	public void updatePyPICache(PyPackageService service) throws IOException
	{
		service.LAST_TIME_CHECKED = System.currentTimeMillis();

		PypiPackageCache.getInstance().dropCache();

		if(service.PYPI_REMOVED)
		{
			return;
		}

		parsePyPIList(parsePyPIListFromWeb(PYPI_LIST_URL, true), service);
	}

	private void parsePyPIList(List<String> packages, PyPackageService service)
	{
		myPackageNames = null;

		List<String> toWritePackages = new ArrayList<>(packages.size());
		for(String pyPackage : packages)
		{
			try
			{
				String packageName = URLDecoder.decode(pyPackage, "UTF-8");
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

	private static List<String> parsePyPIListFromWeb(String url, boolean isSimpleIndex) throws IOException
	{
		LOG.debug("Fetching index of all packages available on " + url);
		return HttpRequests.request(url).userAgent(getUserAgent()).connect(request -> {
			final List<String> packages = new ArrayList<>();
			Reader reader = request.getReader();
			new ParserDelegator().parse(reader, new HTMLEditorKit.ParserCallback()
			{
				boolean inTable = false;
				HTML.Tag myTag;

				@Override
				public void handleStartTag(HTML.Tag tag, MutableAttributeSet set, int i)
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
				public void handleText(char[] data, int pos)
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
				public void handleEndTag(HTML.Tag tag, int i)
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

	public Collection<String> getPackageNames()
	{
		Map<String, String> pyPIPackages = getPyPIPackages();
		ArrayList<String> list = Lists.newArrayList(pyPIPackages.keySet());
		Collections.sort(list);
		return list;
	}

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

	public static Map<String, String> getPyPIPackages()
	{
		List<String> cache = PypiPackageCache.getInstance().getCache();
		return cache.stream().collect(Collectors.toMap(s -> s, s -> ""));
	}

	public boolean isInPyPI(String packageName)
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


			public String getVersion()
			{
				return version;
			}

			public String getAuthor()
			{
				return author;
			}

			public String getAuthorEmail()
			{
				return authorEmail;
			}

			public String getHomePage()
			{
				return homePage;
			}

			public String getSummary()
			{
				return summary;
			}
		}

		@SerializedName("info")
		private Info info = new Info();
		@SerializedName("releases")
		private Map<String, Object> releases = Collections.emptyMap();

		public Info getInfo()
		{
			return info;
		}

		public List<String> getReleases()
		{
			return new ArrayList<>(releases.keySet());
		}
	}
}
