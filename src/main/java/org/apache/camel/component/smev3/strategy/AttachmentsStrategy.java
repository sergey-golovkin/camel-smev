package org.apache.camel.component.smev3.strategy;

import org.apache.camel.Exchange;
import org.apache.camel.attachment.Attachment;

import javax.activation.DataHandler;
import java.io.IOException;

public interface AttachmentsStrategy
{
    DataHandler get(Exchange exchange, String messageId, String attachmentId, String attachmentName, String mimeType, byte[] signaturePKCS7) throws Exception;
    boolean process(Exchange exchange, Attachment attachment, DataHandler dataHandler) throws Exception;
    void done(DataHandler dataHandler) throws Exception;
}
