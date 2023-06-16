package org.apache.camel.component.smev3;

import org.apache.camel.*;
import org.apache.camel.attachment.Attachment;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.attachment.DefaultAttachment;
import org.apache.camel.component.smev3.strategy.AttachmentsStrategy;
import org.apache.camel.spi.PollingConsumerPollStrategy;
import org.apache.camel.support.ScheduledPollConsumer;
import org.apache.xerces.impl.dv.util.Base64;
import ru.voskhod.crypto.XMLTransformHelper;
import ru.voskhod.smev.client.api.factory.Factory;
import ru.voskhod.smev.client.api.services.signature.Signer;
import ru.voskhod.smev.client.api.services.template.WSTemplate;
import ru.voskhod.smev.client.api.services.transport.LargeAttachmentTransport;
import ru.voskhod.smev.client.api.signature.impl.SignerFactory;
import ru.voskhod.smev.client.api.types.exception.SMEVException;
import ru.voskhod.smev.client.api.types.exception.SMEVRuntimeException;
import ru.voskhod.smev.client.api.types.message.SMEVMessage;
import ru.voskhod.smev.client.api.types.message.attachment.LargeAttachment;
import ru.voskhod.smev.client.api.types.message.attachment.MTOMAttachment;
import ru.voskhod.smev.client.api.types.message.attachment.SMEVAttachment;
import ru.voskhod.smev.client.api.types.message.system.processing.ProcessingInformation;

import javax.activation.DataHandler;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class Smev3Consumer extends ScheduledPollConsumer implements PollingConsumerPollStrategy
{
    private final Smev3Configuration conf;
    private WSTemplate wsTemplate;
    private Signer signer;
    private LargeAttachmentTransport laTransport;
    private SMEVMessage message;
    private Boolean accepted;
    private List<DataHandler> attachments = new ArrayList<>();

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
                    null, //Factory.getLargeAttachmentTransportInstance(conf.getLargeAttachmentTransportConfiguration(), conf.getSmevVersion()),
                    conf.getGeoTemplateConfiguration(),
                    conf.getSmevVersion());
            laTransport = Factory.getLargeAttachmentTransportInstance(conf.getLargeAttachmentTransportConfiguration(), conf.getSmevVersion());
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
            fillExchangeAttachments(exchange, message, laTransport);

            Smev3Constants.set(exchange, Smev3Constants.SMEV3_MESSAGE_ACCEPTED, true);

            this.getProcessor().process(exchange);

            accepted = Smev3Constants.get(exchange, Smev3Constants.SMEV3_MESSAGE_ACCEPTED, Boolean.class);

            return 1; // polled messages
        }
        finally
        {
            doneAttachments();
        }
    }

    private void doneAttachments()
    {
        AttachmentsStrategy attachmentsStrategy = conf.getAttachmentsStrategy();

        for(DataHandler dh : attachments)
        {
            try
            {
                attachmentsStrategy.done(dh);
            }
            catch (Exception e)
            {
            }
        }

        attachments.clear();
    }

    private void fillExchangeAttachments(Exchange exchange, SMEVMessage message, LargeAttachmentTransport laTransport) throws Exception
    {
        if(message != null && message.getData() != null)
        {
            AttachmentMessage attachmentMessage = exchange.getIn(AttachmentMessage.class);
            AttachmentsStrategy attachmentsStrategy = conf.getAttachmentsStrategy();

            for(SMEVAttachment attachment : message.getData().getAttachments())
            {
                if(attachment instanceof MTOMAttachment)
                {
                    MTOMAttachment mtomAttachment = (MTOMAttachment) attachment;

                    DataHandler dataHandler = attachmentsStrategy.get(
                            exchange,
                            message.getSMEVMetadata().getMessageIdentity().getMessageId(),
                            mtomAttachment.getAttachmentId(),
                            mtomAttachment.getContent().getName(),
                            mtomAttachment.getMimeType(),
                            mtomAttachment.getSignaturePKCS7()
                    );
                    attachments.add(dataHandler);

                    OutputStream outputStream = dataHandler.getOutputStream();
                    try { mtomAttachment.getContent().getInputStream().transferTo(outputStream); } // fill
                    finally { outputStream.flush(); outputStream.close(); }

                    Attachment a = new DefaultAttachment(dataHandler);
                    Smev3Constants.set(a,"AttachmentMimeType", attachment.getMimeType());
                    Smev3Constants.set(a,"AttachmentUUId", mtomAttachment.getAttachmentId());
                    Smev3Constants.set(a,"AttachmentName", mtomAttachment.getAttachmentId());
                    Smev3Constants.set(a,"AttachmentSignaturePKCS7", Base64.encode(attachment.getSignaturePKCS7()));
                    Smev3Constants.set(a,"AttachmentPassportId", attachment.getPassportId());

                    if(attachmentsStrategy.process(exchange, a, dataHandler))
                        attachmentMessage.addAttachmentObject(mtomAttachment.getContent().getName(), a);
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
                            largeAttachment.getSignaturePKCS7()
                            );
                    attachments.add(dataHandler);

                    OutputStream outputStream = dataHandler.getOutputStream();
                    try { laTransport.download(largeAttachment, outputStream); } // fill
                    finally { outputStream.flush(); outputStream.close(); }

                    Attachment a = new DefaultAttachment(dataHandler);
                    Smev3Constants.set(a,"AttachmentMimeType", attachment.getMimeType());
                    Smev3Constants.set(a,"AttachmentUUId", largeAttachment.getUuid().toString());
                    Smev3Constants.set(a,"AttachmentName", largeAttachment.getFileRef());
                    Smev3Constants.set(a,"AttachmentSignaturePKCS7", Base64.encode(attachment.getSignaturePKCS7()));
                    Smev3Constants.set(a,"AttachmentPassportId", attachment.getPassportId());
                    Smev3Constants.set(a,"AttachmentHash", largeAttachment.getHash());

                    if(attachmentsStrategy.process(exchange, a, dataHandler))
                        attachmentMessage.addAttachmentObject(largeAttachment.getUuid().toString(), a);
                }
                else
                    throw new Exception(); // TODO log
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
        catch (SMEVException e)
        {
            // TODO log
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
        // TODO log
        Thread.sleep(conf.getErrorDelay());
        message = null;
        return false; // false = no retry
    }
}
