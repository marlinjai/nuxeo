/*
 * (C) Copyright 2006-2024 Nuxeo (http://nuxeo.com/) and others.
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
 *    Mariana Cedica
 */
package org.nuxeo.ecm.automation.core.operations.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.automation.jaxrs.io.documents.PaginableDocumentModelListImpl;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.platform.query.api.PageProvider;

/**
 * Run an embedded operation chain for each page of the pageProvider given as input. The output is undefined (Void)
 *
 * @since 5.6
 */
@Operation(id = RunOperationOnProvider.ID, category = Constants.CAT_SUBCHAIN_EXECUTION, label = "Run For Each Page", description = "Run an operation for each page of the provider defined by the provider name, the operation input is the curent page ", aliases = {
        "Context.RunOperationOnProvider" })
public class RunOperationOnProvider {

    private static final Logger log = LogManager.getLogger(RunOperationOnProvider.class);

    public static final String ID = "RunOperationOnProvider";

    @Context
    protected OperationContext ctx;

    @Context
    protected AutomationService service;

    @Param(name = "id")
    protected String chainId;

    @Param(name = "isolate", required = false, values = { "true" })
    protected boolean isolate = true;

    @OperationMethod
    public void run(PaginableDocumentModelListImpl paginableList) throws OperationException {
        PageProvider<DocumentModel> pageProvider = paginableList.getProvider();
        long pageSize = Math.max(pageProvider.getPageSize(), 1);
        try (OperationContext subctx = ctx.getSubContext(isolate)) {
            long initialCount = pageProvider.getResultsCount();
            long initialNoPages = pageProvider.getNumberOfPages();
            long nbLoops = 0;

            log.info("Run the automation: {} on pageProvider: {} - number of pages: {} - resultsCount: {}",
                    () -> chainId, pageProvider::getName, () -> initialNoPages, () -> initialCount);
            while (pageProvider.getCurrentPageIndex() < initialNoPages) {
                if (nbLoops == initialNoPages + 1) {
                    log.warn("Processing integrity of automation: {} on pageProvider: {} is not guaranteed, "
                            + "you should use the Bulk Action Framework", () -> chainId, pageProvider::getName);
                } else if (nbLoops > pageSize * initialNoPages) {
                    throw new NuxeoException("Infinite loop detected while running automation: " + chainId
                            + " on pageProvider: " + pageProvider.getName());
                }
                log.debug("Run the automation: {} on pageProvider: {} - pages: {}/{} - resultsCount: {}", () -> chainId,
                        pageProvider::getName, () -> pageProvider.getCurrentPageIndex() + 1,
                        pageProvider::getNumberOfPages, pageProvider::getResultsCount);
                var input = new PaginableDocumentModelListImpl(pageProvider);
                subctx.setInput(input);
                service.run(subctx, chainId);
                if (!pageProvider.isNextPageAvailable()) {
                    break;
                }
                pageProvider.refresh(); // clear the cached result set
                pageProvider.getCurrentPage(); // trigger the query
                // check if the result set has been affected by the sub chain
                // don't go to next page if the sub chain "consumes" documents, ie: the previous page was removed from
                // page provider results, thus leading to an offset move, so current page has to be processed
                if (pageProvider.getResultsCount() == initialCount) {
                    log.trace("Requesting the next page for automation: {} on pageProvider: {}", () -> chainId,
                            pageProvider::getName);
                    pageProvider.nextPage();
                }
                nbLoops++;
            }
        }

    }
}
