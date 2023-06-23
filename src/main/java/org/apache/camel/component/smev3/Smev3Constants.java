package org.apache.camel.component.smev3;

import org.apache.camel.Exchange;
import org.apache.camel.attachment.Attachment;
import org.apache.camel.spi.Metadata;
import org.apache.xerces.impl.dv.util.Base64;
import ru.voskhod.smev.client.api.types.exception.SMEVProcessingException;
import ru.voskhod.smev.client.api.types.message.system.SMEVMetadata;
import ru.voskhod.smev.client.api.types.message.system.processing.RequestInformation;
import ru.voskhod.smev.client.api.types.message.system.processing.ResponseInformation;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Paths;
import java.util.UUID;

public class Smev3Constants
{
    public static void set(Exchange exchange, String name, Object value)
    {
        if(value != null)
            exchange.getMessage().setHeader(name, value);
    }

    public static <T> T get(Exchange exchange, String name, Class<T> type)
    {
        return exchange.getMessage().getHeader(name, type);
    }

    public static <T> T get(Exchange exchange, String name, Object defaultValue, Class<T> type)
    {
        return exchange.getMessage().getHeader(name, defaultValue, type);
    }

    public static <T> T get(Attachment attachment, String name, T defaultValue, Class<T> type)
    {
        String value = attachment.getHeader(name);

        if(type.equals(String.class))
            return (T) (value == null ? defaultValue : value);
        if(type.equals(Integer.class))
            return (T) (value == null ? defaultValue : (Integer) Integer.parseInt(value));
        if(type.equals(Long.class))
            return (T) (value == null ? defaultValue : (Long) Long.parseLong(value));
        if(type.equals(byte[].class))
            return (T) (value == null ? defaultValue : Base64.decode(value));
        if(type.equals(UUID.class))
            return (T) (value == null ? defaultValue : UUID.fromString(value));
        if(type.equals(URI.class))
            return (T) (value == null ? defaultValue : Paths.get(value).toUri());

        throw new ArithmeticException("type");
    }

    public static void set(Attachment attachment, String name, Object value)
    {
        if(value != null)
            attachment.setHeader(name, value.toString());
    }

    public static void fillExchangeHeaders(Exchange exchange, SMEVMetadata smevMetadata)
    {
        if(smevMetadata != null)
        {
            if(smevMetadata.getMessageIdentity() != null)
            {
                Smev3Constants.set(exchange, Smev3Constants.SMEV3_MESSAGE_ID, smevMetadata.getMessageIdentity().getMessageId());
                Smev3Constants.set(exchange, Smev3Constants.SMEV3_MESSAGE_REFERENCE_ID, smevMetadata.getMessageIdentity().getReferenceMessageId());
                Smev3Constants.set(exchange, Smev3Constants.SMEV3_METADATA_TRANSACTION_CODE, smevMetadata.getMessageIdentity().getTransactionCode());
            }
            if(smevMetadata.getProcessingInformation() != null)
            {
                Smev3Constants.set(exchange, Smev3Constants.SMEV3_METADATA_NODEID, smevMetadata.getProcessingInformation().getNodeId());
                Smev3Constants.set(exchange, Smev3Constants.SMEV3_METADATA_MESSAGETYPE, smevMetadata.getProcessingInformation().getType());
                if(smevMetadata.getProcessingInformation() instanceof ResponseInformation)
                {
                    ResponseInformation responseInformation = (ResponseInformation) smevMetadata.getProcessingInformation();
                    Smev3Constants.set(exchange, Smev3Constants.SMEV3_ORIGINAL_MESSAGEID, responseInformation.getOriginalMessageId());
                    Smev3Constants.set(exchange, Smev3Constants.SMEV3_MESSAGE_REPLYTO, responseInformation.getReplyTo());
                }
                else if(smevMetadata.getProcessingInformation() instanceof RequestInformation)
                {
                    RequestInformation requestInformation = (RequestInformation) smevMetadata.getProcessingInformation();
                    Smev3Constants.set(exchange, Smev3Constants.SMEV3_METADATA_TESTMESSAGE, requestInformation.isTestMessage());
                    Smev3Constants.set(exchange, Smev3Constants.SMEV3_METADATA_EOL, requestInformation.getEol());
                }
            }
            if(smevMetadata.getSmevContext() != null)
            {
                Smev3Constants.set(exchange, Smev3Constants.SMEV3_METADATA_IDTRANSPORT, smevMetadata.getSmevContext().getIdTransport());
                Smev3Constants.set(exchange, Smev3Constants.SMEV3_METADATA_PROCESSINGDETAILS, smevMetadata.getSmevContext().getProcessingDetails());
                Smev3Constants.set(exchange, Smev3Constants.SMEV3_METADATA_INTERACTIONTYPE, smevMetadata.getSmevContext().getInteractionType());
                Smev3Constants.set(exchange, Smev3Constants.SMEV3_METADATA_DESTINATIONNAME, smevMetadata.getSmevContext().getDestinationName());
                Smev3Constants.set(exchange, Smev3Constants.SMEV3_METADATA_SENDINGTIMESTAMP, smevMetadata.getSmevContext().getSendingTimestamp());
                Smev3Constants.set(exchange, Smev3Constants.SMEV3_METADATA_DELIVERYTIMESTAMP, smevMetadata.getSmevContext().getDeliveryTimestamp());
                Smev3Constants.set(exchange, Smev3Constants.SMEV3_METADATA_STATUS, smevMetadata.getSmevContext().getSmevStatus());
                Smev3Constants.set(exchange, Smev3Constants.SMEV3_MESSAGE_REPLYTO, smevMetadata.getSmevContext().getReplyTo());

                if(smevMetadata.getSmevContext().getSender() != null)
                {
                    Smev3Constants.set(exchange, Smev3Constants.SMEV3_METADATA_SENDER_MNEMONIC, smevMetadata.getSmevContext().getSender().getMnemonic());
                    Smev3Constants.set(exchange, Smev3Constants.SMEV3_METADATA_SENDER_HUMANREADABLENAME, smevMetadata.getSmevContext().getSender().getHumanReadableName());
                }
                if(smevMetadata.getSmevContext().getRecipient() != null)
                {
                    Smev3Constants.set(exchange, Smev3Constants.SMEV3_METADATA_RECIPIENT_MNEMONIC, smevMetadata.getSmevContext().getRecipient().getMnemonic());
                    Smev3Constants.set(exchange, Smev3Constants.SMEV3_METADATA_RECIPIENT_HUMANREADABLENAME, smevMetadata.getSmevContext().getRecipient().getHumanReadableName());
                }
                if(smevMetadata.getSmevContext().getException() != null)
                {
                    Smev3Constants.set(exchange, Smev3Constants.SMEV3_EXCEPTION_CAUSE, smevMetadata.getSmevContext().getException().getCause());
                    Smev3Constants.set(exchange, Smev3Constants.SMEV3_EXCEPTION_LOCALIZED_MESSAGE, smevMetadata.getSmevContext().getException().getLocalizedMessage());
                    Smev3Constants.set(exchange, Smev3Constants.SMEV3_EXCEPTION_MESSAGE, smevMetadata.getSmevContext().getException().getMessage());
                    Smev3Constants.set(exchange, Smev3Constants.SMEV3_EXCEPTION_STACKTRACE, printStackTrace(smevMetadata.getSmevContext().getException().getStackTrace()));
                    Smev3Constants.set(exchange, Smev3Constants.SMEV3_EXCEPTION_DUMP, printException(smevMetadata.getSmevContext().getException()));
                    if(smevMetadata.getSmevContext().getException() instanceof SMEVProcessingException)
                        Smev3Constants.set(exchange, Smev3Constants.SMEV3_EXCEPTION_CODE, ((SMEVProcessingException) smevMetadata.getSmevContext().getException()).getCode());
                }
            }
        }
    }

    public static String printStackTrace(StackTraceElement[] trace)
    {
        StringBuilder sb = new StringBuilder();

        for (StackTraceElement traceElement : trace)
            sb.append("\tat ").append(traceElement);

        return sb.toString();
    }
    public static String printException(Exception ex)
    {
        try(StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw))
        {
            ex.printStackTrace(pw);
            return sw.toString();
        }
        catch (Exception ignore)
        {
            return "oops!";
        }
    }


    static final String SMEV3_HEADER_PREFIX = "CamelSmev3";

    @Metadata(description = "Идентификатор, присвоенный сообщению отправителем. Генерируется в соответствии с RFC-4122, по варианту 1 (на основании MAC-адреса и текущего времени).")
    public static final String SMEV3_MESSAGE_ID = SMEV3_HEADER_PREFIX + "MessageId";
    @Metadata(description = "Идентификатор исходного Request сообщения для которого отправляется Response. Генерируется в соответствии с RFC-4122, по варианту 1 (на основании MAC-адреса и текущего времени).")
    public static final String SMEV3_ORIGINAL_MESSAGEID = SMEV3_HEADER_PREFIX + "MessageOriginalId";
    @Metadata(description = "Идентификатор сообщения, порождающего цепочку сообщений. При отправке подчиненных сообщений значение соответствует MessageID корневого сообщения цепочки сообщений. Для корневого сообщения значение совпадает с MessageID")
    public static final String SMEV3_MESSAGE_REFERENCE_ID = SMEV3_HEADER_PREFIX + "MessageReferenceId";
    @Metadata(description = "Идентификатор кода транзакции.")
    public static final String SMEV3_METADATA_TRANSACTION_CODE = SMEV3_HEADER_PREFIX + "MetadataTransactionCode";
    @Metadata(description = "Идентификатор ноды отправителя.")
    public static final String SMEV3_METADATA_NODEID = SMEV3_HEADER_PREFIX + "MetadataNodeId";
    @Metadata(description = "Если этот элемент присутствует, то запрос - тестовый. В этом случае, ИС-поставщик данных должна гарантировать, что её данные не будут изменены в результате выполнения этого запроса.")
    public static final String SMEV3_METADATA_TESTMESSAGE = SMEV3_HEADER_PREFIX + "MetadataTestMessage";
// Информация об отправителе, дате отправки, маршрутизации сообщения, и другая (см. определение типа).
// Все данные заполняются СМЭВ. Элемент //MessageMetadata/SendingTimestamp содержит дату и время, начиная с которых
// отсчитывается срок исполнения запроса. Остальные данные предназначены для целей анализа (машинного и ручного) качества обслуживания
// информационной системы - получателя сообщения, а также для предоставления службе поддержки оператора СМЭВ в случае необходимости.
    @Metadata(description = "Время ")
    public static final String SMEV3_METADATA_EOL = SMEV3_HEADER_PREFIX + "MessageEndOfLife";
    @Metadata(description = "")
    public static final String SMEV3_METADATA_IDTRANSPORT = SMEV3_HEADER_PREFIX + "MetadataTransportId";
    @Metadata(description = "")
    public static final String SMEV3_METADATA_PROCESSINGDETAILS = SMEV3_HEADER_PREFIX + "MetadataProcessingDetails";
    @Metadata(description = "")
    public static final String SMEV3_METADATA_INTERACTIONTYPE = SMEV3_HEADER_PREFIX + "MetadataInteractionType";
    @Metadata(description = "")
    public static final String SMEV3_METADATA_DESTINATIONNAME = SMEV3_HEADER_PREFIX + "MetadataDestinationName";
    @Metadata(description = "Тип сообщения")
    public static final String SMEV3_METADATA_MESSAGETYPE = SMEV3_HEADER_PREFIX + "MetadataMessageType";
    @Metadata(description = "Мнемоника отправителя. Для машинной обработки. Вычисляется на основании данных сетрификата.")
    public static final String SMEV3_METADATA_SENDER_MNEMONIC = SMEV3_HEADER_PREFIX + "MetadataSenderMnemonic";
    @Metadata(description = "Наименование отправителя в форме, удобной для восприятия человеком. Вычисляется на основании данных сертификата. Не обязано полностью совпадать с официальным названием организации или органа власти.")
    public static final String SMEV3_METADATA_SENDER_HUMANREADABLENAME = SMEV3_HEADER_PREFIX + "MetadataSenderHumanReadableName";
    @Metadata(description = "Дата и время отправки сообщения в СМЭВ, начиная с которых отсчитывается срок исполнения запроса.")
    public static final String SMEV3_METADATA_SENDINGTIMESTAMP = SMEV3_HEADER_PREFIX + "MetadataSendingTimestamp";
    @Metadata(description = "Мнемоника получателя. Для машинной обработки. Вычисляется на основании данных сетрификата.")
    public static final String SMEV3_METADATA_RECIPIENT_MNEMONIC = SMEV3_HEADER_PREFIX + "MetadataRecipientMnemonic";
    @Metadata(description = "Наименование получателя в форме, удобной для восприятия человеком. Вычисляется на основании данных сертификата. Не обязано полностью совпадать с официальным названием организации или органа власти.")
    public static final String SMEV3_METADATA_RECIPIENT_HUMANREADABLENAME = SMEV3_HEADER_PREFIX + "MetadataRecipientHumanReadableName";
    @Metadata(description = "Дата и время доставки сообщения, по часам СМЭВ.")
    public static final String SMEV3_METADATA_DELIVERYTIMESTAMP = SMEV3_HEADER_PREFIX + "MetadataDeliveryTimestamp";
    @Metadata(description = "Статус сообщения.")
    public static final String SMEV3_METADATA_STATUS = SMEV3_HEADER_PREFIX + "MetadataStatus";
    @Metadata(description = "Аналог обратного адреса; непрозрачный объект, по которому СМЭВ сможет вычислить, кому доставить ответ на этот запрос. При отправке ответа нужно скопировать это значение в //SenderProvidedResponseData/To/text(). N.B. Формат обратного адреса не специфицирован, и может меняться со временем. Больше того, в запросах, пришедших от одного и того же отправителя через сколь угодно малый промежуток времени, обратный адрес не обязан быть одним и тем же. Если получатель хочет идентифицировать отправителя, можно использовать сертификат отправителя (//GetMessageIfAnyResponse/CallerInformationSystemSignature/xmldsig:Signature/...)")
    public static final String SMEV3_MESSAGE_REPLYTO = SMEV3_HEADER_PREFIX + "MessageReplyTo";
    @Metadata(description = "Код статуса.")
    public static final String SMEV3_STATUS_CODE = SMEV3_HEADER_PREFIX + "MessageStatusCode";
    @Metadata(description = "Код причины отклонения запроса.")
    public static final String SMEV3_REJECTION_REASON_CODE = SMEV3_HEADER_PREFIX + "MessageRejectionReasonCode";
    @Metadata(description = "Причина отклонения запроса, в человекочитаемом виде.")
    public static final String SMEV3_DESCRIPTION = SMEV3_HEADER_PREFIX + "MessageDescription";
    @Metadata(javaType = "Boolean", description = "Признак необходимости произвести подтверждение операции чтения. Если присутствует в сообщении, то подтверждение отправляется в СМЭВ автоматически. Значение \"acceped\" берется из этого поля. Может быть true или false.")
    public static final String SMEV3_MESSAGE_ACCEPTED = SMEV3_HEADER_PREFIX + "MessageAccepted";
    @Metadata(description = "Наименование пространства имен бизнес сообщения.")
    public static final String SMEV3_CONTENT_NAMESPACE_URI = SMEV3_HEADER_PREFIX + "ContentNamespaceURI";
    @Metadata(description = "Наименование корневого тега бизнес сообщения без префикса пространства имен.")
    public static final String SMEV3_CONTENT_ROOT_TYPE = SMEV3_HEADER_PREFIX + "ContentRootElementLocalName";
    @Metadata(description = "ЭП отправителя сообщения")
    public static final String SMEV3_CONTENT_PERSONAL_SIGNATURE = SMEV3_HEADER_PREFIX + "ContentPersonalSignature";
    @Metadata(description = "Причина ошибки")
    public static final String SMEV3_EXCEPTION_CAUSE = SMEV3_HEADER_PREFIX + "MetadataExceptionCause";
    @Metadata(description = "Стэк трейс ошибки на стороне СМЭВ")
    public static final String SMEV3_EXCEPTION_STACKTRACE = SMEV3_HEADER_PREFIX + "MetadataExceptionStackTrace";
    @Metadata(description = "Сообщение об ошибке")
    public static final String SMEV3_EXCEPTION_MESSAGE = SMEV3_HEADER_PREFIX + "MetadataExceptionMessage";
    @Metadata(description = "Локализованное сообщение об ошибке")
    public static final String SMEV3_EXCEPTION_LOCALIZED_MESSAGE = SMEV3_HEADER_PREFIX + "MetadataExceptionLocalizedMessage";
    @Metadata(description = "Код ошибки")
    public static final String SMEV3_EXCEPTION_CODE = SMEV3_HEADER_PREFIX + "MetadataExceptionCode";
    @Metadata(description = "Exception dump")
    public static final String SMEV3_EXCEPTION_DUMP = SMEV3_HEADER_PREFIX + "MetadataExceptionDump";

    public static final String SMEV3_ATTACHMENT_MIMETYPE = "AttachmentMimeType";
    public static final String SMEV3_ATTACHMENT_SIGNATUREPKCS7 = "AttachmentSignaturePKCS7";
    public static final String SMEV3_ATTACHMENT_PASSPORTID = "AttachmentPassportId";
    public static final String SMEV3_ATTACHMENT_UUID = "AttachmentUUId";
    public static final String SMEV3_ATTACHMENT_NAME = "AttachmentName";
    public static final String SMEV3_ATTACHMENT_LENGTH = "AttachmentLength";
    public static final String SMEV3_ATTACHMENT_HASH = "AttachmentHash";
}
