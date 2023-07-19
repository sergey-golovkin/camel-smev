package org.apache.camel.component.smev3;

import org.apache.camel.component.smev3.strategy.AttachmentsStrategy;
import org.apache.camel.component.smev3.strategy.InMemoryAttachmentsStrategy;
import org.apache.camel.support.DefaultComponent;
import org.springframework.core.env.Environment;
import ru.voskhod.smev.client.api.configuration.SMEVVersion;
import ru.voskhod.smev.client.api.message.configuration.MessageMapperConfigurationImpl;
import ru.voskhod.smev.client.api.services.identification.configuration.IdentityServiceConfiguration;
import ru.voskhod.smev.client.api.services.messaging.configuration.MessageGenerationConfiguration;
import ru.voskhod.smev.client.api.services.signature.configuration.SignerConfiguration;
import ru.voskhod.smev.client.api.services.template.configuration.GeoTemplateConfiguration;
import ru.voskhod.smev.client.api.services.template.configuration.WSTemplateConfiguration;
import ru.voskhod.smev.client.api.services.transport.configuration.GeoMessageTransportConfiguration;
import ru.voskhod.smev.client.api.services.transport.configuration.LargeAttachmentTransportConfiguration;
import ru.voskhod.smev.client.api.services.transport.configuration.MessageTransportConfiguration;
import ru.voskhod.smev.client.api.services.transport.configuration.TransportEndpointConfiguration;
import ru.voskhod.smev.client.api.services.validation.configuration.ValidatorConfiguration;
import ru.voskhod.smev.client.api.transport.configuration.TransportEndpointConfigurationImpl;
import ru.voskhod.smev.client.api.types.message.system.processing.ProcessingInformation;
import ru.voskhod.smev.client.api.types.message.system.processing.QueryInformation;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class Smev3Configuration
{
    public enum Smev3BodyType
    {
        Content, Envelop, SMEVMessage
    }

    public enum Smev3Mode
    {
        Request,
        Response,
        Status,
        Reject,
        Ack
    }


    private static final int DEFAULT_ERROR_DELAY = 60000;
    private static final int DEFAULT_TIMEOUT_MILLIS = 1000;
    private static final int DEFAULT_RETRIES_COUNT = 5;
    private static final int DEFAULT_LARGE_ATTACHMENT_THRESHOLD = 512 * 1024;
    private static final String DEFAULT_FTP_LOGIN = "anonymous";
    private static final String DEFAULT_FTP_PASSWORD = "smev";

    private QueryInformation queryInformation;
    private Smev3Mode mode;
    private boolean isGreedy;
    private Smev3BodyType bodyType;
    private int errorDelay;
    private boolean isAutoAck;
    private boolean omitXMLDeclaration;
    private AttachmentsStrategy attachmentsStrategy;

    private String signerFileCertificateStore;
    private String signerCertificateAlias;
    private String signerPrivateKeyAlias;
    private String signerPrivateKeyPassword;
    private String signerKeyStoreType;

    private String ftpAddress;
    private String ftpLogin;
    private String ftpPassword;
    private int ftpMaxAttempts;
    private int ftpTimeout;
    private int largeAttachmentThreshold;

    private String transportMainUrl;
    private int transportMainTimeout;
    private int transportMainRetriesCount;
    private String transportReserveUrl;
    private int transportReserveTimeout;
    private int transportReserveRetriesCount;
    private SMEVVersion smevVersion;

    private boolean validateInput;
    private boolean validateOutput;
    private boolean loggingInput;
    private boolean loggingOutput;
    private boolean validateSMEVSignature;

    private String messageStorage;

    private SignerConfiguration signerConfiguration;
    private MessageTransportConfiguration messageTransportConfiguration;
    private WSTemplateConfiguration wsTemplateConfiguration;
    private GeoTemplateConfiguration geoTemplateConfiguration;
    private LargeAttachmentTransportConfiguration largeAttachmentTransportConfiguration;
    private MessageGenerationConfiguration messageGenerationConfiguration;


    public Smev3Configuration(DefaultComponent component, String uri, String remaining, Map<String, Object> parameters, Environment environment) throws ClassNotFoundException
    {
        QueryInformation.Type type;
        if (remaining.equalsIgnoreCase(Smev3Mode.Request.toString()))
        {
            type = ProcessingInformation.Type.REQUEST;
            mode = Smev3Mode.Request;
        }
        else if (remaining.equalsIgnoreCase(Smev3Mode.Response.toString()))
        {
            type = ProcessingInformation.Type.RESPONSE;
            mode = Smev3Mode.Response;
        }
        else if (remaining.equalsIgnoreCase(Smev3Mode.Status.toString()))
        {
            type = ProcessingInformation.Type.STATUS;
            mode = Smev3Mode.Status;
        }
        else if (remaining.equalsIgnoreCase(Smev3Mode.Reject.toString()))
        {
            type = ProcessingInformation.Type.RESPONSE;
            mode = Smev3Mode.Reject;
        }
        else if (remaining.equalsIgnoreCase(Smev3Mode.Ack.toString()))
        {
            type = ProcessingInformation.Type.REQUEST;
            mode = Smev3Mode.Ack;
        }
        else
            throw new IllegalArgumentException("Invalid type: (" + remaining + "). Must be type in (Request, Response, Reject, Status, Ack)");

        smevVersion = SMEVVersion.getByValue(component.getAndRemoveParameter(parameters, "version", String.class, SMEVVersion.V1_3.getValue()), true);

        queryInformation = new QueryInformation(component.getAndRemoveParameter(parameters, "nodeId", String.class, null), component.getAndRemoveParameter(parameters, "namespaceURI", String.class, null), component.getAndRemoveParameter(parameters, "rootElementLocalName", String.class, null), type);

        String param = component.getAndRemoveParameter(parameters, "bodyType", String.class, Smev3BodyType.Content.toString());
        if (param.equalsIgnoreCase(Smev3BodyType.Content.toString()))
            bodyType = Smev3BodyType.Content;
        else if (param.equalsIgnoreCase(Smev3BodyType.Envelop.toString()))
            bodyType = Smev3BodyType.Envelop;
        else if (param.equalsIgnoreCase(Smev3BodyType.SMEVMessage.toString()))
            bodyType = Smev3BodyType.SMEVMessage;
        else
            throw new IllegalArgumentException("Invalid bodyType: (" + param + "). Must be type in (Content, Envelop, SMEVMessage)");

        isGreedy = component.getAndRemoveParameter(parameters, "greedy", Boolean.class, true);
        errorDelay = component.getAndRemoveParameter(parameters, "errorDelay", Integer.class, DEFAULT_ERROR_DELAY);
        isAutoAck = component.getAndRemoveParameter(parameters, "autoAck", Boolean.class, true);
        omitXMLDeclaration = component.getAndRemoveParameter(parameters, "omitXMLDeclaration", Boolean.class, true);

        ftpAddress = environment.getProperty("smev3.large.attachment.transport.address");
        ftpLogin = environment.getProperty("smev3.large.attachment.transport.login", String.class, DEFAULT_FTP_LOGIN);
        ftpPassword = environment.getProperty("smev3.large.attachment.transport.password", String.class, DEFAULT_FTP_PASSWORD);
        ftpMaxAttempts = environment.getProperty("smev3.large.attachment.transport.retries", Integer.class, DEFAULT_RETRIES_COUNT);
        ftpTimeout = environment.getProperty("smev3.large.attachment.transport.timeout", Integer.class, DEFAULT_TIMEOUT_MILLIS);
        largeAttachmentThreshold = environment.getProperty("smev3.large.attachment.threshold", Integer.class, DEFAULT_LARGE_ATTACHMENT_THRESHOLD);

        signerFileCertificateStore = environment.getProperty("smev3.signer.certificate.store");
        signerCertificateAlias = environment.getProperty("smev3.signer.certificate.alias");
        signerPrivateKeyAlias = environment.getProperty("smev3.signer.private.key.alias");
        signerPrivateKeyPassword = environment.getProperty("smev3.signer.private.key.password");
        signerKeyStoreType = environment.getProperty("smev3.signer.key.store.type", String.class, "HDImageStore");

        transportMainUrl = environment.getProperty("smev3.transport." + smevVersion.getValue() + ".main.url");
        transportMainTimeout = environment.getProperty("smev3.transport." + smevVersion.getValue() + ".main.timeout", Integer.class, DEFAULT_TIMEOUT_MILLIS);
        transportMainRetriesCount = environment.getProperty("smev3.transport." + smevVersion.getValue() + ".main.retries.count", Integer.class, DEFAULT_RETRIES_COUNT);

        transportReserveUrl = environment.getProperty("smev3.transport." + smevVersion.getValue() + ".reserve.url");
        transportReserveTimeout = environment.getProperty("smev3.transport." + smevVersion.getValue() + ".reserve.timeout", Integer.class, DEFAULT_TIMEOUT_MILLIS);
        transportReserveRetriesCount = environment.getProperty("smev3.transport." + smevVersion.getValue() + ".reserve.retries.count", Integer.class, DEFAULT_RETRIES_COUNT);

        validateInput = environment.getProperty("smev3.validate.input", Boolean.class, true);
        validateOutput = environment.getProperty("smev3.validate.output", Boolean.class, true);
        loggingInput = environment.getProperty("smev3.log.input", Boolean.class, false);
        loggingOutput = environment.getProperty("smev3.log.output", Boolean.class, false);
        validateSMEVSignature = environment.getProperty("smev3.validate.smev.signature", Boolean.class, true);

        messageStorage = environment.getProperty("smev3.message.storage", String.class, null);

        attachmentsStrategy = component.getAndRemoveOrResolveReferenceParameter(parameters, "attachmentsStrategy", AttachmentsStrategy.class, new InMemoryAttachmentsStrategy());
    }

    public QueryInformation getQueryInformation()
    {
        return queryInformation;
    }

    public Smev3Mode getMode()
    {
        return mode;
    }

    public Smev3BodyType getBodyType()
    {
        return bodyType;
    }

    public boolean isGreedy()
    {
        return isGreedy;
    }

    public int getErrorDelay()
    {
        return errorDelay;
    }

    public boolean isAutoAck()
    {
        return isAutoAck;
    }

    public boolean isOmitXMLDeclaration()
    {
        return omitXMLDeclaration;
    }

    public int getLargeAttachmentThreshold()
    {
        return largeAttachmentThreshold;
    }

    public AttachmentsStrategy getAttachmentsStrategy()
    {
        return attachmentsStrategy;
    }

    public SMEVVersion getSmevVersion()
    {
        return smevVersion;
    }

    public SignerConfiguration getSignerConfiguration()
    {
        if (this.signerConfiguration == null)
        {
            this.signerConfiguration = new SignerConfiguration()
            {
                public String getProviderName()
                {
                    return "JCP2";
                }

                public String getCertificateAlias()
                {
                    return signerCertificateAlias;
                }

                public String getPrivateKeyAlias()
                {
                    return signerPrivateKeyAlias;
                }

                public String getPrivateKeyPassword()
                {
                    return signerPrivateKeyPassword;
                }

                public File getSMEVFileCertificateStore()
                {
                    return signerFileCertificateStore == null ? null : new File(signerFileCertificateStore);
                }

                public String getKeyStoreType()
                {
                    return signerKeyStoreType;
                }
            };
        }

        return this.signerConfiguration;
    }

    public MessageTransportConfiguration getMessageTransportConfiguration()
    {
        if (this.messageTransportConfiguration == null)
        {
            this.messageTransportConfiguration = new MessageTransportConfiguration()
            {
                public String getUrl()
                {
                    return transportMainUrl;
                }

                public int getTimeout()
                {
                    return transportMainTimeout;
                }
            };
        }

        return this.messageTransportConfiguration;
    }

    public LargeAttachmentTransportConfiguration getLargeAttachmentTransportConfiguration()
    {
        if (this.largeAttachmentTransportConfiguration == null)
        {
            this.largeAttachmentTransportConfiguration = new LargeAttachmentTransportConfiguration()
            {
                public String getAddress()
                {
                    return ftpAddress;
                }

                public String getLogin()
                {
                    return ftpLogin;
                }

                public String getPass()
                {
                    return ftpPassword;
                }

                public int getMaxAttempts()
                {
                    return ftpMaxAttempts;
                }

                public int getTimeout()
                {
                    return ftpTimeout;
                }
            };
        }

        return this.largeAttachmentTransportConfiguration;
    }

    public WSTemplateConfiguration getWSTemplateConfiguration()
    {
        if (this.wsTemplateConfiguration == null)
        {
            this.wsTemplateConfiguration = new WSTemplateConfiguration()
            {
                public IdentityServiceConfiguration getIdentityConfig()
                {
                    return null;
                }

                public SignerConfiguration getSignerConfig()
                {
                    return getSignerConfiguration();
                }

                public ValidatorConfiguration getValidatorConfig()
                {
                    return null;
                }

                public MessageGenerationConfiguration getMessageGenerationConfig()
                {
                    if(messageGenerationConfiguration == null)
                    {
                        messageGenerationConfiguration = new MessageMapperConfigurationImpl();
                        messageGenerationConfiguration.setSmevRejectionExceptionConfiguration(new ArrayList<>());
                    }
                    return messageGenerationConfiguration;
                }

                public LargeAttachmentTransportConfiguration getLargeAttachmentTransportConfig()
                {
                    return getLargeAttachmentTransportConfiguration();
                }

                public boolean getLoggingOutput()
                {
                    return loggingOutput;
                }

                public boolean getValidateOutput()
                {
                    return validateOutput;
                }

                public boolean getValidateInput()
                {
                    return validateInput;
                }

                public boolean getLoggingInput()
                {
                    return loggingInput;
                }

                public boolean getValidateSMEVSignature()
                {
                    return validateSMEVSignature;
                }

                public String getLocalStorage()
                {
                    return null;
                } // Must be null

                public String getMessageStorage()
                {
                    return messageStorage;
                }

                public MessageTransportConfiguration getMessageTransportConfiguration()
                {
                    return Smev3Configuration.this.getMessageTransportConfiguration();
                }
            };
        }

        return this.wsTemplateConfiguration;
    }

    public GeoTemplateConfiguration getGeoTemplateConfiguration()
    {
        if (this.geoTemplateConfiguration == null)
        {
            this.geoTemplateConfiguration = new GeoTemplateConfiguration()
            {
                public IdentityServiceConfiguration getIdentityConfig()
                {
                    return null;
                }

                public SignerConfiguration getSignerConfig()
                {
                    return getSignerConfiguration();
                }

                public ValidatorConfiguration getValidatorConfig()
                {
                    return null;
                }

                public MessageGenerationConfiguration getMessageGenerationConfig()
                {
                    if(messageGenerationConfiguration == null)
                    {
                        messageGenerationConfiguration = new MessageMapperConfigurationImpl();
                        messageGenerationConfiguration.setSmevRejectionExceptionConfiguration(new ArrayList<>());
                    }
                    return messageGenerationConfiguration;
                }

                public LargeAttachmentTransportConfiguration getLargeAttachmentTransportConfig()
                {
                    return getLargeAttachmentTransportConfiguration();
                }

                public boolean getLoggingOutput()
                {
                    return loggingOutput;
                }

                public boolean getValidateOutput()
                {
                    return validateOutput;
                }

                public boolean getValidateInput()
                {
                    return validateInput;
                }

                public boolean getLoggingInput()
                {
                    return loggingInput;
                }

                public boolean getValidateSMEVSignature()
                {
                    return validateSMEVSignature;
                }

                public String getLocalStorage()
                {
                    return null;
                } // Must be null

                public String getMessageStorage()
                {
                    return messageStorage;
                }

                public GeoMessageTransportConfiguration getGeoMessageTransportConfiguration()
                {
                    return () -> {
                        List<TransportEndpointConfiguration> transports = new ArrayList<>();

                        if(transportMainUrl != null && transportMainUrl.length() > 0)
                            transports.add(new TransportEndpointConfigurationImpl(
                                    "main",
                                    transportMainUrl,
                                    transportMainTimeout,
                                    transportMainRetriesCount
                            ));

                        if(transportReserveUrl != null && transportReserveUrl.length() > 0)
                            transports.add(new TransportEndpointConfigurationImpl(
                                    "reserve",
                                    transportReserveUrl,
                                    transportReserveTimeout,
                                    transportReserveRetriesCount
                            ));
                        return transports;
                    };
                }
            };
        }

        return this.geoTemplateConfiguration;
    }
}
