/*
 * (C) Copyright 2016 Nuxeo SA (http://nuxeo.com/) and others.
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
 */
package org.nuxeo.automation.scripting.internals;

import static org.nuxeo.automation.scripting.api.AutomationScriptingConstants.AUTOMATION_SCRIPTING_PRECOMPILE;
import static org.nuxeo.automation.scripting.api.AutomationScriptingConstants.COMPLIANT_JAVA_VERSION_CACHE;
import static org.nuxeo.automation.scripting.api.AutomationScriptingConstants.COMPLIANT_JAVA_VERSION_CLASS_FILTER;
import static org.nuxeo.automation.scripting.api.AutomationScriptingConstants.DEFAULT_PRECOMPILE_STATUS;
import static org.nuxeo.automation.scripting.api.AutomationScriptingConstants.NASHORN_JAVA_VERSION;
import static org.nuxeo.automation.scripting.api.AutomationScriptingConstants.NASHORN_WARN_CACHE;
import static org.nuxeo.automation.scripting.api.AutomationScriptingConstants.NASHORN_WARN_CLASS_FILTER;
import static org.nuxeo.launcher.config.ConfigurationChecker.checkJavaVersion;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.automation.scripting.api.AutomationScriptingService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.api.Framework;
import org.openjdk.nashorn.api.scripting.ClassFilter;
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.openjdk.nashorn.api.scripting.ScriptObjectMirror;

public class AutomationScriptingServiceImpl implements AutomationScriptingService {

    private static final Logger log = LogManager.getLogger(AutomationScriptingServiceImpl.class);

    /** @since 2023.9 */
    public static final String OPTIMISTIC_TYPES_ENABLED_PROPERTY_KEY = "nuxeo.automation.scripting.optimistic.types.enabled";

    protected final ScriptEngine engine = getScriptEngine();

    protected volatile CompiledScript mapperScript;

    protected AutomationScriptingParamsInjector paramsInjector;

    // updated in-place only by extension points, so no concurrency issues
    protected Set<String> allowedClassNames = new HashSet<>();

    @Override
    public Session get(CoreSession session) {
        return get(new OperationContext(session));
    }

    @Override
    public Session get(OperationContext context) {
        return new Bridge(context);
    }

    protected CompiledScript getMapperScript() {
        if (mapperScript == null) {
            synchronized (this) {
                if (mapperScript == null) {
                    mapperScript = AutomationMapper.compile((Compilable) engine);
                }
            }
        }
        return mapperScript;
    }

    protected synchronized void clearMapperScript() {
        this.mapperScript = null;
    }

    class Bridge implements Session {

        final Compilable compilable = ((Compilable) engine);

        final Invocable invocable = ((Invocable) engine);

        final ScriptContext scriptContext = engine.getContext();

        final AutomationMapper mapper;

        final ScriptObjectMirror global;

        Bridge(OperationContext operationContext) {
            mapper = new AutomationMapper(operationContext);
            try {
                getMapperScript().eval(mapper);
            } catch (ScriptException cause) {
                throw new NuxeoException("Cannot execute mapper " + mapperScript, cause);
            }
            global = (ScriptObjectMirror) mapper.get("nashorn.global");
            scriptContext.setBindings(mapper, ScriptContext.ENGINE_SCOPE);
        }

        @Override
        public <T> T handleof(InputStream input, Class<T> typeof) {
            run(input);
            T handle = invocable.getInterface(global, typeof);
            if (handle == null) {
                throw new NuxeoException("Script doesn't implements " + typeof.getName());
            }
            return typeof.cast(Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                    new Class[] { typeof }, new InvocationHandler() {

                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            return mapper.unwrap(method.invoke(handle, mapper.wrap(args[0]), mapper.wrap(args[1])));
                        }
                    }));
        }

        @Override
        public Object run(InputStream input) {
            try {
                return mapper.unwrap(engine.eval(new InputStreamReader(input), mapper));
            } catch (ScriptException cause) {
                throw new NuxeoException("Cannot evaluate automation script", cause);
            }
        }

        <T> T handleof(Class<T> typeof) {
            return invocable.getInterface(global, typeof);
        }

        @Override
        public <T> T adapt(Class<T> typeof) {
            if (typeof.isAssignableFrom(engine.getClass())) {
                return typeof.cast(engine);
            }
            if (typeof.isAssignableFrom(AutomationMapper.class)) {
                return typeof.cast(mapper);
            }
            if (typeof.isAssignableFrom(scriptContext.getClass())) {
                return typeof.cast(scriptContext);
            }
            throw new IllegalArgumentException("Cannot adapt scripting context to " + typeof.getName());
        }

        @Override
        public void close() throws Exception {
            mapper.flush();
        }
    }

    protected ScriptEngine getScriptEngine() {
        String version = Framework.getProperty("java.version");
        // Check if jdk8
        if (!checkJavaVersion(version, NASHORN_JAVA_VERSION)) {
            throw new UnsupportedOperationException(NASHORN_JAVA_VERSION);
        }
        // Check if version < jdk8u25 -> no cache.
        if (!checkJavaVersion(version, COMPLIANT_JAVA_VERSION_CACHE)) {
            log.warn(NASHORN_WARN_CACHE);
            return getScriptEngine(false, false);
        }
        boolean cache = Boolean.parseBoolean(
                Framework.getProperty(AUTOMATION_SCRIPTING_PRECOMPILE, DEFAULT_PRECOMPILE_STATUS));
        // Check if jdk8u25 <= version < jdk8u40 -> only cache.
        if (!checkJavaVersion(version, COMPLIANT_JAVA_VERSION_CLASS_FILTER)) {
            log.warn(NASHORN_WARN_CLASS_FILTER);
            return getScriptEngine(cache, false);
        }
        // Else if version >= jdk8u40 -> cache + class filter
        try {
            return getScriptEngine(cache, true);
        } catch (NoClassDefFoundError cause) {
            log.warn(NASHORN_WARN_CLASS_FILTER);
            return getScriptEngine(cache, false);
        }
    }

    protected ScriptEngine getScriptEngine(boolean cache, boolean filter) {
        NashornScriptEngineFactory nashorn = new NashornScriptEngineFactory();
        String[] args = cache
                ? new String[] { "-strict", "--optimistic-types=" + isOptimisticTypesEnabled(),
                        "--persistent-code-cache", "--class-cache-size=50" }
                : new String[] { "-strict" };
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        ClassFilter classFilter = filter ? getClassFilter() : null;
        return nashorn.getScriptEngine(args, classLoader, classFilter);
    }

    protected boolean isOptimisticTypesEnabled() {
        return Boolean.parseBoolean(Framework.getProperty(OPTIMISTIC_TYPES_ENABLED_PROPERTY_KEY));
    }

    protected ClassFilter getClassFilter() {
        return className -> allowedClassNames.contains(className);
    }

}
