package works.com.hellovision2;

import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.androidplot.Plot;
import com.androidplot.util.PlotStatistics;
import com.androidplot.util.Redrawer;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.StepMode;
import com.androidplot.xy.XYPlot;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

public class VisionActivity extends ActionBarActivity implements CameraBridgeViewBase.CvCameraViewListener2
{
    private final String TAG = "APP";
    private Tutorial3View mOpenCvCameraView;
    private SubMenu mColorEffectsMenu;
    TextView rgbVal;
    float lastTouchY=0;
    int cannyThreshold=50;

    

    private boolean flashOn = false;

    long initTs;
    long prevTs;
    long sampleRate = 100;

    // plot things
    private XYPlot colorPlot = null;
    private XYPlot bonusPlot = null;

    private SimpleXYSeries rHistorySeries; // history of red values
    private SimpleXYSeries tHistorySeries; // history of time associated w/ red
    ArrayList<Double> redValues;

    private boolean JUST_RED = true;
    private SimpleXYSeries gHistorySeries;
    ArrayList<Double> greenValues;
    private SimpleXYSeries bHistorySeries;
    ArrayList<Double> blueValues;

    private Redrawer redrawer;

    int bufferCounter = 0;
    int BUFFER_SIZE = 500;
    float sum=0;
    float halfWaySum = 0;
    Float ave = new Float(0);

    ArrayList<Float> testArray;


    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();

                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "called onCreate");
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_vision);
        mOpenCvCameraView = (Tutorial3View) findViewById(R.id.HelloOpenCvView);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        initTs = System.currentTimeMillis();
        prevTs = initTs;
        rgbVal = (TextView) findViewById(R.id.rgbVal);

        redValues = new ArrayList<Double>();

        if(!JUST_RED) {
            greenValues = new ArrayList<Double>();
            blueValues = new ArrayList<Double>();
        }

        // set up graphs!
        colorPlot = (XYPlot) findViewById(R.id.colorValuePlot);
        bonusPlot = (XYPlot) findViewById(R.id.bonusPlot);
        bonusPlot.setVisibility(View.GONE);

        rHistorySeries = new SimpleXYSeries("red");
        rHistorySeries.useImplicitXVals();
        tHistorySeries = new SimpleXYSeries("time");


        if(!JUST_RED) {
            gHistorySeries = new SimpleXYSeries("green");
            gHistorySeries.useImplicitXVals();
            bHistorySeries = new SimpleXYSeries("blue");
            bHistorySeries.useImplicitXVals();
        }

        colorPlot.setDomainBoundaries(0, BUFFER_SIZE, BoundaryMode.FIXED);
       if(JUST_RED) {
           colorPlot.setRangeBoundaries(170, 220, BoundaryMode.FIXED);
           colorPlot.setRangeStepMode(StepMode.INCREMENT_BY_VAL);
           colorPlot.setRangeStepValue(10);
       }else {
           colorPlot.setRangeBoundaries(0, 256, BoundaryMode.FIXED);
       }
        colorPlot.addSeries(rHistorySeries, new LineAndPointFormatter(Color.rgb(200, 100, 100), null, null, null));
        if(!JUST_RED) {
            colorPlot.addSeries(gHistorySeries, new LineAndPointFormatter(Color.rgb(100, 200, 100), null, null, null));
            colorPlot.addSeries(bHistorySeries, new LineAndPointFormatter(Color.rgb(100, 100, 200), null, null, null));
        }

      //  final PlotStatistics colorStats = new PlotStatistics(1000, false);
        redrawer = new Redrawer(Arrays.asList(new Plot[]{colorPlot}), 100, false);

    }

    @Override
    public void onResume() {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_6, this, mLoaderCallback);
        redrawer.start();
    }

    @Override
    public void onPause() {
        redrawer.pause();

        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();

        super.onPause();

    }

    public void onDestroy() {
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
        redrawer.finish();
        super.onDestroy();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mColorEffectsMenu = menu.addSubMenu("Flash On");
        mColorEffectsMenu = menu.addSubMenu("Flash Off");
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "called onOptionsItemSelected; selected item: " + item);

        if (item.toString() == "Flash On") {
            mOpenCvCameraView.setFlashOn();
        } else if (item.toString() == "Flash Off") {
            mOpenCvCameraView.setFlashOff();
        }
        return true;
    }

    public void toggleFlash(View view) {
        if(flashOn) {
            Log.d(TAG, "Turning off Flash");
            flashOn = false;
            mOpenCvCameraView.setFlashOff();
        } else {
            Log.d(TAG, "Turning on Flash");
            flashOn = true;
            mOpenCvCameraView.setFlashOn();
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
    }

    @Override
    public void onCameraViewStopped() {
    }

    public double calculateMean(ArrayList<Double> values)
    {
        Log.d(TAG, "Calculating Mean for array of size " + values.size());
        float mean= (float) 0.0;
       for (int i=0;i<values.size();i++)
       {
           Log.d(TAG, values.get(i) + "");
           mean += values.get(i);

       }
        mean /= values.size();
        return mean;
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        Mat currentFrame = inputFrame.rgba();

        long now = System.currentTimeMillis();
        boolean useSampleRate = false;
        if(useSampleRate && ((now - prevTs) < sampleRate)) {
            return currentFrame;
        }

        // TODO for now, assume that each sample is exactly 100ms apart

        String debugString;

        int windowSize = 4;
        Mat window = currentFrame.submat(currentFrame.rows()/2 - windowSize/2,
                            currentFrame.rows()/2 + windowSize/2,
                            currentFrame.cols()/2 - windowSize/2,
                            currentFrame.cols()/2 + windowSize/2);
        Log.d(TAG, "window size " + window.rows() +", " +
                window.cols());

        // TODO
        // collect color values of every pixel within window

        // put it in the graph
        ArrayList<Double> reds = new ArrayList<Double>();

            ArrayList<Double> greens = new ArrayList<Double>();
            ArrayList<Double> blues = new ArrayList<Double>();

        for(int i = 0; i < window.rows(); i++) {
            for (int j = 0; j < window.cols(); j++) {
                double[] pixel = window.get(i, j);
                reds.add(pixel[0]);
                if(!JUST_RED) {
                    greens.add(pixel[1]);
                    blues.add(pixel[2]);
                }
            }
        }

        // get the average
        double redMean = calculateMean(reds);
        double greenMean = 0;
        double blueMean = 0;
        if(!JUST_RED) {
            greenMean = calculateMean(greens);
            blueMean = calculateMean(blues);
        }

        double[] pixel = currentFrame.get(currentFrame.rows() / 2, currentFrame.cols() / 2);
        //debugString = "R " + pixel[0] + "; G " + pixel[1] + "; B " + pixel[3];
        debugString = "R " + redMean; // + "; G " + greenMean + "; B " + blueMean;
        Log.d(TAG, debugString);

        long timeElapsed = System.currentTimeMillis() - initTs;
        Log.d(TAG, "Time Elapsed: " + timeElapsed);

        if(redValues.size() > BUFFER_SIZE) {
            // FIXME is this right?
            redValues.remove(0);
            rHistorySeries.removeFirst();
            tHistorySeries.removeFirst();
            if(!JUST_RED) {
                greenValues.remove(0);
                gHistorySeries.removeFirst();
                blueValues.remove(0);
                bHistorySeries.removeFirst();
            }
        }

        redValues.add(redMean);
        rHistorySeries.addLast(null, redMean);
        tHistorySeries.addLast(null, now);
        if(!JUST_RED) {
            greenValues.add(greenMean);
            gHistorySeries.addLast(null, greenMean);
            blueValues.add(blueMean);
            bHistorySeries.addLast(null, blueMean);
        }

        prevTs = now;

        // TODO next steps...
        // TODO check for consistent pattern
        // what is the derivative? will that help me see direction changes?


        // TODO can we "zoom" the graph into the red values?
        // TODO what if we take a "moving average"?

        // TODO save data?





        //CANNY Edge Detection
        //Imgproc.cvtColor(currentFrame, currentFrame, Imgproc.COLOR_RGBA2GRAY);
        //Imgproc.Canny(currentFrame,currentFrame,cannyThreshold/3,cannyThreshold );



        return currentFrame;
    }

    public double calculateDifference(double lastVal, double currentVal) {
        return currentVal - lastVal;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        // MotionEvent reports input details from the touch screen
        // and other input controls. In this case, you are only
        // interested in events where the touch position changed.
        float y = e.getY();
        if (e.getAction() == MotionEvent.ACTION_MOVE) {
            if (lastTouchY > y)
                cannyThreshold += 5;
            else
                cannyThreshold -= 5;
            lastTouchY = y;
        }

        if (e.getAction() == MotionEvent.ACTION_UP)
            lastTouchY = 0;
        return true;
    }

    /*
    For saving historical data
     */
    public void saveData(View view) {
        Log.d(TAG, "Saving data");

        String csvFile = "Index,time,R\n";
        for (int i=0; i < rHistorySeries.size(); i++) {
            csvFile += "" + i;
            csvFile += "," + tHistorySeries.getY(i);
            csvFile += "," + rHistorySeries.getY(i);
            csvFile += "\n";
        }
        String allData = rHistorySeries.getyVals().toString();
        //allData += yHistorySeries.toString() + zHistorySeries.toString();
        String fileName = "color_data_" + (System.currentTimeMillis() % 1000) + ".csv";
        addLog(fileName, csvFile, false);
    }

    public void addLog(String filename, String text, boolean timestamp)
    {
        String extStore = System.getenv("EXTERNAL_STORAGE");
        String location = extStore + '/' + filename;
        Log.d(TAG, "Logging to external storage " + location);
        File f_exts = new File(extStore);

        File logFile = new File(location);
        if (!logFile.exists())
        {
            try
            {
                logFile.createNewFile();
            }
            catch (IOException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        try
        {
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));

            if(timestamp) {
                // Getting the current timestamp
                Long tsLong = System.currentTimeMillis();
                String ts = tsLong.toString();

                buf.append(ts + ";"); //Adding timestamp to everything is good practice
            }
            buf.append(text);
            buf.append("\n");
            buf.close();
        }
        catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}