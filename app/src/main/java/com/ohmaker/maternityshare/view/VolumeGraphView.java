package com.ohmaker.maternityshare.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.ohmaker.maternityshare.R;

/*
 * 音量グラフ
 */
public class VolumeGraphView extends View {
    private short mData[];
    private int mThreshold;

    public VolumeGraphView(Context context) {
        super(context);
    }

    public VolumeGraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public VolumeGraphView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void updateData(short data[]){
        mData = data;

        this.invalidate();
    }

    public void setThreshold(int threshold){
        mThreshold = threshold;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = this.getWidth();
        int height = this.getHeight();

        Paint paint = new Paint();
        paint.setColor(getResources().getColor(R.color.mother_primary));
        paint.setStrokeWidth(1);

        if (mData != null && mData.length > 0) {
            int div = mData.length / width / 2;

            for (int i = 1; i < width; i++) {
                float y = (float) mData[i * div] / 100;
                float prev = (float) mData[(i - 1) * div] / 100;

                canvas.drawLine(i-1, (height / 2) - prev, i, (height / 2) - y, paint);
            }
        }
    }
}
