package io.cyberstock.tcdop.server;

import io.cyberstock.tcdop.api.DOConfigConstants;
import io.cyberstock.tcdop.util.DOMessagesHelper;
import jetbrains.buildServer.clouds.*;
import jetbrains.buildServer.serverSide.AgentDescription;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;


import java.util.HashMap;
import java.util.Map;

/**
 * Created by beolnix on 08/05/15.
 */
public class DOCloudClientFactory implements CloudClientFactory {

    @NotNull private final DOMessagesHelper msg;
    @NotNull private final String doProfileJspPath;
    @NotNull private final PropertiesProcessor doPropertiesProcessor = new DOPropertiesProcessor();

    public DOCloudClientFactory(@NotNull final CloudRegistrar cloudRegistrar,
                                @NotNull final PluginDescriptor pluginDescriptor,
                                @NotNull DOMessagesHelper msg) {
        this.msg = msg;
        this.doProfileJspPath = pluginDescriptor.getPluginResourcesPath("do-profile.jsp");

        cloudRegistrar.registerCloudFactory(this);
    }

    @NotNull
    public CloudClientEx createNewClient(CloudState cloudState, CloudClientParameters cloudClientParameters) {
        //TODO: implement this
        throw new NotImplementedException();
    }

    @NotNull
    public String getCloudCode() {
        return DOConfigConstants.CLOUD_CODE;
    }

    @NotNull
    public String getDisplayName() {
        return msg.getMsg("tcdop.server.display_name");
    }

    @Nullable
    public String getEditProfileUrl() {
        return doProfileJspPath;
    }

    @NotNull
    public Map<String, String> getInitialParameterValues() {
        final HashMap<String, String> initialParamsMap = new HashMap<String, String>();

        //TODO: Add initial params here if required

        return initialParamsMap;
    }

    @NotNull
    public PropertiesProcessor getPropertiesProcessor() {
        //TODO: implement this
        throw new NotImplementedException();
    }

    public boolean canBeAgentOfType(AgentDescription agentDescription) {
        final Map<String, String> configParams = agentDescription.getConfigurationParameters();

        return configParams.containsKey(DOConfigConstants.IDENTITY_KEY)
                && DOConfigConstants.IDENTITY_VALUE.equals(configParams.get(DOConfigConstants.IDENTITY_KEY));
    }
}
