# camel-smev 

Реализация apache camel компоненты для доступа к СМЭВ3 (чтение и запись).
## Версии
JRE: 11

Apache Camel: 3.20

### [Примеры использования]

Чтение из очереди СМЭВ вида сведений и сохранение их в папку "/smev_input"
```xml

<!-- Используем стратегию сохранения вложений в папку -->
<bean id="fileAttachmentsStrategy" class="org.apache.camel.component.smev3.strategy.FileAttachmentsStrategy"> 
	<constructor-arg index="0" value="/smev_input"/>
	<constructor-arg index="1" value="false"/>
</bean>	

<route>
	<from uri="smev3:request?attachmentsStrategy=#fileAttachmentsStrategy"/> <!-- читаем сообщения из очереди СМЭВ -->

	<to uri="file:_in_out_/in?fileName=${headers.CamelSmev3MessageId}_request.xml"/> <!-- сохраняем бизнес содержимое -->

	<setBody><simple>${headers}</simple></setBody>
	<convertBodyTo type="java.lang.String"/>
	<to uri="file:_in_out_/in?fileName=${headers.CamelSmev3MessageId}_headers.xml"/> <!-- сохраняем содержимое заголовков -->

</route>
```

Чтение статусов из очереди СМЭВ и сохранение их в папку "/smev_input"
```xml
<route>
	<from uri="smev3:status?attachmentsStrategy=#fileAttachmentsStrategy"/> <!-- читаем статусы из очереди СМЭВ -->

	<setBody><simple>${headers}</simple></setBody>
	<convertBodyTo type="java.lang.String"/>
	<to uri="file:_in_out_/in?fileName=${headers.CamelSmev3MessageReferenceId}_status_headers.xml"/> <!-- сохраняем содержимое заголовков -->

</route>
```

Отправка сообщения в очередь СМЭВ
```xml
<route>
	<from/> <!-- Чтение откуда то -->
	<convertBodyTo type="java.lang.String"/>
	
	<setHeader name="CamelSmev3MessageOriginalId">....</setHeader> <!-- заполнение обязательных заголовков -->
	<setHeader name="CamelSmev3MetadataTransportId">....</setHeader>
	<setHeader name="CamelSmev3MetadataTransactionCode">....</setHeader>
	<setHeader name="CamelSmev3MessageReplyTo">....</setHeader>
    
	<to uri="smev3:response"/> <!-- отправка в очередь СМЭВ -->

	<setBody><simple>${headers}</simple></setBody>
	<convertBodyTo type="java.lang.String"/>
	<to uri="file:_in_out_/in?fileName=${headers.CamelSmev3MessageId}_result_headers.xml"/> <!-- сохранение заголовков операции отправки в очередь СМЭВ -->
</route>

```


### [Сборка]

Для сборки требуются бибилиотеки:
- клиент СМЭВ3 версии 3.1.8 - берется с сайта госуслуг.
	
Используются модули:

		api-3.1.8.jar
		commons-3.1.8.jar
		crypto-adapter-1.2.jar
		factory-3.1.8.jar
		identification-3.1.8.jar
		message-3.1.8.jar
		server-api-3.1.8.jar
		signature-3.1.8.jar
		template-3.1.8.jar
		transaction-3.1.8.jar
		transport-3.1.8.jar
		util-3.1.8.jar
		validation-3.1.8.jar

- крипто провайдер Крипто-Про - покупается у соответствующего поставщика.
- apache camel 3.20

Для использования требуется:
- наличие регистрации в ЛК СМЭВ3, получение ключей и сертификатов в соответсвии с процедурой СМЭВ3.


### [Описание]

Чтение из очереди СМЭВ3:
```xml
    <from uri="smev3:[mode]?[delay]&amp;[bodyType]&amp;[version]&amp;[nodeId]&amp;[errorDelay]&amp;[rootElementLocalName]&amp;[namespaceURI]"/>
		mode=request|response|status - тип очереди откуда производится чтение. Обязательный параметр.
		bodyType=content|envelop|smevmessage - тип содержимого, которе будет помещено в ${body}
				 content - бизнес содержимое вида сведений СМЭВ3 полученное из очереди. По умолчанию content.
				 envelop - полное soap сообщение полученное из очереди
				 smevmessage - объект SMEVMessage полученный из очереди
		version=1.1|1.2|1.3 - версия пакета обмена СМЭВ3. По умолчанию 1.3
		nodeId=наименование ноды, если идет прллельная обработка сообщений. Не обязательный параметр.
		rootElementLocalName=нименование корневого тега вида сведений СМЭВ3. Не обязательный параметр
		namespaceURI=наименование пространства имен корневого тега вида сведений СМЭВ3. Не обязательный параметр
				Усли не указывать rootElementLocalName и namespaceURI, то будут читаться все сообщения из очереди.
		errorDelay=задержка в миллисекундах, в случае сбоя обработки входящего сообщения. По умолчанию 60 сек.

	После операции чтения из очереди СМЭВ3 заполняются:
	
	1. body - в зависимости от bodyType

	2. все вложения, при их наличии в полученном виде сведений.

	Для работы с вложениями используется стандартный механизм apache camel.
		AttachmentMessage attMsg = exchange.getIn(AttachmentMessage.class);
		Attachment attachment = attMsg.getAttachmentObject("myAttachment");
		DataHandler dh = attachment.getDataHandler();

	3. заголовки (при их наличии в сообщении):

	header.CamelSmev3MessageId - Идентификатор, присвоенный сообщению отправителем. Генерируется в соответствии с RFC-4122, по варианту 1 (на основании MAC-адреса и текущего времени).
	header.CamelSmev3MessageOriginalId - Идентификатор исходного Request сообщения для которого отправляется Response. Генерируется в соответствии с RFC-4122, по варианту 1 (на основании MAC-адреса и текущего времени).
	header.CamelSmev3MessageReferenceId - Идентификатор сообщения, порождающего цепочку сообщений. При отправке подчиненных сообщений значение соответствует MessageID корневого сообщения цепочки сообщений. Для корневого сообщения значение совпадает с MessageID.
	header.CamelSmev3MetadataTransactionCode - Идентификатор кода транзакции.
	header.CamelSmev3MetadataNodeId - Идентификатор ноды отправителя.
	header.CamelSmev3MetadataTestMessage - Если этот элемент присутствует, то запрос - тестовый. В этом случае, ИС-поставщик данных должна гарантировать, что её данные не будут изменены в результате выполнения этого запроса.
	header.CamelSmev3MetadataTransportId - Наименование транспорта
	header.CamelSmev3MetadataMessageType - Тип сообщения
	header.CamelSmev3MetadataSenderMnemonic - Мнемоника отправителя. Для машинной обработки. Вычисляется на основании данных сетрификата.
	header.CamelSmev3MetadataSenderHumanReadableName - Наименование отправителя в форме, удобной для восприятия человеком. Вычисляется на основании данных сертификата. Не обязано полностью совпадать с официальным названием организации или органа власти.
	header.CamelSmev3MetadataSendingTimestamp - Дата и время отправки сообщения в СМЭВ, начиная с которых отсчитывается срок исполнения запроса.
	header.CamelSmev3MetadataRecipientMnemonic - Мнемоника получателя. Для машинной обработки. Вычисляется на основании данных сетрификата.
	header.CamelSmev3MetadataRecipientHumanReadableName - Наименование получателя в форме, удобной для восприятия человеком. Вычисляется на основании данных сертификата. Не обязано полностью совпадать с официальным названием организации или органа власти.
	header.CamelSmev3MetadataDeliveryTimestamp - Дата и время доставки сообщения, по часам СМЭВ.
	header.CamelSmev3MetadataStatus - Статус сообщения.
	header.CamelSmev3MessageReplyTo - Аналог обратного адреса; непрозрачный объект, по которому СМЭВ сможет вычислить, кому доставить ответ на этот запрос. При отправке ответа нужно скопировать это значение в //SenderProvidedResponseData/To/text(). N.B. Формат обратного адреса не специфицирован, и может меняться со временем. Больше того, в запросах, пришедших от одного и того же отправителя через сколь угодно малый промежуток времени, обратный адрес не обязан быть одним и тем же. Если получатель хочет идентифицировать отправителя, можно использовать сертификат отправителя (//GetMessageIfAnyResponse/CallerInformationSystemSignature/xmldsig:Signature/...)
	header.CamelSmev3MessageStatusCode - Код статуса.
	header.CamelSmev3MessageRejectionReasonCode - Код причины отклонения запроса.
	header.CamelSmev3MessageDescription - Причина отклонения запроса, в человекочитаемом виде.
	header.CamelSmev3MessageAccepted - Признак необходимости произвести подтверждение операции чтения. Если присутствует в сообщении, то подтверждение отправляется в СМЭВ автоматически. Значение \"acceped\" берется из этого поля. Может быть true или false.
	header.CamelSmev3ContentNamespaceURI - Наименование пространства имен бизнес сообщения.
	header.CamelSmev3ContentRootElementLocalName - Наименование корневого тега бизнес сообщения без префикса пространства имен.
	header.CamelSmev3ContentPersonalSignature - ЭП отправителя сообщения
	header.CamelSmev3MetadataExceptionCause - Причина ошибки
	header.CamelSmev3MetadataExceptionStackTrace - Стэк трейс ошибки на стороне СМЭВ
	header.CamelSmev3MetadataExceptionMessage - Сообщение об ошибке
	header.CamelSmev3MetadataExceptionLocalizedMessage - Локализованное сообщение об ошибке
	header.CamelSmev3MetadataExceptionCode - Код ошибки
	header.CamelSmev3MetadataExceptionDump - Exception dump

```

Запись в очередь:    
```xml
    <to uri="smev3:[mode]?[version]&amp;"/>
		mode=request|response|status|ack|reject - тип очереди куда производится запись. Обязательный параметр.
		version=1.1|1.2|1.3 - версия пакета обмена СМЭВ3. По умолчанию 1.3

	Перед операцией отправки в очередь СМЭВ3 заполняются:
	
	1. body - бизнес содержимое вида сведений СМЭВ3

	2. все вложения, при их наличии в полученном виде сведений.

	Для работы с вложениями используется стандартный механизм apache camel.
	Для каждого вложения необходимо заполнить заголовки вложения:
		AttachmentName
		AttachmentMimeType
		AttachmentLength

        AttachmentMessage attachmentMessage = exchange.getIn(AttachmentMessage.class);
        File f = new File("myAttachment.pdf");
        Attachment a = new DefaultAttachment(new DataHandler(new FileDataSource(f)));
        a.setHeader("AttachmentLength", Long.toString(f.length()));
        a.setHeader("AttachmentMimeType", "application/pdf");
        a.setHeader("AttachmentName", f.getName());
        attachmentMessage.addAttachmentObject(f.getName(), a);

	3. заголовки сообщения:

	header.CamelSmev3MessageOriginalId - Идентификатор исходного Request сообщения для которого отправляется Response. Генерируется в соответствии с RFC-4122, по варианту 1 (на основании MAC-адреса и текущего времени).
	header.CamelSmev3MetadataTransportId - Наименование транспорта
	header.CamelSmev3MetadataTransactionCode - Идентификатор кода транзакции.
	header.CamelSmev3MessageReplyTo - Аналог обратного адреса; непрозрачный объект, по которому СМЭВ сможет вычислить, кому доставить ответ на этот запрос. При отправке ответа нужно скопировать это значение в //SenderProvidedResponseData/To/text(). N.B. Формат обратного адреса не специфицирован, и может меняться со временем. Больше того, в запросах, пришедших от одного и того же отправителя через сколь угодно малый промежуток времени, обратный адрес не обязан быть одним и тем же. Если получатель хочет идентифицировать отправителя, можно использовать сертификат отправителя (//GetMessageIfAnyResponse/CallerInformationSystemSignature/xmldsig:Signature/...)
		
```

Так же в application.properties должны быть добавлены настройки (как минимум).

smev3.jcp.license.key = 

smev3.signer.certificate.alias = 

smev3.signer.private.key.alias = 

smev3.signer.private.key.password = 

smev3.signer.key.store.type = 

smev3.transport.1.2.main.url = http://smev3-n0.test.gosuslugi.ru:7500/smev/v1.2/ws

smev3.transport.1.3.main.url = http://smev3-n0.test.gosuslugi.ru:5000/transport_1_0_2/

smev3.large.attachment.transport.address = smev3-n0.test.gosuslugi.ru

smev3.large.attachment.transport.login = 

smev3.large.attachment.transport.password = 

smev3.large.attachment.threshold = 1048576


Подробнее в Smev3Configuration.java
