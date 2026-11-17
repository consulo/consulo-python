// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.sdk;

import consulo.module.Module;
import consulo.project.Project;

/**
 * Lots of actions might be run on the project level or module level.
 * [project] is always exist (though it could be a default project), module is optional.
 * If a module exists, a project is always a module's project and never default.
 * <p>
 * This class must be used instead of separate [Project], `Module` fields as it guarantees that project is module's project and never aother.
 * <p>
 * So, instead of
 * <pre>{@code
 * fun foo(module:Module?, project:Project) {
 *   if (module != null) {
 *   //what if module.project != project??
 *   }
 * }
 * }</pre>
 * use
 * <pre>{@code
 * foo (moduleOrProject:ModuleOrProject) {
 *    moduleOrProject.project // is always correct
 *    when(moduleOrProject) {
 *     is ProjectOnly -> //no module
 *     is ProjectAndModule -> moduleOrProject.module
 *    }
 * }
 * }</pre>
 */
public abstract sealed class ModuleOrProject permits ModuleOrProject.ProjectOnly, ModuleOrProject.ModuleAndProject {
  private final Project project;

  private ModuleOrProject(Project project) {
    this.project = project;
  }

  public Project getProject() {
    return project;
  }

  public static final class ProjectOnly extends ModuleOrProject {
    public ProjectOnly(Project project) {
      super(project);
    }
  }

  /**
   * [module] always exists.
   */
  public static final class ModuleAndProject extends ModuleOrProject {
    private final Module module;

    // TODO consulo: com.jetbrains.python.project.PyProject missing
    public ModuleAndProject(Module module) {
      super(module.getProject());
      this.module = module;
    }

    public Module getModule() {
      return module;
    }
  }
}
