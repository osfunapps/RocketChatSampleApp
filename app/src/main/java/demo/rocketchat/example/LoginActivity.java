package demo.rocketchat.example;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.AppCompatButton;
import android.support.v7.widget.AppCompatEditText;
import android.view.View;

import com.rocketchat.common.RocketChatException;
import com.rocketchat.common.network.Socket;
import com.rocketchat.core.RocketChatClient;
import com.rocketchat.core.callback.LoginCallback;
import com.rocketchat.core.model.Token;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.UiThread;
import org.androidannotations.annotations.ViewById;

import demo.rocketchat.example.activity.MyAdapterActivity;
import demo.rocketchat.example.application.RocketChatApplication;
import demo.rocketchat.example.utils.AppUtils;


@EActivity(R.layout.activity_login)
public class LoginActivity extends MyAdapterActivity {

    @ViewById(R.id.username)
    AppCompatEditText username;

    @ViewById(R.id.password)
    AppCompatEditText password;

    @ViewById(R.id.login)
    AppCompatButton login;

    RocketChatClient api;

    private SharedPreferences.Editor editor;
    private SharedPreferences sharedPref;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().setTitle("RocketChat Login");
        api = ((RocketChatApplication) getApplicationContext()).getRocketChatAPI();
        api.setReconnectionStrategy(null);
        api.connect(this);
        sharedPref = getPreferences(MODE_PRIVATE);
        editor = sharedPref.edit();

    }

    @AfterViews
    void afterViewSetUp() {
        String Username = sharedPref.getString("username", null);
        String Password = sharedPref.getString("password", null);
        if (Username != null) {
            username.setText(Username);
            password.setText(Password);
        }
    }

    @UiThread
    void onLoginSuccess(Token token) {
        ((RocketChatApplication)getApplicationContext()).setToken(token.getAuthToken());
        AppUtils.showToast(LoginActivity.this, "Login successful", true);
        Intent intent = new Intent(LoginActivity.this, RoomActivity_.class);
        startActivity(intent);
        finish();
    }

    @UiThread
    void onLoginError(RocketChatException error) {
        AppUtils.showToast(LoginActivity.this, error.getMessage(), true);
    }

    @Click(R.id.login)
    void onLoginButtonClicked() {
        if (api.getWebsocketImpl().getSocket().getState() == Socket.State.CONNECTED) {
            String uname = username.getText().toString();
            String passwd = password.getText().toString();
            if (!(uname.equals("") || passwd.equals(""))) {
                api.login(uname, passwd, new LoginCallback() {
                    @Override
                    public void onLoginSuccess(Token token) {
                        editor.putString("username", username.getText().toString());
                        editor.putString("password", password.getText().toString());
                        editor.commit();
                        LoginActivity.this.onLoginSuccess(token);
                    }

                    @Override
                    public void onError(RocketChatException error) {
                        LoginActivity.this.onLoginError(error);
                    }
                });
            } else {
                AppUtils.showToast(LoginActivity.this, "Username or password shouldn't be null", true);
            }
        } else {
            AppUtils.showToast(LoginActivity.this, "Not connected to server", true);
        }
    }


    @UiThread
    @Override
    public void onConnect(String sessionID) {
        Snackbar
                .make(findViewById(R.id.activity_login), R.string.connected, Snackbar.LENGTH_LONG)
                .show();
    }

    @UiThread
    @Override
    public void onDisconnect(boolean closedByServer) {
        AppUtils.getSnackbar(findViewById(R.id.activity_login), R.string.disconnected_from_server)
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
        AppUtils.getSnackbar(findViewById(R.id.activity_login), R.string.connection_error)
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
