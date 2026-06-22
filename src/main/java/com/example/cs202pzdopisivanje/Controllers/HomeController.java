package com.example.cs202pzdopisivanje.Controllers;

import Enums.SceneEnum;
import com.example.cs202pzdopisivanje.CellFactories.ChatCell;
import com.example.cs202pzdopisivanje.Database.DbManager;
import com.example.cs202pzdopisivanje.HomeApplication;
import com.example.cs202pzdopisivanje.Network.Client;
import com.example.cs202pzdopisivanje.Objects.Chat;
import com.example.cs202pzdopisivanje.Objects.Message;
import com.example.cs202pzdopisivanje.Requests.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * HomeController manages all the functions of the Home page in the application.
 * Supports a separated friends and groups menu, displays the chatbox, traversal to other pages and logging out.
 */
public class HomeController {

    /**
     * The ListView of friends in the Home page.
     */
    @FXML
    private ListView<Chat> friendsList;
    /**
     * The ListView of groups in the Home page.
     */
    @FXML
    private ListView<Chat> groupsList;
    /**
     * The Button used for creating a group.
     */
    @FXML
    private Button createGroupButton;
    /**
     * The VBox displaying the join group menu.
     */
    @FXML
    private VBox joinGroupVBox;
    /**
     * The VBox displaying the group creation menu.
     */
    @FXML
    private VBox createGroupVBox;
    /**
     * The chatBox area of the screen.
     */
    @FXML
    private BorderPane chatBox;
    /**
     * The error label for the group creation page.
     */
    @FXML
    private Label errorLabelCreate;
    /**
     * The error label for the join group page.
     */
    @FXML
    private Label errorLabelJoin;
    /**
     * The textField for the group creation menu group name.
     */
    @FXML
    private TextField createGroupText;
    /**
     * The textField for the group join menu group name.
     */
    @FXML
    private TextField joinGroupText;
    /**
     * The TextArea for sending a message.
     */
    @FXML
    private TextArea messageTextArea;
    /**
     * The Scrollpane for the chat box.
     */
    @FXML
    private ScrollPane chatScrollPane;
    /**
     * The TextFlow containing text nessages.
     */
    @FXML
    private TextFlow chatTextFlow;

    /**
     * The List containing friends.
     */
    private final ObservableList<Chat> friends = FXCollections.observableArrayList();
    /**
     * The List containing groups.
     */
    private final ObservableList<Chat> groups = FXCollections.observableArrayList();

    /**
     * The Object of the currently selected Chat.
     */
    private Chat selectedChat = null;

    /**
     * The message update scheduler.
     */
    private ScheduledExecutorService messagePollingService;
    /**
     * The task to schedule.
     */
    private ScheduledFuture<?> pollingTask;
    /**
     * The bool to track if message updating is active.
     */
    private volatile boolean isPollingActive = false;


    /**
     * Is run on opening the friend's page.
     */
    @FXML
    public void initialize() throws IOException {
        if (friendsList != null) {
            friendsList.setItems(friends);
            friendsList.setCellFactory(listView -> new ChatCell());

            friendsList.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> {
                    selectedChat = newValue;
                    if (selectedChat != null) {
                        groupsList.getSelectionModel().clearSelection();
                        System.out.println("Selected friend chat: " + selectedChat.getChatName() + 
                                         " (ID: " + selectedChat.getChatId() + ")");
                        onChatSelectionChanged(selectedChat);
                    }
                }
            );
            friendsList.toFront();
        }

        chatTextFlow.heightProperty().addListener((obs, oldHeight, newHeight) -> chatScrollPane.setVvalue(1.0));

        if (groupsList != null) {
            groupsList.setCellFactory(listView -> new ChatCell());

            groupsList.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> {
                    selectedChat = newValue;
                    if (selectedChat != null) {
                        friendsList.getSelectionModel().clearSelection();
                        System.out.println("Selected group chat: " + selectedChat.getChatName() + 
                                         " (ID: " + selectedChat.getChatId() + ")");
                        onChatSelectionChanged(selectedChat);
                    }
                }
            );
        }

        joinGroupVBox.setVisible(false);
        joinGroupVBox.setDisable(true);
        createGroupVBox.setVisible(false);
        createGroupVBox.setDisable(true);
        chatBox.setVisible(true);
        chatBox.setDisable(false);

        messagePollingService = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MessagePolling");
            t.setDaemon(true);
            return t;
        });

        try {
            Client.getHandler().send(new GroupRequest(DbManager.getAccountID()));
            GroupRequest response = (GroupRequest) Client.getHandler().tryReceive();
            Client.getHandler().send(new FriendRequest(DbManager.getAccountID()));
            FriendRequest response2 = (FriendRequest) Client.getHandler().tryReceive();
            
            if (response != null && response.getGroups() != null) {
                groups.clear();
                List<Chat> chatList = response.getGroups();
                groups.addAll(chatList);
                System.out.println("Loaded " + groups.size() + " groups");
            } else {
                System.out.println("No groups received");
            }

            if (response2 != null && response2.getFriends() != null) {
                friends.clear();
                friends.addAll(response2.getFriends());
                System.out.println("Loaded " + friends.size() + " friends");
            } else {
                System.out.println("No friends received");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Changes which chat is selected when the user clicks a chat.
     * @param newValue selected chat.
     */
    private void onChatSelectionChanged(Chat newValue) {
        if (newValue != null) {
            selectedChat = newValue;
            Platform.runLater(() -> chatTextFlow.getChildren().clear());
            fetchAndDisplayMessages(selectedChat.getChatId());

            startMessagePolling();
        } else {
            stopMessagePolling();
        }
    }

    /**
     * Checks for new messages.
     */
    private synchronized void startMessagePolling() {
        stopMessagePolling();
        
        if (selectedChat == null) return;
        
        isPollingActive = true;
        final int currentChatId = selectedChat.getChatId();
        
        pollingTask = messagePollingService.scheduleAtFixedRate(() -> {
            if (isPollingActive && selectedChat != null && selectedChat.getChatId() == currentChatId) {
                try {
                    synchronized (Client.getHandler()) {
                        Client.getHandler().send(new GetMessagesRequest(currentChatId));
                        Object response = Client.getHandler().tryReceive();
                        
                        if (response instanceof GetMessagesRequest) {
                            GetMessagesRequest messageResponse = (GetMessagesRequest) response;
                            if (messageResponse.getMessages() != null) {
                                List<Message> messages = messageResponse.getMessages();

                                if (selectedChat != null && selectedChat.getChatId() == currentChatId) {
                                    Platform.runLater(() -> {
                                        if (selectedChat != null && selectedChat.getChatId() == currentChatId) {
                                            updateChatView(messages);
                                        }
                                    });
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    /**
     * Stops checking for new messages to not overload the server.
     */
    private synchronized void stopMessagePolling() {
        isPollingActive = false;
        if (pollingTask != null && !pollingTask.isCancelled()) {
            pollingTask.cancel(true);
            pollingTask = null;
        }
    }

    /**
     * Fetches messages from the database and displays them in the chat window.
     * @param chatId id of the selected chat.
     */
    private void fetchAndDisplayMessages(int chatId) {
        messagePollingService.submit(() -> {
            try {
                synchronized (Client.getHandler()) {
                    Client.getHandler().send(new GetMessagesRequest(chatId));
                    Object response = Client.getHandler().tryReceive();

                    if (response instanceof GetMessagesRequest) {
                        GetMessagesRequest messageResponse = (GetMessagesRequest) response;
                        List<Message> messages = messageResponse.getMessages();

                        Platform.runLater(() -> {
                            if (selectedChat != null && selectedChat.getChatId() == chatId) {
                                updateChatView(messages);
                            }
                        });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Updates the chat window.
     * @param messages messages that will be displayed.
     */
    private void updateChatView(List<Message> messages) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> updateChatView(messages));
            return;
        }
        
        chatTextFlow.getChildren().clear();
        if (messages != null) {
            for (Message msg : messages) {
                Text messageText = new Text(msg.getUsername() + ": " + msg.getMessage() + "\n");
                chatTextFlow.getChildren().add(messageText);
            }
        }
    }

    /**
     * Opens profile editing menu.
     */
    @FXML
    public void OnEditMenuClick(ActionEvent actionEvent) {
        cleanup();
        HomeApplication.switchScene(SceneEnum.PROFILE);
    }

    /**
     * Logs out the current user of the application.
     */
    @FXML
    public void OnLogOutMenuClick(ActionEvent actionEvent) {
        cleanup();
        HomeApplication.switchScene(SceneEnum.LOGIN);
    }

    /**
     * Opens friends menu.
     */
    @FXML
    public void OnFriendsButtonClick(ActionEvent actionEvent) {
        cleanup();
        HomeApplication.switchScene(SceneEnum.FRIENDS);
    }

    /**
     * Changes display to show a user's friends.
     */
    @FXML
    public void OnFriendsMenuSelected(ActionEvent actionEvent) {
        if (friendsList != null) {
            groupsList.getSelectionModel().clearSelection();
            selectedChat = null;
            stopMessagePolling();
            
            friendsList.toFront();
            groupsList.toBack();
            groupsList.setDisable(true);
            friendsList.setDisable(false);
            friendsList.setItems(friends);
            createGroupButton.setVisible(false);
            createGroupButton.setDisable(true);

            Platform.runLater(() -> chatTextFlow.getChildren().clear());
        }
    }

    /**
     * Changes display to show a user's groups.
     */
    @FXML
    public void OnGroupsMenuSelected(ActionEvent actionEvent) {
        if (groupsList != null) {
            friendsList.getSelectionModel().clearSelection();
            selectedChat = null;
            stopMessagePolling();
            
            groupsList.toFront();
            friendsList.toBack();
            groupsList.setDisable(false);
            friendsList.setDisable(true);
            groupsList.setItems(groups);
            createGroupButton.setVisible(true);
            createGroupButton.setDisable(false);

            Platform.runLater(() -> chatTextFlow.getChildren().clear());
        }
    }

    /**
     * Tries to create a group with a given name.
     */
    public void onCreateGroupClick(ActionEvent actionEvent) {
        String groupName = createGroupText.getText();
        try {
            if (groupName.isEmpty()) {
                showError("Group name cannot be empty", errorLabelCreate);
                return;
            }

            if (groupName.length() < 3) {
                showError("Group name must be at least 3 characters long", errorLabelCreate);
                return;
            }

            if (groupName.length() > 20) {
                showError("Group name cannot exceed 20 characters", errorLabelCreate);
                return;
            }

            CreateGroupRequest createRequest = new CreateGroupRequest(groupName, 1);

            Client.getHandler().send(createRequest);

            CreateGroupRequest response = (CreateGroupRequest) Client.getHandler().tryReceive();
            
            if (response != null) {
                showSuccess("Group '" + groupName + "' created successfully!", errorLabelCreate);

                Chat newChat = new Chat(0, groupName);
                if (!groups.contains(newChat)) {
                    groups.add(newChat);
                }

                createGroupText.clear();

                groupsList.setItems(groups);
                
            } else {
                showError("Error", errorLabelCreate);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            showError("Error.", errorLabelCreate);
        }
    }

    /**
     * Tries to join a group with a given name.
     */
    public void onJoinGroupClick(ActionEvent actionEvent) {
        String groupName = joinGroupText.getText();
        try {
            if (groupName.isEmpty()) {
                showError("Group name cannot be empty", errorLabelJoin);
                return;
            }

            if (groupName.length() < 3) {
                showError("Group name must be at least 3 characters long", errorLabelJoin);
                return;
            }

            if (groupName.length() > 20) {
                showError("Group name cannot exceed 20 characters", errorLabelJoin);
                return;
            }

            JoinGroupRequest joinRequest = new JoinGroupRequest(HomeApplication.currentUser.getUserId(), groupName, "member");

            Client.getHandler().send(joinRequest);

            JoinGroupRequest response = (JoinGroupRequest) Client.getHandler().tryReceive();

            if (response != null) {
                showSuccess("Group '" + groupName + "' joined successfully!", errorLabelJoin);

                Chat newChat = new Chat(0, groupName);
                if (!groups.contains(newChat)) {
                    groups.add(newChat);
                }

                joinGroupText.clear();

                groupsList.setItems(groups);

            } else {
                showError("Error", errorLabelJoin);
            }

        } catch (Exception e) {
            e.printStackTrace();
            showError("Error.", errorLabelJoin);
        }
    }

    /**
     * Displays create a group menu.
     */
    public void OnCreateGroupMenuClick(ActionEvent actionEvent) {
        stopMessagePolling();
        joinGroupVBox.setVisible(false);
        joinGroupVBox.setDisable(true);
        createGroupVBox.setVisible(true);
        createGroupVBox.setDisable(false);
        chatBox.setVisible(false);
        chatBox.setDisable(true);
    }

    /**
     * Displays join a group menu.
     */
    public void OnJoinGroupMenuClick(ActionEvent actionEvent) {
        stopMessagePolling();
        joinGroupVBox.setVisible(true);
        joinGroupVBox.setDisable(false);
        createGroupVBox.setVisible(false);
        createGroupVBox.setDisable(true);
        chatBox.setVisible(false);
        chatBox.setDisable(true);
    }

    /**
     * showError displays an error.
     * @param s Text that will be displayed.
     * @param errorLabel Label to be changed.
     */
    private void showError(String s, Label errorLabel) {
        if (errorLabel != null) {
            errorLabel.setText(s);
            errorLabel.setVisible(true);
            errorLabel.setTextFill(javafx.scene.paint.Color.RED);
        }
    }

    /**
     * showError displays a success.
     * @param s Text that will be displayed.
     * @param errorLabel Label to be changed.
     */
    private void showSuccess(String s, Label errorLabel) {
        if (errorLabel != null) {
            errorLabel.setText(s);
            errorLabel.setVisible(true);
            errorLabel.setTextFill(javafx.scene.paint.Color.GREEN);
        }
    }

    /**
     * Sends a message to the selected chat.
     */
    public void OnSendButtonClick(ActionEvent actionEvent) throws IOException {
        String message = messageTextArea.getText().trim();
        
        if (selectedChat == null) {
            return;
        }
        
        if (message.isEmpty()) {
            return;
        }

        messagePollingService.submit(() -> {
            try {
                synchronized (Client.getHandler()) {
                    SendMessageRequest sendMessageRequest = new SendMessageRequest(
                            DbManager.getAccountID(), selectedChat.getChatId(), message);

                    Client.getHandler().send(sendMessageRequest);
                    Object response = Client.getHandler().tryReceive();

                    if (response instanceof SendMessageRequest) {
                        System.out.println("Message sent to chat: " + selectedChat.getChatName());
                        Platform.runLater(() -> {
                            if (selectedChat != null) {
                                fetchAndDisplayMessages(selectedChat.getChatId());
                            }
                        });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        messageTextArea.clear();
    }

    /**
     * Clean up resources when the controller is destroyed or scene is changed
     */
    public void cleanup() {
        stopMessagePolling();
        if (messagePollingService != null && !messagePollingService.isShutdown()) {
            messagePollingService.shutdown();
            try {
                if (!messagePollingService.awaitTermination(2, TimeUnit.SECONDS)) {
                    messagePollingService.shutdownNow();
                }
            } catch (InterruptedException e) {
                messagePollingService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}