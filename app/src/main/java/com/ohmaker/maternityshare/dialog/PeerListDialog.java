package com.ohmaker.maternityshare.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;

/*
 * SkyWay接続設定ダイアログ
 */
public class PeerListDialog extends DialogFragment {
    public interface PeerListDialogInterface{
        void onPeerSelected(String peer);
    }

    public static PeerListDialog newInstance(String[] peers) {
        PeerListDialog dialog = new PeerListDialog();

        Bundle args = new Bundle();
        args.putStringArray("PEERS", peers);

        dialog.setArguments(args);

        return dialog;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final String[] peers = getArguments().getStringArray("PEERS");

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setItems(peers, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ((PeerListDialogInterface)getActivity()).onPeerSelected(peers[which]);
            }
        });

        return builder.create();
    }
}
