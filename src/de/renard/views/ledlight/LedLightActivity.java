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
		final LEDLightView led1 = (LEDLightView) findViewById(R.id.light1);
		final LEDLightView led2 = (LEDLightView) findViewById(R.id.light2);
		final LEDLightView led3 = (LEDLightView) findViewById(R.id.light3);
		final LEDLightView led4 = (LEDLightView) findViewById(R.id.light4);
		final LEDLightView led5 = (LEDLightView) findViewById(R.id.light5);
		final LEDLightView led6 = (LEDLightView) findViewById(R.id.light6);
		final LEDLightView led7 = (LEDLightView) findViewById(R.id.light7);
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