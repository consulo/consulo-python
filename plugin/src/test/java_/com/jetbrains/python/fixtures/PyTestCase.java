package com.jetbrains.python.fixtures;

import consulo.module.Module;
import consulo.content.bundle.Sdk;
import consulo.module.content.layer.ContentEntry;
import consulo.module.content.layer.ModifiableRootModel;
import consulo.content.OrderRootType;
import consulo.content.library.Library;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiReference;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.TestModuleDescriptor;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import consulo.ide.impl.idea.util.Consumer;
import com.jetbrains.python.impl.PythonHelpersLocator;
import com.jetbrains.python.PythonMockSdk;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.impl.psi.impl.PyFileImpl;
import com.jetbrains.python.impl.psi.impl.PythonLanguageLevelPusher;
import consulo.container.boot.ContainerPathManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;

/**
 * @author yole
 */
@TestDataPath("$CONTENT_ROOT/../../testData/")
public abstract class PyTestCase extends UsefulTestCase
{
	public static String PYTHON_2_MOCK_SDK = "2.7";
	public static String PYTHON_3_MOCK_SDK = "3.2";

	private static final PyLightProjectDescriptor ourPyDescriptor = new PyLightProjectDescriptor(PYTHON_2_MOCK_SDK);
	protected static final PyLightProjectDescriptor ourPy3Descriptor = new PyLightProjectDescriptor(PYTHON_3_MOCK_SDK);
	private static final String PARSED_ERROR_MSG = "Operations should have been performed on stubs but caused file to be parsed";

	protected CodeInsightTestFixture myFixture;

	@Nullable
	protected static VirtualFile getVirtualFileByName(String fileName)
	{
		return LocalFileSystem.getInstance().findFileByPath(fileName.replace(File.separatorChar, '/'));
	}

	@Override
	protected void setUp() throws Exception
	{
		super.setUp();
		initPlatformPrefix();
		IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
		TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder = factory.createLightFixtureBuilder(getProjectDescriptor());
		final IdeaProjectTestFixture fixture = fixtureBuilder.getFixture();
		myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixture, new LightTempDirTestFixtureImpl(true));
		myFixture.setUp();

		myFixture.setTestDataPath(getTestDataPath());
	}

	protected String getTestDataPath()
	{
		return PythonTestUtil.getTestDataPath();
	}

	@Override
	protected void tearDown() throws Exception
	{
		setLanguageLevel(null);
		myFixture.tearDown();
		myFixture = null;
		super.tearDown();
	}

	@Nullable
	protected TestModuleDescriptor getProjectDescriptor()
	{
		return ourPyDescriptor;
	}

	protected PsiReference findReferenceBySignature(final String signature)
	{
		int pos = findPosBySignature(signature);
		return findReferenceAt(pos);
	}

	protected PsiReference findReferenceAt(int pos)
	{
		return myFixture.getFile().findReferenceAt(pos);
	}

	protected int findPosBySignature(String signature)
	{
		return PsiDocumentManager.getInstance(myFixture.getProject()).getDocument(myFixture.getFile()).getText().indexOf(signature);
	}

	protected void setLanguageLevel(@Nullable LanguageLevel languageLevel)
	{
		PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), languageLevel);
	}

	protected void runWithLanguageLevel(@Nonnull LanguageLevel languageLevel, @Nonnull Runnable action)
	{
		setLanguageLevel(languageLevel);
		try
		{
			action.run();
		}
		finally
		{
			setLanguageLevel(null);
		}
	}

	protected static void assertNotParsed(PyFile file)
	{
		assertNull(PARSED_ERROR_MSG, ((PyFileImpl) file).getTreeElement());
	}

	protected static class PyLightProjectDescriptor implements TestModuleDescriptor
	{
		private final String myPythonVersion;

		public PyLightProjectDescriptor(String pythonVersion)
		{
			myPythonVersion = pythonVersion;
		}

		@Override
		public void configureSdk(@Nonnull Consumer<Sdk> consumer)
		{
			consumer.consume(PythonMockSdk.findOrCreate(myPythonVersion));
		}

		@Override
		public void configureModule(Module module, ModifiableRootModel model, ContentEntry contentEntry)
		{
		}

		protected void createLibrary(ModifiableRootModel model, final String name, final String path)
		{
			final Library.ModifiableModel modifiableModel = model.getModuleLibraryTable().createLibrary(name).getModifiableModel();
			final VirtualFile home = LocalFileSystem.getInstance().refreshAndFindFileByPath(ContainerPathManager.get().getHomePath() + path);

			modifiableModel.addRoot(home, OrderRootType.CLASSES);
			modifiableModel.commit();
		}
	}

	public static void initPlatformPrefix()
	{
	}

	public static String getHelpersPath()
	{
		return new File(PythonHelpersLocator.getPythonCommunityPath(), "helpers").getPath();
	}
}
