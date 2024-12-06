package com.dji.sdk.sample.internal.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class ObjectDetectionOverlayView extends View {

    private RectF boundingBox;
    private String label;
    private Paint boxPaint;
    private Paint labelPaint;
    private boolean showDetection = false;

    public ObjectDetectionOverlayView(Context context) {
        super(context);
        init();
    }

    public ObjectDetectionOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        boxPaint = new Paint();
        boxPaint.setColor(getResources().getColor(android.R.color.holo_blue_light));
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(6);

        labelPaint = new Paint();
        labelPaint.setColor(getResources().getColor(android.R.color.white));
        labelPaint.setTextSize(48);
        labelPaint.setStyle(Paint.Style.FILL);
    }

    public void setBoundingBox(float left, float top, float right, float bottom, String label) {
        this.boundingBox = new RectF(left, top, right, bottom);
        this.label = label;
        this.showDetection = true;
        invalidate(); // Redraw the view
    }

    public void clearBoundingBox() {
        this.boundingBox = null;
        this.label = null;
        this.showDetection = false;
        invalidate(); // Redraw the view
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (showDetection && boundingBox != null) {
            // Draw bounding box
            canvas.drawRect(boundingBox, boxPaint);

            // Draw label above the bounding box
            float labelX = boundingBox.left;
            float labelY = boundingBox.top - 10; // Slightly above the box
            canvas.drawText(label, labelX, labelY, labelPaint);
        }
    }
}
