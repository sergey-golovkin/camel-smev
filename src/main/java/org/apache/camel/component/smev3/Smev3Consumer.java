package org.apache.camel.component.smev3;

import org.apache.camel.*;
import org.apache.camel.attachment.Attachment;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.attachment.DefaultAttachment;
import org.apache.camel.component.smev3.strategy.AttachmentsStrategy;
import org.apache.camel.component.smev3.utils.ApacheFTPTransport;
import org.apache.camel.spi.PollingConsumerPollStrategy;
import org.apache.camel.support.ScheduledPollConsumer;
import org.apache.xerces.impl.dv.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.voskhod.crypto.util.XMLTransformHelper;
import ru.voskhod.smev.client.api.factory.Factory;
import ru.voskhod.smev.client.api.services.signature.Signer;
import ru.voskhod.smev.client.api.services.template.WSTemplate;
import ru.voskhod.smev.client.api.signature.impl.SignerFactory;
import ru.voskhod.smev.client.api.types.exception.SMEVException;
import ru.voskhod.smev.client.api.types.exception.SMEVRuntimeException;
import ru.voskhod.smev.client.api.types.message.SMEVMessage;
import ru.voskhod.smev.client.api.types.message.attachment.LargeAttachment;
import ru.voskhod.smev.client.api.types.message.attachment.MTOMAttachment;
import ru.voskhod.smev.client.api.types.message.attachment.SMEVAttachment;
import ru.voskhod.smev.client.api.types.message.system.processing.ProcessingInformation;
import javax.activation.DataHandler;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class Smev3Consumer extends ScheduledPollConsumer implements PollingConsumerPollStrategy
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Smev3Consumer.class);
    private final Smev3Configuration conf;
    private final WSTemplate wsTemplate;
    private final Signer signer;
    private final ApacheFTPTransport laTransport;
    private SMEVMessage message;
    private Boolean accepted;
    private final List<DataHandler> attachments = new ArrayList<>();

    Smev3Consumer(Smev3Endpoint endpoint, Processor processor, Smev3Configuration conf) throws FailedToCreateConsumerException
    {
        super(endpoint, processor);
        this.conf = conf;
        this.setGreedy(conf.isGreedy());

        try
        {
            signer = SignerFactory.getSigner(
                    conf.getSignerConfiguration(),
                    conf.getSignerConfiguration().getCertificateAlias(),
                    conf.getSignerConfiguration().getPrivateKeyAlias(),
                    conf.getSignerConfiguration().getPrivateKeyPassword());
            wsTemplate = Factory.getWsTemplateInstance(
                    signer,
                    Factory.getValidatorInstance(conf.getWSTemplateConfiguration().getValidatorConfig(), conf.getSmevVersion()),
                    Factory.getMessageMapperInstance(conf.getWSTemplateConfiguration().getMessageGenerationConfig(), conf.getSmevVersion()),
                    Factory.getMessageTransportInstance(conf.getGeoTemplateConfiguration().getGeoMessageTransportConfiguration(), conf.getSmevVersion()),
                    Factory.getExceptionMapperInstance(conf.getWSTemplateConfiguration().getMessageGenerationConfig(), conf.getSmevVersion()),
                    null, // must be null, use laTransport instead
                    conf.getGeoTemplateConfiguration(),
                    conf.getSmevVersion());
            laTransport = new ApacheFTPTransport(conf.getLargeAttachmentTransportConfiguration());
        }
        catch (SMEVRuntimeException e)
        {
            throw new FailedToCreateConsumerException(endpoint, e);
        }
    }

    @Override
    protected int poll() throws Exception
    {
        try
        {
            Exchange exchange = this.getEndpoint().createExchange(ExchangePattern.InOnly);
            accepted = null;
            message = wsTemplate.get(conf.getQueryInformation());

            if (message == null)
                return 0;

            if (message.getData() != null && message.getData().getContent() != null)
            {
                Smev3Constants.set(exchange, Smev3Constants.SMEV3_CONTENT_NAMESPACE_URI, message.getData().getContent().getNamespaceURI());
                Smev3Constants.set(exchange, Smev3Constants.SMEV3_CONTENT_ROOT_TYPE, message.getData().getContent().getLocalName());
            }

            if (conf.getBodyType().equals(Smev3Configuration.Smev3BodyType.Content) && message.getData() != null)
            {
                exchange.getMessage().setBody(XMLTransformHelper.elementToString(message.getData().getContent(), conf.isOmitXMLDeclaration()));
                Smev3Constants.set(exchange, Smev3Constants.SMEV3_CONTENT_PERSONAL_SIGNATURE, XMLTransformHelper.elementToString(message.getData().getPersonalSignature(), conf.isOmitXMLDeclaration()));
            }
            else if (conf.getBodyType().equals(Smev3Configuration.Smev3BodyType.Envelop))
            {
                exchange.getMessage().setBody(message.getInEnvelop());
                if (message.getData() != null)
                    Smev3Constants.set(exchange, Smev3Constants.SMEV3_CONTENT_PERSONAL_SIGNATURE, XMLTransformHelper.elementToString(message.getData().getPersonalSignature()));
            }
            else if (conf.getBodyType().equals(Smev3Configuration.Smev3BodyType.SMEVMessage))
            {
                exchange.getMessage().setBody(message);
                if (message.getData() != null)
                    Smev3Constants.set(exchange, Smev3Constants.SMEV3_CONTENT_PERSONAL_SIGNATURE, message.getData().getPersonalSignature());
            }

            Smev3Constants.fillExchangeHeaders(exchange, message.getSMEVMetadata());
            fillExchangeAttachments(exchange, message);

            if(conf.isAutoAck())
                Smev3Constants.set(exchange, Smev3Constants.SMEV3_MESSAGE_ACCEPTED, true);

            this.getProcessor().process(exchange);
            if(exchange.isFailed())
            {
                if(exchange.getException() != null)
                    throw CamelExecutionException.wrapCamelExecutionException(exchange, exchange.getException());
                else
                    throw new CamelExecutionException("Exception occurred during execution", exchange);
            }

            accepted = Smev3Constants.get(exchange, Smev3Constants.SMEV3_MESSAGE_ACCEPTED, Boolean.class);
            return 1; // polled messages
        }
        finally
        {
            AttachmentsStrategy attachmentsStrategy = conf.getAttachmentsStrategy();
            attachments.forEach(dh -> { try { attachmentsStrategy.done(dh); } catch(Exception ex) { } });
            attachments.clear();
        }
    }

    private void fillExchangeAttachments(Exchange exchange, SMEVMessage message) throws Exception
    {
        if(message != null && message.getData() != null)
        {
            AttachmentMessage attachmentMessage = exchange.getIn(AttachmentMessage.class);
            AttachmentsStrategy attachmentsStrategy = conf.getAttachmentsStrategy();

            int current = 0;
            int total = message.getData().getAttachments().size();
            for(SMEVAttachment attachment : message.getData().getAttachments())
            {
                if(attachment instanceof MTOMAttachment)
                {
                    MTOMAttachment mtomAttachment = (MTOMAttachment) attachment;

                    DataHandler dataHandler = attachmentsStrategy.get(
                            exchange,
                            message.getSMEVMetadata().getMessageIdentity().getMessageId(),
                            mtomAttachment.getAttachmentId(),
                            mtomAttachment.getAttachmentId(),
                            mtomAttachment.getMimeType(),
                            mtomAttachment.getSignaturePKCS7(),
                            current++,
                            total
                    );
                    if(dataHandler != null)
                    {
                        attachments.add(dataHandler);

                        try (OutputStream outputStream = dataHandler.getOutputStream(); InputStream inputStream = mtomAttachment.getContent().getInputStream())
                        {
                            inputStream.transferTo(outputStream);
                        }

                        Attachment a = new DefaultAttachment(dataHandler);
                        Smev3Constants.set(a, Smev3Constants.SMEV3_ATTACHMENT_MIMETYPE, attachment.getMimeType());
                        Smev3Constants.set(a, Smev3Constants.SMEV3_ATTACHMENT_UUID, mtomAttachment.getAttachmentId());
                        Smev3Constants.set(a, Smev3Constants.SMEV3_ATTACHMENT_NAME, mtomAttachment.getAttachmentId());
                        Smev3Constants.set(a, Smev3Constants.SMEV3_ATTACHMENT_SIGNATUREPKCS7, Base64.encode(attachment.getSignaturePKCS7()));
                        Smev3Constants.set(a, Smev3Constants.SMEV3_ATTACHMENT_PASSPORTID, attachment.getPassportId());

                        if (attachmentsStrategy.process(exchange, a, dataHandler))
                            attachmentMessage.addAttachmentObject(mtomAttachment.getAttachmentId(), a);
                    }
                }
                else if(attachment instanceof LargeAttachment)
                {
                    LargeAttachment largeAttachment = (LargeAttachment) attachment;

                    DataHandler dataHandler = attachmentsStrategy.get(
                            exchange,
                            message.getSMEVMetadata().getMessageIdentity().getMessageId(),
                            largeAttachment.getUuid().toString(),
                            largeAttachment.getFileRef(),
                            largeAttachment.getMimeType(),
                            largeAttachment.getSignaturePKCS7(),
                            current++,
                            total
                    );
                    if(dataHandler != null)
                    {
                        attachments.add(dataHandler);

                        try (OutputStream outputStream = dataHandler.getOutputStream())
                        {
                            laTransport.download(largeAttachment, outputStream);
                        }

                        Attachment a = new DefaultAttachment(dataHandler);
                        Smev3Constants.set(a, Smev3Constants.SMEV3_ATTACHMENT_MIMETYPE, attachment.getMimeType());
                        Smev3Constants.set(a, Smev3Constants.SMEV3_ATTACHMENT_UUID, largeAttachment.getUuid().toString());
                        Smev3Constants.set(a, Smev3Constants.SMEV3_ATTACHMENT_NAME, largeAttachment.getFileRef());
                        Smev3Constants.set(a, Smev3Constants.SMEV3_ATTACHMENT_SIGNATUREPKCS7, Base64.encode(attachment.getSignaturePKCS7()));
                        Smev3Constants.set(a, Smev3Constants.SMEV3_ATTACHMENT_PASSPORTID, attachment.getPassportId());
                        Smev3Constants.set(a, Smev3Constants.SMEV3_ATTACHMENT_HASH, largeAttachment.getHash());

                        if (attachmentsStrategy.process(exchange, a, dataHandler))
                            attachmentMessage.addAttachmentObject(largeAttachment.getUuid().toString(), a);
                    }
                }
                else
                    throw new Exception("Unexpected attachment type"); // TODO log
            }
        }
    }

    @Override
    public boolean begin(Consumer consumer, Endpoint endpoint)
    {
        return true; // true = ready to read message
    }

    @Override
    public void commit(Consumer consumer, Endpoint endpoint, int polledMessages)
    {
        try
        {
            if(polledMessages > 0 && accepted != null && conf.getQueryInformation().getType() != ProcessingInformation.Type.STATUS)
                wsTemplate.ack(message.getSMEVMetadata(), accepted); // true, если ЭП-СМЭВ прошла валидацию и сообщение передано ИС. false, если ЭП-СМЭВ отвергнута, и сообщение проигнорировано.
        }
        catch (SMEVException ex)
        {
            LOGGER.error("ack: " + message.getSMEVMetadata().getMessageIdentity().getMessageId(), ex);
        }
        finally
        {
            message = null;
            accepted = null;
        }
    }

    @Override
    public boolean rollback(Consumer consumer, Endpoint endpoint, int retryCounter, Exception cause) throws Exception
    {
        LOGGER.error("stop procesing! rollback", cause);

        Thread.sleep(conf.getErrorDelay());
        message = null;
        return false; // false = no retry
    }
}
