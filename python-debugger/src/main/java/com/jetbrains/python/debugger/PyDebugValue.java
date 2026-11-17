// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import consulo.application.Application;
import consulo.application.ReadAction;
import consulo.logging.Logger;
import consulo.execution.debug.frame.XCompositeNode;
import consulo.execution.debug.frame.XFullValueEvaluator;
import consulo.execution.debug.frame.XNamedValue;
import consulo.execution.debug.frame.XNavigatable;
import consulo.execution.debug.frame.XReferrersProvider;
import consulo.execution.debug.frame.XStackFrame;
import consulo.execution.debug.frame.XValue;
import consulo.execution.debug.frame.XValueChildrenList;
import consulo.execution.debug.frame.XValueModifier;
import consulo.execution.debug.frame.XValueNode;
import consulo.execution.debug.frame.XValuePlace;
import consulo.execution.debug.frame.presentation.XRegularValuePresentation;
import com.jetbrains.python.debugger.pydev.PyDebugCallback;
import com.jetbrains.python.debugger.render.PyNodeRenderer;
import consulo.execution.debug.icon.ExecutionDebugIconGroup;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.python.debugger.localize.PydevLocalize;
import consulo.ui.image.Image;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.jetbrains.python.debugger.PyDebugValueGroupsKt.DUNDER_LEN;
import static com.jetbrains.python.debugger.PyDebugValueGroupsKt.extractChildrenToGroup;
import static com.jetbrains.python.debugger.PyDebugValueGroupsKt.getPROTECTED_ATTRS_EXCLUDED;

public class PyDebugValue extends XNamedValue implements PyXDebugValue {
    protected static final Logger LOG = Logger.getInstance(PyDebugValue.class);
    private static final String ARRAY = "Array";
    private static final String DATA_FRAME = "DataFrame";
    private static final String SERIES = "Series";
    private static final Map<String, String> EVALUATOR_POSTFIXES = ImmutableMap.<String, String>builder()
      .put(NodeTypes.NDARRAY_NODE_TYPE, ARRAY)
      .put(NodeTypes.RECARRAY_NODE_TYPE, ARRAY)
      .put(NodeTypes.EAGER_TENSOR_NODE_TYPE, ARRAY)
      .put(NodeTypes.RESOURCE_VARIABLE_NODE_TYPE, ARRAY)
      .put(NodeTypes.SPARSE_TENSOR_NODE_TYPE, ARRAY)
      .put(NodeTypes.TENSOR_NODE_TYPE, ARRAY)
      .put(NodeTypes.DATA_FRAME_NODE_TYPE, DATA_FRAME)
      .put(NodeTypes.SERIES_NODE_TYPE, SERIES)
      .put(NodeTypes.GEO_DATA_FRAME_NODE_TYPE, DATA_FRAME)
      .put(NodeTypes.GEO_SERIES_NODE_TYPE, SERIES)
      .put(NodeTypes.DATASET_NODE_TYPE, DATA_FRAME)
      .build();
    private static final int MAX_ITEMS_TO_HANDLE = 100;
    public static final int MAX_VALUE = 256;
    public static final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();

    public static final String RETURN_VALUES_PREFIX = "__pydevd_ret_val_dict";

    private @Nullable String myTempName = null;
    private final @Nullable String myType;
    private final @Nullable String myTypeQualifier;
    protected @Nullable String myValue;
    private final boolean myContainer;
    protected final @Nullable String myShape;
    private final @Nullable String myArrayElementType;
    private final boolean myIsReturnedVal;
    private final boolean myIsIPythonHidden;
    private @Nullable PyDebugValue myParent;
    private @Nullable String myId = null;
    protected ValuesPolicy myLoadValuePolicy;
    private PyFrameAccessor myFrameAccessor;
    protected final List<XValueNode> myValueNodes = new ArrayList<>();
    private final boolean myErrorOnEval;
    private final @Nullable String myTypeRendererId;
    private int myOffset;
    private int myCollectionLength = -1;

    private PyDebugValueDescriptor myDescriptor = new PyDebugValueDescriptor();

    private static final Map<String, ValuesPolicy> POLICY_DEFAULT_VALUES = ImmutableMap.of("__pydevd_value_async", ValuesPolicy.ASYNC,
                                                                                           "__pydevd_value_on_demand",
                                                                                           ValuesPolicy.ON_DEMAND);

    public static final Map<ValuesPolicy, String> POLICY_ENV_VARS = ImmutableMap.of(ValuesPolicy.ASYNC, "PYDEVD_LOAD_VALUES_ASYNC",
                                                                                    ValuesPolicy.ON_DEMAND, "PYDEVD_LOAD_VALUES_ON_DEMAND");

    public PyDebugValue(String name,
                        @Nullable String type,
                        @Nullable String typeQualifier,
                        @Nullable String value,
                        boolean container,
                        @Nullable String shape,
                        boolean isReturnedVal,
                        boolean isIPythonHidden,
                        boolean errorOnEval,
                        @Nullable String typeRendererId,
                        PyFrameAccessor frameAccessor) {
        this(name, type, typeQualifier, value, container, shape, isReturnedVal, isIPythonHidden, errorOnEval, typeRendererId, null,
             frameAccessor);
    }

    public PyDebugValue(String name,
                        @Nullable String type,
                        @Nullable String typeQualifier,
                        @Nullable String value,
                        @Nullable String shape,
                        @Nullable String arrayElementType,
                        boolean isReturnedVal,
                        boolean isIPythonHidden,
                        boolean errorOnEval,
                        @Nullable String typeRendererId,
                        PyFrameAccessor frameAccessor) {
        this(name, type, typeQualifier, value, EVALUATOR_POSTFIXES.containsKey(type), shape, arrayElementType, isReturnedVal, isIPythonHidden, errorOnEval, typeRendererId, null,
             frameAccessor);
    }

    /**
     * Represents instance of a Python variable available at runtime. Used in Debugger and various Variable Viewers
     *
     * @param name            variable name
     * @param type            variable type
     * @param typeQualifier   type qualifier
     * @param value           string representation of a value
     * @param container       does variable have fields for expanding
     * @param shape           variable's shape field (available for numeric containers)
     * @param isReturnedVal   is value was returned from a function during debug session
     * @param isIPythonHidden does value belong to IPython util variables group
     * @param errorOnEval     did an error occur during evaluation
     * @param typeRendererId  user type renderer unique identifier
     * @param parent          parent variable in Variables tree
     * @param frameAccessor   frame accessor used for evaluation
     */
    public PyDebugValue(String name,
                        @Nullable String type,
                        @Nullable String typeQualifier,
                        @Nullable String value,
                        boolean container,
                        @Nullable String shape,
                        boolean isReturnedVal,
                        boolean isIPythonHidden,
                        boolean errorOnEval,
                        @Nullable String typeRendererId,
                        @Nullable PyDebugValue parent,
                        PyFrameAccessor frameAccessor) {
        this(name, type, typeQualifier, value, container, shape, null, isReturnedVal, isIPythonHidden, errorOnEval, typeRendererId, parent, frameAccessor);
    }


    public PyDebugValue(String name,
                        @Nullable String type,
                        @Nullable String typeQualifier,
                        @Nullable String value,
                        boolean container,
                        @Nullable String shape,
                        @Nullable String arrayElementType,
                        boolean isReturnedVal,
                        boolean isIPythonHidden,
                        boolean errorOnEval,
                        @Nullable String typeRendererId,
                        PyFrameAccessor frameAccessor) {
        this(name, type, typeQualifier, value, container, shape, arrayElementType, isReturnedVal, isIPythonHidden, errorOnEval, typeRendererId,
             null, frameAccessor);
    }


    public PyDebugValue(String name,
                        @Nullable String type,
                        @Nullable String typeQualifier,
                        @Nullable String value,
                        boolean container,
                        @Nullable String shape,
                        @Nullable String arrayElementType,
                        boolean isReturnedVal,
                        boolean isIPythonHidden,
                        boolean errorOnEval,
                        @Nullable String typeRendererId,
                        @Nullable PyDebugValue parent,
                        PyFrameAccessor frameAccessor) {
        super(name);
        myType = type;
        myTypeQualifier = Strings.isNullOrEmpty(typeQualifier) ? null : typeQualifier;
        myValue = value;
        myContainer = container;
        myShape = shape;
        myArrayElementType = arrayElementType;
        myIsReturnedVal = isReturnedVal;
        myIsIPythonHidden = isIPythonHidden;
        myErrorOnEval = errorOnEval;
        myTypeRendererId = typeRendererId;
        myParent = parent;
        myFrameAccessor = frameAccessor;
        myLoadValuePolicy = ValuesPolicy.SYNC;
        if (POLICY_DEFAULT_VALUES.containsKey(myValue)) {
            myLoadValuePolicy = POLICY_DEFAULT_VALUES.get(myValue);
            setValue(" ");
        }
    }

    public PyDebugValue(PyDebugValue value, String newName) {
        this(newName, value.getType(), value.getTypeQualifier(), value.getValue(), value.isContainer(), value.getShape(), value.getArrayElementType(), value.isReturnedVal(),
             value.isIPythonHidden(), value.isErrorOnEval(), value.getTypeRendererId(), value.getParent(), value.getFrameAccessor());
        myOffset = value.getOffset();
        setLoadValuePolicy(value.getLoadValuePolicy());
        setTempName(value.getTempName());
    }

    public PyDebugValue(PyDebugValue value) {
        this(value, value.getName());
    }

    public @Nullable String getTempName() {
        return myTempName != null ? myTempName : myName;
    }

    public void setTempName(@Nullable String tempName) {
        myTempName = tempName;
    }

    @Override
    public @Nullable String getType() {
        return myType;
    }

    public void setValue(@Nullable String newValue) {
        myValue = newValue;
    }

    public @Nullable String getValue() {
        return myValue;
    }

    public boolean isContainer() {
        return myContainer;
    }

    public @Nullable String getShape() {
        return myShape;
    }

    public boolean isReturnedVal() {
        return myIsReturnedVal;
    }

    public boolean isIPythonHidden() {
        return myIsIPythonHidden;
    }

    public boolean isErrorOnEval() {
        return myErrorOnEval;
    }

    public @Nullable String getTypeRendererId() {
        return myTypeRendererId;
    }

    public @Nullable String getArrayElementType() {
        return myArrayElementType;
    }

    public @Nullable PyDebugValue getParent() {
        return myParent;
    }

    public void setParent(@Nullable PyDebugValue parent) {
        myParent = parent;
    }

    public @Nullable PyDebugValue getTopParent() {
        return myParent == null ? this : myParent.getTopParent();
    }

    public ValuesPolicy getLoadValuePolicy() {
        return myLoadValuePolicy;
    }

    public void setLoadValuePolicy(ValuesPolicy loadValueAsync) {
        myLoadValuePolicy = loadValueAsync;
    }

    public List<XValueNode> getValueNodes() {
        return myValueNodes;
    }

    @Override
    public String getEvaluationExpression() {
        StringBuilder stringBuilder = new StringBuilder();
        buildExpression(stringBuilder);
        return wrapWithPrefix(stringBuilder.toString());
    }

    void buildExpression(StringBuilder result) {
        if (myParent == null) {
            result.append(getTempName());
        }
        else {
            myParent.buildExpression(result);
            if ((NodeTypes.DICT_NODE_TYPE.equals(myParent.getType()) ||
                 NodeTypes.LIST_NODE_TYPE.equals(myParent.getType()) ||
                 NodeTypes.TUPLE_NODE_TYPE.equals(myParent.getType()) ||
                 NodeTypes.ORDERED_DICT_NODE_TYPE.equals(myParent.getType()) ||
                 NodeTypes.NESTED_ORDERED_DICT_NODE_TYPE.equals(myParent.getType()) ||
                 NodeTypes.DATASET_DICT_NODE_TYPE.equals(myParent.getType())
                ) && !isLen(myName)) {
                result.append('[').append(removeLeadingZeros(removeId(myName))).append(']');
            }
            else if ((NodeTypes.SET_NODE_TYPE.equals(myParent.getType())) && !isLen(myName)) {
                //set doesn't support indexing
            }
            else if (isLen(myName)) {
                result.append('.').append(myName).append("()");
            }
            else if ((NodeTypes.NDARRAY_NODE_TYPE.equals(myParent.getType()) || NodeTypes.MATRIX_NODE_TYPE.equals(myParent.getType())) &&
                     myName.equals(NodeTypes.ARRAY_NODE_TYPE)) {
                // return the string representation of an ndarray
            }
            else if (NodeTypes.ARRAY_NODE_TYPE.equals(myParent.getName()) &&
                     myParent.myParent != null &&
                     NodeTypes.NDARRAY_NODE_TYPE.equals(myParent.myParent.getType())) {
                result.append("[").append(removeLeadingZeros(myName)).append("]");
            }
            else {
                result.append('.').append(myName);
            }
        }
    }

    /**
     * Evaluate full name to access variable at runtime
     * Used for evaluation values of "Returned values". They are saved in a separate dictionary on Python side, so the variable's name
     * should be transformed.
     *
     * @return full variable name at runtime
     */
    public String getFullName() {
        return wrapWithPrefix(getName());
    }

    /**
     * Remove util information from variable name to hide it from user
     *
     * @return variable name without util information
     */
    public String getVisibleName() {
        return removeId(myName);
    }

    /**
     * Removes object id from variable name. Object id is saved inside dict keys to find object at runtime
     *
     * @param name variable name with or without object id ('a' (11259136))
     * @return variable name without object id ('a')
     */
    private static String removeId(String name) {
        if (name.endsWith(")")) {
            final int lastInd = name.lastIndexOf('(');
            if (lastInd != -1) {
                name = name.substring(0, lastInd).trim();
            }
        }
        return name;
    }

    private static String removeLeadingZeros(String name) {
        //bugs.python.org/issue15254: "0" prefix for octal
        while (name.length() > 1 && name.startsWith("0")) {
            name = name.substring(1);
        }
        return name;
    }

    private static boolean isLen(String name) {
        return DUNDER_LEN.equals(name);
    }

    private String wrapWithPrefix(String name) {
        if (isReturnedVal()) {
            // return values are saved in dictionary on Python side, so the variable's name should be transformed
            return RETURN_VALUES_PREFIX + "[\"" + name + "\"]";
        }
        else {
            return name;
        }
    }

    private String getTypeString() {
        if (myShape != null) {
            return myType + ": " + myShape;
        }
        return myType;
    }

    private void setElementPresentation(XValueNode node, String value) {
        if (myParent != null && NodeTypes.SET_NODE_TYPE.equals(myParent.getType())) {
            // hide object id and '=' when showing set elements
            node.setPresentation(getValueIcon(), new XRegularValuePresentation(value, getTypeString()) {
                @Override
                public String getSeparator() {
                    return myName.equals(DUNDER_LEN) ? " = " : "";
                }

                // TODO consulo: XValuePresentation#isShowName() missing - JB hides the name of a set element unless it is __len__
            }, myContainer);
        }
        else {
            node.setPresentation(getValueIcon(), getTypeString(), value, myContainer);
        }
    }

    @Override
    public void computePresentation(XValueNode node, XValuePlace place) {
        String value = PyTypeHandler.format(this);
        setFullValueEvaluator(node, value);
        setAdditionalLinks(node);
        if (value.length() >= MAX_VALUE) {
            value = value.substring(0, MAX_VALUE);
        }
        value = applyRendererIfApplicable(value);
        setElementPresentation(node, value);
    }

    private void setAdditionalLinks(XValueNode node) {
        // TODO consulo: XValueNodeEx#addAdditionalHyperlink(XDebuggerTreeNodeHyperlink) missing -
        //  JB adds the "configure type renderer" link from myFrameAccessor.getUserTypeRenderersLink(getTypeRendererId())
    }

    public void updateNodeValueAfterLoading(XValueNode node,
                                            String value,
                                            LocalizeValue linkText,
                                            @Nullable String errorMessage) {
        if (errorMessage != null) {
            node.setPresentation(getValueIcon(), new XRegularValuePresentation(value, myType) {
                @Override
                public void renderValue(XValueTextRenderer renderer) {
                    renderer.renderError(LocalizeValue.of(errorMessage));
                }
            }, myContainer);
        }
        else {
            setElementPresentation(node, value);
        }

        if (isNumericContainer()) { // do not update FullValueEvaluator not to break Array Viewer
            return;
        }
        if (value.length() >= MAX_VALUE) {
            node.setFullValueEvaluator(new PyFullValueEvaluator(myFrameAccessor, getEvaluationExpression()));
        }
        else {
            node.setFullValueEvaluator(new XFullValueEvaluator(linkText) {
                @Override
                public void startEvaluation(XFullValueEvaluationCallback callback) {
                    callback.evaluated(value);
                }

                @Override
                public boolean isShowValuePopup() {
                    return false;
                }
            });
        }
    }

    public PyDebugCallback<String> createDebugValueCallback() {
        return new PyDebugCallback<>() {
            @Override
            public void ok(String value) {
                myLoadValuePolicy = ValuesPolicy.SYNC;
                myValue = value;
                for (XValueNode node : myValueNodes) {
                    if (node != null && !node.isObsolete()) {
                        updateNodeValueAfterLoading(node, value, LocalizeValue.empty(), null);
                    }
                }
            }

            @Override
            public void error(PyDebuggerException exception) {
                LOG.error(exception.getMessage());
            }
        };
    }

    public boolean isNumericContainer() {
        return EVALUATOR_POSTFIXES.get(myType) != null;
    }

    public static List<PyFrameAccessor.PyAsyncValue<String>> getAsyncValuesFromChildren(XValueChildrenList childrenList) {
        List<PyFrameAccessor.PyAsyncValue<String>> variables = new ArrayList<>();
        for (int i = 0; i < childrenList.size(); i++) {
            XValue value = childrenList.getValue(i);
            if (value instanceof PyDebugValue debugValue) {
                if (debugValue.getLoadValuePolicy() == ValuesPolicy.ASYNC) {
                    variables.add(new PyFrameAccessor.PyAsyncValue<>(debugValue, debugValue.createDebugValueCallback()));
                }
            }
        }
        return variables;
    }

    public static void getAsyncValues(@Nullable XStackFrame frame,
                                      PyFrameAccessor frameAccessor,
                                      XValueChildrenList childrenList) {
        List<PyFrameAccessor.PyAsyncValue<String>> variables = getAsyncValuesFromChildren(childrenList);
        int chunkSize = Math.max(1, variables.size() / AVAILABLE_PROCESSORS);
        int left = 0;
        int right = Math.min(chunkSize, variables.size());
        while (left < variables.size()) {
            frameAccessor.loadAsyncVariablesValues(frame, variables.subList(left, right));
            left = right;
            right = Math.min(right + chunkSize, variables.size());
        }
    }

    private void setFullValueEvaluator(XValueNode node, String value) {
        String treeName = getEvaluationExpression();
        String postfix = EVALUATOR_POSTFIXES.get(myType);
        myValueNodes.add(node);
        if (postfix == null) {
            if (value.length() >= MAX_VALUE) {
                node.setFullValueEvaluator(new PyFullValueEvaluator(myFrameAccessor, treeName));
            }
            if (myLoadValuePolicy == ValuesPolicy.ASYNC) {
                node.setFullValueEvaluator(new PyLoadingValueEvaluator(PydevLocalize.pydevLoadingValue(), myFrameAccessor, treeName));
            }
            else if (myLoadValuePolicy == ValuesPolicy.ON_DEMAND) {
                node.setFullValueEvaluator(new PyOnDemandValueEvaluator(PydevLocalize.pydevShowValue(), myFrameAccessor, this, node));
            }
            return;
        }

        addAdditionalHyperlinks(node);

        LocalizeValue linkText = PydevLocalize.pydevViewAs(postfix);
        node.setFullValueEvaluator(new PyNumericContainerValueEvaluator(linkText, myFrameAccessor, treeName));
    }

    private void addAdditionalHyperlinks(XValueNode valueNode) {
        // TODO consulo: XValueNodeEx missing - JB calls PyDebugValueAdditionalHyperlinkProvider.computeHyperlink(this, valueNodeEx)
    }

    @Override
    public void computeChildren(XCompositeNode node) {
        if (node.isObsolete()) {
            return;
        }
        Application.get().executeOnPooledThread(() -> {
            try {
                XValueChildrenList values = myFrameAccessor.loadVariable(this);

                restoreChildrenRenderers(values);

                if (values != null && !node.isObsolete()) {
                    updateLengthIfIsCollection(values);

                    if (isLargeCollection()) {
                        values = processLargeCollection(values);
                    }
                    if (myFrameAccessor.isSimplifiedView()) {
                        extractChildrenToGroup(PydevLocalize.pydevValueProtectedAttributesGroupName(), PlatformIconGroup.nodesC_protected(), node,
                                               values, (String name) -> name.startsWith("_"),
                                               getPROTECTED_ATTRS_EXCLUDED());
                    }
                    else {
                        node.addChildren(values, true);
                    }
                    if (isLargeCollection()) {
                        updateOffset(node, values);
                    }

                    getAsyncValues(null, myFrameAccessor, values);
                }
            }
            catch (PyDebuggerException e) {
                if (!node.isObsolete()) {
                    node.setErrorMessage("Unable to display children:" + e.getMessage());
                }
                LOG.warn(e);
            }
        });
    }

    @Override
    public XValueModifier getModifier() {
        return new PyValueModifier(myFrameAccessor, this);
    }

    private Image getValueIcon() {
        if (!myContainer) {
            return ExecutionDebugIconGroup.nodePrimitive();
        }
        else if (NodeTypes.LIST_NODE_TYPE.equals(myType) || NodeTypes.TUPLE_NODE_TYPE.equals(myType)) {
            return ExecutionDebugIconGroup.nodeArray();
        }
        else {
            return ExecutionDebugIconGroup.nodeValue();
        }
    }

    @Override
    public @Nullable XReferrersProvider getReferrersProvider() {
        if (myFrameAccessor.getReferrersLoader() != null) {
            return new XReferrersProvider() {
                @Override
                public XValue getReferringObjectsValue() {
                    return new PyReferringObjectsValue(PyDebugValue.this);
                }
            };
        }
        else {
            return null;
        }
    }

    @Override
    public PyFrameAccessor getFrameAccessor() {
        return myFrameAccessor;
    }

    @Override
    public PyDebugValue toPyDebugValue() {
        return this;
    }

    @Override
    public void setFrameAccessor(PyFrameAccessor frameAccessor) {
        myFrameAccessor = frameAccessor;
    }

    public @Nullable String getId() {
        return myId;
    }

    public void setId(@Nullable String id) {
        myId = id;
    }

    @Override
    public void computeSourcePosition(XNavigatable navigatable) {
        Application.get().executeOnPooledThread(
          () -> ReadAction.run(
            () -> {
                if (myParent == null) {
                    navigatable.setSourcePosition(myFrameAccessor.getSourcePositionForName(myName, null));
                }
                else {
                    navigatable.setSourcePosition(myFrameAccessor.getSourcePositionForName(myName, myParent.getDeclaringType()));
                }
            }
          )
        );
    }

    @Override
    public boolean canNavigateToTypeSource() {
        return true;
    }

    private static final Pattern IS_TYPE_DECLARATION = Pattern.compile("<(?:class|type)\\s*'(?<TYPE>.*?)'>");

    @Override
    public void computeTypeSourcePosition(XNavigatable navigatable) {
        String lookupType = getDeclaringType();
        navigatable.setSourcePosition(myFrameAccessor.getSourcePositionForType(lookupType));
    }

    protected final @Nullable String getDeclaringType() {
        String lookupType = getQualifiedType();
        if (!Strings.isNullOrEmpty(myValue)) {
            Matcher matcher = IS_TYPE_DECLARATION.matcher(myValue);
            if (matcher.matches()) {
                lookupType = matcher.group("TYPE");
            }
        }
        return lookupType;
    }

    public @Nullable String getQualifiedType() {
        if (Strings.isNullOrEmpty(myType)) {
            return null;
        }
        return (myTypeQualifier == null) ? myType : (myTypeQualifier + "." + myType);
    }

    public @Nullable String getTypeQualifier() {
        return myTypeQualifier;
    }

    public boolean isTemporary() {
        return myTempName != null;
    }

    public int getOffset() {
        return myOffset;
    }

    public void setOffset(int offset) {
        myOffset = offset;
    }

    private boolean isLargeCollection() {
        return myCollectionLength > MAX_ITEMS_TO_HANDLE;
    }

    private void updateLengthIfIsCollection(XValueChildrenList values) {
        if (myCollectionLength > 0 && values.size() == 0) {
            return;
        }

        final int lastIndex = values.size() - 1;

        // If there is the __len__ attribute it should always goes last.
        if (values.size() > 0 && isLen(values.getName(lastIndex))) {
            PyDebugValue len = (PyDebugValue)values.getValue(lastIndex);
            try {
                if (myCollectionLength == -1 && len.getValue() != null) {
                    myCollectionLength = Integer.parseInt(len.getValue());
                }
            }
            catch (NumberFormatException ex) {
                // Do nothing.
            }
        }
    }

    private XValueChildrenList processLargeCollection(XValueChildrenList values) {
        if (values.size() > 0 && isLargeCollection()) {
            if (myOffset + Math.min(MAX_ITEMS_TO_HANDLE, values.size()) < myCollectionLength) {
                XValueChildrenList newValues = new XValueChildrenList();
                for (int i = 0; i < values.size() - 1; i++) {
                    newValues.add(values.getName(i), values.getValue(i));
                }
                return newValues;
            }
        }
        return values;
    }

    private void updateOffset(XCompositeNode node, XValueChildrenList values) {
        if (myContainer && isLargeCollection()) {
            if (myOffset + Math.min(values.size(), MAX_ITEMS_TO_HANDLE) >= myCollectionLength) {
                myOffset = myCollectionLength;
            }
            else {
                node.tooManyChildren(myCollectionLength - Math.min(values.size(), MAX_ITEMS_TO_HANDLE) - myOffset);
                myOffset += Math.min(values.size(), MAX_ITEMS_TO_HANDLE);
            }
        }
    }

    public PyDebugValueDescriptor getDescriptor() {
        return myDescriptor;
    }

    public void setDescriptor(PyDebugValueDescriptor descriptor) {
        myDescriptor = descriptor;
    }

    private void restoreChildrenRenderers(@Nullable XValueChildrenList values) {
        PyDebugValueDescriptor descriptor = getDescriptor();
        Map<String, PyDebugValueDescriptor> childrenDescriptors = descriptor.getChildrenDescriptors();

        if (childrenDescriptors == null) {
            childrenDescriptors = Maps.newHashMap();
            descriptor.setChildrenDescriptors(childrenDescriptors);
        }

        if (values == null) {
            return;
        }

        for (int i = 0; i < values.size(); i++) {
            if (values.getValue(i) instanceof PyDebugValue value) {
                descriptor = childrenDescriptors.getOrDefault(value.getName(), null);
                if (descriptor == null) {
                    descriptor = new PyDebugValueDescriptor();
                    childrenDescriptors.put(value.getName(), descriptor);
                }
                value.setDescriptor(descriptor);
            }
        }
    }

    private String applyRendererIfApplicable(String value) {
        final PyNodeRenderer renderer = getDescriptor().getRenderer();
        final String type = getType();
        if (renderer == null || type == null) {
            return value;
        }
        if (renderer.isApplicable(type)) {
            return renderer.render(value);
        }
        else {
            getDescriptor().setRenderer(null);
        }
        return value;
    }

    public static Map<String, String> getEvaluatorPostfixes() {
        return EVALUATOR_POSTFIXES;
    }
}