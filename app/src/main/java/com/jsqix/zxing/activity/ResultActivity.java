package com.jsqix.zxing.activity;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.ImageView;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;

import com.jsqix.zxing.R;
import com.zxing.decode.DecodeThread;


public class ResultActivity extends Activity {

	private ImageView mResultImage;
	private TextView mResultText;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_result);

		Bundle extras = getIntent().getExtras();
		
		mResultImage = (ImageView) findViewById(R.id.result_image);
		mResultText = (TextView) findViewById(R.id.result_text);

		if (null != extras&&extras.getInt("width")>0) {
			int width = extras.getInt("width");
			int height = extras.getInt("height");

			LayoutParams lps = new LayoutParams(width, height);
			lps.topMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30, getResources().getDisplayMetrics());
			lps.leftMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics());
			lps.rightMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics());
			
			mResultImage.setLayoutParams(lps);

			String result = extras.getString("result");

			Bitmap barcode = null;
			byte[] compressedBitmap = extras.getByteArray(DecodeThread.BARCODE_BITMAP);
			if (compressedBitmap != null) {
				barcode = BitmapFactory.decodeByteArray(compressedBitmap, 0, compressedBitmap.length, null);
				// Mutable copy:
				barcode = barcode.copy(Bitmap.Config.RGB_565, true);
			}

			mResultText.setText(result);
			mResultImage.setImageBitmap(barcode);
		}else{
			String img = getIntent().getStringExtra("imgPath");
			String result = getIntent().getStringExtra("result");
			mResultText.setText(result);
			if(img!=null){
				if("123".equals(img)){
					mResultImage.setImageResource(R.drawable.qcode);
					return;
				}
				try {
					BitmapFactory.Options options = new BitmapFactory.Options();
					options.inJustDecodeBounds = true; // 先获取原大小
					Bitmap scanBitmap = BitmapFactory.decodeFile(img, options);
					options.inJustDecodeBounds = false; // 获取新的大小

					int sampleSize = (int) (options.outHeight / (float) 200);

					if (sampleSize <= 0)
						sampleSize = 1;
					options.inSampleSize = sampleSize;
					scanBitmap = BitmapFactory.decodeFile(img, options);
					mResultImage.setImageBitmap(scanBitmap);
				} catch (Exception e) {
					// TODO: handle exception
				}
			}
		}
	}
}
