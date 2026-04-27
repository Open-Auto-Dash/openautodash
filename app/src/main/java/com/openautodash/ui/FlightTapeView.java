package com.openautodash.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.openautodash.R;

public class FlightTapeView extends View {

    private Paint backgroundPaint;
    private Paint linePaint;
    private Paint textPaint;
    private Paint windowPaint;
    private Paint windowBorderPaint;

    private float currentValue = 0f;
    private boolean isRightSide = false; // False = Speed (Left), True = Alt (Right)
    private float cornerRadius;

    // Animation
    private ValueAnimator animator;
    private final int ANIMATION_DURATION = 970;

    // Constants
    private final float STROKE_WIDTH = 5f;
    private final float HALF_STROKE = STROKE_WIDTH / 2f;

    // Configuration
    private int majorTickStep = 10;
    private int minorTickStep = 5;
    private float pixelsPerUnit = 15f;

    public FlightTapeView(Context context) {
        super(context);
        init();
    }

    public FlightTapeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        cornerRadius = 6 * density;

        // Background - White (Light Mode)
        backgroundPaint = new Paint();
        backgroundPaint.setColor(ContextCompat.getColor(getContext(), R.color.colorBackgroundLight));
        backgroundPaint.setStyle(Paint.Style.FILL);
        backgroundPaint.setAntiAlias(true);

        // Lines/Ticks - Dark Grey/Black
        linePaint = new Paint();
        linePaint.setColor(ContextCompat.getColor(getContext(), R.color.colorTextTitle));
        linePaint.setStrokeWidth(3f);
        linePaint.setAntiAlias(true);

        // Text - Dark Grey/Black
        textPaint = new Paint();
        textPaint.setColor(ContextCompat.getColor(getContext(), R.color.colorTextTitle));
        textPaint.setTextSize(40f);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setAntiAlias(true);
        textPaint.setTextAlign(Paint.Align.CENTER);

        // Center Window Background - Very Light Grey
        windowPaint = new Paint();
        windowPaint.setColor(ContextCompat.getColor(getContext(), R.color.colorBackgroundDefault));
        windowPaint.setStyle(Paint.Style.FILL);
        windowPaint.setAntiAlias(true);

        // Center Window Border - Dark Grey/Black
        windowBorderPaint = new Paint();
        windowBorderPaint.setColor(ContextCompat.getColor(getContext(), R.color.colorTextTitle));
        windowBorderPaint.setStyle(Paint.Style.STROKE);
        windowBorderPaint.setStrokeWidth(STROKE_WIDTH);
        windowBorderPaint.setAntiAlias(true);
    }

    public void setValue(float targetValue, boolean isRightSide) {
        this.isRightSide = isRightSide;

        // Update config based on type
        if (isRightSide) {
            majorTickStep = 100; // Altitude
            minorTickStep = 20;
            pixelsPerUnit = 1.5f;
        } else {
            majorTickStep = 10; // Speed
            minorTickStep = 10;
            pixelsPerUnit = 8f;
        }

        // Cancel existing animation if running
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }

        // Create new animation from CURRENT render value to NEW target
        animator = ValueAnimator.ofFloat(currentValue, targetValue);
        animator.setDuration(ANIMATION_DURATION);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            currentValue = (float) animation.getAnimatedValue();
            invalidate(); // Redraw with intermediate value
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // 1. Clip View to Rounded Corners
        Path viewPath = new Path();
        RectF viewRect = new RectF(0, 0, getWidth(), getHeight());
        viewPath.addRoundRect(viewRect, cornerRadius, cornerRadius, Path.Direction.CW);
        canvas.clipPath(viewPath);

        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        float cy = h / 2f;

        // 2. Draw Background
        canvas.drawRect(0, 0, w, h, backgroundPaint);

        // 3. Draw Moving Ticks
        float visibleRange = (h / 2f) / pixelsPerUnit;
        int minVal = (int) (currentValue - visibleRange - majorTickStep);
        int maxVal = (int) (currentValue + visibleRange + majorTickStep);

        minVal = (minVal / minorTickStep) * minorTickStep;

        for (int i = minVal; i <= maxVal; i += minorTickStep) {
            float y = cy - (i - currentValue) * pixelsPerUnit;

            boolean isMajor = (i % majorTickStep == 0);
            float tickLength = isMajor ? w * 0.4f : w * 0.2f;

            float startX, endX, textX;

            if (isRightSide) {
                startX = 0;
                endX = tickLength;
                textX = tickLength + 30;
                textPaint.setTextAlign(Paint.Align.LEFT);
            } else {
                startX = w - tickLength;
                endX = w;
                textX = w - tickLength - 30;
                textPaint.setTextAlign(Paint.Align.RIGHT);
            }

            canvas.drawLine(startX, y, endX, y, linePaint);

            if (isMajor) {
                float textOffset = (textPaint.descent() + textPaint.ascent()) / 2;
                canvas.drawText(String.valueOf(i), textX, y - textOffset, textPaint);
            }
        }

        // 4. Draw Center Reading Window
        float windowHeight = 80f;

        // RECT 1: Fill (Uses full width)
        RectF fillRect = new RectF(0, cy - windowHeight/2, w, cy + windowHeight/2);
        canvas.drawRoundRect(fillRect, cornerRadius, cornerRadius, windowPaint);

        // RECT 2: Border (Inset by half stroke width)
        RectF borderRect = new RectF(
                HALF_STROKE,
                cy - windowHeight/2 + HALF_STROKE,
                w - HALF_STROKE,
                cy + windowHeight/2 - HALF_STROKE
        );
        canvas.drawRoundRect(borderRect, cornerRadius, cornerRadius, windowBorderPaint);

        // 5. Draw Text
        String valText = String.valueOf((int)currentValue);
        float textOffset = (textPaint.descent() + textPaint.ascent()) / 2;
        textPaint.setTextAlign(Paint.Align.CENTER);

        float originalSize = textPaint.getTextSize();
        textPaint.setTextSize(originalSize * 1.2f);
        canvas.drawText(valText, w / 2f, cy - textOffset, textPaint);
        textPaint.setTextSize(originalSize);
    }
}