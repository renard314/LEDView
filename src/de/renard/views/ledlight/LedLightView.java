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

	private RectF mBounds = new RectF(); // bounds for round led light
	private RectF mInnerBounds = new RectF(); // bounds of glass dome
	private RectF mGlowBounds = new RectF(); // bounds for glow
	private final Paint mRimPaint; // metallic background
	private final Paint mRimCirclePaint; // border of metallic background
	private final Paint mRimShadowPaint; // inset shadow of metallic background
	private final Paint mGlowPaint; // light source
	private final Paint mGlassPaint;// glass dome above the light TODO make it
									// more look like glass
	private ObjectAnimator mAnimator; // animates between on and off state

	//pre allocated arrays
	final int[] glassGradientColors = new int[5];
	final int[] glowGradientColors = new int[3];
	final float[] glassGradientPositions = new float[5];
	final float[] glowGradientPositions = new float[3];
	final float[] hsv = new float[3];
	float mLightIntensity = 0; // current intensity of the light. range [0-1]
	private boolean mOn = false;
	int mLightColor;
	private boolean mRecalculateGlowShader;
	private Bitmap mBackground = null;


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
	
	private void init(AttributeSet attrs) {
		TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.LEDLightView);
		final int color = a.getColor(R.styleable.LEDLightView_LightColor, Color.GREEN);
		setLightColor(color);
		a.recycle();
	}


	/**
	 * the glow is made up of a radial gradient dependant on the current light intensity
	 */
	private void initGlowColors() {
		//outer edges fade out
		final int i1 = (int) Math.min(255, 0 * mLightIntensity);
		final int i2 = (int) Math.min(255, 155 * mLightIntensity);
		final int i3 = (int) Math.min(255, 255 * mLightIntensity);
		//convert to hsv to change intensity
		Color.colorToHSV(mLightColor, hsv);
		hsv[2] = mLightIntensity;
		//convert back to argb
		final int color = Color.HSVToColor(hsv);
		final int r = Color.red(color);
		final int g = Color.green(color);
		final int b = Color.blue(color);
		glowGradientColors[2] = Color.argb(i1, r, g, b);
		glowGradientColors[1] = Color.argb(i2, r, g, b);
		hsv[1] -= hsv[1]* 0.25f * mLightIntensity; //make the center of the light a litte bit white
		glowGradientColors[0] = Color.HSVToColor(i3, hsv);
		glowGradientPositions[2] = 0.90f;
		glowGradientPositions[1] = 0.70f;
		glowGradientPositions[0] = 0.25f;
		//next onDraw() will create a new glow shader
		mRecalculateGlowShader = true;
	}

	/**
	 * glass dome is emulated by a radial gradient
	 * glass has a light tint of the light color
	 */
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
			float inset = size * 0.15f; //led is 15% smaller than the bounds so that the glow of the light can be larger than the led
			mBounds.inset(inset, inset);
			mInnerBounds.set(mBounds.left + o, mBounds.top + o, mBounds.right - o, mBounds.bottom - o);
			mGlowBounds.set(mInnerBounds);
			mGlowBounds.inset(-inset, -inset); //glow bounds take up all space
			final float innerSize = mInnerBounds.width();

			final float insetSize = size-2*inset;
			mRimPaint.setShader(new LinearGradient(0.40f * insetSize + mBounds.left, 0.0f + mBounds.top, 0.60f * insetSize + mBounds.left, mBounds.bottom, Color.rgb(0x80, 0x85, 0x80), Color.rgb(0x30, 0x31, 0x30), Shader.TileMode.CLAMP));

			mRimCirclePaint.setStrokeWidth(0.005f * insetSize);

			mRimShadowPaint.setShader(new RadialGradient(mInnerBounds.centerX(), mInnerBounds.centerY(), innerSize / 2, new int[] { 0x00000000, 0x00000500, 0x50000500 }, new float[] { 0.96f, 0.96f, 0.99f }, Shader.TileMode.MIRROR));
			mRimShadowPaint.setStyle(Paint.Style.FILL);

			RadialGradient glassShader = new RadialGradient(mBounds.centerX(), mBounds.centerY(), (int) mBounds.height(), glassGradientColors, glassGradientPositions, TileMode.CLAMP);
			mGlassPaint.setShader(glassShader);
			mGlassPaint.setXfermode(new PorterDuffXfermode(Mode.ADD)); //glass is added on top of light

			createDrawingCache(width, height);

		}
	}

	/**
	 * draw background of the led into a bitmap
	 * 
	 * @param width
	 * @param height
	 */
	private void createDrawingCache(final int width, final int height) {
		if (mBackground != null) {
			mBackground.recycle();
		}

		mBackground = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		Canvas c = new Canvas(mBackground);
		c.drawOval(mBounds, mRimPaint);
		c.drawOval(mBounds, mRimCirclePaint);
		c.drawOval(mInnerBounds, mRimCirclePaint);
		c.drawOval(mInnerBounds, mRimShadowPaint);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		//eclipse cant handle this view
		if (isInEditMode()) {
			canvas.drawOval(mBounds, mRimPaint);
			return;
		}

		canvas.drawBitmap(mBackground, 0, 0, null);

		if (mRecalculateGlowShader) {
			final float radius = mGlowBounds.height() / 2;
			RadialGradient glowShader = new RadialGradient(mGlowBounds.centerX(), mGlowBounds.centerY(), radius, glowGradientColors, glowGradientPositions, TileMode.CLAMP);
			mGlowPaint.setShader(glowShader);
			mRecalculateGlowShader = false;
		}
		canvas.drawOval(mGlowBounds, mGlowPaint);
		canvas.drawOval(mInnerBounds, mGlassPaint);
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



	private void startAnimation(final float target) {
		mAnimator = ObjectAnimator.ofFloat(this, "lightIntensity", mLightIntensity, target);
		mAnimator.setInterpolator(new DecelerateInterpolator());
		final float scale = Math.abs(target - mLightIntensity);
		mAnimator.setDuration((long) (150 * scale));
		mAnimator.start();
	}

	public void setLightColor(final int color) {
		mLightColor = color;
		initGlassColors();
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
		return mOn ;
	}

	@Override
	public void toggle() {
		setChecked(!mOn);

	}

}
