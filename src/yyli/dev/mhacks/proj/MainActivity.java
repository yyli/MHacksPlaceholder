package yyli.dev.mhacks.proj;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.ExifInterface;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

public class MainActivity extends Activity {
	public static final String CAMERA_IMAGE_BUCKET_NAME =
	        Environment.getExternalStorageDirectory().toString()
	        + "/DCIM/Camera";
//	        + "/DCIM/100MEDIA";
	public static final String CAMERA_IMAGE_BUCKET_ID =
	        getBucketId(CAMERA_IMAGE_BUCKET_NAME);
	
	static final Integer MAX_LOADED_IMAGES = 10;
	static final Integer LOADED_IMAGES_PER_PAGE = 2;
	
	protected GoogleMap map;
	protected CurrentlyLoadedEvents curLoad = new CurrentlyLoadedEvents(this);
	protected Marker curMarker;
	protected Calendar cal;
	protected HashMap<Marker, Integer> MarkerMap = new HashMap<Marker, Integer>();
	
	protected void setCurMarkerLatLng(LatLng loc, Integer pos) {
		if (loc != null) {
			Marker tempMarker = null;
			if (curMarker == null ||
					Math.abs(curMarker.getPosition().latitude - loc.latitude) > 0.000001 ||
					Math.abs(curMarker.getPosition().longitude - loc.longitude) > 0.0000001) {
						tempMarker = map.addMarker(new MarkerOptions().position(loc));
			}
			if (tempMarker != null) {
				if (curMarker != null) {
					curMarker.remove();
				}
				curMarker = tempMarker;
			}
			MarkerMap.put(curMarker, pos);
			map.moveCamera(CameraUpdateFactory.newLatLngZoom(loc, 15));
		}
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
//		// set up calendar
//		cal = Calendar.getInstance();
//    	cal.clear();
//    	cal.set(1000,8,15);
//    	cal.add(Calendar.HOUR, 11);
//    	cal.add(Calendar.MINUTE, 21);
//    	cal.add(Calendar.SECOND, 15);
//		
//    	imageList = getCameraImages(this);
//    	
//    	MostRecent = new ArrayList<Bitmap>();
//		try {
//			MostRecent = getMostRecent();
//		} catch( IOException e) {
//		}
    	
		// set up get location
		LocationManager mlocManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        LocationListener mlocListener = new MyLocationListener();
        mlocManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, mlocListener);

		// set up map
		MapFragment fm = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
		map = fm.getMap();
		map.getUiSettings().setZoomControlsEnabled(false);
		map.setOnMarkerClickListener(new OnMarkerClickListener() {
			@Override
			public boolean onMarkerClick(Marker mark) {
				Integer pos = MarkerMap.get(mark);
				setCurMarkerLatLng(curLoad.getLatLngAtIdx(pos), pos);
				ListView listview = (ListView) findViewById(R.id.listview);
				listview.setSelection(pos);
				return true;
			}	
		});
		
		// set to last known location
		Location cur = mlocManager.getLastKnownLocation(mlocManager.getBestProvider(new Criteria(), false));
		if (cur != null) {			
			map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(cur.getLatitude(), cur.getLongitude()), 15));
			map.addMarker(new MarkerOptions()
		    .position(new LatLng(cur.getLatitude(), cur.getLongitude()))
		    .anchor(0.5f, 0.5f)
		    .icon(BitmapDescriptorFactory.fromResource(R.drawable.red_marker)));
		} else {
			Toast.makeText(getApplicationContext(), "Still waiting to get GPS Lock", Toast.LENGTH_SHORT).show();
		}
		
		curLoad.init();
		
		ListView listview = (ListView) findViewById(R.id.listview);
		listview.setAdapter(new ImageAdapter(this));
		listview.setOnScrollListener(new EndlessScrollListener());
		
		listview.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
				//Toast.makeText(MainActivity.this, "" + position, Toast.LENGTH_SHORT).show();
			}
		});
	}
	
	public void onResume(Bundle savedInstanceState) {
		super.onResume();
	}
	
	@SuppressLint("DefaultLocale")
	public static String getBucketId(String path) {
	    return String.valueOf(path.toLowerCase().hashCode());
	}
	
	public class CurrentlyLoadedEvents {
		private Context cxt;
		
		List<String> imageList;
		List< Pair<Bitmap, LatLng> > CurLoaded = new ArrayList< Pair<Bitmap, LatLng> >();
		Integer CurLoadedIdx = 0;
		
		public CurrentlyLoadedEvents(Context context) {
			cxt = context;
		}
		
		public Integer getNumberOfLoadedImages() {
			return CurLoaded.size();
		}
		
		public Bitmap getBitmapAtIdx(Integer idx) {
			return CurLoaded.get(idx).first;
		}
		
		public LatLng getLatLngAtIdx(Integer idx) {
			return CurLoaded.get(idx).second;
		}
		
		public void init() {
			imageList = getCameraImages(cxt);
			for (int i = 0; i < LOADED_IMAGES_PER_PAGE && i < imageList.size(); i++) {
				Pair<Bitmap, LatLng> pair = getBitmapLatLngFromString(imageList.get(i));
				CurLoaded.add(pair);
				Marker mark = map.addMarker(new MarkerOptions()
			    .position(pair.second)
			    .anchor(0.5f, 0.5f)
			    .icon(BitmapDescriptorFactory.fromResource(R.drawable.blue_marker)));
				
				MarkerMap.put(mark, i);
			}
		}
		
		public void loadMore() {
			int idx = CurLoaded.size() - 1;
			int count = 0;
			while (count < LOADED_IMAGES_PER_PAGE && idx < imageList.size()) {
				Log.e("imageStr", imageList.get(idx) + ", " + CurLoaded.size());
				Pair<Bitmap, LatLng> pair = getBitmapLatLngFromString(imageList.get(idx));
				CurLoaded.add(pair);
				Marker mark = map.addMarker(new MarkerOptions()
			    .position(pair.second)
			    .anchor(0.5f, 0.5f)
			    .icon(BitmapDescriptorFactory.fromResource(R.drawable.blue_marker)));
				idx++;
				count++;
				MarkerMap.put(mark, idx);
			}
		}
		
		private Pair<Bitmap, LatLng> getBitmapLatLngFromString(String str) {
			LatLng geoCorr = getExifFromStr(str);
			Bitmap pic = getBitmapFromStr(str);
			
			Pair<Bitmap, LatLng> result = new Pair<Bitmap, LatLng>(pic, geoCorr);
			return result;
		}
		
		private LatLng getExifFromStr(String str) {
			Float Latitude = null, Longitude = null;
			try {
				ExifInterface exif = new ExifInterface(str);
				
				String longit = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE);
				String latit = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE);
				String latRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF);
				String longRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF);
				
				if((latit != null) && (latRef != null) && (longit != null) && (longRef != null)) {
					if (latRef.equals("N")) {
						Latitude = convertStringToDegree(latit);
					} else {
						Latitude = 0 - convertStringToDegree(latit);
					}
	
					if (longRef.equals("E")) {
						Longitude = convertStringToDegree(longit);
					} else {
						Longitude = 0 - convertStringToDegree(longit);
					}
				}
			} catch (IOException e) {
				return null;
			}
			
			if (Latitude == null || Longitude == null) {
				return null;
			} else {
				return new LatLng(Latitude, Longitude);
			}
		}
		
		private Bitmap getBitmapFromStr(String str) {
	        final BitmapFactory.Options options = new BitmapFactory.Options();
	        options.inJustDecodeBounds = true;
	        BitmapFactory.decodeFile(str, options);
	        
	        options.inSampleSize = 2;
	        options.inJustDecodeBounds = false;
	        return BitmapFactory.decodeFile(str, options);
		}
		
		private List<String> getCameraImages(Context context) {
	        final String[] projection = { MediaStore.Images.Media.DATA };
	        final String selection = MediaStore.Images.Media.BUCKET_ID + " = ?";
	        final String[] selectionArgs = { CAMERA_IMAGE_BUCKET_ID };
	        
	        final Cursor cursor = context.getContentResolver().query(Images.Media.EXTERNAL_CONTENT_URI, 
	                projection, 
	                selection, 
	                selectionArgs, 
	                null);
	        
	        ArrayList<String> result = new ArrayList<String>(cursor.getCount());
	        
	        if (cursor.moveToLast()) {
	            final int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
	            do {
	                final String data = cursor.getString(dataColumn);
	                result.add(data);
	            } while (cursor.moveToPrevious());
	        }
	        
	        cursor.close();
	        return result;
	    }
		
		private Float convertStringToDegree(String stringDMS){
			Float result = null;
			String[] DMS = stringDMS.split(",", 3);
	
			String[] stringD = DMS[0].split("/", 2);
			Double D0 = Double.valueOf(stringD[0]);
			Double D1 = Double.valueOf(stringD[1]);
			Double FloatD = D0/D1;
	
			String[] stringM = DMS[1].split("/", 2);
			Double M0 = Double.valueOf(stringM[0]);
			Double M1 = Double.valueOf(stringM[1]);
			Double FloatM = M0/M1;
	
			String[] stringS = DMS[2].split("/", 2);
			Double S0 = Double.valueOf(stringS[0]);
			Double S1 = Double.valueOf(stringS[1]);
			Double FloatS = S0/S1;
	
			result = Double.valueOf(FloatD + (FloatM/60) + (FloatS/3600)).floatValue();
			return result;
		}
	}

	public class ImageAdapter extends BaseAdapter {
	    private Context mContext;

	    public ImageAdapter(Context c) {
	        mContext = c;
	    }

	    public int getCount() {
	    	return curLoad.getNumberOfLoadedImages();
	    }

	    public Object getItem(int position) {
	        return null;
	    }

	    public long getItemId(int position) {
	        return 0;
	    }

	    // create a new ImageView for each item referenced by the Adapter
	    public View getView(int position, View convertView, ViewGroup parent) {
    		final float SCALE = getBaseContext().getResources().getDisplayMetrics().density;
	        ImageView imageView;
	        if (convertView == null || !(convertView instanceof ImageView)) {  // if it's not recycled, initialize some attributes
	            imageView = new ImageView(mContext);
	            int heightPixel = (int) (500 * SCALE + 0.5f);
	            imageView.setLayoutParams(new GridView.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, heightPixel));
	            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
	            imageView.setPadding(0, 0, 0, 0);
	        } else {
	            imageView = (ImageView) convertView;
	        }

            imageView.setOnClickListener(new ImageOnClickListener(position));
            imageView.setImageBitmap(curLoad.getBitmapAtIdx(position));
	        return imageView;
	    }
	    
	    public class ImageOnClickListener implements OnClickListener {
	    	Integer pos;
	    	public ImageOnClickListener(Integer _pos) {
	    		pos = _pos;
	    	}
	    	
			@Override
			public void onClick(View view) {
				setCurMarkerLatLng(curLoad.getLatLngAtIdx(pos), pos);
			}
	    }
	}
	
    public class EndlessScrollListener implements OnScrollListener {

        private int visibleThreshold = 2;
        private int previousTotal = 0;
        private boolean loading = true;

        public EndlessScrollListener() {
        }
        
        public EndlessScrollListener(int visibleThreshold) {
            this.visibleThreshold = visibleThreshold;
        }

        @Override
        public void onScroll(AbsListView view, int firstVisibleItem,
                int visibleItemCount, int totalItemCount) {
        	Integer pos = view.getFirstVisiblePosition();
        	if (view.getChildAt(0) != null && view.getChildAt(0).getTop() < 0) {
        		pos++;
        	}
        	if (pos < curLoad.getNumberOfLoadedImages()) {
        		setCurMarkerLatLng(curLoad.getLatLngAtIdx(pos), pos);
        	}
        	
            if (loading) {
                if (totalItemCount > previousTotal) {
                    loading = false;
                    previousTotal = totalItemCount;
                }
            }
            if (!loading && (totalItemCount - visibleItemCount) <= (firstVisibleItem + visibleThreshold)) {
				curLoad.loadMore();
				((BaseAdapter)view.getAdapter()).notifyDataSetChanged();
                loading = true;
                Log.e("a","here");
            }
        }

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
        }
    }
	
	public class MyLocationListener implements LocationListener
    {
    	@Override
    	public void onLocationChanged(Location loc)
    	{
    		map.addMarker(new MarkerOptions()
		    .position(new LatLng(loc.getLatitude(), loc.getLongitude()))
		    .anchor(0.5f, 0.5f)
		    .icon(BitmapDescriptorFactory.fromResource(R.drawable.red_marker)));
    		Log.e("HelloWorld", loc.getLatitude() + ", " + loc.getLongitude());
    	}

    	@Override
    	public void onProviderDisabled(String provider)
    	{
    		Toast.makeText( getApplicationContext(),"Gps Disabled",Toast.LENGTH_SHORT ).show();

    	}
    	@Override
    	public void onProviderEnabled(String provider)
    	{
    		Toast.makeText( getApplicationContext(),"Gps Enabled",Toast.LENGTH_SHORT).show();
    	}

    	@Override
    	public void onStatusChanged(String provider, int status, Bundle extras)
    	{

    	}
    }
}