/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.rest.testing;

import java.net.URI;

import org.apache.brooklyn.api.location.LocationRegistry;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampPlatformLauncherNoServer;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.location.BasicLocationRegistry;
import org.apache.brooklyn.core.mgmt.ManagementContextInjectable;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.rest.BrooklynRestApi;
import org.apache.brooklyn.rest.BrooklynRestApiLauncherTest;
import org.apache.brooklyn.rest.util.BrooklynRestResourceUtils;
import org.apache.brooklyn.rest.util.ManagementContextProvider;
import org.apache.brooklyn.rest.util.NoOpRecordingShutdownHandler;
import org.apache.brooklyn.rest.util.NullHttpServletRequestProvider;
import org.apache.brooklyn.rest.util.NullServletConfigProvider;
import org.apache.brooklyn.rest.util.ShutdownHandlerProvider;
import org.apache.brooklyn.rest.util.json.BrooklynJacksonJsonProvider;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeMethod;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.core.DefaultResourceConfig;
import com.sun.jersey.test.framework.AppDescriptor;
import com.sun.jersey.test.framework.JerseyTest;
import com.sun.jersey.test.framework.LowLevelAppDescriptor;
import com.sun.jersey.test.framework.spi.container.TestContainer;
import com.sun.jersey.test.framework.spi.container.TestContainerException;
import com.sun.jersey.test.framework.spi.container.TestContainerFactory;
import com.sun.jersey.test.framework.spi.container.inmemory.InMemoryTestContainerFactory;

public abstract class BrooklynRestApiTest {

    protected ManagementContext manager;
    
    protected boolean useLocalScannedCatalog = false;
    protected NoOpRecordingShutdownHandler shutdownListener = createShutdownHandler();

    @BeforeMethod(alwaysRun = true)
    public void setUpMethod() {
        shutdownListener.reset();
    }
    
    protected synchronized void useLocalScannedCatalog() {
        if (manager!=null && !useLocalScannedCatalog)
            throw new IllegalStateException("useLocalScannedCatalog must be specified before manager is accessed/created");
        useLocalScannedCatalog = true;
    }
    
    private NoOpRecordingShutdownHandler createShutdownHandler() {
        return new NoOpRecordingShutdownHandler();
    }

    protected synchronized ManagementContext getManagementContext() {
        if (manager==null) {
            if (useLocalScannedCatalog) {
                manager = new LocalManagementContext();
                BrooklynRestApiLauncherTest.forceUseOfDefaultCatalogWithJavaClassPath(manager);
            } else {
                manager = new LocalManagementContextForTests();
            }
            manager.getHighAvailabilityManager().disabled();
            BasicLocationRegistry.addNamedLocationLocalhost(manager);
            
            new BrooklynCampPlatformLauncherNoServer()
                .useManagementContext(manager)
                .launch();
        }
        return manager;
    }
    
    protected ObjectMapper mapper() {
        return BrooklynJacksonJsonProvider.findSharedObjectMapper(getManagementContext());
    }
    
    @AfterClass
    public void tearDown() throws Exception {
        destroyManagementContext();
    }

    protected void destroyManagementContext() {
        if (manager!=null) {
            Entities.destroyAll(manager);
            manager = null;
        }
    }
    
    public LocationRegistry getLocationRegistry() {
        return new BrooklynRestResourceUtils(getManagementContext()).getLocationRegistry();
    }

    private JerseyTest jerseyTest;
    protected DefaultResourceConfig config = new DefaultResourceConfig();
    
    protected final void addResource(Object resource) {
        Preconditions.checkNotNull(config, "Must run before setUpJersey");
        
        if (resource instanceof Class)
            config.getClasses().add((Class<?>)resource);
        else
            config.getSingletons().add(resource);
        
        if (resource instanceof ManagementContextInjectable) {
            ((ManagementContextInjectable)resource).setManagementContext(getManagementContext());
        }
    }
    
    protected final void addProvider(Class<?> provider) {
        Preconditions.checkNotNull(config, "Must run before setUpJersey");
        
        config.getClasses().add(provider);
    }
    
    protected void addDefaultResources() {
        // seems we have to provide our own injector because the jersey test framework 
        // doesn't inject ServletConfig and it all blows up
        // and the servlet config provider must be an instance; addClasses doesn't work for some reason
        addResource(new NullServletConfigProvider());
        addResource(new ManagementContextProvider(getManagementContext()));
        addProvider(NullHttpServletRequestProvider.class);
        addResource(new ShutdownHandlerProvider(shutdownListener));
    }

    protected final void setUpResources() {
        addDefaultResources();
        addBrooklynResources();
        for (Object r: BrooklynRestApi.getMiscResources())
            addResource(r);
    }

    /** intended for overriding if you only want certain resources added, or additional ones added */
    protected void addBrooklynResources() {
        for (Object r: BrooklynRestApi.getBrooklynRestResources())
            addResource(r);
    }

    protected void setUpJersey() {
        setUpResources();
        
        jerseyTest = createJerseyTest();
        config = null;
        try {
            jerseyTest.setUp();
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    protected JerseyTest createJerseyTest() {
        return new JerseyTest() {
            @Override
            protected AppDescriptor configure() {
                return new LowLevelAppDescriptor.Builder(config).build();
            }

            @Override
            protected TestContainerFactory getTestContainerFactory() throws TestContainerException {
                return new TestContainerFactory() {
                    TestContainerFactory delegate = new InMemoryTestContainerFactory();
                    
                    @Override
                    public Class<? extends AppDescriptor> supports() {
                        return delegate.supports();
                    }
                    
                    @Override
                    public TestContainer create(URI baseUri, AppDescriptor ad)
                            throws IllegalArgumentException {
                        URI uri = URI.create(baseUri.toString() + "v1/");
                        System.out.println(uri);;
                        return delegate.create(uri, (LowLevelAppDescriptor)ad);
                    }
                };
            }
        };
    }
    
    protected void tearDownJersey() {
        if (jerseyTest != null) {
            try {
                jerseyTest.tearDown();
            } catch (Exception e) {
                throw Exceptions.propagate(e);
            }
        }
        config = new DefaultResourceConfig();
    }

    public Client client() {
        Preconditions.checkNotNull(jerseyTest, "Must run setUpJersey first");
        return jerseyTest.client();
    }

    public WebResource resource(String uri) {
        Preconditions.checkNotNull(jerseyTest, "Must run setUpJersey first");
        return jerseyTest.resource().path(uri);
    }
}
