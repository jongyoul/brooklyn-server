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
package org.apache.brooklyn.rest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.ws.rs.ext.ContextResolver;

import org.apache.brooklyn.rest.filter.HaHotCheckResourceFilter;
import org.apache.brooklyn.rest.filter.SwaggerFilter;
import org.apache.brooklyn.rest.util.ManagementContextProvider;
import org.apache.brooklyn.util.collections.MutableList;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;

import com.sun.jersey.api.container.filter.GZIPContentEncodingFilter;
import com.sun.jersey.api.core.DefaultResourceConfig;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.spi.container.ContainerRequestFilter;
import com.sun.jersey.spi.container.ContainerResponseFilter;
import com.sun.jersey.spi.container.ResourceFilterFactory;
import com.sun.jersey.spi.container.servlet.ServletContainer;

public class RestApiSetup {

    public static void installRest(ServletContextHandler context, Object... providers) {
        List<Object> providerList = MutableList.copyOf(Arrays.asList(providers))
                .append(new GZIPContentEncodingFilter());

        injectHa(providers);

        ResourceConfig config = new DefaultResourceConfig();
        // load all our REST API modules, JSON, and Swagger
        for (Object r: BrooklynRestApi.getAllResources())
            config.getSingletons().add(r);
        for (Object o: getProvidersOfType(providerList, ContextResolver.class))
            config.getSingletons().add(o);

        config.getProperties().put(ResourceConfig.PROPERTY_CONTAINER_REQUEST_FILTERS, getProvidersOfType(providerList, ContainerRequestFilter.class));
        config.getProperties().put(ResourceConfig.PROPERTY_CONTAINER_RESPONSE_FILTERS, getProvidersOfType(providerList, ContainerResponseFilter.class));
        config.getProperties().put(ResourceConfig.PROPERTY_RESOURCE_FILTER_FACTORIES, getProvidersOfType(providerList, ResourceFilterFactory.class));

        // configure to match empty path, or any thing which looks like a file path with /assets/ and extension html, css, js, or png
        // and treat that as static content
        config.getProperties().put(ServletContainer.PROPERTY_WEB_PAGE_CONTENT_REGEX, "(/?|[^?]*/assets/[^?]+\\.[A-Za-z0-9_]+)");
        // and anything which is not matched as a servlet also falls through (but more expensive than a regex check?)
        config.getFeatures().put(ServletContainer.FEATURE_FILTER_FORWARD_ON_404, true);
        // finally create this as a _filter_ which falls through to a web app or something (optionally)
        FilterHolder filterHolder = new FilterHolder(new ServletContainer(config));

        filterHolder.setInitParameter(ServletContainer.PROPERTY_FILTER_CONTEXT_PATH, "/v1");
        context.addFilter(filterHolder, "/v1/*", EnumSet.allOf(DispatcherType.class));

        installServletFilters(context, SwaggerFilter.class);
    }
    
    // An ugly hack to work around injection missing for HaHotCheckProvider
    private static void injectHa(Object[] providers) {
        HaHotCheckResourceFilter haFilter = null;
        ManagementContextProvider mgmtProvider = null;
        for (Object o : providers) {
            if (o instanceof HaHotCheckResourceFilter) {
                haFilter = (HaHotCheckResourceFilter) o;
            } else if (o instanceof ManagementContextProvider) {
                mgmtProvider = (ManagementContextProvider) o;
            }
        }
        haFilter.setManagementContext(mgmtProvider);
    }

    private static List<Object> getProvidersOfType(List<Object> providers, Class<?> type) {
        List<Object> ret = new ArrayList<>();
        for (Object o : providers) {
            if (type.isInstance(o)) {
                ret.add(o);
            }
        }
        return ret;
    }

    @SafeVarargs
    public static void installServletFilters(ServletContextHandler context, Class<? extends Filter>... filters) {
        installServletFilters(context, Arrays.asList(filters));
    }

    public static void installServletFilters(ServletContextHandler context, Collection<Class<? extends Filter>> filters) {
        for (Class<? extends Filter> filter : filters) {
            context.addFilter(filter, "/*", EnumSet.allOf(DispatcherType.class));
        }
    }
}
