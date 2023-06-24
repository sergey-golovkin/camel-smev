package org.apache.camel.component.smev3.utils;

import org.apache.camel.Exchange;
import org.apache.camel.attachment.Attachment;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.attachment.DefaultAttachment;
import org.apache.camel.component.smev3.Smev3Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Map;

public class Attachments
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Attachments.class);

    public static void sendFile(String filePath, String mimeType, String attachmentId, Exchange exchange)
    {
        LOGGER.debug("sendFile filePath: \"{}\"", filePath);
        AttachmentMessage attachmentMessage = exchange.getIn(AttachmentMessage.class);
        if(attachmentMessage != null)
        {
            File file = new File(filePath);
            Attachment attachment = new DefaultAttachment(new DataHandler(new FileDataSource(file)));
            attachment.setHeader(Smev3Constants.SMEV3_ATTACHMENT_LENGTH, Long.toString(file.length()));
            attachment.setHeader(Smev3Constants.SMEV3_ATTACHMENT_MIMETYPE, mimeType);
            attachment.setHeader(Smev3Constants.SMEV3_ATTACHMENT_NAME, file.getName());
            if(attachmentId != null && attachmentId.length() > 0)
                attachment.setHeader(Smev3Constants.SMEV3_ATTACHMENT_UUID, attachmentId);

            attachmentMessage.addAttachmentObject(file.getName(), attachment);
        }
    }

    public static void saveAttachments(String rootPath, Exchange exchange) throws Exception
    {
        LOGGER.debug("saveAttachments rootPath: \"{}\"", rootPath);
        AttachmentMessage attachmentMessage = exchange.getIn(AttachmentMessage.class);
        if(attachmentMessage != null && attachmentMessage.hasAttachments())
        {
            for (Map.Entry<String, Attachment> entry : attachmentMessage.getAttachmentObjects().entrySet())
            {
                Attachment attachment = entry.getValue();
                String attachmentName = Smev3Constants.get(attachment, Smev3Constants.SMEV3_ATTACHMENT_NAME, entry.getKey(), String.class);
                LOGGER.debug("saveAttachments attachment: \"{}\"", attachmentName);
                DataHandler dataHandler = attachment.getDataHandler();
                try(FileOutputStream outputStream = new FileOutputStream(Paths.get(rootPath, attachmentName).toFile());
                    InputStream inputStream = dataHandler.getInputStream())
                {
                    inputStream.transferTo(outputStream);
                }
            }
        }
    }
}
