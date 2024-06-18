/*
 * (C) Copyright 2017-2024 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ecm.core.management.test;

import static org.assertj.core.api.Assertions.assertThat;

import javax.management.JMX;
import javax.management.MBeanServer;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.management.standby.StandbyCommand;
import org.nuxeo.ecm.core.management.standby.StandbyMXBean;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.management.ObjectNameFactory;
import org.nuxeo.runtime.management.ServerLocator;
import org.nuxeo.runtime.test.runner.BlacklistComponent;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * @since 9.2
 */
@RunWith(FeaturesRunner.class)
@Features(CoreFeature.class)
@Deploy("org.nuxeo.runtime.management")
@Deploy("org.nuxeo.ecm.core.management")
// it registers a locator after the standby bean registration, so test doesn't get the same server as StandbyComponent
@BlacklistComponent("org.nuxeo.runtime.management.tests.isolated-server-contrib")
public class CanStandbyAndResumeTest {

    @Test
    public void canCommand() throws InterruptedException {
        MBeanServer server = Framework.getService(ServerLocator.class).lookupServer();
        StandbyMXBean bean = JMX.newMBeanProxy(server, ObjectNameFactory.getObjectName(StandbyCommand.class.getName()),
                StandbyMXBean.class);

        // commit transaction before standby, to release resources held in thread-locals
        TransactionHelper.commitOrRollbackTransaction();

        assertThat(bean.isStandby()).isFalse();
        bean.standby(10);
        assertThat(bean.isStandby()).isTrue();
        bean.resume();
        assertThat(bean.isStandby()).isFalse();
    }

}
