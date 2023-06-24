package com.jetbrains.python.impl.packaging.pip;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.container.boot.ContainerPathManager;
import consulo.logging.Logger;
import consulo.ui.UIAccess;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * @author VISTALL
 * @since 24/06/2023
 */
@Singleton
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
public class PypiPackageCache
{
	private static final Logger LOG = Logger.getInstance(PypiPackageCache.class);

	private List<String> cache;

	public static PypiPackageCache getInstance()
	{
		return Application.get().getInstance(PypiPackageCache.class);
	}

	public Path getFilePath()
	{
		Path systemDir = ContainerPathManager.get().getSystemDir();
		return systemDir.resolve("python_packages").resolve("packages_v1.txt");
	}

	private List<String> readCache()
	{
		UIAccess.assetIsNotUIThread();

		Path filePath = getFilePath();

		if(Files.exists(filePath))
		{
			try
			{
				return Files.readAllLines(filePath, StandardCharsets.UTF_8);
			}
			catch(IOException e)
			{
				LOG.warn(e);
			}
		}

		return List.of();
	}

	public void dropCache()
	{
		cache = null;
		try
		{
			Path filePath = getFilePath();
			Files.deleteIfExists(filePath);
		}
		catch(IOException e)
		{
			LOG.warn(e);
		}
	}

	public void updateCache(List<String> packages)
	{
		this.cache = packages;
		try
		{
			Path targetFile = getFilePath();
			Files.createDirectories(targetFile.getParent());
			Files.write(targetFile, packages, StandardCharsets.UTF_8);
		}
		catch(IOException e)
		{
			LOG.warn(e);
		}
	}

	public List<String> getCache()
	{
		if(cache == null)
		{
			cache = readCache();
		}

		return cache;
	}
}
