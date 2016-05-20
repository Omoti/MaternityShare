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
 * 音量閾値設定ダイアログ
 */
public class ThresholdDialog extends DialogFragment {
    public interface ThresholdDialogInterface{
        void onThresholdChanged(int threshold);
    }

    private EditText mEditText;

    public static ThresholdDialog newInstance(int threshold) {
        ThresholdDialog dialog = new ThresholdDialog();

        Bundle args = new Bundle();
        args.putInt("THRESHOLD", threshold);

        dialog.setArguments(args);

        return dialog;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        int threshold= getArguments().getInt("THRESHOLD");

        LayoutInflater inflater = (LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.editdialog_layout,null);
        mEditText = (EditText)view.findViewById(R.id.edit_threshold);
        mEditText.setText(String.valueOf(threshold));

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setView(view);
        builder.setTitle("閾値");
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                int threshold = Integer.parseInt(mEditText.getText().toString());
                ((ThresholdDialogInterface) getActivity()).onThresholdChanged(threshold);
            }
        });

        return builder.create();
    }
}
