// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger;

public final class NodeTypes {
    public static final String ARRAY_NODE_TYPE = "array";
    public static final String DICT_NODE_TYPE = "dict";
    public static final String LIST_NODE_TYPE = "list";
    public static final String TUPLE_NODE_TYPE = "tuple";
    public static final String SET_NODE_TYPE = "set";
    public static final String MATRIX_NODE_TYPE = "matrix";
    public static final String NDARRAY_NODE_TYPE = "ndarray";
    public static final String RECARRAY_NODE_TYPE = "recarray";
    public static final String EAGER_TENSOR_NODE_TYPE = "EagerTensor";
    public static final String RESOURCE_VARIABLE_NODE_TYPE = "ResourceVariable";
    public static final String SPARSE_TENSOR_NODE_TYPE = "SparseTensor";
    public static final String TENSOR_NODE_TYPE = "Tensor";
    public static final String IMAGE_NODE_TYPE = "Image";
    public static final String PNG_IMAGE_NODE_TYPE = "PngImageFile";
    public static final String JPEG_IMAGE_NODE_TYPE = "JpegImageFile";
    public static final String FIGURE_NODE_TYPE = "Figure";
    public static final String DATA_FRAME_NODE_TYPE = "DataFrame";
    public static final String SERIES_NODE_TYPE = "Series";
    public static final String GEO_DATA_FRAME_NODE_TYPE = "GeoDataFrame";
    public static final String GEO_SERIES_NODE_TYPE = "GeoSeries";
    public static final String DATASET_NODE_TYPE = "Dataset";
    public static final String NESTED_ORDERED_DICT_NODE_TYPE = "NestedOrderedDict";
    public static final String ORDERED_DICT_NODE_TYPE = "OrderedDict";
    public static final String DATASET_DICT_NODE_TYPE = "DatasetDict";

    private NodeTypes() {
    }
}
