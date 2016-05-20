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
 * FFT閾値設定ダイアログ
 */
public class FFTDialog extends DialogFragment {
    public interface FFTDialogInterface{
        void onThresholdChanged(int threshold, int min, int max);
    }

    private EditText mEditText;
    private EditText mMinText;
    private EditText mMaxText;

    public static FFTDialog newInstance(int threshold, int min, int max) {
        FFTDialog dialog = new FFTDialog();

        Bundle args = new Bundle();
        args.putInt("THRESHOLD", threshold);
        args.putInt("MIN", min);
        args.putInt("MAX", max);

        dialog.setArguments(args);

        return dialog;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        int threshold= getArguments().getInt("THRESHOLD");
        int min= getArguments().getInt("MIN");
        int max= getArguments().getInt("MAX");

        LayoutInflater inflater = (LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.fftdialog_layout,null);
        mEditText = (EditText)view.findViewById(R.id.edit_threshold);
        mEditText.setText(String.valueOf(threshold));

        mMinText = (EditText)view.findViewById(R.id.edit_min);
        mMinText.setText(String.valueOf(min));

        mMaxText = (EditText)view.findViewById(R.id.edit_max);
        mMaxText.setText(String.valueOf(max));

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setView(view);
        builder.setTitle("FFT閾値、f min, f max");
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                int threshold = Integer.parseInt(mEditText.getText().toString());
                int min = Integer.parseInt(mMinText.getText().toString());
                int max = Integer.parseInt(mMaxText.getText().toString());
                ((FFTDialogInterface) getActivity()).onThresholdChanged(threshold, min,max);
            }
        });

        return builder.create();
    }
}
