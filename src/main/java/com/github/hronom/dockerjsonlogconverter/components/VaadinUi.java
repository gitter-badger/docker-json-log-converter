package com.github.hronom.dockerjsonlogconverter.components;

import com.github.hronom.dockerjsonlogconverter.components.docker.json.log.ConvertingService;
import com.vaadin.annotations.Theme;
import com.vaadin.server.VaadinRequest;
import com.vaadin.shared.ui.ContentMode;
import com.vaadin.shared.ui.ValueChangeMode;
import com.vaadin.spring.annotation.SpringUI;
import com.vaadin.ui.Button;
import com.vaadin.ui.CustomLayout;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.Notification;
import com.vaadin.ui.Panel;
import com.vaadin.ui.TextArea;
import com.vaadin.ui.UI;
import com.vaadin.ui.Upload;
import com.vaadin.ui.VerticalLayout;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Theme("mytheme")
@SpringUI
public class VaadinUi extends UI {
    private final Log logger = LogFactory.getLog(getClass());

    private final ConvertingService convertingService;
    private final MessageSource messageSource;

    private final Resource disqusResource;

    private volatile Path sourceTempFilePath;
    private volatile Path targetTempFilePath;
    private volatile OutputStream outputStream;

    @Autowired
    public VaadinUi(
        ConvertingService convertingService,
        MessageSource messageSource,
        @Value("${spring.application.name}") String appName,
        @Value(value = "classpath:data/disqus.html") Resource disqusResource
    ) throws IOException {
        this.convertingService = convertingService;
        this.messageSource = messageSource;
        this.disqusResource = disqusResource;

        getPage().setTitle(appName);
    }

    @Override
    protected void init(VaadinRequest request) {
        Label mainLabel = new Label(getMessageLocalized("main-label"), ContentMode.HTML);
        mainLabel.setSizeUndefined();

        TextArea inputTextArea = new TextArea();
        inputTextArea.setSizeFull();
        inputTextArea.setWordWrap(false);
        inputTextArea.setHeight(250, Unit.PIXELS);
        inputTextArea.setPlaceholder(getMessageLocalized("input-text-area"));
        inputTextArea.setValueChangeMode(ValueChangeMode.LAZY);

        Button convertButton = new Button(getMessageLocalized("convert-button"));
        convertButton.setSizeUndefined();

        Label orLabel = new Label(getMessageLocalized("or-label"), ContentMode.HTML);
        orLabel.setSizeUndefined();
        orLabel.setHeight(100, Unit.PERCENTAGE);

        Upload upload = new Upload();
        upload.setButtonCaption(getMessageLocalized("upload"));
        upload.setSizeUndefined();

        Label uploadProgressLabel = new Label();
        uploadProgressLabel.setWidth(100, Unit.PERCENTAGE);
        uploadProgressLabel.setVisible(false);

        TextArea outputTextArea = new TextArea();
        outputTextArea.setSizeFull();
        outputTextArea.setWordWrap(false);
        outputTextArea.setHeight(250, Unit.PIXELS);
        outputTextArea.setPlaceholder(getMessageLocalized("output-text-area"));
        outputTextArea.setValueChangeMode(ValueChangeMode.LAZY);
        outputTextArea.setReadOnly(true);

        CustomLayout customLayout = null;
        try (InputStream inputStream = disqusResource.getInputStream()) {
            customLayout = new CustomLayout(inputStream);
            customLayout.setSizeFull();
        } catch (IOException e) {
            logger.error("Error", e);
            Notification.show(
                getMessageLocalized("notification-error"),
                Notification.Type.ERROR_MESSAGE
            );
        }

        HorizontalLayout manipulationLayout = new HorizontalLayout();
        manipulationLayout.addComponent(convertButton);
        manipulationLayout.addComponent(orLabel);
        manipulationLayout.addComponent(upload);
        manipulationLayout.setSizeUndefined();

        HorizontalLayout resultLayout = new HorizontalLayout();
        resultLayout.setSizeFull();

        VerticalLayout mainLayout = new VerticalLayout();
        mainLayout.addComponent(mainLabel);
        mainLayout.addComponent(inputTextArea);
        mainLayout.addComponent(manipulationLayout);
        mainLayout.addComponent(resultLayout);
        mainLayout.addComponent(customLayout);
        mainLayout.setSizeFull();
        mainLayout.setExpandRatio(inputTextArea, 1.0f);
        mainLayout.setExpandRatio(manipulationLayout, 0.0f);
        mainLayout.setExpandRatio(resultLayout, 1.0f);
        mainLayout.setExpandRatio(customLayout, 1.0f);

        Panel mainPanel = new Panel();
        mainPanel.setContent(mainLayout);

        setContent(mainPanel);

        upload.setReceiver((Upload.Receiver) (filename, mimeType) -> {
            try {
                sourceTempFilePath = Files.createTempFile("", "_source");
                outputStream = Files.newOutputStream(sourceTempFilePath);
                return outputStream;
            } catch (IOException e) {
                Notification.show(
                    getMessageLocalized("notification-error"),
                    Notification.Type.ERROR_MESSAGE
                );
                logger.error("Error", e);
                return null;
            }
        });
        upload.addStartedListener((Upload.StartedListener) event -> {
            resultLayout.removeAllComponents();

            inputTextArea.clear();

            uploadProgressLabel
                .setValue(getMessageLocalized("upload-progress-label", event.getFilename()));
            resultLayout.addComponent(uploadProgressLabel);

            uploadProgressLabel.setVisible(true);
        });
        upload.addSucceededListener((Upload.SucceededListener) event -> {
            try {
                targetTempFilePath = Files.createTempFile("", "_processed");
                convertingService.saveToTxt(sourceTempFilePath, targetTempFilePath);

                resultLayout.removeAllComponents();
                DownloadFileLink downloadFileLink = new DownloadFileLink(
                    messageSource,
                    targetTempFilePath,
                    event.getFilename() + ".txt"
                );
                resultLayout.addComponent(downloadFileLink);
                uploadProgressLabel.setVisible(false);
            } catch (IOException e) {
                Notification.show(
                    getMessageLocalized("notification-error"),
                    Notification.Type.ERROR_MESSAGE
                );
                logger.error("Error", e);
            }
        });
        convertButton.addClickListener((Button.ClickListener) event -> {
            try {
                resultLayout.removeAllComponents();
                String json = inputTextArea.getValue();
                String result = convertingService.toTxt(json);
                outputTextArea.setValue(result);
                resultLayout.addComponent(outputTextArea);
            } catch (Exception e) {
                Notification.show(
                    getMessageLocalized("notification-error"),
                    Notification.Type.ERROR_MESSAGE
                );
                logger.error("Error", e);
            }
        });
    }

    private String getMessageLocalized(String key, String... args) {
        return messageSource.getMessage(key, args, this.getLocale());
    }
}