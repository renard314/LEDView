package de.renard.views.ledlight;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class LedLightActivity extends Activity {
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		final LedLightView led1 = (LedLightView) findViewById(R.id.light1);
		final LedLightView led2 = (LedLightView) findViewById(R.id.light2);
		final LedLightView led3 = (LedLightView) findViewById(R.id.light3);
		final LedLightView led4 = (LedLightView) findViewById(R.id.light4);
		final LedLightView led5 = (LedLightView) findViewById(R.id.light5);
		final LedLightView led6 = (LedLightView) findViewById(R.id.light6);
		final LedLightView led7 = (LedLightView) findViewById(R.id.light7);
		led2.toggle();
		led4.toggle();
		led6.toggle();

		
		Button b = (Button) findViewById(R.id.button1);
		b.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				led1.toggle();
				
				led2.toggle();
				led3.toggle();
				led4.toggle();
				led5.toggle();
				led6.toggle();
				led7.toggle();
			}
		});
	}
}