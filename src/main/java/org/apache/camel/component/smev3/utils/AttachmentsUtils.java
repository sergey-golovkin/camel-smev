package org.apache.camel.component.smev3.utils;

import org.apache.camel.Exchange;
import org.apache.camel.attachment.Attachment;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.attachment.DefaultAttachment;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import java.io.File;

public class AttachmentsUtils
{
    public static void sendFile(String filePath, String mimeType, Exchange exchange)
    {
        AttachmentMessage attachmentMessage = exchange.getIn(AttachmentMessage.class);
        File f = new File(filePath);
        Attachment att = new DefaultAttachment(new DataHandler(new FileDataSource(f)));
        att.setHeader("AttachmentLength", Long.toString(f.length()));
        att.setHeader("AttachmentMimeType", mimeType);
        att.setHeader("AttachmentContentRef", filePath);
        att.setHeader("AttachmentName", f.getName());
        attachmentMessage.addAttachmentObject(f.getName(), att);
    }
}
