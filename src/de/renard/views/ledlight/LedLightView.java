package de.renard.views.ledlight;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Shader.TileMode;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Checkable;

import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.Animator.AnimatorListener;
import com.nineoldandroids.animation.ObjectAnimator;

public class LEDLightView extends View implements Checkable {
	private RectF mBounds = new RectF();
	private RectF mInnerBounds = new RectF();
	private RectF mGlowBounds = new RectF();
	private final Paint mRimPaint;
	private final Paint mRimCirclePaint;
	private final Paint mRimShadowPaint;
	private final Paint mGlowPaint;
	private final Paint mGlassPaint;
	private ObjectAnimator mAnimator;

	final int[] glassGradientColors = new int[5];
	final int[] glowGradientColors = new int[3];
	final float[] glassGradientPositions = new float[5];
	final float[] glowGradientPositions = new float[3];
	final float[] hsv = new float[3];
	float mLightIntensity = 0;
	private boolean mOn = false;
	int mLightColor;
	private boolean mRecalculateGlowShader;
	private Bitmap mDrawingCache = null;

	private void init(AttributeSet attrs) {

		TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.LEDLightView);
		final int color = a.getColor(R.styleable.LEDLightView_LightColor, Color.GREEN);
		setLightColor(color);
		a.recycle();
	}

	public LEDLightView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(attrs);
		mRimPaint = new Paint();
		mRimPaint.setFlags(Paint.ANTI_ALIAS_FLAG);

		mRimCirclePaint = new Paint();
		mRimCirclePaint.setAntiAlias(true);
		mRimCirclePaint.setStyle(Paint.Style.STROKE);
		mRimCirclePaint.setColor(Color.argb(0x4f, 0x33, 0x36, 0x33));

		mRimShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

		mGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG) {
			{
				setStyle(Paint.Style.FILL);

			}
		};

		mGlassPaint = new Paint(Paint.ANTI_ALIAS_FLAG) {
			{
				setAntiAlias(true);

			}
		};
		initGlassColors();
		initGlowColors();

	}

	private void initGlowColors() {
		final int i1 = (int) Math.min(255, 0 * mLightIntensity);
		final int i2 = (int) Math.min(255, 155 * mLightIntensity);
		final int i3 = (int) Math.min(255, 255 * mLightIntensity);
		Color.colorToHSV(mLightColor, hsv);
		hsv[2] = mLightIntensity;
		final int color = Color.HSVToColor(hsv);
		final int r = Color.red(color);
		final int g = Color.green(color);
		final int b = Color.blue(color);
		glowGradientColors[2] = Color.argb(i1, r, g, b);
		glowGradientColors[1] = Color.argb(i2, r, g, b);
		// hsv[1] = 1 - 0.4f * mLightIntensity;
		glowGradientColors[0] = Color.HSVToColor(i3, hsv);
		glowGradientPositions[2] = 0.90f;
		glowGradientPositions[1] = 0.70f;
		glowGradientPositions[0] = 0.25f;
		mRecalculateGlowShader = true;
	}

	private void initGlassColors() {
		Color.colorToHSV(mLightColor, hsv);
		hsv[2] = 0.27f;
		final int color = Color.HSVToColor(hsv);
		int r = Color.red(color);
		int b = Color.blue(color);
		int g = Color.green(color);
		glassGradientColors[4] = Color.argb(225, r, g, b);
		glassGradientColors[3] = Color.argb(200, r, g, b);
		glassGradientColors[2] = Color.argb(180, r, g, b);
		glassGradientColors[1] = Color.argb(150, r, g, b);
		glassGradientColors[0] = Color.argb(90, r, g, b);
		glassGradientPositions[4] = 1 - 0.0f;
		glassGradientPositions[3] = 1 - 0.06f;
		glassGradientPositions[2] = 1 - 0.10f;
		glassGradientPositions[1] = 1 - 0.20f;
		glassGradientPositions[0] = 1 - 1.0f;
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);
		if (changed) {
			// calculate centered maximum size square rect inside view bounds
			final int height = bottom - top;
			final int width = right - left;
			final float size = Math.min(width, height);
			final float l = (width - size) / 2;
			final float t = (height - size) / 2;
			final float o = 0.02f * size;

			mBounds.set(l, t, l + size, t + size);
			float inset = size * 0.15f;
			mBounds.inset(inset, inset);
			mInnerBounds.set(mBounds.left + o, mBounds.top + o, mBounds.right - o, mBounds.bottom - o);
			mGlowBounds.set(mInnerBounds);
			mGlowBounds.inset(-inset, -inset);
			final float innerSize = mInnerBounds.width();

			mRimPaint.setShader(new LinearGradient(0.40f * size + mBounds.left, 0.0f + mBounds.top, 0.60f * size + mBounds.left, mBounds.bottom, Color.rgb(0x80, 0x85, 0x80), Color.rgb(0x30, 0x31, 0x30), Shader.TileMode.CLAMP));

			mRimCirclePaint.setStrokeWidth(0.005f * size);

			mRimShadowPaint.setShader(new RadialGradient(mInnerBounds.centerX(), mInnerBounds.centerY(), innerSize / 2, new int[] { 0x00000000, 0x00000500, 0x50000500 }, new float[] { 0.96f, 0.96f, 0.99f }, Shader.TileMode.MIRROR));
			mRimShadowPaint.setStyle(Paint.Style.FILL);

			RadialGradient glassShader = new RadialGradient(mBounds.centerX(), mBounds.centerY(), (int) mBounds.height(), glassGradientColors, glassGradientPositions, TileMode.CLAMP);
			mGlassPaint.setShader(glassShader);
			mGlassPaint.setXfermode(new PorterDuffXfermode(Mode.ADD));

			createDrawingCache(width, height);

		}
	}

	/**
	 * create a bitmap containing the background of the LED
	 * 
	 * @param width
	 * @param height
	 */
	private void createDrawingCache(final int width, final int height) {
		if (mDrawingCache != null) {
			mDrawingCache.recycle();
		}

		mDrawingCache = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		Canvas c = new Canvas(mDrawingCache);
		c.drawOval(mBounds, mRimPaint);
		c.drawOval(mBounds, mRimCirclePaint);
		c.drawOval(mInnerBounds, mRimCirclePaint);
		c.drawOval(mInnerBounds, mRimShadowPaint);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		if (isInEditMode()) {
			canvas.drawOval(mBounds, mRimPaint);
			return;
		}

		canvas.drawBitmap(mDrawingCache, 0, 0, null);

		if (mRecalculateGlowShader) {
			final float radius = mGlowBounds.height() / 2;
			RadialGradient glowShader = new RadialGradient(mGlowBounds.centerX(), mGlowBounds.centerY(), radius, glowGradientColors, glowGradientPositions, TileMode.CLAMP);
			mGlowPaint.setShader(glowShader);
			mRecalculateGlowShader = false;
		}
		canvas.drawOval(mGlowBounds, mGlowPaint);
		canvas.drawOval(mInnerBounds, mGlassPaint);
		canvas.restore();
	}

	/**
	 * animates the light value to desired target [0-1].
	 */
	public void animateLightIntensity(final float target) {
		if (mAnimator != null && mAnimator.isRunning()) {
			mAnimator.addListener(new AnimatorListener() {

				@Override
				public void onAnimationStart(Animator animation) {

				}

				@Override
				public void onAnimationRepeat(Animator animation) {

				}

				@Override
				public void onAnimationEnd(Animator animation) {
					startAnimation(target);
				}

				@Override
				public void onAnimationCancel(Animator animation) {

				}
			});
			mAnimator.cancel();
		} else {
			startAnimation(target);
		}
	}

	public void setLightColor(final int color) {
		mLightColor = color;
		initGlassColors();
	}

	private void startAnimation(final float target) {
		mAnimator = ObjectAnimator.ofFloat(this, "lightIntensity", mLightIntensity, target);
		mAnimator.setInterpolator(new DecelerateInterpolator());
		mAnimator.setDuration(150);
		mAnimator.start();
	}

	/**
	 * instantly sets the light to desired intensity. does not animate.
	 * 
	 * @param light
	 *            [0-1]
	 */
	public void setLightIntensity(final float light) {
		mLightIntensity = light;
		initGlowColors();
		this.invalidate();
	}

	@Override
	public void setChecked(boolean checked) {
		mOn = checked;
		if (checked) {
			animateLightIntensity(1);
		} else {
			animateLightIntensity(0);
		}
	}

	@Override
	public boolean isChecked() {
		return mLightIntensity == 1;
	}

	@Override
	public void toggle() {
		setChecked(!mOn);

	}

}
