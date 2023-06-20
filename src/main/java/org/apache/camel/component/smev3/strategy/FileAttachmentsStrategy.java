package org.apache.camel.component.smev3.strategy;

import org.apache.camel.Exchange;
import org.apache.camel.attachment.Attachment;
import org.apache.camel.component.smev3.Smev3Constants;
import org.apache.commons.io.FileUtils;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import java.io.File;
import java.nio.file.Paths;

public class FileAttachmentsStrategy implements AttachmentsStrategy
{
    private String attachmentsStore;
    private boolean remove;

    public FileAttachmentsStrategy()
    {
        this.attachmentsStore = Paths.get(System.getProperty("java.io.tmpdir"), "smev3", "fileattachments").toString();
        this.remove = true;
    }

    public FileAttachmentsStrategy(String attachmentsStore, boolean remove)
    {
        this.attachmentsStore = attachmentsStore;
        this.remove = remove;
    }

    @Override
    public DataHandler get(Exchange exchange, String messageId, String attachmentId, String attachmentName, String mimeType, byte[] signaturePKCS7) throws Exception
    {
        FileUtils.forceMkdir(new File(Paths.get(attachmentsStore, messageId).toUri()));
        return new DataHandler(new FileDataSource(new File(Paths.get(attachmentsStore, messageId, attachmentName).toUri())));
    }

    @Override
    public boolean process(Exchange exchange, Attachment attachment, DataHandler dataHandler) throws Exception
    {
        Smev3Constants.set(attachment, "AttachmentLength", ((FileDataSource)dataHandler.getDataSource()).getFile().length());
        return true;
    }

    @Override
    public void done(DataHandler dataHandler) throws Exception
    {
        if(remove)
            FileUtils.deleteQuietly(((FileDataSource) dataHandler.getDataSource()).getFile().getParentFile());
    }
}
