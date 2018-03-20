package demo.rocketchat.example;

import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.Snackbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.rocketchat.common.RocketChatException;
import com.rocketchat.common.data.lightdb.collection.Collection;
import com.rocketchat.common.data.lightdb.document.UserDocument;
import com.rocketchat.core.ChatRoom;
import com.rocketchat.core.RocketChatClient;
import com.rocketchat.core.callback.HistoryCallback;
import com.rocketchat.core.callback.LoginCallback;
import com.rocketchat.core.callback.MessageCallback;
import com.rocketchat.core.model.Token;
import com.stfalcon.chatkit.messages.MessageInput;
import com.stfalcon.chatkit.messages.MessagesList;
import com.stfalcon.chatkit.messages.MessagesListAdapter;
import com.stfalcon.chatkit.utils.DateFormatter;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import demo.rocketchat.example.activity.MyAdapterActivity;
import demo.rocketchat.example.application.RocketChatApplication;
import demo.rocketchat.example.model.Message;
import demo.rocketchat.example.model.User;
import demo.rocketchat.example.utils.AppUtils;

/**
 * Created by aniket on 05/09/17.
 */

@EActivity(R.layout.activity_chat)
public class ChatActivity extends MyAdapterActivity implements
        MessagesListAdapter.SelectionListener,
        MessagesListAdapter.OnLoadMoreListener,
        MessageInput.InputListener,
        DateFormatter.Formatter,
        MessageInput.AttachmentsListener {

    @ViewById(R.id.messagesList)
    MessagesList messagesList;

    @ViewById(R.id.input)
    MessageInput input;

    RocketChatClient api;
    ChatRoom chatRoom;
    String userId;
    /**
     * This will restrict total messages to 1000
     */
    private static final int TOTAL_MESSAGES_COUNT = 1000;

    /**
     * Variables for storing temporary references
     */
    private Menu menu;
    private int selectionCount;

    /**
     * MessageAdapter for loading messages, has 2 callbacks (on selection and onloadmore)
     */
    protected MessagesListAdapter<Message> messagesAdapter;

    Handler Typinghandler = new Handler();
    Boolean typing = false;

    private Date lastTimestamp;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        api = ((RocketChatApplication) getApplicationContext()).getRocketChatAPI();

        String roomId = getIntent().getStringExtra("roomId");
        userId = roomId.replace(api.getWebsocketImpl().getMyUserId(), "");
        System.out.println("room id is " + roomId);
        api.getWebsocketImpl().getConnectivityManager().register(this);
        chatRoom = api.getChatRoomFactory().getChatRoomById(roomId);
        getSupportActionBar().setTitle(chatRoom.getRoomData().name());
        if (getCurrentUser() !=null) {
            updateUserStatus(getCurrentUser().status().toString());
        }

        api.getDbManager().getUserCollection().register(userId, new Collection.Observer<UserDocument>() {
            @Override
            public void onUpdate(Collection.Type type, UserDocument document) {
                switch (type) {
                    case ADDED:
                        updateUserStatus(document.status().toString());
                        break;
                    case CHANGED:
                        updateUserStatus(document.status().toString());
                        break;
                    case REMOVED:
                        updateUserStatus("UNAVAILABLE");
                        break;
                }
            }
        });

        chatRoom.subscribeRoomMessageEvent(null, this);
        chatRoom.subscribeRoomTypingEvent(null, this);
        chatRoom.getChatHistory(50, lastTimestamp, null, new HistoryCallback() {
            @Override
            public void onLoadHistory(List<com.rocketchat.core.model.Message> list, int unreadNotLoaded) {
                ChatActivity.this.onLoadHistory(list, unreadNotLoaded);
            }

            @Override
            public void onError(RocketChatException error) {

            }
        });


        super.onCreate(savedInstanceState);
    }

    @UiThread
    void updateUserStatus(String status) {
        getSupportActionBar().setSubtitle(status.substring(0,1)+status.substring(1).toLowerCase());
    }

    @AfterViews
    void afterViewsSet() {
        input.setInputListener(this);
        input.setAttachmentsListener(this);
        input.getInputEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (!typing) {
                    typing = true;
                    chatRoom.sendIsTyping(true);
                }
                Typinghandler.removeCallbacks(onTypingTimeout);
                Typinghandler.postDelayed(onTypingTimeout, 600);
            }

            Runnable onTypingTimeout = new Runnable() {
                @Override
                public void run() {
                    typing = false;
                    chatRoom.sendIsTyping(false);
                }
            };

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

        initAdapter();
    }

    private void initAdapter() {
        messagesAdapter = new MessagesListAdapter<>(api.getWebsocketImpl().getMyUserId(), null);
        messagesAdapter.enableSelectionMode(this);
        messagesAdapter.setLoadMoreListener(this);
        messagesAdapter.setDateHeadersFormatter(this);
        messagesList.setAdapter(messagesAdapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.menu = menu;
        getMenuInflater().inflate(R.menu.chat_actions_menu, menu);
        onSelectionChanged(0);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int i = item.getItemId();
        if (i == R.id.action_copy) {
            messagesAdapter.copySelectedMessagesText(this, getMessageStringFormatter(), true);
            AppUtils.showToast(this, R.string.copied_message, true);
        } else if (i == android.R.id.home) {
            onBackPressed();
        }
        return true;
    }

    private MessagesListAdapter.Formatter<Message> getMessageStringFormatter() {
        return new MessagesListAdapter.Formatter<Message>() {
            @Override
            public String format(Message message) {
                String createdAt = new SimpleDateFormat("MMM d, EEE 'at' h:mm a", Locale.getDefault())
                        .format(message.getCreatedAt());

                String text = message.getText();
                if (text == null) text = "[attachment]";

                return String.format(Locale.getDefault(), "%s: %s (%s)",
                        message.getUser().getName(), text, createdAt);
            }
        };
    }



    @UiThread
    void updateMessage(ArrayList<Message> messages) {
        messagesAdapter.addToEnd(messages, false);
    }

    @UiThread
    @Override
    public void onMessage(String roomId, com.rocketchat.core.model.Message message) {
        messagesAdapter.addToStart(new Message(message.id(), new User(message.sender().id(), message.sender().username(), null, true), message.message(), new Date(message.timestamp())), true);
    }

    @UiThread
    @Override
    public void onTyping(String roomId, String user, Boolean istyping) {
        if (istyping) {
            getSupportActionBar().setSubtitle(user + " is typing...");
        } else {
            if (getCurrentUser() != null) {
                updateUserStatus(getCurrentUser().status().toString());
            } else {
                getSupportActionBar().setSubtitle("");
            }
        }
    }


    @UiThread
    void showConnectedSnackbar() {
        Snackbar
                .make(findViewById(R.id.chat_activity), R.string.connected, Snackbar.LENGTH_LONG)
                .show();
    }

    @Override
    public void onConnect(String sessionID) {
        String token = ((RocketChatApplication)getApplicationContext()).getToken();
        api.loginUsingToken(token, new LoginCallback() {
            @Override
            public void onLoginSuccess(Token token) {
                api.subscribeActiveUsers(null);
                api.subscribeUserData(null);
                chatRoom.subscribeRoomMessageEvent(null, ChatActivity.this);
                chatRoom.subscribeRoomTypingEvent(null, ChatActivity.this);
            }

            @Override
            public void onError(RocketChatException error) {

            }
        });
        showConnectedSnackbar();
    }

    @UiThread
    @Override
    public void onDisconnect(boolean closedByServer) {
        chatRoom.unSubscribeAllEvents();
        AppUtils.getSnackbar(findViewById(R.id.chat_activity), R.string.disconnected_from_server)
                .setAction("RETRY", new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        api.getWebsocketImpl().getSocket().reconnect();
                    }
                })
                .show();

    }


    @UiThread
    @Override
    public void onConnectError(Throwable websocketException) {
        chatRoom.unSubscribeAllEvents();
        AppUtils.getSnackbar(findViewById(R.id.chat_activity), R.string.connection_error)
                .setAction("RETRY", new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        api.getWebsocketImpl().getSocket().reconnect();

                    }
                })
                .show();
    }



    @Override
    protected void onDestroy() {
        api.getWebsocketImpl().getConnectivityManager().unRegister(this);
        super.onDestroy();
    }

    @Override
    public void onAddAttachments() {

    }

    @Override
    public boolean onSubmit(CharSequence input) {
        chatRoom.sendMessage(input.toString(), new MessageCallback.MessageAckCallback() {
            @Override
            public void onMessageAck(com.rocketchat.core.model.Message message) {

            }

            @Override
            public void onError(RocketChatException error) {

            }
        });
        return true;
    }

    @UiThread
    public void onLoadHistory(List<com.rocketchat.core.model.Message> list, int unreadNotLoaded) {
        if (list.size() > 0) {
            lastTimestamp = new Date(list.get(list.size() - 1).timestamp());
            final ArrayList<Message> messages = new ArrayList<>();
            for (com.rocketchat.core.model.Message message : list) {
                switch (message.getMsgType()) {
                    case TEXT:
                        messages.add(new Message(message.id(), new User(message.sender().id(), message.sender().username(), null, true), message.message(), new Date(message.timestamp())));
                        break;
                }
            }
            updateMessage(messages);
        }
    }

    @Override
    public void onLoadMore(int page, int totalItemsCount) {
        if (totalItemsCount < TOTAL_MESSAGES_COUNT) {
            chatRoom.getChatHistory(50, lastTimestamp, null, new HistoryCallback() {
                @Override
                public void onLoadHistory(List<com.rocketchat.core.model.Message> list, int unreadNotLoaded) {
                    ChatActivity.this.onLoadHistory(list, unreadNotLoaded);
                }

                @Override
                public void onError(RocketChatException error) {

                }
            });
        }
    }

    @Override
    public void onSelectionChanged(int count) {
        this.selectionCount = count;
        menu.findItem(R.id.action_copy).setVisible(count > 0);
    }

    @Override
    public String format(Date date) {
        if (DateFormatter.isToday(date)) {
            return getString(R.string.date_header_today);
        } else if (DateFormatter.isYesterday(date)) {
            return getString(R.string.date_header_yesterday);
        } else {
            return DateFormatter.format(date, DateFormatter.Template.STRING_DAY_MONTH_YEAR);
        }
    }

    UserDocument getCurrentUser() {
        return api.getDbManager().getUserCollection().get(userId);
    }

    @Override
    public void onBackPressed() {
        if (selectionCount == 0) {
            chatRoom.unSubscribeAllEvents();
            super.onBackPressed();
        } else {
            messagesAdapter.unselectAllItems();
        }
    }
}
