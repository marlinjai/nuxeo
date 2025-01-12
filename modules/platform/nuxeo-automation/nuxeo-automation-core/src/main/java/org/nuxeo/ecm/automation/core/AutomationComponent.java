/*
 * (C) Copyright 2015-2017 Nuxeo (http://nuxeo.com/) and others.
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
 *     bstefanescu
 *     Vladimir Pasquier <vpasquier@nuxeo.com>
 */
package org.nuxeo.ecm.automation.core;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.automation.AutomationAdmin;
import org.nuxeo.ecm.automation.AutomationFilter;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.ChainException;
import org.nuxeo.ecm.automation.OperationChain;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.OperationNotFoundException;
import org.nuxeo.ecm.automation.OperationParameters;
import org.nuxeo.ecm.automation.TypeAdapter;
import org.nuxeo.ecm.automation.context.ContextHelperDescriptor;
import org.nuxeo.ecm.automation.context.ContextHelperRegistry;
import org.nuxeo.ecm.automation.context.ContextService;
import org.nuxeo.ecm.automation.context.ContextServiceImpl;
import org.nuxeo.ecm.automation.core.events.EventHandler;
import org.nuxeo.ecm.automation.core.events.EventHandlerRegistry;
import org.nuxeo.ecm.automation.core.exception.ChainExceptionFilter;
import org.nuxeo.ecm.automation.core.exception.ChainExceptionImpl;
import org.nuxeo.ecm.automation.core.impl.ChainTypeImpl;
import org.nuxeo.ecm.automation.core.impl.OperationServiceImpl;
import org.nuxeo.ecm.automation.core.trace.TracerFactory;
import org.nuxeo.ecm.platform.forms.layout.api.WidgetDefinition;
import org.nuxeo.ecm.platform.forms.layout.descriptors.WidgetDescriptor;
import org.nuxeo.runtime.RuntimeMessage.Level;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.management.ServerLocator;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;

/**
 * Nuxeo component that provide an implementation of the {@link AutomationService} and handle extensions registrations.
 *
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 * @author <a href="mailto:grenard@nuxeo.com">Guillaume Renard</a>
 */
public class AutomationComponent extends DefaultComponent {

    private static final Logger log = LogManager.getLogger(AutomationComponent.class);

    public static final String XP_OPERATIONS = "operations";

    public static final String XP_ADAPTERS = "adapters";

    public static final String XP_CHAINS = "chains";

    public static final String XP_EVENT_HANDLERS = "event-handlers";

    public static final String XP_CHAIN_EXCEPTION = "chainException";

    public static final String XP_AUTOMATION_FILTER = "automationFilter";

    public static final String XP_CONTEXT_HELPER = "contextHelpers";

    protected OperationServiceImpl service;

    protected EventHandlerRegistry handlers;

    protected TracerFactory tracerFactory;

    protected ContextHelperRegistry contextHelperRegistry;

    protected ContextService contextService;

    @Override
    public void activate(ComponentContext context) {
        service = new OperationServiceImpl();
        tracerFactory = new TracerFactory();
        handlers = new EventHandlerRegistry(service);
        contextHelperRegistry = new ContextHelperRegistry();
        contextService = new ContextServiceImpl(contextHelperRegistry);
    }

    protected void bindManagement() throws JMException {
        ObjectName objectName = new ObjectName("org.nuxeo.automation:name=tracerfactory");
        MBeanServer mBeanServer = Framework.getService(ServerLocator.class).lookupServer();
        mBeanServer.registerMBean(tracerFactory, objectName);
    }

    protected void unBindManagement() throws MalformedObjectNameException, NotCompliantMBeanException,
            InstanceAlreadyExistsException, MBeanRegistrationException, InstanceNotFoundException {
        final ObjectName on = new ObjectName("org.nuxeo.automation:name=tracerfactory");
        final ServerLocator locator = Framework.getService(ServerLocator.class);
        if (locator != null) {
            MBeanServer mBeanServer = locator.lookupServer();
            mBeanServer.unregisterMBean(on);
        }
    }

    @Override
    public void deactivate(ComponentContext context) {
        service = null;
        handlers = null;
        tracerFactory = null;
    }

    @Override
    public void registerContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        if (XP_OPERATIONS.equals(extensionPoint)) {
            OperationContribution opc = (OperationContribution) contribution;
            List<WidgetDefinition> widgetDefinitionList = new ArrayList<>();
            if (opc.widgets != null) {
                for (WidgetDescriptor widgetDescriptor : opc.widgets) {
                    widgetDefinitionList.add(widgetDescriptor.getWidgetDefinition());
                }
            }
            try {
                Class<?> type = Class.forName(opc.type);
                service.putOperation(type, opc.replace, contributor.getName().toString(), widgetDefinitionList);
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException("Invalid operation class '" + opc.type + "': class not found.");
            } catch (OperationException e) {
                throw new RuntimeException(e);
            }
        } else if (XP_CHAINS.equals(extensionPoint)) {
            OperationChainContribution occ = (OperationChainContribution) contribution;
            try {
                ChainTypeImpl docChainType = new ChainTypeImpl(service,
                        occ.toOperationChain(contributor.getContext().getBundle()), occ,
                        contributor.getName().toString());
                service.putOperation(docChainType, occ.replace);
            } catch (OperationException e) {
                throw new RuntimeException(e);
            }
        } else if (XP_CHAIN_EXCEPTION.equals(extensionPoint)) {
            ChainExceptionDescriptor chainExceptionDescriptor = (ChainExceptionDescriptor) contribution;
            ChainException chainException = new ChainExceptionImpl(chainExceptionDescriptor);
            service.putChainException(chainException);
        } else if (XP_AUTOMATION_FILTER.equals(extensionPoint)) {
            AutomationFilterDescriptor automationFilterDescriptor = (AutomationFilterDescriptor) contribution;
            ChainExceptionFilter chainExceptionFilter = new ChainExceptionFilter(automationFilterDescriptor);
            service.putAutomationFilter(chainExceptionFilter);
        } else if (XP_ADAPTERS.equals(extensionPoint)) {
            TypeAdapterContribution tac = (TypeAdapterContribution) contribution;
            TypeAdapter adapter;
            try {
                adapter = tac.clazz.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
            service.putTypeAdapter(tac.accept, tac.produce, adapter);
        } else if (XP_EVENT_HANDLERS.equals(extensionPoint)) {
            EventHandler eh = (EventHandler) contribution;
            if (eh.isPostCommit()) {
                handlers.putPostCommitEventHandler(eh);
            } else {
                handlers.putEventHandler(eh);
            }
        } else if (XP_CONTEXT_HELPER.equals(extensionPoint)) {
            contextHelperRegistry.addContribution((ContextHelperDescriptor) contribution);
        }
    }

    @Override
    public void unregisterContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        if (XP_OPERATIONS.equals(extensionPoint)) {
            try {
                Class<?> type = Class.forName(((OperationContribution) contribution).type);
                service.removeOperation(type);
            } catch (ClassNotFoundException e) {
                // ignore
            }
        } else if (XP_CHAINS.equals(extensionPoint)) {
            try {
                OperationChainContribution occ = (OperationChainContribution) contribution;
                service.removeOperation(service.getOperation(occ.getId()));
            } catch (OperationNotFoundException e) {
                log.debug("The operation chain with id: {} was not found", ((OperationChainContribution) contribution).getId(), e);
            }
        } else if (XP_CHAIN_EXCEPTION.equals(extensionPoint)) {
            ChainExceptionDescriptor chainExceptionDescriptor = (ChainExceptionDescriptor) contribution;
            ChainException chainException = new ChainExceptionImpl(chainExceptionDescriptor);
            service.removeExceptionChain(chainException);
        } else if (XP_AUTOMATION_FILTER.equals(extensionPoint)) {
            AutomationFilterDescriptor automationFilterDescriptor = (AutomationFilterDescriptor) contribution;
            AutomationFilter automationFilter = new ChainExceptionFilter(automationFilterDescriptor);
            service.removeAutomationFilter(automationFilter);
        } else if (XP_ADAPTERS.equals(extensionPoint)) {
            TypeAdapterContribution tac = (TypeAdapterContribution) contribution;
            service.removeTypeAdapter(tac.accept, tac.produce);
        } else if (XP_EVENT_HANDLERS.equals(extensionPoint)) {
            EventHandler eh = (EventHandler) contribution;
            if (eh.isPostCommit()) {
                handlers.removePostCommitEventHandler(eh);
            } else {
                handlers.removeEventHandler(eh);
            }
        } else if (XP_CONTEXT_HELPER.equals(extensionPoint)) {
            contextHelperRegistry.removeContribution((ContextHelperDescriptor) contribution);
        }
    }

    @Override
    public <T> T getAdapter(Class<T> adapter) {
        if (adapter == AutomationService.class || adapter == AutomationAdmin.class) {
            return adapter.cast(service);
        }
        if (adapter == EventHandlerRegistry.class) {
            return adapter.cast(handlers);
        }
        if (adapter == TracerFactory.class) {
            return adapter.cast(tracerFactory);
        }
        if (adapter == ContextService.class) {
            return adapter.cast(contextService);
        }
        return null;
    }

    @Override
    public void start(ComponentContext context) {
        checkOperationChains();
        if (!tracerFactory.getRecordingState()) {
            log.info("You can activate automation trace mode to get more informations on automation executions");
        }
        try {
            bindManagement();
        } catch (JMException e) {
            throw new RuntimeException("Cannot bind management", e);
        }
    }

    /**
     * Checks operation references in chains
     *
     * @since 11.3
     */
    protected void checkOperationChains() {
        for (var operationType : service.getOperations()) {
            if (operationType instanceof ChainTypeImpl chainType) {
                var chain = chainType.getChain();
                for (OperationParameters opp : chain.getOperations()) {
                    if (!service.hasOperation(opp.id())) {
                        String msg = String.format(
                                "Operation chain with id '%s' references unknown operation with id '%s'", chain.getId(),
                                opp.id());
                        log.error(msg);
                        addRuntimeMessage(Level.ERROR, msg);
                    }
                }
            }
        }
    }

    @Override
    public void stop(ComponentContext context) {
        service.flushCompiledChains();
        try {
            unBindManagement();
        } catch (JMException e) {
            throw new RuntimeException("Cannot unbind management", e);
        }
    }
}
