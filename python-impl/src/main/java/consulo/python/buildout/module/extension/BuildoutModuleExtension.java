/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package consulo.python.buildout.module.extension;

import com.jetbrains.python.impl.PythonHelpersLocator;
import com.jetbrains.python.impl.facet.FacetLibraryConfigurator;
import com.jetbrains.python.impl.facet.LibraryContributingFacet;
import com.jetbrains.python.impl.facet.PythonPathContributingFacet;
import com.jetbrains.python.impl.run.PythonCommandLineState;
import com.jetbrains.python.impl.sdk.PythonEnvUtil;
import consulo.application.util.LineTokenizer;
import consulo.application.util.SystemInfo;
import consulo.ide.impl.idea.openapi.vfs.VfsUtil;
import consulo.language.util.ModuleUtilCore;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.module.content.layer.ModuleRootLayer;
import consulo.module.content.layer.extension.ModuleExtensionBase;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.cmd.ParametersList;
import consulo.process.cmd.ParamsGroup;
import consulo.project.Project;
import consulo.project.ProjectManager;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author VISTALL
 * @since 20.10.13.
 */
public class BuildoutModuleExtension extends ModuleExtensionBase<BuildoutModuleExtension> implements PythonPathContributingFacet, LibraryContributingFacet {
  private static final Logger LOGGER = Logger.getInstance(BuildoutModuleExtension.class);

  @Nonnull
  public static List<VirtualFile> getExtraPathForAllOpenModules() {
    List<VirtualFile> results = new ArrayList<>();
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      for (Module module : ModuleManager.getInstance(project).getModules()) {
        BuildoutModuleExtension buildoutFacet = ModuleUtilCore.getExtension(module, BuildoutModuleExtension.class);
        if (buildoutFacet != null) {
          List<String> paths = buildoutFacet.getPaths();
          if (paths != null) {
            for (String path : paths) {
              VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
              if (file != null) {
                results.add(file);
              }
            }
          }
        }
      }
    }
    return results;
  }

  @NonNls
  public static final String BUILDOUT_CFG = "buildout.cfg";
  @NonNls
  public static final String SCRIPT_SUFFIX = "-script";
  private static final String BUILDOUT_LIB_NAME = "Buildout Eggs";
  protected String myScriptName;
  protected List<String> myPaths;

  public BuildoutModuleExtension(@Nonnull String id, @Nonnull ModuleRootLayer module) {
    super(id, module);
  }

  public static List<File> getScripts(@Nullable BuildoutModuleExtension buildoutFacet, VirtualFile baseDir) {
    File rootPath = null;
    if (buildoutFacet != null) {
      File configIOFile = buildoutFacet.getConfigFile();
      if (configIOFile != null) {
        rootPath = configIOFile.getParentFile();
      }
    }
    if (rootPath == null || !rootPath.exists()) {
      if (baseDir != null) {
        rootPath = new File(baseDir.getPath());
      }
    }
    if (rootPath != null) {
      File[] scripts = new File(rootPath, "bin").listFiles(new FilenameFilter() {
        @Override
        public boolean accept(File dir, String name) {
          if (SystemInfo.isWindows) {
            return name.endsWith("-script.py");
          }
          String ext = FileUtil.getExtension(name);
          return ext.length() == 0 || FileUtil.namesEqual(ext, "py");
        }
      });
      if (scripts != null) {
        return Arrays.asList(scripts);
      }
    }
    return Collections.emptyList();
  }

  @Nullable
  public static List<String> extractBuildoutPaths(@Nonnull VirtualFile script) {
    try {
      List<String> paths = extractFromScript(script);
      if (paths == null) {
        VirtualFile root = script.getParent().getParent();
        String partName = FileUtil.getNameWithoutExtension(script.getName());
        if (SystemInfo.isWindows && partName.endsWith(SCRIPT_SUFFIX)) {
          partName = partName.substring(0, partName.length() - SCRIPT_SUFFIX.length());
        }
        VirtualFile sitePy = root.findFileByRelativePath("parts/" + partName + "/site.py");
        if (sitePy != null) {
          paths = extractFromSitePy(sitePy);
        }
      }
      return paths;
    }
    catch (IOException e) {
      LOGGER.info(e);
      return null;
    }
  }

  /**
   * Extracts paths from given script, assuming sys.path[0:0] assignment.
   *
   * @param script
   * @return extracted paths, or null if extraction fails.
   */
  @Nullable
  public static List<String> extractFromScript(@Nonnull VirtualFile script) throws IOException {
    String text = VfsUtil.loadText(script);
    Pattern pat = Pattern.compile("(?:^\\s*(['\"])(.*)(\\1),\\s*$)|(\\])", Pattern.MULTILINE);
    String bait_string = "sys.path[0:0]";
    int pos = text.indexOf(bait_string);
    List<String> ret = null;
    if (pos >= 0) {
      pos += bait_string.length();
      Matcher scanner = pat.matcher(text);
      while (scanner.find(pos)) {
        String value = scanner.group(2);
        if (value != null) {
          if (ret == null) {
            ret = new ArrayList<String>();
          }
          ret.add(value);
          pos = scanner.end();
        }
        else {
          break;
        } // we've matched the ']', it's group(4)
      }
    }
    return ret;
  }

  /**
   * Extracts paths from site.py generated by buildout 1.5+
   *
   * @param vFile path to site.py
   * @return extracted paths
   */
  public static List<String> extractFromSitePy(VirtualFile vFile) throws IOException {
    List<String> result = new ArrayList<String>();
    String text = VfsUtil.loadText(vFile);
    String[] lines = LineTokenizer.tokenize(text, false);
    int index = 0;
    while (index < lines.length && !lines[index].startsWith("def addsitepackages(")) {
      index++;
    }
    while (index < lines.length && !lines[index].trim().startsWith("buildout_paths = [")) {
      index++;
    }
    index++;
    while (index < lines.length && !lines[index].trim().equals("]")) {
      String line = lines[index].trim();
      if (line.endsWith(",")) {
        line = line.substring(0, line.length() - 1);
      }
      if (line.startsWith("'") && line.endsWith("'")) {
        result.add(StringUtil.unescapeStringCharacters(line.substring(1, line.length() - 1)));
      }
      index++;
    }
    return result;
  }

  public static void attachLibrary(Module module) {
    BuildoutModuleExtension facet = ModuleUtilCore.getExtension(module, BuildoutModuleExtension.class);
    if (facet == null) {
      return;
    }
    List<String> paths = facet.getPaths();
    FacetLibraryConfigurator.attachLibrary(module, null, BUILDOUT_LIB_NAME, paths);
  }

  public static void detachLibrary(Module module) {
    FacetLibraryConfigurator.detachLibrary(module, BUILDOUT_LIB_NAME);
  }

  @Nullable
  public File getConfigFile() {
    String scriptName = getScriptName();
    if (!StringUtil.isEmpty(scriptName)) {
      return new File(new File(scriptName).getParentFile().getParentFile(), BUILDOUT_CFG);
    }
    return null;
  }

  public void patchCommandLineForBuildout(GeneralCommandLine commandLine) {
    Map<String, String> env = commandLine.getEnvironment();
    ParametersList params = commandLine.getParametersList();
    // alter execution script
    ParamsGroup script_params = params.getParamsGroup(PythonCommandLineState.GROUP_SCRIPT);
    assert script_params != null;
    if (script_params.getParameters().size() > 0) {
      String normal_script = script_params.getParameters().get(0); // expect DjangoUtil.MANAGE_FILE
      String engulfer_path = PythonHelpersLocator.getHelperPath("pycharm/buildout_engulfer.py");
      env.put("PYCHARM_ENGULF_SCRIPT", getScriptName());
      script_params.getParametersList().replaceOrPrepend(normal_script, engulfer_path);
    }
    // add pycharm helpers to pythonpath so that fixGetpass is importable

    PythonEnvUtil.addToPythonPath(env, PythonHelpersLocator.getHelpersRoot().getAbsolutePath());
  /*
	// set prependable paths
    List<String> paths = facet.getAdditionalPythonPath();
    if (paths != null) {
      path_value = PyUtil.joinWith(File.pathSeparator, paths);
      env.put("PYCHARM_PREPEND_SYSPATH", path_value);
    }
    */
  }

  public String getScriptName() {
    return myScriptName;
  }

  /**
   * Generates a <code>sys.path[0:0] = [...]</code> with paths that buildout script wants.
   *
   * @param additionalPythonPath
   * @return the statement, or null if there's no buildout facet.
   */
  @Nullable
  public String getPathPrependStatement(List<String> additionalPythonPath) {
    StringBuilder sb = new StringBuilder("sys.path[0:0]=[");
    for (String s : additionalPythonPath) {
      sb.append("'").append(s).append("',");
      // NOTE: we assume that quotes and spaces are escaped in paths back in the buildout script we extracted them from.
    }
    sb.append("]");
    return sb.toString();
  }

  public List<String> getPaths() {
    return myPaths;
  }

  /**
   * Sets the paths to be prepended to pythonpath, taken from a buildout script.
   *
   * @param paths what to store; the list will be copied.
   */
  void setPaths(@Nullable List<String> paths) {
    if (paths != null) {
      myPaths = new ArrayList<String>(paths.size());
      for (String s : paths) {
        myPaths.add(s);
      }
    }
    else {
      myPaths = null;
    }
  }

  @Override
  public List<String> getAdditionalPythonPath() {
    return myPaths;
  }

  @Override
  public boolean acceptRootAsTopLevelPackage() {
    return false;
  }

  @Override
  public void updateLibrary() {
    updatePaths();
    attachLibrary(getModule());
  }

  @Override
  public void removeLibrary() {
    detachLibrary(getModule());
  }

  @Override
  public void commit(@Nonnull BuildoutModuleExtension mutableModuleExtension) {
    super.commit(mutableModuleExtension);
    myScriptName = mutableModuleExtension.getScriptName();
  }

  @Nullable
  public VirtualFile getScript() {
    return LocalFileSystem.getInstance().findFileByPath(getScriptName());
  }

  public void updatePaths() {
    VirtualFile script = getScript();
    if (script != null) {
      setPaths(extractBuildoutPaths(script));
    }
  }
}
