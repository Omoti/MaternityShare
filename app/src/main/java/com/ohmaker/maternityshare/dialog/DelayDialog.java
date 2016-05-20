package com.ohmaker.maternityshare.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import com.ohmaker.maternityshare.R;

/*
 * 遅延時間設定ダイアログ
 */
public class DelayDialog extends DialogFragment {
    public interface DelayDialogInterface{
        void onDelayChanged(int delay);
    }

    private EditText mEditText;

    public static DelayDialog newInstance(int threshold) {
        DelayDialog dialog = new DelayDialog();

        Bundle args = new Bundle();
        args.putInt("DELAY", threshold);

        dialog.setArguments(args);

        return dialog;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        int delay= getArguments().getInt("DELAY");

        LayoutInflater inflater = (LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.editdialog_layout,null);
        mEditText = (EditText)view.findViewById(R.id.edit_threshold);
        mEditText.setText(String.valueOf(delay));

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setView(view);
        builder.setTitle("Delay");
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                int delay = Integer.parseInt(mEditText.getText().toString());
                ((DelayDialogInterface) getActivity()).onDelayChanged(delay);
            }
        });

        return builder.create();
    }
}
