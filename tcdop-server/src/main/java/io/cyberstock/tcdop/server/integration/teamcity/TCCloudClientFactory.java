package io.cyberstock.tcdop.server.integration.teamcity;

import com.intellij.openapi.diagnostic.Logger;
import com.myjeeva.digitalocean.impl.DigitalOceanClient;
import io.cyberstock.tcdop.model.DOConfigConstants;
import io.cyberstock.tcdop.model.DOSettings;
import io.cyberstock.tcdop.server.error.UnsupportedDOModeError;
import io.cyberstock.tcdop.server.integration.digitalocean.DOAsyncClientService;
import io.cyberstock.tcdop.server.integration.digitalocean.DOAsyncClientServiceFactory;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static io.cyberstock.tcdop.util.SettingsUtils.convertClientParametersToDOSettings;

import static io.cyberstock.tcdop.server.service.ConfigurationValidator.validateConfiguration;

import java.util.*;

/**
 * Created by beolnix on 08/05/15.
 */
public class TCCloudClientFactory implements CloudClientFactory {

    // dependencies
    private final DOAsyncClientServiceFactory asyncClientServiceFactory;

    // state
    private DigitalOceanClient doClient;
    private final String doProfileJspPath;

    // constants
    private static final Logger LOG = Logger.getInstance(TCCloudClientFactory.class.getName());
    private final static String DO_SETTINGS_PAGE_NAME = "do-profile-settings.jsp";
    private final static String DISPLAY_NAME = "Digital ocean type";


    public TCCloudClientFactory(@NotNull final CloudRegistrar cloudRegistrar,
                                @NotNull final PluginDescriptor pluginDescriptor,
                                @NotNull final DOAsyncClientServiceFactory asyncClientServiceFactory) {
        this.asyncClientServiceFactory = asyncClientServiceFactory;

        this.doProfileJspPath = pluginDescriptor.getPluginResourcesPath(DO_SETTINGS_PAGE_NAME);
        cloudRegistrar.registerCloudFactory(this);
    }


    @NotNull
    public CloudClientEx createNewClient(CloudState cloudState, CloudClientParameters cloudClientParameters) {
        DOSettings settings = convertClientParametersToDOSettings(cloudClientParameters);

        if (!settings.isPreparedInstanceMode()) {
            throw new UnsupportedDOModeError(settings.getMode());
        }

        DOAsyncClientService client = asyncClientServiceFactory.createClient(settings.getToken());

        TCCloudClient cloudClient = new TCCloudClient(settings, client);
        cloudClient.setReadyFlag(true);

        return cloudClient;
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
        return doProfileJspPath;
    }

    @NotNull
    public Map<String, String> getInitialParameterValues() {
        return Collections.<String, String>emptyMap();
    }

    @NotNull
    public PropertiesProcessor getPropertiesProcessor() {
        return new PropertiesProcessor() {
            public Collection<InvalidProperty> process(Map<String, String> stringStringMap) {
                DOSettings doSettings = new DOSettings(stringStringMap);
                return validateConfiguration(doSettings);
            }
        };
    }

    public boolean canBeAgentOfType(AgentDescription agentDescription) {
        final Map<String, String> configParams = agentDescription.getConfigurationParameters();

        return configParams.containsKey(DOConfigConstants.IDENTITY_KEY)
                && DOConfigConstants.IDENTITY_VALUE.equals(configParams.get(DOConfigConstants.IDENTITY_KEY));
    }
}