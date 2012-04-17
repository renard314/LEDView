package de.renard.views.ledlight;

import java.io.InputStream;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
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

/**
 * 
 * @author renard
 *
 */
public class LEDLightView extends View implements Checkable {

	final static int DEFAULT_ANIMATION_DURATION = 150;
	final static int DEFAULT_COLOR = Color.RED;
	final static boolean DEFAULT_COLORIZE_GLASS = true;

	private RectF mBounds = new RectF(); // bounds for round led light
	private RectF mInnerBounds = new RectF(); // bounds of glass dome
	private RectF mGlassBounds = new RectF(); // bounds of glass dome for
												// drawing
	private RectF mGlowBounds = new RectF(); // bounds for glow
	private final Paint mRimPaint; // metallic outer background
	private final Paint mBackgroundPaint; // metallic inner background
	private final Paint mRimCirclePaint; // border of metallic background
	private final Paint mRimShadowPaint; // inset shadow of metallic background
	private final Paint mGlowPaint; // paint for light source
	private final Paint mGlassPaint;// glass dome above the light uses a bitmap
									// shader for the glass effect (TODO make it
									// work without a bitmap)
	private ObjectAnimator mAnimator; // animates between on and off state

	// pre allocated arrays
	final int[] glowGradientColors = new int[3];
	final float[] glowGradientPositions = new float[3];
	final float[] hsv = new float[3];

	// values from configuration
	int mLightColor;
	int mAnimationDuration; // duration of animation between on and off
	boolean mColorizeGlass; // if true the glass gets colored depending on the
							// light color

	// transinient variables
	float mLightIntensity = 0; // current intensity of the light. range [0-1]
	private boolean mOn = false;
	private boolean mRecalculateGlowShader;
	private Bitmap mBackground = null;
	private Bitmap mGlass = null;

	public LEDLightView(Context context, AttributeSet attrs) {
		super(context, attrs);
		mRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		mBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		mRimCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		mRimCirclePaint.setAntiAlias(true);
		mRimCirclePaint.setStyle(Paint.Style.STROKE);
		mRimCirclePaint.setColor(Color.argb(0x4f, 0x33, 0x36, 0x33));

		mRimShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

		mGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		mGlowPaint.setStyle(Paint.Style.FILL);

		mGlassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		mGlassPaint.setAntiAlias(true);

		init(attrs);
		setLightIntensity(mLightIntensity);
	}

	private void init(AttributeSet attrs) {
		TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.LEDLightView);
		final int color = a.getColor(R.styleable.LEDLightView_LightColor, DEFAULT_COLOR);
		mColorizeGlass = a.getBoolean(R.styleable.LEDLightView_ColorizeGlass, DEFAULT_COLORIZE_GLASS);
		mAnimationDuration = a.getInteger(R.styleable.LEDLightView_AnimationDuration, DEFAULT_ANIMATION_DURATION);
		setLightColor(color);
		a.recycle();
	}

	/**
	 * the glow is made up of a radial gradient dependant on the current light
	 * intensity
	 */
	private void initGlowColors() {
		// outer edges fade out
		final int i1 = (int) Math.min(255, 0 * mLightIntensity);
		final int i2 = (int) Math.min(255, 155 * mLightIntensity);
		final int i3 = (int) Math.min(255, 255 * mLightIntensity);
		// convert to hsv to change intensity
		Color.colorToHSV(mLightColor, hsv);
		hsv[2] = mLightIntensity;
		// convert back to argb
		final int color = Color.HSVToColor(hsv);
		final int r = Color.red(color);
		final int g = Color.green(color);
		final int b = Color.blue(color);
		glowGradientColors[2] = Color.argb(i1, r, g, b);
		glowGradientColors[1] = Color.argb(i2, r, g, b);
		hsv[1] -= hsv[1] * 0.45f * mLightIntensity; // make the center of the
													// light a litte bit white
		glowGradientColors[0] = Color.HSVToColor(i3, hsv);
		
		glowGradientPositions[2] = 1f;
		glowGradientPositions[1] = 0.70f;
		glowGradientPositions[0] = 0.33f;
		// next onDraw() will create a new glow shader
		mRecalculateGlowShader = true;
	}

	/**
	 * Gives the glass paint a light tint of color
	 */
	private void initGlassColors() {
		if (!isInEditMode()) {
			Color.colorToHSV(mLightColor, hsv);
			hsv[1] = hsv[1] * 0.6f; // desaturate
			hsv[2] = hsv[2] * 0.3f; // darken
			mGlassPaint.setColorFilter(new LightingColorFilter(Color.HSVToColor(hsv), Color.HSVToColor(hsv)));
		}
	}

	@Override
	protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
		boolean changed = false;
		changed |= w != oldw;
		changed |= h != oldh;
		changed &= w > 0;
		changed &= h > 0;
		if (changed) {
			// calculate centered maximum size square rect inside view bounds
			final float size = Math.min(w, h);
			final float l = (w - size) / 2;
			final float t = (h - size) / 2;
			final float o = 0.02f * size;

			mBounds.set(l, t, l + size, t + size);
			float inset = size * 0.10f; // led is smaller than the bounds so
										// that the glow of the light illuminates the metallic border
			mBounds.inset(inset, inset);
			mInnerBounds.set(mBounds.left + o, mBounds.top + o, mBounds.right - o, mBounds.bottom - o);
			mGlassBounds.set(0, 0, mInnerBounds.width(), mInnerBounds.height());
			mGlowBounds.set(mInnerBounds);
			mGlowBounds.inset(-inset, -inset); // glow bounds take up all space
			final float innerSize = mInnerBounds.width();

			final float insetSize = size - 2 * inset;
			// outer ring is a lighter metall
			mBackgroundPaint.setShader(new LinearGradient(0.40f * insetSize + mBounds.left, 0.0f + mBounds.top, 0.60f * insetSize + mBounds.left, mBounds.bottom, Color.rgb(0x30, 0x35, 0x30), Color.rgb(0x10, 0x12, 0x10), Shader.TileMode.CLAMP));
			// area under glass is darker
			mRimPaint.setShader(new LinearGradient(0.40f * insetSize + mBounds.left, 0.0f + mBounds.top, 0.60f * insetSize + mBounds.left, mBounds.bottom, Color.rgb(0xa0, 0xa5, 0xa0), Color.rgb(0x30, 0x32, 0x30), Shader.TileMode.CLAMP));

			mRimCirclePaint.setStrokeWidth(0.005f * insetSize);

			if (!isInEditMode()) {
				mRimShadowPaint.setShader(new RadialGradient(mInnerBounds.centerX(), mInnerBounds.centerY(), innerSize / 2, new int[] { 0x00000000, 0x00000500, 0x50000500 }, new float[] { 0.96f, 0.96f, 0.99f }, Shader.TileMode.MIRROR));
			}
			mRimShadowPaint.setStyle(Paint.Style.FILL);

			if (!isInEditMode()) {
				mGlassPaint.setXfermode(new PorterDuffXfermode(Mode.SCREEN));
			}

			createDrawingCache(w, h);
			createGlassShader(mBounds);

		}
	}

	private void createGlassShader(final RectF innerBounds) {
		if (mGlass != null) {
			mGlass.recycle();
		}
		final int glassResourceId;
		if (mColorizeGlass) {
			glassResourceId = R.drawable.glass_milky;
		} else {
			glassResourceId = R.drawable.glass_clear;
		}
		final InputStream is = getResources().openRawResource(glassResourceId);
		Options opts = new Options();
		opts.inScaled = false;
		Bitmap largBitmap = BitmapFactory.decodeStream(is, null, opts);
		mGlass = Bitmap.createScaledBitmap(largBitmap, (int) innerBounds.width(), (int) innerBounds.height(), true);
		largBitmap.recycle();
		BitmapShader shader = new BitmapShader(mGlass, TileMode.CLAMP, TileMode.CLAMP);
		mGlassPaint.setShader(shader);
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
		c.drawOval(mInnerBounds, mBackgroundPaint);
		c.drawOval(mInnerBounds, mRimShadowPaint);
	}

	@Override
	protected void onDraw(Canvas canvas) {

		if (mBackground == null) {
			return;
		}

		canvas.drawBitmap(mBackground, 0, 0, null);

		if (mRecalculateGlowShader && !isInEditMode()) {
			final float radius = mGlowBounds.height() / 2;
			RadialGradient glowShader = new RadialGradient(mGlowBounds.centerX(), mGlowBounds.centerY(), radius, glowGradientColors, glowGradientPositions, TileMode.CLAMP);
			mGlowPaint.setShader(glowShader);
			mRecalculateGlowShader = false;
		}
		canvas.drawOval(mGlowBounds, mGlowPaint);
		canvas.save();
		canvas.translate(mInnerBounds.left, mInnerBounds.top);
		canvas.drawOval(mGlassBounds, mGlassPaint);
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

	private void startAnimation(final float target) {
		mAnimator = ObjectAnimator.ofFloat(this, "lightIntensity", mLightIntensity, target);
		mAnimator.setInterpolator(new DecelerateInterpolator());
		final float scale = Math.abs(target - mLightIntensity);
		mAnimator.setDuration((long) (mAnimationDuration * scale));
		mAnimator.start();
	}

	public void setLightColor(final int color) {
		mLightColor = color;
		if (mColorizeGlass) {
			initGlassColors();
		}
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
		return mOn;
	}

	@Override
	public void toggle() {
		setChecked(!mOn);
	}

}
