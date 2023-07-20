package org.apache.camel.component.smev3.utils;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.voskhod.smev.client.api.services.transport.configuration.LargeAttachmentTransportConfiguration;
import ru.voskhod.smev.client.api.types.exception.SMEVException;
import ru.voskhod.smev.client.api.types.exception.SMEVRuntimeException;
import ru.voskhod.smev.client.api.types.message.attachment.LargeAttachment;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ApacheFTPTransport
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ApacheFTPTransport.class);
    private final LargeAttachmentTransportConfiguration config;
    private final FTPClient ftpClient;

    public ApacheFTPTransport(LargeAttachmentTransportConfiguration config)
    {
        this.config = config;
        this.ftpClient = new FTPClient();
    }

    private void connect(String login, String password) throws SMEVRuntimeException
    {
        try
        {
            ftpClient.setControlEncoding("UTF-8");
            String[] parts = config.getAddress().split(":", 2);
            if(parts.length == 1)
                ftpClient.connect(parts[0]);
            else if(parts.length == 2)
                ftpClient.connect(parts[0], Integer.parseInt(parts[1]));
            else
                throw new IllegalArgumentException("Invalid ftp address" + config.getAddress());

            if( ! ftpClient.isAvailable())
                throw new IOException(ftpClient.getReplyString());

            LOGGER.debug("connected to: \"{}\"", config.getAddress());
            ftpClient.enterLocalPassiveMode();
        }
        catch (Exception ex)
        {
            LOGGER.error("connect to: \"{}\" failure: {}", new Object[]{ config.getAddress(), ex.getMessage(), ex });
            throw new SMEVRuntimeException(ex.getMessage(), ex);
        }

        try
        {
            if(ftpClient.login(login, password))
            {
                if( ! FTPReply.isPositiveCompletion(ftpClient.getReplyCode()))
                    LOGGER.debug("negative completion, because: {}", ftpClient.getReplyString());

                ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            }
            else
                throw new IOException(ftpClient.getReplyString());

        }
        catch (Exception ex)
        {
            LOGGER.error("login failure: {}", ex.getMessage(), ex);
            throw new SMEVRuntimeException(ex.getMessage(), ex);
        }
    }

    private void disconnect() throws SMEVRuntimeException
    {
        try
        {
            if(ftpClient.isConnected())
                ftpClient.logout();

            ftpClient.disconnect();
            LOGGER.debug("disconnect");
        }
        catch (Exception ex)
        {
            LOGGER.error("disconnect failure: {}", ex.getMessage(), ex);
            //throw new SMEVRuntimeException(ex.getMessage(), ex);
        }
    }

    public void download(LargeAttachment largeAttachment, OutputStream outputStream) throws SMEVException
    {
        LargeAttachment.TransportDetails transportDetails = largeAttachment.getTransportDetails();
        connect(transportDetails.getUserName(), transportDetails.getPassword());

        try
        {
            if( ! ftpClient.retrieveFile(largeAttachment.getFileRef(), outputStream))
                throw new IOException("download \"" + largeAttachment.getFileRef() + "\" from: \"" + config.getAddress() + "\" failure: " + ftpClient.getReplyString());
        }
        catch (Exception ex)
        {
            LOGGER.error("{}", ex.getMessage(), ex);
            throw new SMEVRuntimeException(ex.getMessage(), ex);
        }
        finally
        {
            disconnect();
        }
    }

    public void upload(LargeAttachment largeAttachment, InputStream inputStream) throws SMEVRuntimeException
    {
        LargeAttachment.TransportDetails transportDetails = largeAttachment.getTransportDetails();
        connect(transportDetails.getUserName(), transportDetails.getPassword());

        try
        {
            String dirName = largeAttachment.getUuid().toString();

            if( ! this.ftpClient.makeDirectory(dirName))
                throw new IOException("create directory \"" + dirName + "\" Error: " + this.ftpClient.getReplyString());

            if( ! ftpClient.changeWorkingDirectory(dirName))
                throw new IOException("change directory to \"" + dirName + "\" failure: " + ftpClient.getReplyString());

            if( ! ftpClient.storeFile(largeAttachment.getFileRef(), inputStream))
                throw new IOException("upload \"" + largeAttachment.getFileRef() + "\" to: \"" + this.config.getAddress() + "\" failure: " + this.ftpClient.getReplyString());

        } catch (Exception ex)
        {
            LOGGER.error("{}", ex.getMessage(), ex);
            throw new SMEVRuntimeException(ex.getMessage(), ex);
        }
        finally
        {
            disconnect();
        }
    }
}
