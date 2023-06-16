package org.apache.camel.component.smev3;

import java.util.Map;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

import java.security.Security;
import ru.CryptoPro.JCP.JCP;
import ru.CryptoPro.JCP.tools.License;
import ru.CryptoPro.JCP.pref.ConfigurationException;

@Component("smev3")
public class Smev3Component extends DefaultComponent implements EnvironmentAware
{
    private Environment environment;

    public Smev3Component()
    {
    }

    public Smev3Component(CamelContext context)
    {
        super(context);
    }

    @Override
    public void setEnvironment(Environment environment)
    {
        this.environment = environment;

        try
        {
            Security.addProvider(new JCP());
            License license = new License("", "", environment.getProperty("smev3.jcp.license.key"));
            license.store();
        }
        catch (ConfigurationException e)
        {
            // TODO log
        }
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception
    {
        return new Smev3Endpoint(uri, this, new Smev3Configuration(this, uri, remaining, parameters, environment));
    }
}
