package org.apache.camel.component.smev3;

import org.apache.camel.*;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.support.ScheduledPollEndpoint;

@UriEndpoint(firstVersion = "1.3.0", scheme = "smev3", title = "SMEV3", syntax = "smev3", category = { Category.MESSAGING } , headersClass = Smev3Constants.class)
public class Smev3Endpoint extends ScheduledPollEndpoint
{
    private Smev3Configuration conf;

    Smev3Endpoint(String uri, Smev3Component component, Smev3Configuration conf)
    {
        super(uri, component);
        this.conf = conf;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception
    {
        Smev3Consumer consumer = new Smev3Consumer(this, processor, conf);
        setPollStrategy(consumer);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public Producer createProducer() throws Exception
    {
        return new Smev3Producer(this, conf);
    }

    @Override
    public boolean isSingleton()
    {
        return true;
    }
}
