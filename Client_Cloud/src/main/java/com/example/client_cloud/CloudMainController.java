package com.example.client_cloud;

import io.netty.handler.codec.serialization.ObjectDecoderInputStream;
import io.netty.handler.codec.serialization.ObjectEncoderOutputStream;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.scene.control.ListView;
import org.example.DaemonThreadFactory;
import org.example.model.CloudMessage;
import org.example.model.FileMessage;
import org.example.model.FileRequest;
import org.example.model.ListMessage;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

import static org.example.Command.*;
import static org.example.FileUtils.readFileFromStream;

public class CloudMainController implements Initializable {
    public ListView<String> clientView;
    public ListView<String> serverView;
    private String currentDirectory;

    private Network<ObjectDecoderInputStream, ObjectEncoderOutputStream> network;

    private Socket socket;

    private boolean needReadMessages = true;

    private DaemonThreadFactory factory;

    public void downloadFile(ActionEvent actionEvent) throws IOException {
        String fileName = serverView.getSelectionModel().getSelectedItem();
        network.getOutputStream().writeObject(new FileRequest(fileName));
    }

    public void sendToServer(ActionEvent actionEvent) throws IOException {
        String fileName = clientView.getSelectionModel().getSelectedItem();
        network.getOutputStream().writeObject(new FileMessage(Path.of(currentDirectory).resolve(fileName)));
    }

    private void readMessages() {
        try {
            while (needReadMessages) {
                CloudMessage cloudMessage = (CloudMessage) network.getInputStream().readObject();
                if (cloudMessage instanceof FileMessage fileMessage) {
                    Files.write(Path.of(currentDirectory).resolve(fileMessage.getFileName()), fileMessage.getBytes());
                    Platform.runLater(() -> fillView(clientView, getFiles(currentDirectory)));
                } else if (cloudMessage instanceof ListMessage listMessage) {
                    Platform.runLater(() -> fillView(serverView, listMessage.getFiles()));
                }
            }
        } catch (Exception e) {
            System.err.println("Server off");
            e.printStackTrace();
        }
    }

    private void initNetwork() {
        try {
            socket = new Socket("localhost", 8189);
            network = new Network<>(
                    new ObjectDecoderInputStream(socket.getInputStream()),
                    new ObjectEncoderOutputStream(socket.getOutputStream()));
            factory.getThread(this::readMessages, "cloud-client-read-thread")
                    .start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        needReadMessages = true;
        factory = new DaemonThreadFactory();
        initNetwork();
        setCurrentDirectory(System.getProperty("user.home"));
        fillView(clientView, getFiles(currentDirectory));
        clientView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                String selected = clientView.getSelectionModel().getSelectedItem();
                File selectedFile = new File(currentDirectory + "/" + selected);
                if (selectedFile.isDirectory()) {
                    setCurrentDirectory(currentDirectory + "/" + selected);
                }
            }
        });
    }

    private void setCurrentDirectory(String directory) {
        currentDirectory = directory;
        fillView(clientView, getFiles(currentDirectory));
    }

    private void fillView(ListView<String> view, List<String> data) {
        view.getItems().clear();
        view.getItems().addAll(data);
    }

    private List<String> getFiles(String directory) {
        File dir = new File(directory);
        if (dir.isDirectory()) {
            String[] list = dir.list();
            if (list != null) {
                List<String> files = new ArrayList<>(Arrays.asList(list));
                files.add(0, "..");
                return files;
            }
        }
        return List.of();
    }

}