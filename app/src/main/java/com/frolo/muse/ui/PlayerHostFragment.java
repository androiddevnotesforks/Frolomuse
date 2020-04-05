package com.frolo.muse.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.frolo.muse.engine.Player;
import com.frolo.muse.engine.service.PlayerService;


/**
 * Retained fragment that binds player service to the application context and holds the reference to the connected player.
 * Activity can use it to avoid binding the service to activity's context as it may cause problems with rotation etc.
 *
 * The fragment binds the service to the application context in <code>onCreate</code> and unbinds in <code>onDestroy</code>.
 * Unbinding is in <code>onDestroy</code> because this method is called only once since the fragment instance is retained (See {@link Fragment#setRetainInstance(boolean)}).
 *
 * As the target context the application context is chosen as it lives as long as the app is running and no leak may occur in this case.
 */
public final class PlayerHostFragment extends Fragment {

    public interface PlayerConnectionHandler {
        void onPlayerConnected(@NonNull Player player);

        void onPlayerDisconnected();
    }

    private boolean mPlayerServiceBound = false;
    private PlayerConnectionHandler mConnHandler;
    private Player mPlayer;

    private final ServiceConnection mConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mPlayerServiceBound = true;
            if (service != null && service instanceof PlayerService.PlayerBinder) {
                Player playerInstance = ((PlayerService.PlayerBinder) service).getService();
                mPlayer = playerInstance;
                if (mConnHandler != null) {
                    mConnHandler.onPlayerConnected(playerInstance);
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mPlayerServiceBound = false;
            mPlayer = null;
            if (mConnHandler != null) {
                mConnHandler.onPlayerDisconnected();
            }
        }
    };

    PlayerHostFragment() {
        // It is really important that the fragment instance is retained.
        // This will help avoid binding to the player service on every configuration changes etc.
        // because onCreate will be called only once (except the cases when the app was killed by the system).
        super.setRetainInstance(true);
    }

    @Override
    public void setRetainInstance(boolean retain) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof PlayerConnectionHandler) {
            mConnHandler = (PlayerConnectionHandler) context;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Do we need to check isBound value?
        final Player playerInstance = mPlayer;
        if (playerInstance != null) {
            // Let the host check it itself
            //callback?.onPlayerConnected(playerInstance)
        } else {
            Context hostContext = requireContext();
            Context appContext = hostContext.getApplicationContext();
            Intent intent = PlayerService.newIntent(hostContext);
            appContext.bindService(intent, mConn, Context.BIND_IMPORTANT | Context.BIND_AUTO_CREATE);
        }
    }

    /**
     * Fragment's host is going to be destroyed.
     * It's time to unbind the service.
     */
    @Override
    public void onDestroy() {
        Context appContext = requireContext().getApplicationContext();
        appContext.unbindService(mConn);
        super.onDestroy();
    }

    @Override
    public void onDetach() {
        mConnHandler = null;
        super.onDetach();
    }

    @Nullable
    Player getPlayer() {
        return mPlayer;
    }

}
