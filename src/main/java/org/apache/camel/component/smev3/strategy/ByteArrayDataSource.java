package org.apache.camel.component.smev3.strategy;

import javax.activation.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public final class ByteArrayDataSource implements DataSource
{
    private String name;
    private String contentType;
    private ByteArrayOutputStream outputStream = new ByteArrayOutputStream();


    public ByteArrayDataSource(String name, String contentType)
    {
        this.name = name;
        this.contentType = contentType;
    }

    public String getContentType()
    {
        return contentType == null ? "application/octet-stream" : contentType;
    }

    public InputStream getInputStream()
    {
        return new ByteArrayInputStream(outputStream.toByteArray());
    }

    public String getName()
    {
        return name;
    }

    public OutputStream getOutputStream()
    {
        return outputStream;
    }
}
