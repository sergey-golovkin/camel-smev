package org.apache.camel.component.smev3.strategy;

import org.apache.camel.Exchange;
import org.apache.camel.attachment.Attachment;
import org.apache.camel.component.smev3.Smev3Constants;
import javax.activation.DataHandler;
import java.io.ByteArrayOutputStream;

public class InMemoryAttachmentsStrategy implements AttachmentsStrategy
{
    public InMemoryAttachmentsStrategy()
    {
    }

    @Override
    public DataHandler get(Exchange exchange, String messageId, String attachmentId, String attachmentName, String mimeType, byte[] signaturePKCS7, int current, int total) throws Exception
    {
        return new DataHandler(new ByteArrayDataSource(attachmentName, mimeType));
    }

    @Override
    public boolean process(Exchange exchange, Attachment attachment, DataHandler dataHandler) throws Exception
    {
        Smev3Constants.set(attachment, Smev3Constants.SMEV3_ATTACHMENT_LENGTH, ((ByteArrayOutputStream)((ByteArrayDataSource)dataHandler.getDataSource()).getOutputStream()).toByteArray().length);
        return true;
    }

    @Override
    public void done(DataHandler dataHandler) throws Exception
    {
    }
}
