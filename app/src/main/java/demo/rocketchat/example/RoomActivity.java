package demo.rocketchat.example;

import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.MenuItem;
import android.view.View;

import com.rocketchat.common.RocketChatException;
import com.rocketchat.common.listener.SimpleListCallback;
import com.rocketchat.core.RocketChatClient;
import com.rocketchat.core.callback.LoginCallback;
import com.rocketchat.core.model.Subscription;
import com.rocketchat.core.model.Token;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;

import java.util.List;

import demo.rocketchat.example.activity.MyAdapterActivity;
import demo.rocketchat.example.adapter.RoomAdapter;
import demo.rocketchat.example.application.RocketChatApplication;
import demo.rocketchat.example.utils.AppUtils;

@EActivity(R.layout.activity_room)
public class RoomActivity extends MyAdapterActivity {

    RocketChatClient api;
    private RecyclerView.Adapter adapter;
    private RecyclerView.LayoutManager layoutManager;

    @ViewById(R.id.my_recycler_view)
    RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getSupportActionBar().setTitle("Chat Rooms");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        api = ((RocketChatApplication) getApplicationContext()).getRocketChatAPI();
        api.getWebsocketImpl().getConnectivityManager().register(this);
        api.subscribeActiveUsers(null);
        api.subscribeUserData(null);
        api.getSubscriptions(new SimpleListCallback<Subscription>() {
            @Override
            public void onSuccess(List<Subscription> list) {
                RoomActivity.this.onGetSubscriptions(list);
            }

            @Override
            public void onError(RocketChatException error) {

            }
        });
        super.onCreate(savedInstanceState);
    }

    @UiThread
    public void onGetSubscriptions(List<Subscription> list) {
        adapter = new RoomAdapter(list, RoomActivity.this);
        api.getChatRoomFactory().createChatRooms(list);
        recyclerView.setAdapter(adapter);
    }

    @AfterViews
    void afterViewsSet() {
        recyclerView.setHasFixedSize(true);
        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int i = item.getItemId();
        if (i == android.R.id.home) {
            onBackPressed();
        }
        return true;
    }


    @UiThread
    void showConnectedSnackbar() {
        Snackbar
                .make(findViewById(R.id.activity_room), R.string.connected, Snackbar.LENGTH_LONG)
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
        AppUtils.getSnackbar(findViewById(R.id.activity_room), R.string.disconnected_from_server)
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
        AppUtils.getSnackbar(findViewById(R.id.activity_room), R.string.connection_error)
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
}
