package works.com.hellovision2;

import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.androidplot.Plot;
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
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.w3c.dom.Text;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class VisionActivity extends ActionBarActivity implements CameraBridgeViewBase.CvCameraViewListener2
{
    private final String TAG = "APP";
    private Tutorial3View mOpenCvCameraView;
    private SubMenu mColorEffectsMenu;
    float lastTouchY=0;
    int cannyThreshold=50;

    int TIME_WINDOW_SIZE = 256;
    int TOLERANCE = 20;

    //
    private double LOW_PASS_FILTER_COMPARISON = 2.0;

    ArrayList<Double> timeWindow = new ArrayList<Double>(); // window of red values
    ArrayList<Long> timeWindowTimes = new ArrayList<Long>(); // window of times for those vals
    double windowMin = 256;
    double windowMax = -1;


    ArrayList<Double> heartRateWindow = new ArrayList<Double>();
    int HR_MOVING_AVERAGE_SIZE = 10;

    private boolean flashOn = false;

    long initTs;
    long prevTs;
    long sampleRate = 100;

    // plot things
    private XYPlot colorPlot = null;
    private XYPlot bonusPlot = null;

    private XYPlot freqPlot = null;

    ArrayList<Double> redValues;

    private SimpleXYSeries rHistorySeries; // history of red values
    private SimpleXYSeries tHistorySeries; // history of time associated w/ red

    private SimpleXYSeries freqSeries;


    private Redrawer redrawer;

    int bufferCounter = 0;
    int BUFFER_SIZE = 500;
    float sum=0;
    float halfWaySum = 0;
    Float ave = new Float(0);

    ArrayList<Float> testArray;

    boolean CALCULATING_FFT = false;


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


        redValues = new ArrayList<Double>();


        // set up graphs!
        colorPlot = (XYPlot) findViewById(R.id.colorValuePlot);
        bonusPlot = (XYPlot) findViewById(R.id.bonusPlot);
        bonusPlot.setVisibility(View.GONE);
        freqPlot = (XYPlot) findViewById(R.id.freqPlot);

        rHistorySeries = new SimpleXYSeries("red");
        rHistorySeries.useImplicitXVals();
        tHistorySeries = new SimpleXYSeries("time");

        freqSeries = new SimpleXYSeries("freq");



        colorPlot.setDomainBoundaries(0, BUFFER_SIZE, BoundaryMode.FIXED);

        colorPlot.setRangeBoundaries(150, 220, BoundaryMode.FIXED);
        colorPlot.setRangeStepMode(StepMode.INCREMENT_BY_VAL);
        colorPlot.setRangeStepValue(10);

        colorPlot.addSeries(rHistorySeries, new LineAndPointFormatter(Color.rgb(200, 100, 100), null, null, null));

        // TODO
        int MAX_FREQ = 256;
        freqPlot.setDomainBoundaries(0, MAX_FREQ, BoundaryMode.FIXED);
        freqPlot.setRangeBoundaries(0, 0.1, BoundaryMode.FIXED);
        freqPlot.setRangeStepMode(StepMode.INCREMENT_BY_VAL);
        freqPlot.setRangeStepValue(0.01);
        freqPlot.setDomainLabel("frequency (cycles per sample)");

        freqPlot.addSeries(freqSeries, new LineAndPointFormatter(Color.rgb(100, 200, 200), null, null, null));


        //  final PlotStatistics colorStats = new PlotStatistics(1000, false);
        redrawer = new Redrawer(Arrays.asList(new Plot[]{colorPlot, freqPlot}), 100, false);

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


    public void toggleFlash(View view) {
        if(flashOn) {
            Log.d(TAG, "Turning off Flash");
            flashOn = false;
            TextView flashButton = (TextView) findViewById(R.id.toggle);
            flashButton.setText("TURN ON FLASH");
            mOpenCvCameraView.setFlashOff();
        } else {
            Log.d(TAG, "Turning on Flash");
            flashOn = true;
            TextView flashButton = (TextView) findViewById(R.id.toggle);
            flashButton.setText("TURN OFF FLASH");
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
           //Log.d(TAG, values.get(i) + "");
           mean += values.get(i);

       }
        mean /= values.size();
        return mean;
    }


    private void updateText(final TextView v, final String text) {
        if (v != null) {
            new Thread() {
                public void run() {
                    VisionActivity.this.runOnUiThread(new Runnable() {
                        public void run() {
                            v.setText(text);
                        }
                    });
                }
            }.start();
        }
    }

    private void setFFTMessage(String message) {
        TextView fftStatus = (TextView) findViewById(R.id.fftStatus);
        updateText(fftStatus, message);
    }

    private void setHeartRate(String message) {
        TextView heartRateVal = (TextView) findViewById(R.id.heartRateVal);
        updateText(heartRateVal, message);
    }


    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        Mat currentFrame = inputFrame.rgba();

        long now = System.currentTimeMillis();

        double x =  Core.sumElems(currentFrame).val[0];
        double redMean = x / (currentFrame.rows() * currentFrame.cols());

        // TODO for now, assume that each sample is exactly 100ms apart
/*
        String debugString;

        // collect color values of every pixel within window
        int pixelWindowSize = 8;
        Mat pixelWindow = currentFrame.submat(currentFrame.rows()/2 - pixelWindowSize/2,
                            currentFrame.rows()/2 + pixelWindowSize/2,
                            currentFrame.cols()/2 - pixelWindowSize/2,
                            currentFrame.cols()/2 + pixelWindowSize/2);
        //Log.d(TAG, "window size " + pixelWindow.rows() +", " +
//                pixelWindow.cols());



        // put it in the graph
        ArrayList<Double> reds = new ArrayList<Double>();


        for(int i = 0; i < pixelWindow.rows(); i++) {
            for (int j = 0; j < pixelWindow.cols(); j++) {
                double[] pixel = pixelWindow.get(i, j);
                reds.add(pixel[0]);

            }
        }

        // get the average
        double redMean = calculateMean(reds);
*/


        double[] pixel = currentFrame.get(currentFrame.rows() / 2, currentFrame.cols() / 2);
        //debugString = "R " + pixel[0] + "; G " + pixel[1] + "; B " + pixel[3];
        //debugString = "R " + redMean; // + "; G " + greenMean + "; B " + blueMean;
        // Log.d(TAG, debugString);

        long timeElapsed = System.currentTimeMillis() - initTs;
        //Log.d(TAG, "Time Elapsed: " + timeElapsed);

        // display buffer
        if(redValues.size() > BUFFER_SIZE) {
            // FIXME is this right?
            redValues.remove(0);
            rHistorySeries.removeFirst();
            tHistorySeries.removeFirst();

        }

        if(redMean > windowMax) {
            windowMax = redMean;
            Log.d(TAG, "New Max: " + windowMax);
            if(windowMax - windowMin > TOLERANCE) {
                // reset window
                timeWindow.clear();
                timeWindowTimes.clear();
                windowMin = 256;
                windowMax = -1;
                Log.d(TAG, "TOLERANCE exceeded, resetting window");
                setFFTMessage("waiting for steady signal...");
            }

        }

        if (redMean < windowMin) {
            windowMin = redMean;
            Log.d(TAG, "New Min: " + windowMin);
            if(windowMax - windowMin > TOLERANCE) {
                // reset window
                timeWindow.clear();
                timeWindowTimes.clear();
                windowMin = 256;
                windowMax = -1;
                Log.d(TAG, "TOLERANCE exceeded, resetting window");
                setFFTMessage("waiting for steady signal...");
            }
        }

        timeWindow.add(redMean);
        timeWindowTimes.add(now);


        // calculation window
        if (timeWindow.size() >= TIME_WINDOW_SIZE) {

            setFFTMessage("calculating using FFT...");

            if(! CALCULATING_FFT) {
                calculateFFT();
            }

            // drop first value
            timeWindow.remove(0);
            timeWindowTimes.remove(0);

        }


        redValues.add(redMean);
        rHistorySeries.addLast(null, redMean);
        tHistorySeries.addLast(null, now);


        prevTs = now;



        //CANNY Edge Detection
        //Imgproc.cvtColor(currentFrame, currentFrame, Imgproc.COLOR_RGBA2GRAY);
        //Imgproc.Canny(currentFrame,currentFrame,cannyThreshold/3,cannyThreshold );



        return currentFrame;
    }

    private void calculateFFT() {

        CALCULATING_FFT = true;


        // magnitude calculations
        int N = timeWindow.size();
        double sum = 0.0;
        for(int i = 0; i < N; i++) {
            sum+= timeWindow.get(i);
        }
        double mean = sum / (double) N;

        Log.d(TAG, "there are " + N + " elements in time window");
        double[] reals = new double[N];
        double[] imaginaries = new double[N];

        for(int i = 0; i< N; i++) {
            reals[i] = timeWindow.get(i) - mean; // don't forget to detrend!!!
            imaginaries[i] = 0;
        }

        double[] fourier = FFTbase.fft(reals, imaginaries, true);
        Log.d(TAG, "Performed FFT, maybe");
        Log.d(TAG, "" + fourier);

        // what is the sample time?
        long totalWindowTime = timeWindowTimes.get(N-1) - timeWindowTimes.get(0);
        double meanSampleTimeS = totalWindowTime / (double) N / (double) 1000;
        double meanSampleTimeMin = meanSampleTimeS / 60;
        Log.d(TAG, "mean sample time in seconds: " + meanSampleTimeS);
        Log.d(TAG, "mean sample time in minutes: " + meanSampleTimeMin);

        freqSeries.clear();

        for(int i = 0; i < N; i++) {
            double mag = 2.0 / N * Math.abs(fourier[i]);
            double freqPerMs= i / (double) meanSampleTimeS;
            double freqPerSecond = freqPerMs * 1000;
            double freqPerMinute = freqPerSecond * 60;
           // freqSeries.addLast(freqPerMinute, mag);
            freqSeries.addLast(i, mag);
        }

        //int MAX_FREQ = (int) (N / ((double) meanSampleTimeS) *1000 * 60);



        int MIN_HEART_RATE = 40;
        int INDEX_THRESHOLD = (int) (MIN_HEART_RATE * 2 * meanSampleTimeMin * 256);
        Log.d(TAG, "won't count frequencies at index higher than " + INDEX_THRESHOLD);


        double lpfAmplitude = 0; // amplitude for low frequencies...
        for(int i = 0; i < INDEX_THRESHOLD; i++) {
            if(fourier[i] > lpfAmplitude) {
                lpfAmplitude = fourier[i];
            }
        }

        double maxAmplitude = 0;
        int maxIndex = -1;

        for(int i = INDEX_THRESHOLD; i < N; i++) {
            if (fourier[i] > maxAmplitude) {
                maxAmplitude = fourier[i];
                maxIndex = i;
            }
        }

        // if the amplitude is strong enough relative to low filter signal, update heart rate
        Log.d(TAG, "comparing " + maxAmplitude + " to " + lpfAmplitude);

        if(maxAmplitude * LOW_PASS_FILTER_COMPARISON > lpfAmplitude) {
            double heartRate = maxIndex / (2 * meanSampleTimeMin * 256);

            heartRateWindow.add(heartRate);

            if(heartRateWindow.size() > HR_MOVING_AVERAGE_SIZE) {
                heartRateWindow.remove(0);

                int hrMean = (int) calculateMean(heartRateWindow);
                setHeartRate("heart rate = " + hrMean);
            }


        } else {
            setHeartRate("signal still noisy...");
            heartRateWindow.clear();
        }

        /*
        testing display with random noise
        int DATA_SIZE = 256;
        for (int i = 0; i < DATA_SIZE; i++) {
            freqSeries.addLast(i, Math.random());
        }
*/

        CALCULATING_FFT = false;
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