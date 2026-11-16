// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*
 * Copyright 2013-2026 consulo.io
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
package com.jetbrains.python;

import com.jetbrains.python.psi.LanguageLevel;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class PyNames {
  private PyNames() {
  }

  /**
   * Fully-qualified names of well-known Python symbols.
   *
   * Builtins are qualified as `builtins.<name>` to match `QualifiedNameFinder.getQualifiedName`,
   * which qualifies every builtin (including Python 2 `__builtin__` members) under `builtins`.
   */
  public static final class FQN {
    private FQN() {
    }

    public static final String INT = "builtins.int";
    public static final String BOOL = "builtins.bool";
    public static final String LONG = "builtins.long";
    public static final String FLOAT = "builtins.float";
    public static final String COMPLEX = "builtins.complex";
    /** `unicode` string type (Python 2). See [TYPE_STRING_TYPES]. */
    public static final String UNICODE = "builtins.unicode";
    /** `str` type. See [TYPE_STRING_TYPES]. */
    public static final String STR = "builtins.str";
    public static final String BYTES = "builtins.bytes";
    public static final String BYTEARRAY = "builtins.bytearray";
    public static final String TYPE = "builtins.type";
    public static final String OBJECT = "builtins.object";
    public static final String TUPLE = "builtins.tuple";
    public static final String LIST = "builtins.list";
    public static final String SET = "builtins.set";
    public static final String SLICE = "builtins.slice";
    public static final String DICT = "builtins.dict";
    public static final String DATE = "datetime.date";
    public static final String DATETIME = "datetime.datetime";
    public static final String TIME = "datetime.time";
    public static final String ENUM = "enum.Enum";
    public static final String ENUM_META = "enum.EnumMeta";
    public static final String ENUM_FLAG = "enum.Flag";
    public static final String ENUM_AUTO = "enum.auto";
    public static final String ENUM_MEMBER = "enum.member";
    public static final String ENUM_NONMEMBER = "enum.nonmember";
    public static final String NONE_TYPE = "_typeshed.NoneType";
    public static final List<String> NONES = List.of("types.NoneType", NONE_TYPE);
    public static final String FUNCTION_TYPE = "types.FunctionType";
    public static final String COROUTINE_TYPE = "types.CoroutineType";
    public static final String METHOD_TYPE = "types.UnboundMethodType";
    public static final String TEMPLATELIB_TEMPLATE = "string.templatelib.Template";
    public static final String ABC_META = "abc.ABCMeta";
    public static final String ABC = "abc.ABC";
    public static final String NAMEDTUPLE = "collections.namedtuple";
    public static final String COLLECTIONS_NAMEDTUPLE_PY2 = NAMEDTUPLE;
    public static final String COLLECTIONS_NAMEDTUPLE_PY3 = "collections.__init__.namedtuple";

    /**
     * If [qualifiedName] is the qualified name of a symbol defined in the builtins module
     * (for example `builtins.int`), returns its unqualified short name (`int`).
     * Otherwise returns [qualifiedName] unchanged (`null` in, `null` out).
     *
     * `QualifiedNameFinder.getQualifiedName` qualifies every builtin as `builtins.<name>` — Python 2 builtins, whose
     * runtime module is `__builtin__`, are normalized to `builtins` too — so a single prefix is enough here.
     * Use it to compare a possibly-builtin qualified name against a short builtin name such as [PyNames.TUPLE] or [PyNames.OBJECT].
     */
    public static @Nullable String unqualifyBuiltinName(@Nullable String qualifiedName) {
      if (qualifiedName == null) {
        return null;
      }
      return qualifiedName.startsWith("builtins.") ? qualifiedName.substring("builtins.".length()) : qualifiedName;
    }
  }

  public static final String SITE_PACKAGES = "site-packages";
  public static final String DIST_PACKAGES = "dist-packages";

  /**
   * int type
   */
  public static final String TYPE_INT = "int";
  public static final String TYPE_LONG = "long";

  /**
   * float type
   */
  public static final String TYPE_FLOAT = "float";

  /**
   * complex type
   */
  public static final String TYPE_COMPLEX = "complex";

  /**
   * unicode string type (see [TYPE_STRING_TYPES]
   */
  public static final String TYPE_UNICODE = "unicode";

  /**
   * string type (see [.TYPE_STRING_TYPES]
   */
  public static final String TYPE_STR = "str";

  /**
   * Any string type
   */
  public static final List<String> TYPE_STRING_TYPES = List.of(TYPE_UNICODE, TYPE_STR);

  /**
   * date type
   *
   * @deprecated Use PyNames.FQN.DATE
   */
  @Deprecated
  public static final String TYPE_DATE = "datetime.date";

  /**
   * datetime type
   *
   * @deprecated Use PyNames.FQN.DATETIME
   */
  @Deprecated
  public static final String TYPE_DATE_TIME = "datetime.datetime";

  /**
   * time type
   *
   * @deprecated Use PyNames.FQN.TIME
   */
  @Deprecated
  public static final String TYPE_TIME = "datetime.time";

  public static final String TYPE_BYTES = "bytes";

  public static final String TYPE_BYTEARRAY = "bytearray";

  /**
   * @deprecated Use PyNames.FQN.ENUM
   */
  @Deprecated
  public static final String TYPE_ENUM = "enum.Enum";
  /**
   * @deprecated Use PyNames.FQN.ENUM_META
   */
  @Deprecated
  public static final String TYPE_ENUM_META = "enum.EnumMeta";
  /**
   * @deprecated Use PyNames.FQN.ENUM_FLAG
   */
  @Deprecated
  public static final String TYPE_ENUM_FLAG = "enum.Flag";
  /**
   * @deprecated Use PyNames.FQN.ENUM_AUTO
   */
  @Deprecated
  public static final String TYPE_ENUM_AUTO = "enum.auto";
  /**
   * @deprecated Use PyNames.FQN.ENUM_MEMBER
   */
  @Deprecated
  public static final String TYPE_ENUM_MEMBER = "enum.member";
  /**
   * @deprecated Use PyNames.FQN.ENUM_NONMEMBER
   */
  @Deprecated
  public static final String TYPE_ENUM_NONMEMBER = "enum.nonmember";

  /**
   * @deprecated Use PyNames.FQN.NONE_TYPE
   */
  @Deprecated
  public static final String TYPE_NONE = "_typeshed.NoneType";
  /**
   * @deprecated Use PyNames.FQN.NONES
   */
  @Deprecated
  public static final Set<String> TYPE_NONE_NAMES = setOf("types.NoneType", FQN.NONE_TYPE);

  public static final Set<String> BUILTINS_MODULES = setOf("builtins.py", "__builtin__.py");
  public static final String PYTHON_SDK_ID_NAME = "Python SDK";
  public static final String VERBOSE_REG_EXP_LANGUAGE_ID = "PythonVerboseRegExp";

  public static final String TESTCASE_SETUP_NAME = "setUp";
  public static final String PY_DOCSTRING_ID = "Doctest";
  public static final String PY_DOCSTRING_CODE_BLOCK_ID = "PythonDocstringCodeBlock";
  public static final String END_WILDCARD = ".*";

  public static final String INIT = "__init__";
  public static final String DUNDER_DICT = "__dict__";
  public static final String DOT_PY = ".py";
  public static final String DOT_PYI = ".pyi";

  public static final String INIT_DOT_PY = INIT + DOT_PY;

  public static final String INIT_DOT_PYI = INIT + DOT_PYI;

  public static final String SETUP_DOT_PY = "setup" + DOT_PY;

  public static final String NEW = "__new__";
  public static final String GETATTR = "__getattr__";
  public static final String GETATTRIBUTE = "__getattribute__";
  public static final String DUNDER_GET = "__get__";
  public static final String DUNDER_SET = "__set__";
  public static final String __CLASS__ = "__class__";
  public static final String DUNDER_METACLASS = "__metaclass__";

  public static final String METACLASS = "metaclass";
  public static final String TYPE = "type";

  public static final String SUPER = "super";

  public static final String OBJECT = "object";

  public static final String NONE = "None";
  public static final String TRUE = "True";
  public static final String FALSE = "False";
  public static final String ELLIPSIS = "...";
  public static final String FUNCTION = "function";

  /**
   * @deprecated Use PyNames.FQN.FUNCTION_TYPE
   */
  @Deprecated
  public static final String TYPES_FUNCTION_TYPE = "types.FunctionType";
  /**
   * @deprecated Use PyNames.FQN.COROUTINE_TYPE
   */
  @Deprecated
  public static final String TYPES_COROUTINE_TYPE = "types.CoroutineType";
  /**
   * @deprecated Use PyNames.FQN.UNBOUND_METHOD_TYPE
   */
  @Deprecated
  public static final String TYPES_METHOD_TYPE = "types.UnboundMethodType";

  public static final String FUTURE_MODULE = "__future__";
  public static final String UNICODE_LITERALS = "unicode_literals";

  public static final String TEMPLATELIB_TEMPLATE = "string.templatelib.Template";

  public static final String CLASSMETHOD = "classmethod";
  public static final String STATICMETHOD = "staticmethod";
  public static final String OVERLOAD = "overload";

  public static final String OVERRIDE = "override";

  public static final String PROPERTY = "property";
  public static final String SETTER = "setter";
  public static final String DELETER = "deleter";
  public static final String GETTER = "getter";
  /**
   * @deprecated Deprecated
   */
  @Deprecated
  public static final String CACHED_PROPERTY = "cached_property";

  public static final String ALL = "__all__";
  public static final String SLOTS = "__slots__";
  public static final String DEBUG = "__debug__";

  public static final String DUNDER_REPR = "__repr__";
  public static final String DUNDER_STR = "__str__";
  public static final String DUNDER_FORMAT = "__format__";

  public static final String ISINSTANCE = "isinstance";
  public static final String ASSERT_IS_INSTANCE = "assertIsInstance";
  public static final String HAS_ATTR = "hasattr";
  public static final String ISSUBCLASS = "issubclass";

  public static final String DOC = "__doc__";
  public static final String DOCFORMAT = "__docformat__";

  public static final String DIRNAME = "dirname";
  public static final String ABSPATH = "abspath";
  public static final String NORMPATH = "normpath";
  public static final String REALPATH = "realpath";
  public static final String JOIN = "join";
  public static final String REPLACE = "replace";
  public static final String FILE = "__file__";
  public static final String PARDIR = "pardir";
  public static final String CURDIR = "curdir";

  public static final String WARN = "warn";
  public static final String DEPRECATION_WARNING = "DeprecationWarning";
  public static final String PENDING_DEPRECATION_WARNING = "PendingDeprecationWarning";

  public static final String CONTAINER = "Container";
  public static final String HASHABLE = "Hashable";
  public static final String ITERABLE = "Iterable";
  public static final String ITERATOR = "Iterator";
  public static final String SIZED = "Sized";
  public static final String CALLABLE = "Callable";
  public static final String SEQUENCE = "Sequence";
  public static final String MAPPING = "Mapping";
  public static final String MUTABLE_MAPPING = "MutableMapping";
  public static final String ABC_SET = "Set";
  public static final String ABC_MUTABLE_SET = "MutableSet";

  public static final String AWAITABLE = "Awaitable";
  public static final String ASYNC_ITERABLE = "AsyncIterable";
  public static final String ABSTRACT_CONTEXT_MANAGER = "AbstractContextManager";
  public static final String ABSTRACT_ASYNC_CONTEXT_MANAGER = "AbstractAsyncContextManager";

  public static final String ABC_NUMBER = "Number";
  public static final String ABC_COMPLEX = "Complex";
  public static final String ABC_REAL = "Real";
  public static final String ABC_RATIONAL = "Rational";
  public static final String ABC_INTEGRAL = "Integral";

  public static final String CONTAINS = "__contains__";
  public static final String HASH = "__hash__";
  public static final String HASH_FUNCTION = "hash";
  public static final String ITER = "__iter__";
  public static final String NEXT = "next";
  public static final String DUNDER_NEXT = "__next__";
  public static final String DIVMOD = "divmod";
  public static final String LEN = "__len__";
  public static final String BOOL = "__bool__";
  public static final String CALL = "__call__";
  public static final String GETITEM = "__getitem__";
  public static final String SETITEM = "__setitem__";
  public static final String DELITEM = "__delitem__";
  public static final String POS = "__pos__";
  public static final String EQ = "__eq__";
  public static final String LT = "__lt__";
  public static final String LE = "__le__";
  public static final String GT = "__gt__";
  public static final String GE = "__ge__";
  public static final String NEG = "__neg__";
  public static final String DIV = "__div__";
  public static final String TRUEDIV = "__truediv__";
  public static final String AITER = "__aiter__";
  public static final String ANEXT = "__anext__";
  public static final String AENTER = "__aenter__";
  public static final String AEXIT = "__aexit__";
  public static final String DUNDER_AWAIT = "__await__";
  public static final String SIZEOF = "__sizeof__";
  public static final String INIT_SUBCLASS = "__init_subclass__";
  public static final String COMPLEX = "__complex__";
  public static final String FLOAT = "__float__";
  public static final String INT = "__int__";
  public static final String BYTES = "__bytes__";
  public static final String ABS = "__abs__";
  public static final String ROUND = "__round__";
  public static final String CLASS_GETITEM = "__class_getitem__";
  public static final String PREPARE = "__prepare__";
  public static final String MATCH_ARGS = "__match_args__";

  public static final String NAME = "__name__";
  public static final String QUALNAME = "__qualname__";
  public static final String ANNOTATIONS = "__annotations__";
  public static final String MODULE = "__module__";
  public static final String ENTER = "__enter__";
  public static final String EXIT = "__exit__";

  public static final String CALLABLE_BUILTIN = "callable";
  public static final String NAMEDTUPLE = "namedtuple";
  public static final String COLLECTIONS = "collections";

  public static final String COLLECTIONS_NAMEDTUPLE_PY2 = COLLECTIONS + "." + NAMEDTUPLE;
  public static final String COLLECTIONS_NAMEDTUPLE_PY3 = COLLECTIONS + "." + INIT + "." + NAMEDTUPLE;

  public static final String FORMAT = "format";

  public static final String ABSTRACTMETHOD = "abstractmethod";
  public static final String ABSTRACTPROPERTY = "abstractproperty";
  public static final String ABC_META_CLASS = "ABCMeta";
  /**
   * @deprecated Moved to PyNames.FQN
   */
  @Deprecated
  public static final String ABC = "abc.ABC";
  /**
   * @deprecated Moved to PyNames.FQN
   */
  @Deprecated
  public static final String ABC_META = "abc.ABCMeta";

  public static final String TUPLE = "tuple";
  public static final String SET = "set";
  public static final String SLICE = "slice";
  public static final String DICT = "dict";

  public static final String KEYS = "keys";
  public static final String APPEND = "append";
  public static final String EXTEND = "extend";
  public static final String UPDATE = "update";
  public static final String CLEAR = "clear";
  public static final String POP = "pop";
  public static final String POPITEM = "popitem";
  public static final String SETDEFAULT = "setdefault";

  public static final String PASS = "pass";

  public static final String TEST_CASE = "TestCase";

  public static final String PYCACHE = "__pycache__";

  public static final String NOT_IMPLEMENTED_ERROR = "NotImplementedError";

  public static final String ANY_TYPE = "Any";

  public static final String UNKNOWN_TYPE = "Unknown";

  public static final String OVERLOAD_TYPE = "Overload";

  public static final String UNNAMED_ELEMENT = "<unnamed>";

  public static final String UNDERSCORE = "_";

  // Consulo: removed in JB, still referenced by Consulo code
  public static final String FAKE_OLD_BASE = "___Classobj";
  public static final String FAKE_GENERATOR = "__generator";
  public static final String FAKE_FUNCTION = "__function";
  public static final String FAKE_METHOD = "__method";
  public static final String FAKE_NAMEDTUPLE = "__namedtuple";
  public static final String FAKE_COROUTINE = "__coroutine";
  public static final String FAKE_ASYNC_GENERATOR = "__asyncgenerator";
  public static final String NOSE_TEST = "nose";
  public static final String PY_TEST = "pytest";
  public static final String AT_TEST = "Attest";

  /**
   * Contains all known predefined names of "__foo__" form.
   */
  public static final Set<String> UNDERSCORED_ATTRIBUTES = setOf(
    "__all__",
    "__annotations__",
    "__author__",
    "__bases__",
    "__closure__",
    "__code__",
    "__defaults__",
    "__dict__",
    "__dir__",
    "__doc__",
    "__docformat__",
    "__file__",
    "__func__",
    "__globals__",
    "__kwdefaults__",
    "__members__",
    "__metaclass__",
    "__mod__",
    "__module__",
    "__mro__",
    "__name__",
    "__path__",
    "__qualname__",
    "__self__",
    "__slots__",
    "__version__"
  );

  public static final Set<String> COMPARISON_OPERATORS = setOf(
    "__eq__",
    "__ne__",
    "__lt__",
    "__le__",
    "__gt__",
    "__ge__",
    "__cmp__",
    "__contains__"
  );

  public static final Set<String> SUBSCRIPTION_OPERATORS = setOf(
    GETITEM,
    SETITEM,
    DELITEM
  );

  /**
   * Binary operators that are resolved exclusively on the right-hand side operand
   * and have no left-hand side counterpart or reflected equivalent.
   */
  public static final Set<String> STANDALONE_RIGHT_OPERATORS = setOf(CONTAINS);

  /**
   * Readable source text for compound operators spread across multiple sibling tokens, keyed by their
   * concatenated token text, for operators
   * whose PSI anchor spans only their first token - e.g. `not in` is anchored on its leading `not`.
   */
  public static final Map<String, String> COMPOUND_OPERATOR_DISPLAY_TEXT = Map.of("notin", "not in");

  private static final BuiltinDescription onlySelfDescr = new BuiltinDescription("(self)");

  private static BuiltinDescription onlySelfDescr(String returnType, String... imports) {
    return new BuiltinDescription("(self) -> " + returnType, imports);
  }

  private static final BuiltinDescription selfOtherDescr = new BuiltinDescription("(self, other)");
  private static final BuiltinDescription selfItemDescr = new BuiltinDescription("(self, item)");
  private static final BuiltinDescription selfKeyDescr = new BuiltinDescription("(self, key)");
  private static final BuiltinDescription exitDescr = new BuiltinDescription("(self, exc_type: type[BaseException] | None, exc_val: BaseException | None, exc_tb: TracebackType | None)", "types.TracebackType");

  private static final Map<String, BuiltinDescription> BuiltinMethods = mapOf(
    entry(ABS, onlySelfDescr),
    entry("__add__", selfOtherDescr),
    entry("__and__", selfOtherDescr),
    //"__all__" to _only_self_descr,
    //"__author__" to _only_self_descr,
    //"__bases__" to _only_self_descr,
    entry(CALL, new BuiltinDescription("(self, *args, **kwargs)")),
    entry("__ceil__", onlySelfDescr),
    entry("__class__", onlySelfDescr),
    entry("__cmp__", selfOtherDescr),
    entry("__coerce__", selfOtherDescr),
    entry(COMPLEX, onlySelfDescr),
    entry("__contains__", selfItemDescr),
    entry("__copy__", onlySelfDescr),
    // "__debug__" to _only_self_descr,
    entry("__deepcopy__", new BuiltinDescription("(self, memo)")),
    entry("__del__", onlySelfDescr),
    entry("__delete__", new BuiltinDescription("(self, instance)")),
    entry("__delattr__", selfItemDescr),
    entry("__delitem__", selfKeyDescr),

    entry("__delslice__", new BuiltinDescription("(self, i, j)")),
    // "__dict__", _only_self_descr,
    entry("__divmod__", selfOtherDescr),
    //"__doc__", _only_self_descr,
    //"__docformat__" to _only_self_descr,
    entry("__enter__", onlySelfDescr),
    entry("__exit__", exitDescr),
    entry("__eq__", selfOtherDescr),
    // "__file__" to _only_self_descr,
    entry(FLOAT, onlySelfDescr),
    entry("__floor__", onlySelfDescr),
    entry("__floordiv__", selfOtherDescr),
    //"__future__" to _only_self_descr;
    entry("__ge__", selfOtherDescr),
    entry("__get__", new BuiltinDescription("(self, instance, owner)")),
    entry("__getattr__", selfItemDescr),
    entry("__getattribute__", selfItemDescr),
    entry("__getinitargs__", onlySelfDescr),
    entry("__getitem__", selfItemDescr),
    entry("__getnewargs__", onlySelfDescr),
    //"__getslice__" to BuiltinDescription("(self, i, j)"),
    entry("__getstate__", onlySelfDescr),
    entry("__gt__", selfOtherDescr),
    entry("__hash__", onlySelfDescr),
    entry("__hex__", onlySelfDescr),
    entry("__iadd__", selfOtherDescr),
    entry("__iand__", selfOtherDescr),
    entry("__idiv__", selfOtherDescr),
    entry("__ifloordiv__", selfOtherDescr),
    //"__import__" to _only_self_descr
    entry("__ilshift__", selfOtherDescr),
    entry("__imod__", selfOtherDescr),
    entry("__imul__", selfOtherDescr),
    entry("__index__", onlySelfDescr),
    entry(INIT, onlySelfDescr),
    entry(INT, onlySelfDescr),
    entry("__invert__", onlySelfDescr),
    entry("__ior__", selfOtherDescr),
    entry("__ipow__", selfOtherDescr),
    entry("__irshift__", selfOtherDescr),
    entry("__isub__", selfOtherDescr),
    entry("__iter__", onlySelfDescr),
    entry("__itruediv__", selfOtherDescr),
    entry("__ixor__", selfOtherDescr),
    entry("__le__", selfOtherDescr),
    entry("__len__", onlySelfDescr),
    entry("__long__", onlySelfDescr),
    entry("__lshift__", selfOtherDescr),
    entry("__lt__", selfOtherDescr),
    //"__members__" to_only_self_descr,
    //"__metaclass__" to _only_self_descr,
    entry("__missing__", selfKeyDescr),
    entry("__mod__", selfOtherDescr),
    //"__mro__" to _only_self_descr,
    entry("__mul__", selfOtherDescr),
    //"__name__" to _only_self_descr,
    entry("__ne__", selfOtherDescr),
    entry("__neg__", onlySelfDescr),
    entry(NEW, new BuiltinDescription("(cls, *args, **kwargs)")),
    entry("__oct__", onlySelfDescr),
    entry("__or__", selfOtherDescr),
    //_"__path__" to _only_self_descr,
    entry("__pos__", onlySelfDescr),
    entry("__pow__", new BuiltinDescription("(self, power, modulo=None)")),
    entry("__radd__", selfOtherDescr),
    entry("__rand__", selfOtherDescr),
    entry("__rdiv__", selfOtherDescr),
    entry("__rdivmod__", selfOtherDescr),
    entry("__reduce__", onlySelfDescr),
    entry("__reduce_ex__", new BuiltinDescription("(self, protocol)")),
    entry(PyNames.DUNDER_REPR, onlySelfDescr),
    entry("__reversed__", onlySelfDescr),
    entry("__rfloordiv__", selfOtherDescr),
    entry("__rlshift__", selfOtherDescr),
    entry("__rmod__", selfOtherDescr),
    entry("__rmul__", selfOtherDescr),
    entry("__ror__", selfOtherDescr),
    entry("__rpow__", selfOtherDescr),
    entry("__rrshift__", selfOtherDescr),
    entry("__rshift__", selfOtherDescr),
    entry("__rsub__", selfOtherDescr),
    entry("__rtruediv__", selfOtherDescr),
    entry("__rxor__", selfOtherDescr),
    entry("__set__", new BuiltinDescription("(self, instance, value)")),
    entry("__setattr__", new BuiltinDescription("(self, key, value)")),
    entry("__setitem__", new BuiltinDescription("(self, key, value)")),
    entry("__setslice__", new BuiltinDescription("(self, i, j, sequence)")),
    entry("__setstate__", new BuiltinDescription("(self, state)")),
    entry(SIZEOF, onlySelfDescr),
    //_"__self__" to_only_self_descr),
    //_"__slots__" to_only_self_descr),
    entry(DUNDER_STR, onlySelfDescr),
    entry("__sub__", selfOtherDescr),
    entry("__truediv__", selfOtherDescr),
    entry("__trunc__", onlySelfDescr),
    entry("__unicode__", onlySelfDescr),
    //_"__version__" to _only_self_descr,
    entry("__xor__", selfOtherDescr)
  );

  private static final Map<String, BuiltinDescription> PY2_BUILTIN_METHODS = plus(BuiltinMethods,
    entry("__nonzero__", onlySelfDescr),
    entry("__div__", selfOtherDescr),
    entry(NEXT, onlySelfDescr)
  );

  private static final Map<String, BuiltinDescription> PY3_BUILTIN_METHODS = plus(BuiltinMethods,
    entry("__bool__", onlySelfDescr),
    entry(BYTES, onlySelfDescr),
    entry(DUNDER_FORMAT, new BuiltinDescription("(self, format_spec: str) -> str")),
    entry("__instancecheck__", new BuiltinDescription("(self, instance)")),
    entry(PREPARE, new BuiltinDescription("(metacls, name: str, bases: tuple[type, ...], **kwargs: object) -> Mapping[str, object]", "collections.abc.Mapping")),
    entry(ROUND, new BuiltinDescription("(self, n=None)")),
    entry("__subclasscheck__", new BuiltinDescription("(self, subclass)")),
    entry(DUNDER_NEXT, onlySelfDescr)
  );

  private static final Map<String, BuiltinDescription> PY35_BUILTIN_METHODS = plus(PY3_BUILTIN_METHODS,
    entry("__imatmul__", selfOtherDescr),
    entry("__matmul__", selfOtherDescr),
    entry("__rmatmul__", selfOtherDescr),
    entry(DUNDER_AWAIT, onlySelfDescr),
    entry(AENTER, onlySelfDescr),
    entry(AEXIT, exitDescr),
    entry(AITER, onlySelfDescr),
    entry(ANEXT, onlySelfDescr)
  );

  private static final Map<String, BuiltinDescription> PY36_BUILTIN_METHODS = plus(PY35_BUILTIN_METHODS,
    entry(INIT_SUBCLASS, new BuiltinDescription("(cls, **kwargs)")),
    entry("__set_name__", new BuiltinDescription("(self, owner: object, name: str)")),
    entry("__fspath__", onlySelfDescr("AnyStr", "typing.AnyStr"))
  );

  private static final Map<String, BuiltinDescription> PY37_BUILTIN_METHODS = plus(PY36_BUILTIN_METHODS,
    entry(CLASS_GETITEM, new BuiltinDescription("(cls, item: object) -> object")),
    entry("__mro_entries__", new BuiltinDescription("(self, bases: tuple[type, ...]) -> tuple[type, ...]"))
  );

  private static final Map<String, BuiltinDescription> PY37_MODULE_BUILTIN_METHODS = mapOf(
    entry("__getattr__", new BuiltinDescription("(name: str) -> object")),
    entry("__dir__", new BuiltinDescription("() -> Iterable[object]", "collections.abc.Iterable"))
  );

  public static Map<String, BuiltinDescription> getBuiltinMethods(LanguageLevel level) {
    if (level.isAtLeast(LanguageLevel.PYTHON37)) {
      return PY37_BUILTIN_METHODS;
    }
    else if (level.isAtLeast(LanguageLevel.PYTHON36)) {
      return PY36_BUILTIN_METHODS;
    }
    else if (level.isAtLeast(LanguageLevel.PYTHON35)) {
      return PY35_BUILTIN_METHODS;
    }
    else if (!level.isPython2()) {
      return PY3_BUILTIN_METHODS;
    }
    else {
      return PY2_BUILTIN_METHODS;
    }
  }

  public static Map<String, BuiltinDescription> getModuleBuiltinMethods(LanguageLevel level) {
    if (level.isAtLeast(LanguageLevel.PYTHON37)) {
      return PY37_MODULE_BUILTIN_METHODS;
    }

    return Map.of();
  }

  // canonical names, not forced by interpreter
  public static final String CANONICAL_SELF = "self";
  public static final String CANONICAL_CLS = "cls";
  public static final String BASESTRING = "basestring";

  /*
  Python keywords
 */
  public static final String CLASS = "class";
  public static final String DEF = "def";
  public static final String IF = "if";
  public static final String ELSE = "else";
  public static final String ELIF = "elif";
  public static final String TRY = "try";
  public static final String EXCEPT = "except";
  public static final String FINALLY = "finally";
  public static final String WHILE = "while";
  public static final String FOR = "for";
  public static final String WITH = "with";
  public static final String AS = "as";
  public static final String ASSERT = "assert";
  public static final String DEL = "del";
  public static final String EXEC = "exec";
  public static final String FROM = "from";
  public static final String IMPORT = "import";
  public static final String RAISE = "raise";
  public static final String PRINT = "print";
  public static final String BREAK = "break";
  public static final String CONTINUE = "continue";
  public static final String GLOBAL = "global";
  public static final String RETURN = "return";
  public static final String YIELD = "yield";
  public static final String NONLOCAL = "nonlocal";
  public static final String AND = "and";
  public static final String OR = "or";
  public static final String IS = "is";
  public static final String IN = "in";
  public static final String NOT = "not";
  public static final String LAMBDA = "lambda";
  public static final String ASYNC = "async";
  public static final String AWAIT = "await";
  public static final String MATCH = "match";
  public static final String CASE = "case";
  public static final String LAZY = "lazy";

  /**
   * Contains keywords as of CPython 2.5.
   */
  public static final Set<String> KEYWORDS = setOf(
    AND,
    DEL,
    FROM,
    NOT,
    WHILE,
    AS,
    ELIF,
    GLOBAL,
    OR,
    WITH,
    ASSERT,
    ELSE,
    IF,
    PASS,
    YIELD,
    BREAK,
    EXCEPT,
    IMPORT,
    PRINT,
    CLASS,
    EXEC,
    IN,
    RAISE,
    CONTINUE,
    FINALLY,
    IS,
    RETURN,
    DEF,
    FOR,
    LAMBDA,
    TRY
  );

  // As per: https://docs.python.org/3/reference/lexical_analysis.html#keywords
  public static final Set<String> PY3_KEYWORDS = setOf(
    FALSE, AWAIT, ELSE, IMPORT, PASS,
    NONE, BREAK, EXCEPT, IN, RAISE,
    TRUE, CLASS, FINALLY, IS, RETURN,
    AND, CONTINUE, FOR, LAMBDA, TRY,
    AS, DEF, FROM, NONLOCAL, WHILE,
    ASSERT, DEL, GLOBAL, NOT, WITH,
    ASYNC, ELIF, IF, OR, YIELD
  );

  public static final Set<String> BUILTIN_INTERFACES = setOf(
    CALLABLE, HASHABLE, ITERABLE, ITERATOR, SIZED, CONTAINER, SEQUENCE, MAPPING, ABC_COMPLEX, ABC_REAL, ABC_RATIONAL, ABC_INTEGRAL,
    ABC_NUMBER
  );

  /**
   * TODO: dependency on language level.
   *
   * @param name what to check
   * @return true iff the name is either a keyword or a reserved name, like None.
   */
  public static boolean isReserved(@Nullable String name) {
    return name != null && KEYWORDS.contains(name) || NONE.equals(name);
  }

  // NOTE: includes unicode only good for py3k
  public static final String IDENTIFIER_RE = "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*";
  private static final Pattern IDENTIFIER_PATTERN = Pattern.compile(IDENTIFIER_RE);

  /**
   * TODO: dependency on language level.
   *
   * @param name what to check
   * @return true iff name is not reserved and is a well-formed identifier.
   */
  public static boolean isIdentifier(String name) {
    return !isReserved(name) && isIdentifierString(name);
  }

  public static boolean isIdentifierString(String name) {
    return IDENTIFIER_PATTERN.matcher(name).matches();
  }

  public static boolean isRightOperatorName(@Nullable String name) {
    if ("__rshift__".equals(name)) return false;
    return name != null && (name.matches("__r[a-z]+__") || CONTAINS.equals(name));
  }

  public static boolean isRightOperatorName(@Nullable String referencedName, @Nullable String calleeName) {
    if (isRightOperatorName(calleeName)) return true;

    return referencedName != null && calleeName != null && calleeName.equals(leftToRightComparisonOperatorName(referencedName));
  }

  private static final Pattern INPLACE_OPERATOR_PATTERN = Pattern.compile("__i([a-z]+)__");

  public static boolean isInplaceOperatorName(@Nullable String name) {
    return name != null && (INPLACE_OPERATOR_PATTERN.matcher(name).matches());
  }

  public static @Nullable String leftToRightOperatorName(@Nullable String name) {
    if (name == null) return null;

    String rightComparisonOperatorName = leftToRightComparisonOperatorName(name);
    if (rightComparisonOperatorName != null) return rightComparisonOperatorName;

    return name.replaceFirst("__([a-z]+)__", "__r$1__");
  }

  public static @Nullable String inplaceToLeftOperatorName(@Nullable String name) {
    if (name == null) return null;

    return INPLACE_OPERATOR_PATTERN.matcher(name).replaceFirst("__$1__");
  }

  public static @Nullable String inplaceToRightOperatorName(@Nullable String name) {
    if (name == null) return null;

    return INPLACE_OPERATOR_PATTERN.matcher(name).replaceFirst("__r$1__");
  }

  /**
   * @deprecated Use `PyAstElement.protectionLevel` instead
   */
  @Deprecated
  public static boolean isProtected(String name) {
    return ProtectionLevel.forName(name) == ProtectionLevel.PROTECTED;
  }

  /**
   * @deprecated Use `PyAstElement.protectionLevel` instead
   */
  @Deprecated
  public static boolean isPrivate(String name) {
    return ProtectionLevel.forName(name) == ProtectionLevel.PRIVATE;
  }

  public static boolean isSunder(String name) {
    return name.length() > 2 && name.startsWith("_") && name.endsWith("_") && name.charAt(1) != '_' && name.charAt(name.length() - 2) != '_';
  }

  public static boolean isDunder(String name) {
    return name.length() > 4 && name.startsWith("__") && name.endsWith("__") && name.charAt(2) != '_' && name.charAt(name.length() - 3) != '_';
  }

  private static @Nullable String leftToRightComparisonOperatorName(String name) {
    return switch (name) {
      case "__lt__" -> "__gt__";
      case "__gt__" -> "__lt__";
      case "__ge__" -> "__le__";
      case "__le__" -> "__ge__";
      default -> null;
    };
  }

  /**
   * Available in Python 3 and Python 2 starting from 2.6.
   *
   *
   * Attributes `__doc__`, `__dict__` and `__module__` should be inherited from object.
   */
  public static final Set<String> FUNCTION_SPECIAL_ATTRIBUTES = setOf(
    "__defaults__",
    "__globals__",
    "__closure__",
    "__code__",
    "__name__"
  );

  public static final Set<String> LEGACY_FUNCTION_SPECIAL_ATTRIBUTES = setOf(
    "func_defaults",
    "func_globals",
    "func_closure",
    "func_code",
    "func_name",
    "func_doc",
    "func_dict"
  );

  public static final Set<String> PY3_ONLY_FUNCTION_SPECIAL_ATTRIBUTES = setOf("__annotations__", "__kwdefaults__");

  public static final Set<String> METHOD_SPECIAL_ATTRIBUTES = setOf("__func__", "__self__", "__name__");

  public static final Set<String> LEGACY_METHOD_SPECIAL_ATTRIBUTES = setOf("im_func", "im_self", "im_class");

  public static final String MRO = "mro";

  public static class BuiltinDescription {
    private final String mySignature;
    private final String myDoc;
    private final Set<String> myImports;

    public BuiltinDescription(String signature, String... imports) {
      this(signature, imports, "");
    }

    public BuiltinDescription(String signature, String[] imports, String doc) {
      mySignature = signature;
      myDoc = doc;
      myImports = setOf(imports);
    }

    public String getSignature() {
      return mySignature;
    }

    public String getDoc() {
      return myDoc;
    }

    public Set<String> getImports() {
      return myImports;
    }
  }

  // Consulo: Java counterparts of the Kotlin setOf/mapOf/plus used by JB (insertion-ordered, read-only)
  private static Set<String> setOf(String... elements) {
    Set<String> set = new LinkedHashSet<>();
    Collections.addAll(set, elements);
    return Collections.unmodifiableSet(set);
  }

  private static Map.Entry<String, BuiltinDescription> entry(String name, BuiltinDescription description) {
    return Map.entry(name, description);
  }

  @SafeVarargs
  private static Map<String, BuiltinDescription> mapOf(Map.Entry<String, BuiltinDescription>... entries) {
    return plus(Map.of(), entries);
  }

  @SafeVarargs
  private static Map<String, BuiltinDescription> plus(Map<String, BuiltinDescription> base,
                                                      Map.Entry<String, BuiltinDescription>... entries) {
    Map<String, BuiltinDescription> map = new LinkedHashMap<>(base);
    for (Map.Entry<String, BuiltinDescription> entry : entries) {
      map.put(entry.getKey(), entry.getValue());
    }
    return Collections.unmodifiableMap(map);
  }
}

