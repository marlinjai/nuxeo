/*
 * (C) Copyright 2024 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Guillaume Renard
 */
package org.nuxeo.common.function;

import java.util.function.UnaryOperator;

/**
 * @param <T> the type of the input and output of the operator
 * @param <E> the type of exception to throw
 * @since 2023.14
 */
@FunctionalInterface
public interface ThrowableUnaryOperator<T, E extends Throwable> {

    T apply(T t) throws E;

    /**
     * @return this {@link ThrowableUnaryOperator} as a {@link UnaryOperator} throwing the checked exception as an
     *         unchecked one
     */
    default UnaryOperator<T> toFunction() {
        return asUnaryOperator(this);
    }

    /**
     * @return the given {@link ThrowableUnaryOperator} as a {@link UnaryOperator} throwing the checked exception as an
     *         unchecked one
     */
    static <T, E extends Throwable> UnaryOperator<T> asUnaryOperator(
            ThrowableUnaryOperator<T, E> throwableUnaryOperator) {
        return arg -> {
            try {
                return throwableUnaryOperator.apply(arg);
            } catch (Throwable t) { // NOSONAR
                return FunctionUtils.sneakyThrow(t); // will never return
            }
        };
    }

}
