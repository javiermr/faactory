package com.develop.safepath;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.content.res.AssetManager;
import android.os.Environment;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ar.core.PointCloud;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.AugmentedImageDatabase;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.develop.augmentedimage.sceneform.AugmentedImageNode;
import com.develop.common.helpers.CameraPermissionHelper;
import com.develop.common.helpers.FullScreenHelper;
import com.develop.common.helpers.SnackbarHelper;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.DpToMetersViewSizer;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {


   // private ModelRenderable andyRenderable;



    class PointCluster {
        int id;
        float distance;
        float[] center;
        List<PointCluster> pointClusters;
        public PointCluster(int id, float[] center)
        {
            // This keyword refers to current object itself
            this.id = id;
            this.center = center;
            pointClusters= new ArrayList<PointCluster>();
        }

        void setDistance(float distance)
        {
            this.distance = distance;

        }

        void setPoint(PointCluster pointCluster)
        {
            this.pointClusters.add(pointCluster);
        }

        List<PointCluster> getPoints()
        {
            return this.pointClusters;
        }

    float getDistance()
    {

        return this.distance;
    }
        float[] getCenter()
        {

            return this.center;
        }



    }
    class SortbyPointCluster implements Comparator<PointCluster> {
        @Override
        public int compare(PointCluster a, PointCluster b) {
            Float change1 = Float.valueOf(a.getDistance());
            Float change2 = Float.valueOf(b.getDistance());

            return change1.compareTo(change2);
        }
    }



    private static final String TAG = MainActivity.class.getSimpleName();

    // Rendering. The Renderers are created here, and initialized when the GL surface is created.
    private ArSceneView arSceneView;

    private boolean installRequested;

    private Session session;
    private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();

    private boolean shouldConfigureSession = false;
    private TextView tv,tva,tvv;
    private AugmentedImageNode node=null,node2=null;

    private ArFragment arFragment;
    private float distanceMeters=0.0f;

    private  Vector3 endPose,startPose;

    List<List<Integer>> path_points;

    private static final String MODEL_PATH = ".tflite";

/*

{'89': 1, '110': 2, '131': 3, '132': 4, '133': 5, '134': 6, '135': 7, '136': 8, '137': 9, '138': 10, '159': 11, '112': 12, '93': 13, '114': 14, '117': 15, '98': 16, '119': 17, '139': 18, '95': 19, '76': 20, '97': 21, '118': 22, '111': 23, '92': 24, '113': 25, '116': 26, '90': 27, '91': 28, '94': 29, '115': 30}

 */

    private static final boolean QUANT = false;
    private static final int INPUT_SIZE = 256;

    private Classifier classifier, classifier_path;
    private Executor executor = Executors.newSingleThreadExecutor();
    private Node nodeForLine;
    float[][] result;

    private static ModelRenderable model;


    public class PositionPath{
        int[] path;
        float[] points;

    }


    AnchorNode anchorNode4=null,anchorNode3=null;
int FPS=0;
List<PositionPath> list_points;

List<PointCluster> clusters;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        arSceneView = findViewById(R.id.surfaceview);

        tv = (TextView)findViewById(R.id.textView);
        tvv = (TextView)findViewById(R.id.textView3);


        arSceneView.getPlaneRenderer().setEnabled(false);




        list_points= new ArrayList<PositionPath>();

        clusters= new ArrayList<PointCluster>();



        path_points = new ArrayList<List<Integer>>();


        try {
            InputStream inputStream = getAssets().open("paths.txt");
            int size = inputStream.available();
            byte[] buffer = new byte[size];
            inputStream.read(buffer);
            String string = new String(buffer);
            for(String s:string.split("\n")) {
                List<Integer> temp=new ArrayList<Integer>();

                for (String num_ : s.split(",")) {
                    temp.add(Integer.valueOf(num_));
                }
                path_points.add(temp);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }


        try {
            InputStream inputStream = getAssets().open("cluster.txt");
            int size = inputStream.available();
            byte[] buffer = new byte[size];
            inputStream.read(buffer);
            String string = new String(buffer);
            //Log.d("lista--------",""+string.split("\n").length);
            int i=0;
            for(String s:string.split("\n")) {
                String[] text = s.split(",");
                PositionPath p = new PositionPath();
                Log.d("", "" + s);

                float[] point = new float[3];
                point[0] = Float.valueOf(text[0]);
                point[1] = Float.valueOf(text[1]);
                point[2] = Float.valueOf(text[2]);

                PointCluster c = new PointCluster(i,point);
                i=i+1;
                clusters.add(c);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }


        try {
            InputStream inputStream = getAssets().open("clusterpoints.txt");
            int size = inputStream.available();
            byte[] buffer = new byte[size];
            inputStream.read(buffer);
            String string = new String(buffer);
            //Log.d("lista--------",""+string.split("\n").length);

            for(String s:string.split("\n")) {
                String[] text = s.split(",");
                PositionPath p = new PositionPath();
                Log.d("", "" + s);

                float[] point = new float[3];

                int select = Integer.valueOf(text[0]);

                point[0] = Float.valueOf(text[1]);
                point[1] = Float.valueOf(text[2]);
                point[2] = Float.valueOf(text[3]);

                int i = Integer.valueOf(text[4]);

                PointCluster c = new PointCluster(i,point);
                clusters.get(select).setPoint(c);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }


/*
        ModelRenderable.builder()
                .setSource(this, R.raw.andy)
                .build()
                .thenAccept(renderable -> model = renderable)
                .exceptionally(
                        throwable -> {
                            Toast toast =
                                    Toast.makeText(this, "Unable to load andy renderable", Toast.LENGTH_LONG);
                            toast.setGravity(Gravity.CENTER, 0, 0);
                            toast.show();
                            return null;
                        });
*/



        installRequested = false;

        initializeSceneView();

        result=new float[1][10];
        result[0][0] = -1;
        result[0][1] = -1;
        result[0][2] = -1;
        result[0][3] = -1;

       /* MaterialFactory.makeTransparentWithColor(this, new Color(0.0f,1.0f,1.0f,0.5f))
                .thenAccept(
                        material -> model = ShapeFactory.makeSphere(
                                0.005f,
                                Vector3.zero(), material)
                );
*/

        MaterialFactory.makeTransparentWithColor(this, new Color(1.0f,1.0f,0.0f,0.5f))
                .thenAccept(
                        material -> model = ShapeFactory.makeCylinder(
                                0.01f, 0.001f,
                                Vector3.zero(), material)
                );











        initTensorFlowAndLoadModel();


        Runnable runnable = () -> {

            for(int i=0;i<10000;i++) {
                try {
                    Thread.sleep(1000);
                    Log.d("FPS", String.valueOf(counter));
                    FPS=counter;
                    counter = 0;

                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();

                }
            }
        };
        Thread t2 = new Thread(runnable);
        t2.start();


        try {
            InputStream inputStream = getAssets().open("points.txt");
            int size = inputStream.available();
            byte[] buffer = new byte[size];
            inputStream.read(buffer);
            String string = new String(buffer);
            //Log.d("lista--------",""+string.split("\n").length);
            for(String s:string.split("\n")) {
                String[] text = s.split(",");
                PositionPath p = new PositionPath();
                Log.d("", "" + s);

                float[] point = new float[3];
                point[0] = Float.valueOf(text[0]);
                point[1] = Float.valueOf(text[1]);
                point[2] = Float.valueOf(text[2]);

                p.points = point;
                list_points.add(p);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }





    }

    private void initTensorFlowAndLoadModel() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    classifier = TensorFlowImageClassifier.create(
                            getAssets(),
                            MODEL_PATH,
                            INPUT_SIZE,
                            QUANT);
                } catch (final Exception e) {
                    throw new RuntimeException("Error initializing TensorFlow!", e);
                }


            }
        });
    }




    @Override
    protected void onResume() {
        super.onResume();

        if (session == null) {
            Exception exception = null;
            String message = null;
            try {
                switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    case INSTALL_REQUESTED:
                        installRequested = true;
                        return;
                    case INSTALLED:
                        break;
                }

                // ARCore requires camera permissions to operate. If we did not yet obtain runtime
                // permission on Android M and above, now is a good time to ask the user for it.
                if (!CameraPermissionHelper.hasCameraPermission(this)) {
                    CameraPermissionHelper.requestCameraPermission(this);
                    return;
                }

                session = new Session(/* context = */ this);





            } catch (UnavailableArcoreNotInstalledException
                    | UnavailableUserDeclinedInstallationException e) {
                message = "Please install ARCore";
                exception = e;
            } catch (UnavailableApkTooOldException e) {
                message = "Please update ARCore";
                exception = e;
            } catch (UnavailableSdkTooOldException e) {
                message = "Please update this app";
                exception = e;
            } catch (Exception e) {
                message = "This device does not support AR";
                exception = e;
            }

            if (message != null) {
                messageSnackbarHelper.showError(this, message);
                Log.e(TAG, "Exception creating session", exception);
                return;
            }

            shouldConfigureSession = true;
        }

        if (shouldConfigureSession) {
            configureSession();
            shouldConfigureSession = false;
            arSceneView.setupSession(session);
        }

        // Note that order matters - see the note in onPause(), the reverse applies here.
        try {
            session.resume();
            arSceneView.resume();
        } catch (CameraNotAvailableException e) {
            // In some cases (such as another camera app launching) the camera may be given to
            // a different app instead. Handle this properly by showing a message and recreate the
            // session at the next iteration.
            messageSnackbarHelper.showError(this, "Camera not available. Please restart the app.");
            session = null;
            return;
        }




    }

    @Override
    public void onPause() {
        super.onPause();
        if (session != null) {
            // Note that the order matters - GLSurfaceView is paused first so that it does not try
            // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
            // still call session.update() and get a SessionPausedException.
            arSceneView.pause();
            session.pause();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(
                    this, "Camera permissions are needed to run this application", Toast.LENGTH_LONG)
                    .show();
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this);
            }
            finish();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
    }

    private void initializeSceneView() {
        arSceneView.getScene().addOnUpdateListener(this::onUpdateFrame);


    }

    int counter = 0;
boolean isFlag = true;
    int P=9;
    int num_img = 0;
    long lastPointCloudTimestamp = 0;
float[] lastPoint={500,500,500};
float[] currentPoint={0,0,0};
    Node c=null;
    List<Node> listElements;
    private void onUpdateFrame(FrameTime frameTime) {
        Frame frame = arSceneView.getArFrame();

        try {



tvv.setText("FPS: "+String.valueOf(FPS));

            Image image = frame.acquireCameraImage();
            byte[] nv21;


            PointCloud pointCloud = frame.acquirePointCloud();
            IntBuffer pointCloudIds = pointCloud.getIds();
            int[] pointCloudIdsArray = new int[pointCloudIds.limit()];
            pointCloudIds.get(pointCloudIdsArray);

            Log.i(TAG, "onDrawFrame: Point Cloud Id Array Length " +
                    pointCloudIdsArray.length);

            FloatBuffer pointCloudData = pointCloud.getPoints();

            float[] pointCloudArray = new float[pointCloudData.limit()];
            pointCloudData.get(pointCloudArray);

            Log.i(TAG, "onDrawFrame: Point Cloud Data Array Length " + pointCloudArray.length);

            long PointCloudTimeStamp = pointCloud.getTimestamp();

            if (pointCloudIdsArray.length > 0) {
                float x =0f;
                float y =0f;
                float z=0f;
                for (int i = 0; i < pointCloudIdsArray.length; i++) {

                    /*Log.i("TAG",Long.toString(PointCloudTimeStamp) + ";" +
                            Integer.toString(pointCloudIdsArray[i]) + ";" +
                            Float.toString(pointCloudArray[i * 4]) + ";" +
                            Float.toString(pointCloudArray[i * 4 + 1]) + ";" +
                            Float.toString(pointCloudArray[i * 4 + 2]) + ";" +
                            Float.toString(pointCloudArray[i * 4 + 3]) + "\n");*/
                    x = pointCloudArray[i * 4] +x ;
                    y = pointCloudArray[i * 4+1] +y ;
                    z = pointCloudArray[i * 4+2] +z ;

                }
                x = x / pointCloudIdsArray.length;
                y = y / pointCloudIdsArray.length;
                z = z / pointCloudIdsArray.length;

                //Log.i("TAG",Float.toString(x)+" "+Float.toString(y)+" "+Float.toString(z));
                currentPoint[0] = x;
                currentPoint[1] = y;
                currentPoint[2] = z;

            }
            float dist = distance(lastPoint,currentPoint);
            Log.i("dist",Float.toString(dist));

            lastPoint[0]=currentPoint[0] ;
            lastPoint[1]=currentPoint[1] ;
            lastPoint[2]=currentPoint[2] ;

            // Get the three planes.

                ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
                ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
                ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

                int ySize = yBuffer.remaining();
                int uSize = uBuffer.remaining();
                int vSize = vBuffer.remaining();


                nv21 = new byte[ySize + uSize + vSize];

                //U and V are swapped
                yBuffer.get(nv21, 0, ySize);
                vBuffer.get(nv21, ySize, vSize);
                uBuffer.get(nv21, ySize + vSize, uSize);

                int width = image.getWidth();
                int height = image.getHeight();

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
                yuv.compressToJpeg(new Rect(0, 0, width, height), 100, out);
                byte[] byteArray = out.toByteArray();


                Bitmap bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);


                int widthold = bitmap.getWidth();
                int heightold = bitmap.getHeight();
                float scaleWidth = ((float) 256) / widthold;
                float scaleHeight = ((float) 256) / heightold;
                // CREATE A MATRIX FOR THE MANIPULATION
                Matrix matrix = new Matrix();
                // RESIZE THE BIT MAP
                matrix.postScale(scaleWidth, scaleHeight);
                Bitmap resizedBitmap = Bitmap.createBitmap(
                        bitmap, 0, 0, width, height, matrix, false);
                num_img = num_img + 1;
                if (num_img > 200) {
                    num_img = 0;
                }
                bitmap.recycle();

                counter = counter + 1;

            if(dist>0.2) {
                result = classifier.recognizeImg(resizedBitmap);


                for (int p_ = 0; p_ < clusters.size(); p_++) {

                    float[] res = new float[3];
                    res[0] = result[0][0];
                    res[1] = result[0][1];
                    res[2] = result[0][2];

                    float d = distance(res, clusters.get(p_).getCenter());

                    clusters.get(p_).setDistance(d);
                }
                clusters.sort(new SortbyPointCluster());

                for (int p_ = 0; p_ < clusters.get(0).getPoints().size(); p_++) {

                    float[] res = new float[3];
                    res[0] = result[0][0];
                    res[1] = result[0][1];
                    res[2] = result[0][2];

                    Log.d("res", "" + res[0] + " " + res[1] + " " + res[2]);

                    float d = distance(res, clusters.get(0).getPoints().get(p_).getCenter());

                    clusters.get(0).getPoints().get(p_).setDistance(d);

                }
                clusters.get(0).getPoints().sort(new SortbyPointCluster());


                if (isFlag) {
                    Log.d("dist2", "" + arSceneView.getScene().getChildren().size());

                    listElements = new ArrayList<>();

                    for (int i_local = 0; i_local < P; i_local++) {
                        Node n1 = new Node();
                        n1.setRenderable(model);
                        n1.setName("node " + i_local);

                        n1.setLocalRotation(new
                                Quaternion(new Vector3(90,90,90)));

                        Vector3 v = arSceneView.getScene().getCamera().getWorldPosition();

                        n1.setWorldPosition(new Vector3(v.x, v.y, v.z + (i_local * -.020f)));

                        arSceneView.getScene().addChild(n1);

                        listElements.add(n1);
/*
                        ViewRenderable.builder().setSizer(new DpToMetersViewSizer(10240 / 2))
                                .setView(arSceneView.getContext(), R.layout.controls)
                                .build()
                                .thenAccept(viewVenerable -> addNodeToScene(n1, viewVenerable, n1.getName()))
                                .exceptionally(throwable -> {
                                            Toast.makeText(arSceneView.getContext(), "Error:" + throwable.getMessage(), Toast.LENGTH_LONG).show();
                                            return null;
                                        }

                                );*/


                    }
                    isFlag = false;






                    for (int i_local = 0; i_local < listElements.size(); i_local++) {
                        //drawLineazul(listElements.get(i_local).getWorldPosition(), listElements.get(i_local + 1).getWorldPosition(), arSceneView.getScene().findByName("node " + i_local));
                    }


                }


                Vector3 v = arSceneView.getScene().getCamera().getWorldPosition();



                // listElements.get(0).setWorldPosition(v);

                int t_path = clusters.get(0).pointClusters.get(0).id;

                List<Integer> path1 = path_points.get(t_path);



                //-------0-------
                Anchor newMarkAnchor0 = session.createAnchor(
                        frame.getCamera().getPose()
                                .compose(Pose.makeTranslation((0f), (0f), (0.0f)))
                                .extractTranslation());
                AnchorNode addedAnchorNode0 = new AnchorNode(newMarkAnchor0);
                addedAnchorNode0.setName("prueba");
                addedAnchorNode0.setRenderable(model);
                arSceneView.getScene().addChild(addedAnchorNode0);
                c =arSceneView.getScene().findByName("prueba");
                listElements.get(0 ).setWorldPosition(c.getWorldPosition());
                arSceneView.getScene().removeChild(c);
                //-------0-------


                for (int p_local = 0; p_local <listElements.size()-1; p_local++) {


                    int i_ = path1.get(p_local);
                    float[] punto = list_points.get(i_).points;


                    Anchor newMarkAnchor = session.createAnchor(
                            frame.getCamera().getPose()
                                    .compose(Pose.makeTranslation(((punto[1]- 0.5f)/5f), (punto[0]/5f), (punto[2] / 1.0f)))
                                    .extractTranslation());



                    AnchorNode addedAnchorNode = new AnchorNode(newMarkAnchor);
                    addedAnchorNode.setName("prueba"+p_local);

                    addedAnchorNode.setRenderable(model);
                    arSceneView.getScene().addChild(addedAnchorNode);

                    c =arSceneView.getScene().findByName("prueba"+p_local);

                    listElements.get(p_local +1).setWorldPosition(c.getWorldPosition());

                    arSceneView.getScene().removeChild(c);



                }
/*
int np=0;
                for (Plane plane : session.getAllTrackables(Plane.class)) {
                    if (plane.getType() == com.google.ar.core.Plane.Type.HORIZONTAL_UPWARD_FACING && plane.getTrackingState() == TrackingState.TRACKING)
                    {
                        Pose p = plane.getCenterPose();

                        Log.d("planes "+np,""+p.tx()+" "+p.ty()+" "+p.tz());




                        Anchor newMarkAnchor = session.createAnchor(
                                frame.getCamera().getPose()
                                        .compose(Pose.makeTranslation(p.tx(), p.ty(),p.tz())
                                        .extractTranslation()));



                        AnchorNode addedAnchorNode = new AnchorNode(newMarkAnchor);
                        addedAnchorNode.setName("prueba"+np);

                        addedAnchorNode.setRenderable(model);
                        arSceneView.getScene().addChild(addedAnchorNode);

                        c =arSceneView.getScene().findByName("prueba"+np);

                        listElements.get(np +1).setWorldPosition(c.getWorldPosition());

                        arSceneView.getScene().removeChild(c);
                        np=np+1;


                    }
                }
*/













/*
                for (int p_local = 0; p_local < listElements.size() -1; p_local++) {


                    int i_ = path1.get(p_local);
                    float[] punto = list_points.get(i_).points;


                    listElements.get(p_local + 1).setWorldPosition(new Vector3(v.x + (punto[0] / 2f), v.y + ((punto[1] - 0.5f) / 2.0f), v.z + (punto[2] / 1.0f)));



                }*/



             /*   Anchor newMarkAnchor = session.createAnchor(
                        frame.getCamera().getPose()
                                .compose(Pose.makeTranslation( listElements.get(0).getWorldPosition().x, listElements.get(0).getWorldPosition().y, listElements.get(0).getWorldPosition().z))
                                .extractTranslation());*/

/*


               c =arSceneView.getScene().findByName("prueba");
                if(c==null) {
                    Anchor newMarkAnchor = session.createAnchor(
                            frame.getCamera().getPose()
                                    .compose(Pose.makeTranslation( 0,0,-1.0f))
                                    .extractTranslation());
                    //arSceneView.getScene().removeChild(arSceneView.getScene().findByName(listElements.get(p_local).getName()));

                    AnchorNode addedAnchorNode = new AnchorNode(newMarkAnchor);
                    addedAnchorNode.setName("prueba");

                    addedAnchorNode.setRenderable(model);
                    addedAnchorNode.setParent(arSceneView.getScene());
                }
                else{
                    arSceneView.getScene().removeChild(c);
                    Anchor newMarkAnchor = session.createAnchor(
                            frame.getCamera().getPose()
                                    .compose(Pose.makeTranslation( 0,0,-1.0f))
                                    .extractTranslation());
                    //arSceneView.getScene().removeChild(arSceneView.getScene().findByName(listElements.get(p_local).getName()));

                    AnchorNode addedAnchorNode = new AnchorNode(newMarkAnchor);
                    addedAnchorNode.setName("prueba");

                    addedAnchorNode.setRenderable(model);
                    addedAnchorNode.setParent(arSceneView.getScene());
                }
*/

                for (int j_local = 0; j_local < listElements.size(); j_local++) {
                    for (Node i : listElements.get(j_local).getChildren())
                        if (i.getName().equals("line")) {

                            Vector3 dst1 = listElements.get(j_local).getWorldPosition();
                            Vector3 dst2 = listElements.get(j_local + 1).getWorldPosition();


                            final Vector3 difference = Vector3.subtract(dst1, dst2);
                            final Vector3 directionFromTopToBottom = difference.normalized();
                            final Quaternion rotationFromAToB =
                                    Quaternion.lookRotation(directionFromTopToBottom, Vector3.up());

                            MaterialFactory.makeTransparentWithColor(getApplicationContext(), new Color(0.0f, 1.0f, 0.0f, 0.5f))
                                    .thenAccept(
                                            material -> {

                                                ModelRenderable model = ShapeFactory.makeCube(
                                                        new Vector3(0.0050f, 0.0050f, difference.length()),
                                                        Vector3.zero(), material);

                                                i.setRenderable(model);
                                                i.setWorldPosition(Vector3.add(dst1, dst2).scaled(.5f));
                                                i.setWorldRotation(rotationFromAToB);
                                            }
                                    );

                        }

                }
            }
            /*else{
int np=0;
                for (Plane plane : session.getAllTrackables(Plane.class)) {
                    if (plane.getType() == com.google.ar.core.Plane.Type.HORIZONTAL_UPWARD_FACING && plane.getTrackingState() == TrackingState.TRACKING)
                    {
                        Pose p = plane.getCenterPose();
                        Log.d("planes "+np,""+p.tx()+" "+p.ty()+" "+p.tz());

np=np+1;
                    }
                }


            }
*/



                frame = null;
            resizedBitmap.recycle();


            image.close();

        }catch(Exception e)
        {}










    }

    private float distance(float[] p1, float[] p2)
    {

        return (float)Math.sqrt(Math.pow(p1[0] - p2[0],2) + Math.pow(p1[1] - p2[1],2) + Math.pow(p1[2] - p2[2],2));
    }



    public static File savebitmap(Bitmap bmp, int num) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
        File f = new File(Environment.getExternalStorageDirectory()
                + File.separator + "Pictures/testimage"+num+".jpg");
        f.createNewFile();
        FileOutputStream fo = new FileOutputStream(f);
        fo.write(bytes.toByteArray());
        fo.close();
        bmp.recycle();
        return f;
    }

    public static Bitmap getBitmapFromAsset(Context context, String filePath) {
        AssetManager assetManager = context.getAssets();

        InputStream istr;
        Bitmap bitmap = null;
        try {
            istr = assetManager.open(filePath);
            bitmap = BitmapFactory.decodeStream(istr);
        } catch (IOException e) {
            // handle exception
        }

        return bitmap;
    }

    private void addNodeToScene( Node node, Renderable renderable, String s) {
        //AnchorNode anchorNode = new AnchorNode(anchor);

        Node sunVisual = new Node();
        /*sunVisual.setParent(anchorNode);
        sunVisual.setRenderable(renderable); // View
        sunVisual.setLocalScale(new Vector3(0.5f, 0.5f, 0.5f));
*/

        //sunVisual.setWorldPosition(new Vector3(node.getWorldPosition().x,node.getWorldPosition().y,node.getWorldPosition().z));
        sunVisual.setRenderable(renderable);
        sunVisual.setParent(node);
        sunVisual.setLocalPosition(new Vector3(0.0f,0.01f,0.0f));
        ViewRenderable r = (ViewRenderable)sunVisual.getRenderable();
        View v = r.getView();
        tva= v.findViewById(R.id.textView2);
        //arFragment.getScene().addChild(anchorNode);
        //node.select();

        tva.setText( s);


    }

    private void configureSession() {
        Config config = new Config(session);
        /*if (!setupAugmentedImageDb(config)) {
            messageSnackbarHelper.showError(this, "Could not setup augmented image database");
        }*/
        config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
        session.configure(config);
    }

    private boolean setupAugmentedImageDb(Config config) {
        AugmentedImageDatabase augmentedImageDatabase;

        Bitmap augmentedImageBitmap = loadAugmentedImage("Robot-1.jpg");
        if (augmentedImageBitmap == null) {
            return false;
        }

        Bitmap augmentedImageBitmap2 = loadAugmentedImage("Robot-1.jpg");
        if (augmentedImageBitmap2 == null) {
            return false;
        }

        augmentedImageDatabase = new AugmentedImageDatabase(session);
        augmentedImageDatabase.addImage("verde", augmentedImageBitmap,0.125f);
        augmentedImageDatabase.addImage("morado", augmentedImageBitmap2,0.125f);


        config.setAugmentedImageDatabase(augmentedImageDatabase);



        return true;
    }



    private Bitmap loadAugmentedImage(String file) {
        try (InputStream is = getAssets().open(file)) {
            return BitmapFactory.decodeStream(is);
        } catch (IOException e) {
            Log.e(TAG, "IO exception loading augmented image bitmap.", e);
        }
        return null;
    }

    private void drawLineazul(Vector3 point1, Vector3 point2, Node n) {
        //Draw a line between two AnchorNodes (adapted from https://stackoverflow.com/a/52816504/334402)

        Node nodeForLine= new Node();
        nodeForLine.setName("line");
        //First, find the vector extending between the two points and define a look rotation
        //in terms of this Vector.
        final Vector3 difference = Vector3.subtract(point1, point2);
        final Vector3 directionFromTopToBottom = difference.normalized();
        final Quaternion rotationFromAToB =
                Quaternion.lookRotation(directionFromTopToBottom, Vector3.up());
        MaterialFactory.makeTransparentWithColor(getApplicationContext(), new Color(0.0f, 0.0f, 1.0f,0.5f))
                .thenAccept(
                        material -> {
                            /* Then, create a rectangular prism, using ShapeFactory.makeCube() and use the difference vector
                                   to extend to the necessary length.  */
                            ModelRenderable model = ShapeFactory.makeCube(
                                    new Vector3(0.1f, 0.1f, difference.length()),
                                    Vector3.zero(), material);
                            /* Last, set the world rotation of the node to the rotation calculated earlier and set the world position to
                                   the midpoint between the given points . */

                            nodeForLine.setParent(n);


                            nodeForLine.setRenderable(model);
                            nodeForLine.setWorldPosition(Vector3.add(point1, point2).scaled(.5f));
                            nodeForLine.setWorldRotation(rotationFromAToB);
                        }
                );

    }




}
