/* The MIT License
 *
 * Copyright (c) 2010-2015 Danila A. (atmakin.dv@gmail.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 */
package io.cyberstock.tcdop.server.integration.teamcity;

import com.intellij.openapi.diagnostic.Logger;
import io.cyberstock.tcdop.model.DOConfigConstants;
import io.cyberstock.tcdop.model.DOSettings;
import io.cyberstock.tcdop.server.error.UnsupportedDOModeError;
import io.cyberstock.tcdop.server.integration.digitalocean.DOAsyncClientService;
import io.cyberstock.tcdop.server.integration.digitalocean.storage.CloudImageStorage;
import io.cyberstock.tcdop.server.integration.digitalocean.storage.CloudImageStorageFactory;
import io.cyberstock.tcdop.server.integration.digitalocean.DOAsyncClientServiceFactory;
import io.cyberstock.tcdop.server.integration.teamcity.web.ConfigurationValidator;
import io.cyberstock.tcdop.server.integration.teamcity.web.DOSettingsUtils;
import io.cyberstock.tcdop.server.integration.teamcity.web.TCDOPSettingsController;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static io.cyberstock.tcdop.server.integration.teamcity.web.DOSettingsUtils.convertToDOSettings;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by beolnix on 08/05/15.
 */
public class DOCloudClientFactory implements CloudClientFactory {

    // dependencies
    private final DOAsyncClientServiceFactory asyncClientServiceFactory;
    private final CloudImageStorageFactory cloudImageStorageFactory;
    private final ConfigurationValidator configValidator;

    // state
    private final String doProfileHtmlPath;
    private Integer backgroundThreadsLimit = 4; //default value

    // constants
    private static final Logger LOG = Logger.getInstance(DOCloudClientFactory.class.getName());

    private final static String DISPLAY_NAME = "Digital Ocean";

    public DOCloudClientFactory(@NotNull final CloudRegistrar cloudRegistrar,
                                @NotNull final PluginDescriptor pluginDescriptor,
                                @NotNull final DOAsyncClientServiceFactory asyncClientServiceFactory,
                                @NotNull final CloudImageStorageFactory cloudImageStorageFactory,
                                @NotNull final ConfigurationValidator configValidator) {
        this.asyncClientServiceFactory = asyncClientServiceFactory;
        this.cloudImageStorageFactory = cloudImageStorageFactory;
        this.configValidator = configValidator;

        this.doProfileHtmlPath = pluginDescriptor.getPluginResourcesPath(TCDOPSettingsController.HTML_PAGE_NAME);
        cloudRegistrar.registerCloudFactory(this);

        LOG.info("Digital Ocean client factory initialized. Settings HTML path: " + doProfileHtmlPath);
    }

    @NotNull
    public CloudClientEx createNewClient(CloudState cloudState, CloudClientParameters cloudClientParameters) {
        DOSettings settings = convertToDOSettings(cloudClientParameters);

        if (!settings.isPreparedInstanceMode()) {
            throw new UnsupportedDOModeError(settings.getMode());
        }

        ExecutorService executor = Executors.newFixedThreadPool(backgroundThreadsLimit);
        DOAsyncClientService asyncClient = asyncClientServiceFactory.createClient(executor, settings.getToken());
        CloudImageStorage imageStorage = cloudImageStorageFactory.createStorage(executor, settings.getToken());

        return new DOCloudClient(settings, asyncClient, imageStorage, executor);
    }

    @NotNull
    public String getCloudCode() {
        return DOConfigConstants.CLOUD_CODE;
    }

    @NotNull
    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Nullable
    public String getEditProfileUrl() {
        return doProfileHtmlPath;
    }

    @NotNull
    public Map<String, String> getInitialParameterValues() {
        return Collections.<String, String>emptyMap();
    }

    @NotNull
    public PropertiesProcessor getPropertiesProcessor() {
        return new PropertiesProcessor() {
            public Collection<InvalidProperty> process(Map<String, String> stringStringMap) {
                Collection<InvalidProperty> errors = configValidator.formatValidation(stringStringMap);
                if (errors.size() > 0) {
                    return errors;
                } else {
                    DOSettings doSettings = DOSettingsUtils.convertToDOSettings(stringStringMap);
                    return configValidator.validateConfigurationValues(doSettings);
                }
            }
        };
    }

    public boolean canBeAgentOfType(AgentDescription agentDescription) {
        final Map<String, String> configParams = agentDescription.getAvailableParameters();
        boolean result = configParams.containsKey(DOConfigConstants.IDENTITY_KEY)
                && DOConfigConstants.IDENTITY_VALUE.equals(configParams.get(DOConfigConstants.IDENTITY_KEY));

        if (LOG.isDebugEnabled()) {
            String agentName = agentDescription.getConfigurationParameters().get(DOConfigConstants.AGENT_NAME_PROP);
            LOG.debug("Agent with name: " + agentName + " can be managed by DO plugin: " + result);
        }

        return result;
    }

    public void setBackgroundThreadsLimit(Integer backgroundThreadsLimit) {
        if (backgroundThreadsLimit > 0) {
            this.backgroundThreadsLimit = backgroundThreadsLimit;
            LOG.info("Number of allowed background threads is " + backgroundThreadsLimit);
        } else {
            LOG.warn("Somebody tried to set negative number of background threads " + backgroundThreadsLimit + ". " +
                     "Falling back to the default value: " + this.backgroundThreadsLimit);
        }
    }
}
