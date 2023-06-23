package org.apache.camel.component.smev3;

import org.apache.camel.Exchange;
import org.apache.camel.FailedToCreateConsumerException;
import org.apache.camel.attachment.Attachment;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.component.smev3.attachments.ApacheFTPTransport;
import org.apache.camel.support.DefaultProducer;
import org.apache.xerces.impl.dv.util.Base64;
import org.w3c.dom.*;
import ru.voskhod.crypto.util.SmevTransformUtil;
import ru.voskhod.crypto.util.XMLTransformHelper;
import ru.voskhod.smev.client.api.factory.Factory;
import ru.voskhod.smev.client.api.services.identification.IdentityService;
import ru.voskhod.smev.client.api.services.signature.Signer;
import ru.voskhod.smev.client.api.services.template.WSTemplate;
import ru.voskhod.smev.client.api.signature.impl.SignerFactory;
import ru.voskhod.smev.client.api.types.exception.SMEVException;
import ru.voskhod.smev.client.api.types.exception.SMEVRuntimeException;
import ru.voskhod.smev.client.api.types.exception.processing.SMEVSignatureException;
import ru.voskhod.smev.client.api.types.message.SMEVMessage;
import ru.voskhod.smev.client.api.types.message.attachment.LargeAttachment;
import ru.voskhod.smev.client.api.types.message.attachment.MTOMAttachment;
import ru.voskhod.smev.client.api.types.message.attachment.SMEVAttachment;
import ru.voskhod.smev.client.api.types.message.business.data.BusinessContent;
import ru.voskhod.smev.client.api.types.message.business.data.request.RequestContent;
import ru.voskhod.smev.client.api.types.message.business.data.response.RejectResponseContent;
import ru.voskhod.smev.client.api.types.message.business.data.response.ResponseContent;
import ru.voskhod.smev.client.api.types.message.business.data.response.StatusResponseContent;
import ru.voskhod.smev.client.api.types.message.system.SMEVContext;
import ru.voskhod.smev.client.api.types.message.system.SMEVMetadata;
import ru.voskhod.smev.client.api.types.message.system.processing.RequestInformation;
import ru.voskhod.smev.client.api.types.message.system.processing.ResponseInformation;

import javax.activation.DataHandler;
import javax.xml.datatype.XMLGregorianCalendar;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.util.*;

public class Smev3Producer extends DefaultProducer
{
    private final Smev3Configuration conf;
    private final IdentityService identityService;
    private final WSTemplate wsTemplate;
    private final Signer signer;
    private final ApacheFTPTransport laTransport;

    Smev3Producer(Smev3Endpoint endpoint, Smev3Configuration conf)
    {
        super(endpoint);
        this.conf = conf;

        try
        {
            signer = SignerFactory.getSigner(
                    conf.getSignerConfiguration(),
                    conf.getSignerConfiguration().getCertificateAlias(),
                    conf.getSignerConfiguration().getPrivateKeyAlias(),
                    conf.getSignerConfiguration().getPrivateKeyPassword());
            identityService = Factory.getIdentityGeneratorInstance(null, conf.getSmevVersion());
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
    public void process(final Exchange exchange) throws Exception
    {
        String messageId = Smev3Constants.get(exchange, Smev3Constants.SMEV3_MESSAGE_ID, identityService.generateUUID(), String.class);
        String referenceMessageId = Smev3Constants.get(exchange, Smev3Constants.SMEV3_MESSAGE_REFERENCE_ID, String.class);
        String originalMessageId = Smev3Constants.get(exchange, Smev3Constants.SMEV3_ORIGINAL_MESSAGEID, String.class);
        String transactionCode = Smev3Constants.get(exchange, Smev3Constants.SMEV3_METADATA_TRANSACTION_CODE, String.class);
        String replyTo = Smev3Constants.get(exchange, Smev3Constants.SMEV3_MESSAGE_REPLYTO, String.class);

        XMLGregorianCalendar eol = null; // TODO
        List<Element> businessProcessMetadata = Collections.emptyList();
        Map<String, List<SMEVAttachment>> registryAttachments = Collections.emptyMap();
        Map<String, String> parameters = Collections.emptyMap();

        SMEVMetadata.MessageIdentity messageIdentity = new SMEVMetadata.MessageIdentity(messageId, referenceMessageId, transactionCode);

        if (conf.getMode().equals(Smev3Configuration.Smev3Mode.Request))
        {
            Boolean testMessage = Smev3Constants.get(exchange, Smev3Constants.SMEV3_METADATA_TESTMESSAGE, false, Boolean.class);
            String nodeId = Smev3Constants.get(exchange, Smev3Constants.SMEV3_METADATA_NODEID, String.class);
            Element content = getContent(exchange.getMessage().getBody());
            SMEVMetadata smevMetadata = new SMEVMetadata(messageIdentity, new RequestInformation(messageId, eol, nodeId, testMessage));
            RequestContent businessContent = new RequestContent(content, signer.sign(content), getAttachments(exchange), businessProcessMetadata, registryAttachments);
            sendAndProcessResult(exchange, smevMetadata, businessContent);
        }
        else if (conf.getMode().equals(Smev3Configuration.Smev3Mode.Response))
        {
            Element content = getContent(exchange.getMessage().getBody());
            SMEVMetadata smevMetadata = new SMEVMetadata(messageIdentity, new ResponseInformation(messageId, originalMessageId, replyTo));
            ResponseContent businessContent = new ResponseContent(content, signer.sign(content), getAttachments(exchange), businessProcessMetadata);
            sendAndProcessResult(exchange, smevMetadata, businessContent);
        }
        else if (conf.getMode().equals(Smev3Configuration.Smev3Mode.Status))
        {
            Integer statusCode = Smev3Constants.get(exchange, Smev3Constants.SMEV3_STATUS_CODE, Integer.class);
            String description = Smev3Constants.get(exchange, Smev3Constants.SMEV3_DESCRIPTION, String.class);
            SMEVMetadata smevMetadata = new SMEVMetadata(messageIdentity, new ResponseInformation(messageId, originalMessageId, replyTo));
            StatusResponseContent businessContent = new StatusResponseContent(statusCode, description, parameters, businessProcessMetadata);
            sendAndProcessResult(exchange, smevMetadata, businessContent);
        }
        else if (conf.getMode().equals(Smev3Configuration.Smev3Mode.Reject))
        {
            String rejectionReasonCode = Smev3Constants.get(exchange, Smev3Constants.SMEV3_REJECTION_REASON_CODE, String.class);
            String description = Smev3Constants.get(exchange, Smev3Constants.SMEV3_DESCRIPTION, String.class);
            SMEVMetadata smevMetadata = new SMEVMetadata(messageIdentity, new ResponseInformation(messageId, originalMessageId, replyTo));
            RejectResponseContent businessContent = new RejectResponseContent(businessProcessMetadata);
            businessContent.add(RejectResponseContent.RejectCode.fromValue(rejectionReasonCode), description);
            sendAndProcessResult(exchange, smevMetadata, businessContent);
        }
        else if (conf.getMode().equals(Smev3Configuration.Smev3Mode.Ack))
        {
            String idTransport = Smev3Constants.get(exchange, Smev3Constants.SMEV3_METADATA_IDTRANSPORT, String.class);

            Boolean accepted = Smev3Constants.get(exchange, Smev3Constants.SMEV3_MESSAGE_ACCEPTED, true, Boolean.class);
            SMEVMetadata smevMetadata = new SMEVMetadata(new SMEVMetadata.MessageIdentity(messageId, referenceMessageId, transactionCode), null);
            SMEVContext smevContext = new SMEVContext(idTransport, null);
            smevMetadata.setSmevContext(smevContext);
            wsTemplate.ack(smevMetadata, accepted); // true, если ЭП-СМЭВ прошла валидацию и сообщение передано ИС. false, если ЭП-СМЭВ отвергнута, и сообщение проигнорировано.
        }
        else
            throw new Exception(); // TODO log
    }

    private void sendAndProcessResult(Exchange exchange, SMEVMetadata smevMetadata, BusinessContent businessContent) throws SMEVException
    {
        SMEVMessage result = wsTemplate.send(new SMEVMessage(smevMetadata, businessContent));
        Smev3Constants.fillExchangeHeaders(exchange, result.getSMEVMetadata());
    }

    private Element getContent(Object body) throws Exception
    {
        Element content;
        if(body instanceof Element)
        {
            content = (Element) body;
        }
        else if(body instanceof Document)
        {
            content = ((Document) body).getDocumentElement();
        }
        else if(body instanceof String)
        {
            body = new String(SmevTransformUtil.transform(((String) body).getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
            content = XMLTransformHelper.buildDocumentFromString((String) body).getDocumentElement();
        }
        else
            throw new Exception(); // TODO log

        return content;
    }

    private List<SMEVAttachment> getAttachments(Exchange exchange) throws IOException, SMEVSignatureException, SMEVRuntimeException
    {
        List<SMEVAttachment> attachments = new ArrayList<>();
        AttachmentMessage attachmentMessage = exchange.getIn(AttachmentMessage.class);

        if(attachmentMessage != null && attachmentMessage.hasAttachments())
        {
            for(Map.Entry<String, Attachment> el : attachmentMessage.getAttachmentObjects().entrySet())
            {
                Attachment attachment = el.getValue();
                DataHandler dataHandler = attachment.getDataHandler();
                int length = Smev3Constants.get(attachment, Smev3Constants.SMEV3_ATTACHMENT_LENGTH, conf.getLargeAttachmentThreshold(), Integer.class);
                String attachmentName = Smev3Constants.get(attachment, Smev3Constants.SMEV3_ATTACHMENT_NAME, el.getKey(), String.class);

                try(InputStream inputStream = dataHandler.getInputStream();
                    DigestInputStream digestInputStream = signer.getDigestInputStream(inputStream))
                {
                    SMEVAttachment smevAttachment;
                    byte[] checkSum = signer.getDigest(digestInputStream);
                    byte[] signature = signer.signPKCS7Detached(checkSum);

                    if (length >= conf.getLargeAttachmentThreshold())
                    {
                        UUID attachmentUUId = Smev3Constants.get(attachment, Smev3Constants.SMEV3_ATTACHMENT_UUID, identityService.generateAttachmentUUID(), UUID.class);

                        smevAttachment = new LargeAttachment(
                                Smev3Constants.get(attachment, Smev3Constants.SMEV3_ATTACHMENT_MIMETYPE, "application/stream", String.class),
                                Smev3Constants.get(attachment, Smev3Constants.SMEV3_ATTACHMENT_SIGNATUREPKCS7, signature, byte[].class),
                                Smev3Constants.get(attachment, Smev3Constants.SMEV3_ATTACHMENT_PASSPORTID, null, String.class),
                                attachmentUUId,
                                null, // Must be null
                                Base64.encode(checkSum).getBytes(),
                                new LargeAttachment.TransportDetails(conf.getLargeAttachmentTransportConfiguration().getLogin(), conf.getLargeAttachmentTransportConfiguration().getPass()),
                                attachmentName
                        );

                        try (InputStream tmpIn = dataHandler.getInputStream(); 
                             BufferedInputStream tmpBis = new BufferedInputStream(tmpIn))
                        {
                            laTransport.upload((LargeAttachment) smevAttachment, tmpBis);
                        }
                    }
                    else
                    {
                        smevAttachment = new MTOMAttachment(
                                Smev3Constants.get(attachment, Smev3Constants.SMEV3_ATTACHMENT_MIMETYPE, "application/stream", String.class),
                                Smev3Constants.get(attachment, Smev3Constants.SMEV3_ATTACHMENT_SIGNATUREPKCS7, signature, byte[].class),
                                Smev3Constants.get(attachment, Smev3Constants.SMEV3_ATTACHMENT_PASSPORTID, null, String.class),
                                attachmentName,
                                dataHandler
                        );
                    }

                    attachments.add(smevAttachment);
                }
            }
        }
        return attachments;
    }
}
