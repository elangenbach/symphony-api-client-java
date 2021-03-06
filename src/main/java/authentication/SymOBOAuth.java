package authentication;

import configuration.SymConfig;
import exceptions.NoConfigException;
import model.ClientError;
import model.Token;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.HttpClientBuilderHelper;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.concurrent.TimeUnit;

public class SymOBOAuth  {
    private final Logger logger = LoggerFactory.getLogger(SymOBOAuth.class);
    private String sessionToken;
    private SymConfig config;
    private Client sessionAuthClient;

    public SymOBOAuth(SymConfig config){
        this.config = config;
        ClientBuilder clientBuilder = HttpClientBuilderHelper.getHttpClientAppBuilder(config);
        Client client = clientBuilder.build();
        if(config.getProxyURL()==null){
            this.sessionAuthClient = client;
        }
        else {
            ClientConfig clientConfig = new ClientConfig();
            clientConfig.property(ClientProperties.PROXY_URI, config.getProxyURL());
            if(config.getProxyUsername()!=null && config.getProxyPassword()!=null) {
                clientConfig.property(ClientProperties.PROXY_USERNAME,config.getProxyUsername());
                clientConfig.property(ClientProperties.PROXY_PASSWORD,config.getProxyPassword());
            }
            Client proxyClient = clientBuilder.withConfig(clientConfig).build();
            this.sessionAuthClient = proxyClient;
        }
    }

    public SymOBOAuth(SymConfig config, ClientConfig sessionAuthClientConfig) {
        this.config = config;
        ClientBuilder clientBuilder = HttpClientBuilderHelper.getHttpClientAppBuilder(config);
        if (sessionAuthClientConfig!=null){
            this.sessionAuthClient = clientBuilder.withConfig(sessionAuthClientConfig).build();
        } else {
            this.sessionAuthClient = clientBuilder.build();
        }
    }

    public SymOBOUserAuth getUserAuth(String username){
        SymOBOUserAuth userAuth = new SymOBOUserAuth(config,sessionAuthClient,username, this);
        userAuth.authenticate();
        return userAuth;
    }

    public SymOBOUserAuth getUserAuth(Long uid){
        SymOBOUserAuth userAuth = new SymOBOUserAuth(config,sessionAuthClient,uid, this);
        userAuth.authenticate();
        return userAuth;
    }

    public void sessionAppAuthenticate() {
        if (config!=null) {
            logger.info("Session app auth");
            Response response
                    = sessionAuthClient.target(AuthEndpointConstants.HTTPSPREFIX + config.getSessionAuthHost() + ":" + config.getSessionAuthPort())
                    .path(AuthEndpointConstants.SESSIONAPPAUTH)
                    .request(MediaType.APPLICATION_JSON)
                    .post(null);
            if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                try {
                    ClientError error = response.readEntity((ClientError.class));
                    if (response.getStatus() == 400){
                        logger.error("Client error occurred", error);
                    } else if (response.getStatus() == 401){
                        logger.error("User unauthorized, refreshing tokens");
                    } else if (response.getStatus() == 403){
                        logger.error("Forbidden: Caller lacks necessary entitlement.");
                    } else if (response.getStatus() == 500) {
                        logger.error(error.getMessage());
                    }
                } catch (Exception e){
                    logger.error("Unexpected error");
                    e.printStackTrace();
                }
                try {
                    TimeUnit.SECONDS.sleep(30);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                sessionAppAuthenticate();
            } else {
                Token sessionTokenResponseContent = response.readEntity(Token.class);
                this.sessionToken = sessionTokenResponseContent.getToken();
            }
        } else {
            try {
                throw new NoConfigException("Must provide a SymConfig object to authenticate");
            } catch (NoConfigException e) {
                logger.error(e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public String getSessionToken() {
        return this.sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }


}
