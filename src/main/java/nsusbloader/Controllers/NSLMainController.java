package nsusbloader.Controllers;

import javafx.application.HostServices;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
import nsusbloader.*;
import nsusbloader.ModelControllers.UpdatesChecker;
import nsusbloader.NET.NETCommunications;
import nsusbloader.USB.UsbCommunications;

import java.io.File;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class NSLMainController implements Initializable {

    private ResourceBundle resourceBundle;

    @FXML
    public TextArea logArea;            // Accessible from Mediator
    @FXML
    private Button selectNspBtn;
    @FXML
    private Button uploadStopBtn;
    private Region btnUpStopImage;
    @FXML
    public ProgressBar progressBar;            // Accessible from Mediator
    @FXML
    private ChoiceBox<String> choiceProtocol, choiceNetUsb;
    @FXML
    private Button switchThemeBtn;

    @FXML
    private Pane specialPane;

    @FXML
    public NSTableViewController tableFilesListController;            // Accessible from Mediator
    @FXML
    private SettingsController SettingsTabController;
    @FXML
    private TextField nsIpTextField;
    @FXML
    private Label nsIpLbl;

    private Task<Void> usbNetCommunications;
    private Thread workThread;

    private String previouslyOpenedPath;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        this.resourceBundle = rb;
        logArea.setText(rb.getString("logsGreetingsMessage")+" "+ NSLMain.appVersion+"!\n");
        if (System.getProperty("os.name").toLowerCase().startsWith("lin"))
            if (!System.getProperty("user.name").equals("root"))
                logArea.appendText(rb.getString("logsEnteredAsMsg1")+System.getProperty("user.name")+"\n"+rb.getString("logsEnteredAsMsg2") + "\n");

        logArea.appendText(rb.getString("logsGreetingsMessage2")+"\n");

        MediatorControl.getInstance().setController(this);

        specialPane.getStyleClass().add("special-pane-as-border");  // UI hacks

        uploadStopBtn.setDisable(true);
        selectNspBtn.setOnAction(e->{ selectFilesBtnAction(); });
        uploadStopBtn.setOnAction(e->{ uploadBtnAction(); });

        selectNspBtn.getStyleClass().add("buttonSelect");

        this.btnUpStopImage = new Region();
        btnUpStopImage.getStyleClass().add("regionUpload");
        //uploadStopBtn.getStyleClass().remove("button");
        uploadStopBtn.getStyleClass().add("buttonUp");
        uploadStopBtn.setGraphic(btnUpStopImage);

        ObservableList<String> choiceProtocolList = FXCollections.observableArrayList("TinFoil", "GoldLeaf");
        choiceProtocol.setItems(choiceProtocolList);
        choiceProtocol.getSelectionModel().select(AppPreferences.getInstance().getProtocol());
        choiceProtocol.setOnAction(e-> {
            tableFilesListController.setNewProtocol(choiceProtocol.getSelectionModel().getSelectedItem());
            if (choiceProtocol.getSelectionModel().getSelectedItem().equals("GoldLeaf")) {
                choiceNetUsb.setDisable(true);
                choiceNetUsb.getSelectionModel().select("USB");
                nsIpLbl.setVisible(false);
                nsIpTextField.setVisible(false);
            }
            else {
                choiceNetUsb.setDisable(false);
                if (choiceNetUsb.getSelectionModel().getSelectedItem().equals("NET")) {
                    nsIpLbl.setVisible(true);
                    nsIpTextField.setVisible(true);
                }
            }
        });  // Add listener to notify tableView controller
        tableFilesListController.setNewProtocol(choiceProtocol.getSelectionModel().getSelectedItem());   // Notify tableView controller

        ObservableList<String> choiceNetUsbList = FXCollections.observableArrayList("USB", "NET");
        choiceNetUsb.setItems(choiceNetUsbList);
        choiceNetUsb.getSelectionModel().select(AppPreferences.getInstance().getNetUsb());
        if (choiceProtocol.getSelectionModel().getSelectedItem().equals("GoldLeaf")) {
            choiceNetUsb.setDisable(true);
            choiceNetUsb.getSelectionModel().select("USB");
        }
        choiceNetUsb.setOnAction(e->{
            if (choiceNetUsb.getSelectionModel().getSelectedItem().equals("NET")){
                nsIpLbl.setVisible(true);
                nsIpTextField.setVisible(true);
            }
            else{
                nsIpLbl.setVisible(false);
                nsIpTextField.setVisible(false);
            }
        });
        nsIpTextField.setText(AppPreferences.getInstance().getNsIp());
        if (choiceProtocol.getSelectionModel().getSelectedItem().equals("TinFoil") && choiceNetUsb.getSelectionModel().getSelectedItem().equals("NET")){
            nsIpLbl.setVisible(true);
            nsIpTextField.setVisible(true);
        }
        nsIpTextField.setTextFormatter(new TextFormatter<>(change -> {
            if (change.getControlNewText().contains(" ") | change.getControlNewText().contains("\t"))
                return null;
            else
                return change;
        }));
        this.previouslyOpenedPath = null;

        Region btnSwitchImage = new Region();
        btnSwitchImage.getStyleClass().add("regionLamp");
        switchThemeBtn.setGraphic(btnSwitchImage);
        this.switchThemeBtn.setOnAction(e->switchTheme());

        previouslyOpenedPath = AppPreferences.getInstance().getRecent();

        if (AppPreferences.getInstance().getAutoCheckUpdates()){
            Task<List<String>> updTask = new UpdatesChecker();
            updTask.setOnSucceeded(event->{
                List<String> result = updTask.getValue();
                if (result != null){
                    if (!result.get(0).isEmpty()) {
                        SettingsTabController.setNewVersionLink(result.get(0));
                        ServiceWindow.getInfoNotification(resourceBundle.getString("windowTitleNewVersionAval"), resourceBundle.getString("windowTitleNewVersionAval") + ": " + result.get(0) + "\n\n" + result.get(1));
                    }
                }
                else
                    ServiceWindow.getInfoNotification(resourceBundle.getString("windowTitleNewVersionUnknown"), resourceBundle.getString("windowBodyNewVersionUnknown"));
            });
            Thread updates = new Thread(updTask);
            updates.setDaemon(true);
            updates.start();
        }
    }
    /**
     * Provide hostServices to Settings tab
     * */
    public void setHostServices(HostServices hs ){ SettingsTabController.registerHostServices(hs);}
    /**
     * Changes UI theme on the go
     * */
    private void switchTheme(){
        if (switchThemeBtn.getScene().getStylesheets().get(0).equals("/res/app_dark.css")) {
            switchThemeBtn.getScene().getStylesheets().remove("/res/app_dark.css");
            switchThemeBtn.getScene().getStylesheets().add("/res/app_light.css");
        }
        else {
            switchThemeBtn.getScene().getStylesheets().remove("/res/app_light.css");
            switchThemeBtn.getScene().getStylesheets().add("/res/app_dark.css");
        }
        AppPreferences.getInstance().setTheme(switchThemeBtn.getScene().getStylesheets().get(0));
    }
    /**
     * Functionality for selecting NSP button.
     * Uses setReady and setNotReady to simplify code readability.
     * */
    private void selectFilesBtnAction(){
        List<File> filesList;
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(resourceBundle.getString("btnFileOpen"));

        File validator = new File(previouslyOpenedPath);
        if (validator.exists())
            fileChooser.setInitialDirectory(validator);         // TODO: read from prefs
        else
            fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));         // TODO: read from prefs

        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("NSP ROM", "*.nsp"));

        filesList = fileChooser.showOpenMultipleDialog(logArea.getScene().getWindow());
        if (filesList != null && !filesList.isEmpty()) {
            tableFilesListController.setFiles(filesList);
            uploadStopBtn.setDisable(false);
            previouslyOpenedPath = filesList.get(0).getParent();
        }
    }
    /**
     * It's button listener when no transmission executes
     * */
    private void uploadBtnAction(){
        if ((workThread == null || !workThread.isAlive())){
            // Collect files
            List<File> nspToUpload;
            if ((nspToUpload = tableFilesListController.getFilesForUpload()) == null) {
                logArea.setText(resourceBundle.getString("logsNoFolderFileSelected"));
                return;
            }
            else {
                logArea.setText(resourceBundle.getString("logsFilesToUploadTitle")+"\n");
                for (File item: nspToUpload)
                    logArea.appendText("  "+item.getAbsolutePath()+"\n");
            }
            // If USB selected
            if (choiceProtocol.getSelectionModel().getSelectedItem().equals("GoldLeaf") ||
                    (
                    choiceProtocol.getSelectionModel().getSelectedItem().equals("TinFoil")
                    && choiceNetUsb.getSelectionModel().getSelectedItem().equals("USB")
                    )
            ){
                usbNetCommunications = new UsbCommunications(nspToUpload, choiceProtocol.getSelectionModel().getSelectedItem());
                workThread = new Thread(usbNetCommunications);
                workThread.setDaemon(true);
                workThread.start();
            }
            else {      // NET INSTALL OVER TINFOIL
                if (SettingsTabController.isNsIpValidate() && !nsIpTextField.getText().matches("^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])$"))
                    if (!ServiceWindow.getConfirmationWindow(resourceBundle.getString("windowTitleBadIp"),resourceBundle.getString("windowBodyBadIp")))
                        return;

                String nsIP = nsIpTextField.getText();

                if (!SettingsTabController.getExpertModeSelected())
                    usbNetCommunications = new NETCommunications(nspToUpload, nsIP, false, "", "", "");
                else {
                    usbNetCommunications = new NETCommunications(
                            nspToUpload,
                            nsIP,
                            SettingsTabController.getNotServeSelected(),
                            SettingsTabController.getAutoIpSelected()?"":SettingsTabController.getHostIp(),
                            SettingsTabController.getRandPortSelected()?"":SettingsTabController.getHostPort(),
                            SettingsTabController.getNotServeSelected()?SettingsTabController.getHostExtra():""
                    );
                }

                workThread = new Thread(usbNetCommunications);
                workThread.setDaemon(true);
                workThread.start();
            }
        }
    }
    /**
     * It's button listener when transmission in progress
     * */
    private void stopBtnAction(){
        if (workThread != null && workThread.isAlive()){
            usbNetCommunications.cancel(false);            // TODO: add something abstract to use also for network
        }
    }
    /**
     * This thing modify UI for reusing 'Upload to NS' button and make functionality set for "Stop transmission"
     * Called from mediator
     * */
    public void notifyTransmissionStarted(boolean isTransmissionStarted){
        if (isTransmissionStarted) {
            selectNspBtn.setDisable(true);
            uploadStopBtn.setOnAction(e->{ stopBtnAction(); });

            uploadStopBtn.setText(resourceBundle.getString("btnStop"));

            btnUpStopImage.getStyleClass().remove("regionUpload");
            btnUpStopImage.getStyleClass().add("regionStop");

            uploadStopBtn.getStyleClass().remove("buttonUp");
            uploadStopBtn.getStyleClass().add("buttonStop");
        }
        else {
            selectNspBtn.setDisable(false);
            uploadStopBtn.setOnAction(e->{ uploadBtnAction(); });

            uploadStopBtn.setText(resourceBundle.getString("btnUpload"));

            btnUpStopImage.getStyleClass().remove("regionStop");
            btnUpStopImage.getStyleClass().add("regionUpload");

            uploadStopBtn.getStyleClass().remove("buttonStop");
            uploadStopBtn.getStyleClass().add("buttonUp");
        }
    }
    /**
     * Crunch. Now you see that I'm not a programmer.. This function called from NSTableViewController
     * */
    public void disableUploadStopBtn(boolean disable){
        uploadStopBtn.setDisable(disable);
    }
    /**
     * Drag-n-drop support (dragOver consumer)
     * */
    @FXML
    private void handleDragOver(DragEvent event){
        if (event.getDragboard().hasFiles())
            event.acceptTransferModes(TransferMode.ANY);
    }
    /**
     * Drag-n-drop support (drop consumer)
     * */
    @FXML
    private void handleDrop(DragEvent event){
        if (MediatorControl.getInstance().getTransferActive()) {
            event.setDropCompleted(true);
            return;
        }
        List<File> filesDropped = new ArrayList<>();
        try {
            for (File fileOrDir : event.getDragboard().getFiles()) {
                if (fileOrDir.getName().toLowerCase().endsWith(".nsp"))
                    filesDropped.add(fileOrDir);
                else if (fileOrDir.isDirectory())
                    for (File file : fileOrDir.listFiles())
                        if (file.getName().toLowerCase().endsWith(".nsp"))
                            filesDropped.add(file);
            }
        }
        catch (SecurityException se){
            se.printStackTrace();
        }
        if (!filesDropped.isEmpty())
            tableFilesListController.setFiles(filesDropped);

        event.setDropCompleted(true);
    }
    /**
     * Save preferences before exit
     * */
    public void exit(){ // TODO: add method to set all in AppPreferences
        AppPreferences.getInstance().setAll(
                choiceProtocol.getSelectionModel().getSelectedItem(),
                previouslyOpenedPath,
                choiceNetUsb.getSelectionModel().getSelectedItem(),
                nsIpTextField.getText().trim(),
                SettingsTabController.isNsIpValidate(),
                SettingsTabController.getExpertModeSelected(),
                SettingsTabController.getAutoIpSelected(),
                SettingsTabController.getRandPortSelected(),
                SettingsTabController.getNotServeSelected(),
                SettingsTabController.getHostIp(),
                SettingsTabController.getHostPort(),
                SettingsTabController.getHostExtra(),
                SettingsTabController.getAutoCheckForUpdates()
        );
    }
}
