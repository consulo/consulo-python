// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.resolve;

import java.util.List;
import java.util.stream.Stream;

public final class IPythonBuiltinConstants {
  private IPythonBuiltinConstants() {
  }

  public static final String DISPLAY = "display";
  public static final String GET_IPYTHON = "get_ipython";
  public static final String IN = "In";
  public static final String OUT = "Out";

  public static final String OUT_HIST_DICT = "output_hist";
  public static final String IN_HIST_DICT = "input_hist_parsed";

  public static final String DISPLAY_DOTTED_PATH_OLD = "IPython.core.display.display";
  public static final String DISPLAY_DOTTED_PATH_NEW = "IPython.core.display_functions.display";
  public static final String GET_IPYTHON_DOTTED_PATH = "IPython.core.getipython.get_ipython";
  public static final String HISTORY_MANAGER_DOTTED_PATH = "IPython.core.history.HistoryManager";

  public static final String DOUBLE_UNDERSCORE = "__";
  public static final String TRIPLE_UNDERSCORE = "___";

  // MAGICS
  private static final String AUTOCALL_MAGIC = "autocall";
  private static final String AUTOMAGIC_MAGIC = "automagic";
  public static final List<String> AUTOMAGIC_MAGICS = List.of(AUTOCALL_MAGIC, AUTOMAGIC_MAGIC);

  private static final String ALIAS_MAGIC_MAGIC = "alias_magic";
  private static final String COLORS_MAGIC = "colors";
  private static final String DOCTEST_MODE_MAGIC = "doctest_mode";
  private static final String GUI_MAGIC = "gui";
  private static final String LSMAGIC_MAGIC = "lsmagic";
  private static final String MAGIC_MAGIC = "magic";
  private static final String NOTEBOOK_MAGIC = "notebook";
  private static final String PAGE_MAGIC = "page";
  private static final String PPRINT_MAGIC = "pprint";
  private static final String PRECISION_MAGIC = "precision";
  private static final String QUICKREF_MAGIC = "quickref";
  private static final String XMODE_MAGIC = "xmode";
  public static final List<String> BASIC_MAGICS = List.of(ALIAS_MAGIC_MAGIC, COLORS_MAGIC, DOCTEST_MODE_MAGIC, GUI_MAGIC,
                            LSMAGIC_MAGIC, MAGIC_MAGIC, NOTEBOOK_MAGIC, PAGE_MAGIC,
                            PPRINT_MAGIC, PRECISION_MAGIC, QUICKREF_MAGIC, XMODE_MAGIC);


  private static final String EDIT_MAGIC = "edit";
  private static final String LOADPY_MAGIC = "loadpy";
  private static final String LOAD_MAGIC = "load";
  private static final String PASTEBIN_MAGIC = "pastebin";
  private static final String SAVE_MAGIC = "save";
  public static final List<String> CODE_MAGICS = List.of(EDIT_MAGIC, LOADPY_MAGIC, LOAD_MAGIC, PASTEBIN_MAGIC, SAVE_MAGIC);

  private static final String CONFIG_MAGIC = "config";
  public static final List<String> CONFIG_MAGICS = List.of(CONFIG_MAGIC);

  private static final String DEBUG_MAGIC = "debug";
  private static final String MACRO_MAGIC = "macro";
  private static final String PDB_MAGIC = "pdb";
  private static final String PRUN_MAGIC = "prun";
  private static final String RUN_MAGIC = "run";
  private static final String TB_MAGIC = "tb";
  private static final String TIMEIT_MAGIC = "timeit";
  private static final String TIME_MAGIC = "time";
  public static final List<String> EXECUTION_MAGICS = List.of(DEBUG_MAGIC, MACRO_MAGIC, PDB_MAGIC, PRUN_MAGIC,
                                RUN_MAGIC, TB_MAGIC, TIMEIT_MAGIC, TIME_MAGIC);

  private static final String LOAD_EXT_MAGIC = "load_ext";
  private static final String RELOAD_EXT_MAGIC = "reload_ext";
  private static final String UNLOAD_EXT_MAGIC = "unload_ext";
  public static final List<String> EXTENSION_MAGICS = List.of(LOAD_EXT_MAGIC, RELOAD_EXT_MAGIC, UNLOAD_EXT_MAGIC);

  private static final String HISTORY_MAGIC = "history";
  private static final String RECALL_MAGIC = "recall";
  private static final String RERUN_MAGIC = "rerun";
  public static final List<String> HISTORY_MAGICS = List.of(HISTORY_MAGIC, RECALL_MAGIC, RERUN_MAGIC);

  private static final String LOGOFF_MAGIC = "logoff";
  private static final String LOGON_MAGIC = "logon";
  private static final String LOGSTART_MAGIC = "logstart";
  private static final String LOGSTATE_MAGIC = "logstate";
  private static final String LOGSTOP_MAGIC = "logstop";
  public static final List<String> LOGGING_MAGICS = List.of(LOGOFF_MAGIC, LOGON_MAGIC, LOGSTART_MAGIC, LOGSTATE_MAGIC, LOGSTOP_MAGIC);

  private static final String PDEF_MAGIC = "pdef";
  private static final String PDOC_MAGIC = "pdoc";
  private static final String PFILE_MAGIC = "pfile";
  private static final String PINFO2_MAGIC = "pinfo2";
  private static final String PINFO_MAGIC = "pinfo";
  private static final String PSEARCH_MAGIC = "psearch";
  private static final String PSOURCE_MAGIC = "psource";
  private static final String RESET_MAGIC = "reset";
  private static final String RESET_SELECTIVE_MAGIC = "reset_selective";
  private static final String WHOS_MAGIC = "whos";
  private static final String WHO_LS_MAGIC = "who_ls";
  private static final String WHO_MAGIC = "who";
  private static final String XDEL_MAGIC = "xdel";
  public static final List<String> NAMESPACE_MAGICS = List.of(PDEF_MAGIC, PDOC_MAGIC, PFILE_MAGIC, PINFO2_MAGIC,
                                PINFO_MAGIC, PSEARCH_MAGIC, PSOURCE_MAGIC, RESET_MAGIC,
                                RESET_SELECTIVE_MAGIC, WHOS_MAGIC, WHO_LS_MAGIC, WHO_MAGIC,
                                XDEL_MAGIC);

  private static final String ALIAS_MAGIC = "alias";
  private static final String BOOKMARK_MAGIC = "bookmark";
  private static final String CD_MAGIC = "cd";
  private static final String DHIST_MAGIC = "dhist";
  private static final String DIRS_MAGIC = "dirs";
  private static final String ENV_MAGIC = "env";
  private static final String POPD_MAGIC = "popd";
  private static final String PUSHD_MAGIC = "pushd";
  private static final String PWD_MAGIC = "pwd";
  private static final String PYCAT_MAGIC = "pycat";
  private static final String REGASHX_MAGIC = "rehashx";
  private static final String SC_MAGIC = "sc";
  private static final String SET_ENV_MAGIC = "set_env";
  private static final String SX_MAGIC = "sx";
  private static final String UNALIAS_MAGIC = "unalias";
  public static final List<String> OS_MAGICS = List.of(ALIAS_MAGIC, BOOKMARK_MAGIC, CD_MAGIC, DHIST_MAGIC,
                         DIRS_MAGIC, ENV_MAGIC, POPD_MAGIC, PUSHD_MAGIC,
                         PWD_MAGIC, PYCAT_MAGIC, REGASHX_MAGIC, SC_MAGIC,
                         SET_ENV_MAGIC, SX_MAGIC, UNALIAS_MAGIC);

  private static final String CONDA_MAGIC = "conda";
  private static final String PIP_MAGIC = "pip";
  public static final List<String> PACKAGING_MAGICS = List.of(CONDA_MAGIC, PIP_MAGIC);

  private static final String KILLBGSCRIPTS_MAGIC = "killbgscripts";
  private static final String MATPLOTLIB_MAGIC = "matplotlib";
  private static final String PYLAB_MAGIC = "pylab";
  public static final List<String> PYLAB_MAGICS = List.of(KILLBGSCRIPTS_MAGIC, MATPLOTLIB_MAGIC, PYLAB_MAGIC);

  private static final String AUTOAWAITS_MAGIC = "autoawait";
  public static final List<String> ASYNC_MAGICS = List.of(AUTOAWAITS_MAGIC);

  public static final List<String> MAGICS_LIST = Stream.of(AUTOMAGIC_MAGICS, BASIC_MAGICS, CODE_MAGICS, CONFIG_MAGICS,
                           EXECUTION_MAGICS, EXTENSION_MAGICS, HISTORY_MAGICS, LOGGING_MAGICS,
                           NAMESPACE_MAGICS, OS_MAGICS, PACKAGING_MAGICS, PYLAB_MAGICS,
                           ASYNC_MAGICS).flatMap(List::stream).toList();

  public static final String MAGIC_AUTOMAGIC_DOTTED_PATH = "IPython.core.magics.auto.AutoMagics";
  public static final String MAGIC_BASIC_DOTTED_PATH = "IPython.core.magics.basic.BasicMagics";
  public static final String MAGIC_CODE_DOTTED_PATH = "IPython.core.magics.code.CodeMagics";
  public static final String MAGIC_CONFIG_DOTTED_PATH = "IPython.core.magics.config.ConfigMagics";
  public static final String MAGIC_EXECUTION_DOTTED_PATH = "IPython.core.magics.execution.ExecutionMagics";
  public static final String MAGIC_EXTENSION_DOTTED_PATH = "IPython.core.magics.extension.ExtensionMagics";
  public static final String MAGIC_HISTORY_DOTTED_PATH = "IPython.core.magics.history.HistoryMagics";
  public static final String MAGIC_LOGGING_DOTTED_PATH = "IPython.core.magics.logging.LoggingMagics";
  public static final String MAGIC_NAMESPACE_DOTTED_PATH = "IPython.core.magics.namespace.NamespaceMagics";
  public static final String MAGIC_OS_DOTTED_PATH = "IPython.core.magics.osm.OSMagics";
  public static final String MAGIC_PACKAGING_DOTTED_PATH = "IPython.core.magics.packaging.PackagingMagics";
  public static final String MAGIC_PYLAB_DOTTED_PATH = "IPython.core.magics.pylab.PylabMagics";
  public static final String MAGIC_ASYNC_DOTTED_PATH = "IPython.core.magics.basic.AsyncMagics";
}
