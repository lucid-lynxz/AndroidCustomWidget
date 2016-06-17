package org.lynxz.customstepviewlibrary;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;

/**
 * Created by zxz on 2016/6/16.
 * 步骤条
 */
public class StepView extends View {

    private boolean mShowProgressNum;
    private int mFinishColor;
    private int mUnfinishColor;
    private int mStepCount;
    private Paint mCirclePaint;
    private Paint mLinePaint;
    private float mCircleRadius;
    private static final String TAG = "StepView";
    private float EXPAND_MARK = 1.3f;
    private ArrayList<Float> mCirclePosList = new ArrayList<>();
    private static final int DEFAULT_STEP_COUNT = 3;
    private static final int MIN_STEP_COUNT = 2;
    private int mCurrentProgress;
    private Bitmap mStepDoneBitmap;
    private Rect mDoneBitmapSrcRect;

    public StepView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        TypedArray ta = context.getTheme().obtainStyledAttributes(attrs, R.styleable.StepView, 0, 0);
        mShowProgressNum = ta.getBoolean(R.styleable.StepView_show_progress_number, false);
        mFinishColor = ta.getColor(R.styleable.StepView_finish_color, Color.GREEN);
        mUnfinishColor = ta.getColor(R.styleable.StepView_finish_color, Color.GRAY);
        mStepCount = ta.getInt(R.styleable.StepView_step_count, DEFAULT_STEP_COUNT);
        mCircleRadius = ta.getDimension(R.styleable.StepView_circle_radius, 30);
        mCurrentProgress = ta.getInt(R.styleable.StepView_current_progress, 0);
        int doneBitmapId = ta.getResourceId(R.styleable.StepView_done_bitmap, -1);
        ta.recycle();
        if (mStepCount <= 1) {
            mStepCount = DEFAULT_STEP_COUNT;
        }

        if (mCurrentProgress < 0) {
            mCurrentProgress = 0;
        }

        if (doneBitmapId > 0) {
            mStepDoneBitmap = BitmapFactory.decodeResource(getResources(), doneBitmapId);
            mDoneBitmapSrcRect = new Rect(0, 0, mStepDoneBitmap.getWidth(), mStepDoneBitmap.getHeight());
        }

        initPaint();
    }

    public void setCurrentProgress(int progress) {
        if (progress >= mStepCount) {
            mCurrentProgress = mStepCount;
        } else if (progress < 0) {
            mCurrentProgress = 0;
        } else {
            mCurrentProgress = progress;
        }
        postInvalidate();
    }

    public int getCurrentProgress() {
        return mCurrentProgress;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        compute();
    }

    private void compute() {
        mCirclePosList.clear();
        int measuredWidth = getMeasuredWidth();
        float startX = mCircleRadius * EXPAND_MARK + mCirclePaint.getStrokeWidth() + getPaddingLeft();
        float contentWidth = measuredWidth - getPaddingLeft() - getPaddingRight() - mCircleRadius * (EXPAND_MARK - 1) * 2;
        float lineLength = (contentWidth - mCircleRadius * 2) / (mStepCount - 1);
        for (int i = 0; i < mStepCount; i++) {
            mCirclePosList.add(startX + i * lineLength);
        }
    }

    private void initPaint() {
        mCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCirclePaint.setStyle(Paint.Style.FILL);
        mCirclePaint.setColor(mUnfinishColor);

        mLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mLinePaint.setColor(mUnfinishColor);
        mLinePaint.setStrokeWidth(10);
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        // 高度比图形最大高度大一点
        int desiredHeight = (int) Math.ceil((mCircleRadius * EXPAND_MARK * 2) + mCirclePaint.getStrokeWidth());

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int width = widthMode == MeasureSpec.EXACTLY ? widthSize : getSuggestedMinimumWidth();
        int height = heightMode == MeasureSpec.EXACTLY ? heightSize : desiredHeight;
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float yPos = getMeasuredHeight() / 2;
        // 绘制连接线
        for (int i = 1; i < mCirclePosList.size(); i++) {
            if (i < mCurrentProgress) {
                mLinePaint.setColor(mFinishColor);
            } else {
                mLinePaint.setColor(mUnfinishColor);
            }
            canvas.drawLine(mCirclePosList.get(i - 1) + mCircleRadius, yPos, mCirclePosList.get(i) - mCircleRadius, yPos, mLinePaint);
        }

        // 绘制圆形或者替代图片
        for (int i = 0; i < mCirclePosList.size(); i++) {
            float xPos = mCirclePosList.get(i);
            if (i <= mCurrentProgress - 1) {
                mCirclePaint.setColor(mFinishColor);
                if (mStepDoneBitmap != null) {
                    Rect dstRect = new Rect((int) (xPos - mCircleRadius), (int) (yPos - mCircleRadius),
                            (int) (xPos + mCircleRadius), (int) (yPos + mCircleRadius));
                    canvas.drawBitmap(mStepDoneBitmap, mDoneBitmapSrcRect, dstRect, mCirclePaint);

                } else {
                    canvas.drawCircle(xPos, yPos, mCircleRadius, mCirclePaint);
                }
            } else {
                mCirclePaint.setColor(mUnfinishColor);
                canvas.drawCircle(xPos, yPos, mCircleRadius, mCirclePaint);
            }
        }
    }
}
