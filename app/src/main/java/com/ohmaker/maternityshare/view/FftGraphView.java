package com.ohmaker.maternityshare.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.ohmaker.maternityshare.R;

/*
 * FFTグラフ
 */
public class FftGraphView extends View {
    private double mData[];
    private int mMin = 0;
    private int mMax = 0;
    private int mThreshold = 0;

    public FftGraphView(Context context) {
        super(context);
    }

    public FftGraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FftGraphView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void updateData(double data[]){
        mData = data;

        this.invalidate();
    }

    public void setThreshold(int min, int max, int threshold){
        mMin = min;
        mMax = max;
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

        Paint paint_th = new Paint();
        paint_th.setColor(getResources().getColor(R.color.father_primary));
        paint_th.setStrokeWidth(1);

        if (mData == null){
            return;
        }

        int div = mData.length / width / 3;

        if (mData.length > 0) {
            for (int i = 0; i < width; i++) {
                float y = (float) mData[i * div] / 1000;

                canvas.drawLine(i, height, i, height  - y, paint);
            }
        }

        //Threshold
        canvas.drawLine(mMin / div, height - mThreshold / 1000, mMin / div, height, paint_th);
        canvas.drawLine(mMax / div, height - mThreshold / 1000, mMax / div, height, paint_th);
        canvas.drawLine(mMin / div, height - mThreshold / 1000, mMax / div, height - mThreshold / 1000, paint_th);
    }
}
