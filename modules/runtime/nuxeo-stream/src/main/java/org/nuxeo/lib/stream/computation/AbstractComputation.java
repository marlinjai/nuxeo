/*
 * (C) Copyright 2017 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     bdelbosc
 */
package org.nuxeo.lib.stream.computation;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An abstract {@link Computation} that manages the metadata init.
 * <p>
 * By convention the inputs streams are named internally: i1, i2 ...
 * <p>
 * and the output streams are named: o1, o2 ...
 *
 * @since 9.3
 */
public abstract class AbstractComputation implements Computation {

    private static final Logger log = LogManager.getLogger(AbstractComputation.class);

    protected final ComputationMetadata metadata;

    public static final String INPUT_1 = "i1";

    public static final String INPUT_2 = "i2";

    public static final String INPUT_3 = "i3";

    public static final String OUTPUT_1 = "o1";

    public static final String OUTPUT_2 = "o2";

    public static final String OUTPUT_3 = "o3";

    public static final String OUTPUT_4 = "o4";

    // @since 11.1 can be used as input for single producer pattern
    public static final String INPUT_NULL = "input/null";

    /**
     * Creates a computation with the requested number of input and output streams.
     *
     * @since 10.3
     */
    public AbstractComputation(String name, int nbInputStreams, int nbOutputStreams) {
        this.metadata = new ComputationMetadata(name,
                IntStream.range(1, nbInputStreams + 1).boxed().map(i -> "i" + i).collect(Collectors.toSet()),
                IntStream.range(1, nbOutputStreams + 1).boxed().map(i -> "o" + i).collect(Collectors.toSet()));
    }

    @Override
    public void init(ComputationContext context) {

    }

    @Override
    public void processTimer(ComputationContext context, String key, long timestamp) {

    }

    @Override
    public ComputationMetadata metadata() {
        return metadata;
    }

    @Override
    public void processRetry(ComputationContext context, Throwable failure) {
        log.warn("Computation: {} fails last record: {}, retrying ...", metadata.name(), context.getLastOffset(),
                failure);
    }

    @Override
    public void processFailure(ComputationContext context, Throwable failure) {
        log.error("Computation: {} fails last record: {}, after retries.", metadata.name(), context.getLastOffset(),
                failure);
    }
}
