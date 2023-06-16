package org.apache.camel.component.smev3;

import org.apache.camel.Exchange;
import org.apache.camel.FailedToCreateConsumerException;
import org.apache.camel.attachment.Attachment;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.support.DefaultProducer;
import org.apache.commons.io.FileUtils;
import org.apache.xerces.impl.dv.util.Base64;
import org.w3c.dom.*;
import ru.voskhod.crypto.XMLTransformHelper;
import ru.voskhod.crypto.impl.SmevTransformUtil;
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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.util.*;

public class Smev3Producer extends DefaultProducer
{
    private Smev3Configuration conf;
    private IdentityService identityService;
    private WSTemplate wsTemplate;
    private Signer signer;
    private List<URI> tempFiles = new ArrayList<>();

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
                    Factory.getLargeAttachmentTransportInstance(conf.getLargeAttachmentTransportConfiguration(), conf.getSmevVersion()),
                    conf.getGeoTemplateConfiguration(),
                    conf.getSmevVersion());
        }
        catch (SMEVRuntimeException e)
        {
            throw new FailedToCreateConsumerException(endpoint, e);
        }
    }

    @Override
    public void process(final Exchange exchange) throws Exception
    {
        try
        {
            String messageId = Smev3Constants.get(exchange, Smev3Constants.SMEV3_MESSAGE_ID, identityService.generateUUID(), String.class);
            String referenceMessageId = Smev3Constants.get(exchange, Smev3Constants.SMEV3_MESSAGE_REFERENCE_ID, String.class);
            String transactionCode = Smev3Constants.get(exchange, Smev3Constants.SMEV3_METADATA_TRANSACTION_CODE, String.class);
            String originalMessageId = Smev3Constants.get(exchange, Smev3Constants.SMEV3_ORIGINAL_MESSAGEID, String.class);
            String replyTo = Smev3Constants.get(exchange, Smev3Constants.SMEV3_MESSAGE_REPLYTO, String.class);
            String nodeId = Smev3Constants.get(exchange, Smev3Constants.SMEV3_METADATA_NODEID, String.class);
            Integer statusCode = Smev3Constants.get(exchange, Smev3Constants.SMEV3_STATUS_CODE, Integer.class);
            String rejectionReasonCode = Smev3Constants.get(exchange, Smev3Constants.SMEV3_REJECTION_REASON_CODE, String.class);
            String description = Smev3Constants.get(exchange, Smev3Constants.SMEV3_DESCRIPTION, String.class);
            String idTransport = Smev3Constants.get(exchange, Smev3Constants.SMEV3_METADATA_IDTRANSPORT, String.class);
            Boolean testMessage = Smev3Constants.get(exchange, Smev3Constants.SMEV3_METADATA_TESTMESSAGE, false, Boolean.class);

            XMLGregorianCalendar eol = null; // TODO
            List<Element> businessProcessMetadata = Collections.emptyList();
            Map<String, List<SMEVAttachment>> registryAttachments = Collections.emptyMap();
            Map<String, String> parameters = Collections.emptyMap();

            SMEVMetadata.MessageIdentity messageIdentity = new SMEVMetadata.MessageIdentity(messageId, referenceMessageId, transactionCode);

            if (conf.getMode().equals(Smev3Configuration.Smev3Mode.Request))
            {
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
                SMEVMetadata smevMetadata = new SMEVMetadata(messageIdentity, new ResponseInformation(messageId, originalMessageId, replyTo));
                StatusResponseContent businessContent = new StatusResponseContent(statusCode, description, parameters, businessProcessMetadata);
                sendAndProcessResult(exchange, smevMetadata, businessContent);
            }
            else if (conf.getMode().equals(Smev3Configuration.Smev3Mode.Reject))
            {
                SMEVMetadata smevMetadata = new SMEVMetadata(messageIdentity, new ResponseInformation(messageId, originalMessageId, replyTo));
                RejectResponseContent businessContent = new RejectResponseContent(businessProcessMetadata);
                businessContent.add(RejectResponseContent.RejectCode.fromValue(rejectionReasonCode), description);
                sendAndProcessResult(exchange, smevMetadata, businessContent);
            }
            else if (conf.getMode().equals(Smev3Configuration.Smev3Mode.Ack))
            {
                Boolean accepted = Smev3Constants.get(exchange, Smev3Constants.SMEV3_MESSAGE_ACCEPTED, true, Boolean.class);
                SMEVMetadata smevMetadata = new SMEVMetadata(new SMEVMetadata.MessageIdentity(messageId, referenceMessageId, transactionCode), null);
                SMEVContext smevContext = new SMEVContext(idTransport, null);
                smevMetadata.setSmevContext(smevContext);
                wsTemplate.ack(smevMetadata, accepted); // true, если ЭП-СМЭВ прошла валидацию и сообщение передано ИС. false, если ЭП-СМЭВ отвергнута, и сообщение проигнорировано.
            }
            else throw new Exception(); // TODO
        }
        finally
        {
            removeTempFiles();
        }
    }

    private void sendAndProcessResult(Exchange exchange, SMEVMetadata smevMetadata, BusinessContent businessContent) throws SMEVException
    {
        SMEVMetadata result = wsTemplate.send(new SMEVMessage(smevMetadata, businessContent));
        Smev3Constants.fillExchangeHeaders(exchange, result);
    }

    private void removeTempFiles()
    {
        for(URI uri : tempFiles)
            FileUtils.deleteQuietly(new File(uri));

        tempFiles.clear();
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
            throw new Exception(); // TODO

        return content;
    }

    private List<SMEVAttachment> getAttachments(Exchange exchange) throws IOException, SMEVSignatureException, SMEVRuntimeException
    {
        List<SMEVAttachment> attachments = new ArrayList<>();
        AttachmentMessage attachmentMessage = exchange.getIn(AttachmentMessage.class);

        if(attachmentMessage != null && attachmentMessage.hasAttachments())
        {
            for(Attachment attachment : attachmentMessage.getAttachmentObjects().values())
            {
                SMEVAttachment smevAttachment;
                DataHandler dataHandler = attachment.getDataHandler();
                int length = Smev3Constants.get(attachment, "AttachmentLength", conf.getLargeAttachmentThreshold(), Integer.class);
                URI uri = Smev3Constants.get(attachment, "AttachmentContentRef", null, URI.class);

                InputStream inputStream = dataHandler.getInputStream();
                DigestInputStream dIn = signer.getDigestInputStream(inputStream);
                byte[] checkSum = signer.getDigest(dIn);
                byte[] signature = signer.signPKCS7Detached(checkSum);

                try
                {
                    if (length >= conf.getLargeAttachmentThreshold())
                    {
                        String attachmentName = Smev3Constants.get(attachment, "AttachmentName", null, String.class);
                        UUID attachmentUUId = Smev3Constants.get(attachment, "AttachmentUUId", identityService.generateAttachmentUUID(), UUID.class);

                        if (uri == null)
                        {
                            uri = Paths.get(System.getProperty("java.io.tmpdir"), "smev3", "temp", attachmentUUId.toString()).toUri();
                            if(uri != null)
                                tempFiles.add(uri);

                            File tempDir = new File(uri);
                            tempDir.mkdirs();

                            uri = Paths.get(tempDir.getPath(), attachmentName).toUri();
                            OutputStream tmpOut = new FileOutputStream(new File(uri));
                            InputStream tmpIn = dataHandler.getInputStream();
                            try { tmpIn.transferTo(tmpOut); } // fill
                            finally { tmpOut.flush(); tmpOut.close(); tmpIn.close(); }
                        }

                        smevAttachment = new LargeAttachment(
                                Smev3Constants.get(attachment, "AttachmentMimeType", "application/stream", String.class),
                                Smev3Constants.get(attachment, "AttachmentSignaturePKCS7", signature, byte[].class),
                                Smev3Constants.get(attachment, "AttachmentPassportId", null, String.class),
                                attachmentUUId,
                                uri,
                                Base64.encode(checkSum).getBytes(),
                                null,
                                attachmentName
                        );
                    }
                    else
                    {
                        smevAttachment = new MTOMAttachment(
                                Smev3Constants.get(attachment, "AttachmentMimeType", "application/stream", String.class),
                                Smev3Constants.get(attachment, "AttachmentSignaturePKCS7", signature, byte[].class),
                                Smev3Constants.get(attachment, "AttachmentPassportId", null, String.class),
                                Smev3Constants.get(attachment, "AttachmentName", identityService.generateUUID(), String.class),
                                dataHandler
                        );
                    }

                    attachments.add(smevAttachment);
                }
                finally
                {
                    dIn.close();
                    inputStream.close();
                    if(uri != null)
                        tempFiles.add(uri);
                }
            }
        }
        return attachments;
    }
}
