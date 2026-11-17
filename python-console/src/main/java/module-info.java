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

/**
 * Python Console Thrift protocol and Netty transport (JB intellij.python.console).
 * <p>
 * Not {@code @NullMarked} on module level: the Thrift generated sources in
 * {@code com.jetbrains.python.console.protocol} are kept as-is, so null-marking is applied per package
 * (see {@code package-info.java} of the transport packages).
 */
module consulo.python.console {
    requires consulo.logging.api;
    requires consulo.util.concurrent;

    requires transitive org.apache.thrift;
    requires org.slf4j;
    requires static java.annotation;

    requires io.netty.common;
    requires io.netty.buffer;
    requires io.netty.transport;
    requires io.netty.handler;
    requires io.netty.codec;

    exports com.jetbrains.python.console.protocol;
    exports com.jetbrains.python.console.transport;
    exports com.jetbrains.python.console.transport.server;
}
