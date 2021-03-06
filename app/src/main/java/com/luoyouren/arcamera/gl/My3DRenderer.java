package com.luoyouren.arcamera.gl;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;

import com.luoyouren.arcamera.model.DynamicPoint;
import com.luoyouren.arcamera.model.Ornament;
import com.luoyouren.arcamera.rajawali.MyFragmentShader;
import com.luoyouren.arcamera.rajawali.MyVertexShader;
import com.luoyouren.arcamera.util.BitmapUtils;
import com.luoyouren.arcamera.util.MaterialFactory;
import com.luoyouren.arcamera.util.OrnamentFactory;

import org.rajawali3d.Geometry3D;
import org.rajawali3d.Object3D;
import org.rajawali3d.lights.DirectionalLight;
import org.rajawali3d.lights.PointLight;
import org.rajawali3d.loader.LoaderOBJ;
import org.rajawali3d.loader.ParsingException;
import org.rajawali3d.loader.awd.BlockSkybox;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.methods.DiffuseMethod;
import org.rajawali3d.materials.plugins.IMaterialPlugin;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.AlphaMapTexture;
import org.rajawali3d.materials.textures.StreamingTexture;
import org.rajawali3d.materials.textures.Texture;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Cube;
import org.rajawali3d.primitives.Plane;
import org.rajawali3d.primitives.Sphere;
import org.rajawali3d.renderer.Renderer;
import org.rajawali3d.util.ObjectColorPicker;
import org.rajawali3d.util.OnObjectPickedListener;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Simon on 2017/7/19.
 */

public class My3DRenderer extends Renderer implements OnObjectPickedListener, StreamingTexture.ISurfaceListener {
    private final static String TAG = My3DRenderer.class.getSimpleName();

    private Object3D mContainer;
    private List<Object3D> mObject3DList = new ArrayList<>();
    private DirectionalLight directionalLight;
    private PointLight pointLightLeft, pointLightMid, pointLightRight, pointLightUp;
    private Object3D mPickedObject;
    private Object3D mShaderPlane;
    private Material mCustomMaterial;
    private MyFragmentShader mMyFragmentShader;

    private Ornament mOrnamentModel;
    private boolean mIsNeedUpdateOrnament = false;
    private boolean mIsOrnamentVisible = true;
    private int mScreenW = 1;
    private int mScreenH = 1;
    // 根据肤色更改模型贴图的颜色
    private int mSkinColor = 0xffd4c9b5;

    private int mModelType = Ornament.MODEL_TYPE_NONE;
    // 用于静态3D模型
    private Vector3 mAccValues;
    private float mTransX = 0.0f;
    private float mTransY = 0.0f;
    private float mScale = 1.0f;

    // 用于人脸长方形
    private Rect mRect;

    // 用于动态3D模型
    private List<Geometry3D> mGeometry3DList = new ArrayList<>();
    private List<DynamicPoint> mPoints = new ArrayList<>();
    private boolean mIsChanging = false;
    // 用于ShaderMaterial模型
    private List<Material> mMaterialList = new ArrayList<>();
    private float mMaterialTime = 0;
    private ObjectColorPicker mPicker;

    // StreamingTexture
    private Surface mSurface;
    private View mStreamingView;
    private Handler mStreamingHandler;
    private StreamingTexture mStreamingTexture;
    private volatile boolean mShouldUpdateTexture;
    private final float[] mMatrix = new float[16];
    private boolean mIsStreamingViewMirror = false;

    private final Runnable mUpdateTexture = new Runnable() {
        public void run() {
            // -- Draw the view on the canvas
            if (mSurface != null && mStreamingTexture != null && mStreamingView != null) {
                try {
                    final Canvas canvas = mSurface.lockCanvas(null);
                    canvas.translate(mStreamingView.getScrollX(), -mStreamingView.getScrollY());
                    if (mIsStreamingViewMirror) {
                        // 镜像
                        canvas.scale(-1, 1, mStreamingView.getWidth() / 2, mStreamingView.getHeight() / 2);
                    }
                    mStreamingTexture.getSurfaceTexture().getTransformMatrix(mMatrix);
                    mStreamingView.draw(canvas);
                    mSurface.unlockCanvasAndPost(canvas);
                    // -- Indicates that the texture should be updated on the OpenGL thread.
                    mShouldUpdateTexture = true;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    };


    public My3DRenderer(Context context) {
        super(context);
        mAccValues = new Vector3();
    }

    public void setOrnamentModel(Ornament mOrnamentModel) {
        this.mOrnamentModel = mOrnamentModel;
    }

    public void setIsNeedUpdateOrnament(boolean mIsNeedUpdateOrnament) {
        this.mIsNeedUpdateOrnament = mIsNeedUpdateOrnament;
    }

    // 设置装饰品可见性
    public void setIsOrnamentVisible(boolean mIsOrnamentVisible) {
        this.mIsOrnamentVisible = mIsOrnamentVisible;
    }

    // 设置3D模型的转动角度
    public void setAccelerometerValues(float x, float y, float z) {
        mAccValues.setAll(x, y, z);
    }

    // 设置3D模型的平移
    public void setTransition(float x, float y, float z) {
        if (mModelType == Ornament.MODEL_TYPE_STATIC || mModelType == Ornament.MODEL_TYPE_SHADER) {
            mTransX = x;
            mTransY = y;
            setScale(z);
        }
    }

    // 人脸长方形的位置
    public void setPointsRect(Rect rect)
    {
        if (mModelType == Ornament.MODEL_TYPE_POINT)
        mRect = rect;
    }

    // 设置3D模型的缩放比例
    public void setScale(float scale) {
        mScale = scale;
    }

    public void setScreenW(int width) {
        mScreenW = width;
    }

    public void setScreenH(int height) {
        mScreenH = height;
    }

    public void setSkinColor(int mSkinColor) {
        this.mSkinColor = mSkinColor;
    }

    public void setStreamingView(View streamingView) {
        this.mStreamingView = streamingView;
    }

    public void setStreamingHandler(Handler streamingHandler) {
        this.mStreamingHandler = streamingHandler;
    }

    @Override
    protected void initScene() {

        // 方向光
        directionalLight = new DirectionalLight(0.0f, 0.0f, -1.0);
        directionalLight.setColor(1.0f, 1.0f, 1.0f);
        directionalLight.setPower(0.8f);
        getCurrentScene().addLight(directionalLight);

        float radiu = 12.0f;
        float power = 2.5f;
        // 三盏点光源
        pointLightLeft = new PointLight();
        pointLightLeft.setPosition(-radiu, 0.0f, 0.0f);
        pointLightLeft.setColor(1.0f, 1.0f, 1.0f);
        pointLightLeft.setPower(power);

        pointLightMid = new PointLight();
        pointLightMid.setPosition(0.0f, 0.0f, radiu);
        pointLightMid.setColor(1.0f, 1.0f, 1.0f);
        pointLightMid.setPower(power);

        pointLightRight = new PointLight();
        pointLightRight.setPosition(radiu, 0.0f, 0.0f);
        pointLightRight.setColor(1.0f, 1.0f, 1.0f);
        pointLightRight.setPower(power);

        pointLightUp = new PointLight();
        pointLightUp.setPosition(0.0f, radiu, 0.0f);
        pointLightUp.setColor(1.0f, 1.0f, 1.0f);
        pointLightUp.setPower(power);

//        Object3D lightContainer;

        getCurrentScene().addLight(pointLightLeft);
        getCurrentScene().addLight(pointLightMid);
        getCurrentScene().addLight(pointLightRight);
        getCurrentScene().addLight(pointLightUp);

        try {
            mContainer = new Object3D();
            getCurrentScene().addChild(mContainer);
//            getCurrentScene().getCamera().setZ(5.5);	//original
			getCurrentScene().getCamera().setZ(105.5);

        } catch (Exception e) {
            e.printStackTrace();
        }

        getCurrentScene().setBackgroundColor(0);
    }

    @Override
    protected void onRender(long ellapsedRealtime, double deltaTime) {
        super.onRender(ellapsedRealtime, deltaTime);

        if (mIsNeedUpdateOrnament) {
            mIsNeedUpdateOrnament = false;
            loadOrnament();
        }

        if (mModelType == Ornament.MODEL_TYPE_STATIC || mModelType == Ornament.MODEL_TYPE_SHADER) {
            if (mOrnamentModel != null) {
                /*
                private boolean enableRotation = true;
                private boolean enableTransition = true;
                private boolean enableScale = true;
                // 默认是 使能旋转 */
                if (mOrnamentModel.isEnableRotation()) {

//                    mContainer.setPosition(0.0f, 0.0f, 0.0f);

                    // 处理3D模型的旋转
                    mContainer.setRotation(mAccValues.x, mAccValues.y, mAccValues.z);

//                    if (mContainer.getChildAt(0) != null)
//                    {
//                        mContainer.getChildAt(0).setRotX(mAccValues.x / 2);   // roll 横滚角
//                        mContainer.getChildAt(0).setRotation(new Vector3(0, 1, 3), mAccValues.x / 2);
//                        mContainer.getChildAt(0).setRotY(mAccValues.y);     // yaw 偏航角
//                        mContainer.getChildAt(0).setRotZ(mAccValues.z);     // pitch 俯仰角
//                    }

//                    mContainer.setPosition(mTransX, mTransY, mScale);
                }

                if (mOrnamentModel.isEnableScale()) {
                    String modelName = mObject3DList.get(0).getName();
                    if((modelName != null) && (modelName.equals("ironManTop2")))
                    {
                        //钢铁侠先不进行缩放
                        Log.i(TAG, "123 initOrnamentParams");

                        // 处理3D模型的缩放
//                        mContainer.setScale(mScale);

                        // 通过移动Z轴来实现缩放
                        mContainer.setZ(mScale);
                    }
                    else
                    {
                        // 处理3D模型的缩放
                        mContainer.setScale(mScale);
                    }
                }

                if (mOrnamentModel.isEnableTransition()) {
                    // 处理3D模型的平移
//                    getCurrentCamera().setX(mTransX);
//                    getCurrentCamera().setY(mTransY);
                    //换一种方法移动3D模型
                    mContainer.setX(mTransX);
                    mContainer.setY(mTransY);
                }
            }

            if (mOrnamentModel != null && mOrnamentModel.getTimeStep() > 0 && mMaterialList != null) {
                for (int i = 0; i < mMaterialList.size(); i++) {
                    Material material = mMaterialList.get(i);
                    if (material != null) {
                        material.setTime(mMaterialTime);
                        mMaterialTime += mOrnamentModel.getTimeStep();
                        if (mMaterialTime > 1000) {
                            mMaterialTime = 0;
                        }
                    }
                }
            }

        } else if (mModelType == Ornament.MODEL_TYPE_DYNAMIC) {
            if (!mIsChanging && mPoints != null && mPoints.size() > 0) {
                mIsChanging = true; //正在渲染不允许修改点集

                //专门渲染面具
                try {  // FIXME
                    if (mGeometry3DList != null && mGeometry3DList.size() > 0) {

                        //mGeometry3DList是模型的点集，mPoints是人脸检测处理后的点集
                        Log.d(TAG, "MODEL_TYPE_DYNAMIC: "+ "mGeometry3DList.size() = " + mGeometry3DList.size() + "; mPoints.size() = "+ mPoints.size());
                        Log.d(TAG, "MODEL_TYPE_DYNAMIC: " + mGeometry3DList.get(0).getVertexBufferInfo().toString());
                        for (Geometry3D geometry3D : mGeometry3DList) {
                            FloatBuffer vertBuffer = geometry3D.getVertices();
                            Log.d(TAG, "MODEL_TYPE_DYNAMIC: vertBuffer.limit() = " + vertBuffer.limit());
                            for (int i = 0; i < mPoints.size(); i++) {
                                DynamicPoint point = mPoints.get(i);
                                changePoint(vertBuffer, point.getIndex(), point.getX(), point.getY(), point.getZ());
                            }
                            geometry3D.changeBufferData(geometry3D.getVertexBufferInfo(), vertBuffer, 0, vertBuffer.limit());
                        }
                    }

                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                }

                mIsChanging = false;
            }
        } else if (mModelType == Ornament.MODEL_TYPE_POINT) {
            //luoyouren: add
            if ((mOrnamentModel != null) && (mRect != null)) {
                List<Object3D> points = mOrnamentModel.getObject3DList();
                if (points.size() >= 4) {
                    points.get(0).setPosition(mRect.left, mRect.top, 0);
                    points.get(1).setPosition(mRect.right, mRect.top, 0);
                    points.get(2).setPosition(mRect.left, mRect.bottom, 0);
                    points.get(3).setPosition(mRect.right, mRect.bottom, 0);
                    points.get(4).setPosition(0, 0, 0);
                }
            }
        }

        // TODO
        if (mShaderPlane != null && mOrnamentModel != null && mMyFragmentShader != null && mCustomMaterial != null) {
            mMyFragmentShader.setScreenW(mScreenW);
            mMyFragmentShader.setScreenH(mScreenH);

            if (mMaterialTime == 0) {
                mMyFragmentShader.setFlag(1);
            }

            mMaterialTime += mOrnamentModel.getTimeStep();
            mCustomMaterial.setTime(mMaterialTime);

            if (mMaterialTime > mOrnamentModel.getTimePeriod()) {
                mMyFragmentShader.setFlag(0);
            }

            if (mMaterialTime > 1) {
                mMaterialTime = 0;
            }
        }

        // -- not a really accurate way of doing things but you get the point :)
        if (mSurface != null && mStreamingHandler != null && mFrameCount++ >= (mFrameRate * 0.25)) {
            mFrameCount = 0;
            mStreamingHandler.post(mUpdateTexture);
        }
        // -- update the texture because it is ready
        if (mShouldUpdateTexture) {
            if (mStreamingTexture != null) {
                mStreamingTexture.update();
            }
            mShouldUpdateTexture = false;
        }

        if (mPickedObject != null && mOrnamentModel != null && mObject3DList != null && mObject3DList.size() > 0) {
            Log.i("somebodyluo", "(mPickedObject != null && mOrnamentModel != null && mObject3DList != null && mObject3DList.size() > 0)");
            int index = mObject3DList.indexOf(mPickedObject);
            if (index >= 0) {
                List<Ornament.Model> modelList = mOrnamentModel.getModelList();
                if (modelList != null && modelList.size() > index) {
                    Ornament.Model model = modelList.get(index);
                    if (model != null && model.isNeedObjectPick()) {
                        boolean isPicked = model.isPicked();
                        Log.i("somebodyluo", "picking model: " + model.getName() + "; " + isPicked);
                        if (isPicked) {
                            // 头盔面罩打开
                            mPickedObject.setPosition(model.getAfterX(), model.getAfterY(), model.getAfterZ());
                            mPickedObject.setRotation(model.getAxisX(), model.getAxisY(), model.getAxisZ(),
                                    model.getAfterAngle());
                        } else {
                            // 头盔面罩合上
                            mPickedObject.setPosition(model.getBeforeX(), model.getBeforeY(), model.getBeforeZ());
                            mPickedObject.setRotation(model.getAxisX(), model.getAxisY(), model.getAxisZ(),
                                    model.getBeforeAngle());
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep, int xPixelOffset, int yPixelOffset) {
    }

    @Override
    public void onTouchEvent(MotionEvent event) {
    }

    @Override
    public void onObjectPicked(@NonNull Object3D object) {
        Log.i(TAG, "onObjectPicked: " + object.getName());
        mPickedObject = object;
        if (mOrnamentModel != null && mObject3DList != null && mObject3DList.size() > 0) {
            int index = mObject3DList.indexOf(object);
            if (index >= 0) {
                List<Ornament.Model> modelList = mOrnamentModel.getModelList();
                if (modelList != null && modelList.size() > index) {
                    Ornament.Model model = modelList.get(index);
                    if (model != null && model.isNeedObjectPick()) {
                        boolean isPicked = model.isPicked();
                        isPicked = !isPicked;
                        model.setPicked(isPicked);
                    }
                }
            }
        }
    }

    @Override
    public void onNoObjectPicked() {
    }

    @Override
    public void setSurface(Surface surface) {
        mSurface = surface;
        if (mStreamingTexture != null && mStreamingView != null) {
            mStreamingTexture.getSurfaceTexture().setDefaultBufferSize(mStreamingView.getWidth(), mStreamingView.getHeight());
        }
    }

    private void clearScene() {
        if (mObject3DList != null && mObject3DList.size() > 0) {
            for (int i = 0; i < mObject3DList.size(); i++) {
                Object3D object3D = mObject3DList.get(i);
                if (object3D != null) {
                    if (mPicker != null) {
                        mPicker.unregisterObject(object3D);
                    }
                    mContainer.removeChild(object3D);
                    object3D.destroy();
                }
            }
            mObject3DList.clear();
        }

        mPicker = null;
        mPickedObject = null;

        if (mMaterialList != null && mMaterialList.size() > 0) {
            mMaterialList.clear();
        }

        if (mGeometry3DList != null && mGeometry3DList.size() > 0) {
            mGeometry3DList.clear();
        }

        if (mShaderPlane != null) {
            mContainer.removeChild(mShaderPlane);
            mShaderPlane = null;
        }

        mIsStreamingViewMirror = false;
        mStreamingTexture = null;

        mMaterialTime = 0;
    }

    private void loadOrnament() {
        try {
            clearScene();

            if (mOrnamentModel != null) {
                mModelType = mOrnamentModel.getType();
                switch (mModelType) {
                    case Ornament.MODEL_TYPE_SHADER:
                        loadShaderMaterialModel();
                        break;
                    case Ornament.MODEL_TYPE_STATIC:
                    case Ornament.MODEL_TYPE_DYNAMIC:
                        loadNormalMaterialModel();
                        initOrnamentParams();
                        break;
                    case Ornament.MODEL_TYPE_POINT:
                        loadFacePoints();
                        initOrnamentParams();
                        break;
                }

                boolean isHasShaderPlane = mOrnamentModel.isHasShaderPlane();
                if (isHasShaderPlane) {
                    loadShaderPlane();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadShaderMaterialModel() {
        try {
            List<Object3D> object3DList = mOrnamentModel.getObject3DList();
            List<List<IMaterialPlugin>> materialList = mOrnamentModel.getMaterialList();
            if (object3DList != null && materialList != null) {
                mObject3DList.addAll(object3DList);

                for (List<IMaterialPlugin> pluginList : materialList) {
                    Material material = new Material();
                    material.enableTime(true);
                    for (IMaterialPlugin plugin : pluginList) {
                        material.addPlugin(plugin);
                    }
                    mMaterialList.add(material);
                }

                if (mObject3DList != null && mObject3DList.size() > 0) {
                    for (int i = 0; i < mObject3DList.size(); i++) {
                        Object3D object3D = mObject3DList.get(i);
                        if (object3D != null) {
                            Material material = mMaterialList.get(i);
                            if (material != null) {
                                object3D.setMaterial(material);
                            }
                            mContainer.addChild(object3D);
                        }
                    }

                    mContainer.setScale(mOrnamentModel.getScale());
                    mContainer.setPosition(mOrnamentModel.getOffsetX(), mOrnamentModel.getOffsetY(), mOrnamentModel.getOffsetZ());
                    mContainer.setRotation(mOrnamentModel.getRotateX(), mOrnamentModel.getRotateY(), mOrnamentModel.getRotateZ());
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadFacePoints() {
        //材质
        Material mat = new Material();
        mat.enableLighting(true);
        mat.setColor(0xFFFF0000);
        mat.setDiffuseMethod(new DiffuseMethod.Toon());

        Sphere sphere = new Sphere(2, 16, 16);
        sphere.setPosition(0, 0, 0);
//        sphere.setColor(0xff999900);
        sphere.setMaterial(mat);

        //左上
        Plane plane1 = new Plane(1, 1, 24, 24);
        plane1.setPosition(-5, 5, 0);
        plane1.setMaterial(mat);

        //右上
        Plane plane2 = new Plane(1, 1, 24, 24);
        plane2.setPosition(5, 5, 0);
        plane2.setMaterial(mat);

        //左下
        Plane plane3 = new Plane(1, 1, 24, 24);
        plane3.setPosition(-5, -5, 0);
        plane3.setMaterial(mat);

        //右下
        Plane plane4 = new Plane(1, 1, 24, 24);
        plane4.setPosition(5, -5, 0);
        plane4.setMaterial(mat);

        List<Object3D> PointsList = new ArrayList<>();
        PointsList.add(plane1);
        PointsList.add(plane2);
        PointsList.add(plane3);
        PointsList.add(plane4);
        PointsList.add(sphere);

        mOrnamentModel.setObject3DList(PointsList);

        mObject3DList.addAll(PointsList);
    }

    private void loadNormalMaterialModel() {
        try {
            List<Ornament.Model> modelList = mOrnamentModel.getModelList();
            Log.i(TAG, "modelList.size = " + modelList.size());
            if (modelList != null && modelList.size() > 0) {
                for (Ornament.Model model : modelList) {
                    String texturePath = model.getTexturePath();

                    Object3D object3D;
                    if (texturePath != null) {
                        object3D = loadDynamicModel(model);
                    } else {
                        object3D = loadStaticModel(model);
                    }

                    if (object3D != null) {
                        mObject3DList.add(object3D);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Object3D loadStaticModel(Ornament.Model model) {
        try {
            Object3D object3D;
            int modelResId = model.getModelResId();
            int buildInType = model.getBuildInType();
            if (modelResId != -1) {
                object3D = getExternalModel(modelResId);
            } else if (buildInType != -1) {
                object3D = getBuildInModel(model, buildInType);
            } else {
                throw new RuntimeException("invalid object3d");
            }

            setModelBaseParams(model, object3D);
            setModelTexture(model, object3D);
            setModelMaterial(model, object3D);
            setModelColor(model, object3D);
            handleObjectPicking(model, object3D);
            handleStreamingTexture(model);

            return object3D;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private Object3D getExternalModel(int modelResId) throws ParsingException {
        LoaderOBJ objParser = new LoaderOBJ(mContext.getResources(), mTextureManager, modelResId);
        objParser.parse();
        return objParser.getParsedObject();
    }

    private Object3D getBuildInModel(Ornament.Model model, int buildInType) {
        switch (buildInType) {
            case Ornament.Model.BUILD_IN_PLANE:
                return new Plane(model.getBuildInWidth(), model.getBuildInHeight(),
                        model.getBuildInSegmentsW(), model.getBuildInSegmentsH());
            case Ornament.Model.BUILD_IN_CUBE:
                return new Cube(model.getBuildInWidth());
            case Ornament.Model.BUILD_IN_SPHERE:
                return new Sphere(model.getBuildInWidth(),
                        model.getBuildInSegmentsW(), model.getBuildInSegmentsH());
            default:
                throw new RuntimeException("invalid object3d");
        }
    }

    private void setModelBaseParams(Ornament.Model model, Object3D object3D) {
        String name = model.getName();
        object3D.setName(name == null ? "" : name);
        object3D.setScale(model.getScale());
        object3D.setPosition(model.getOffsetX(), model.getOffsetY(), model.getOffsetZ());
        object3D.setRotation(model.getRotateX(), model.getRotateY(), model.getRotateZ());
    }

    private void setModelTexture(Ornament.Model model, Object3D object3D) throws ATexture.TextureException {
        int textureResId = model.getTextureResId();
        if (textureResId > 0) {
            ATexture texture = object3D.getMaterial().getTextureList().get(0);
            object3D.getMaterial().removeTexture(texture);

            Bitmap bitmap = BitmapFactory.decodeResource(getContext().getResources(), textureResId);
            if (bitmap != null) {
                mIsChanging = true;
                // 调整肤色
                if (model.isNeedSkinColor()) {
                    bitmap = changeSkinColor(bitmap, mSkinColor);
                }
                object3D.getMaterial().addTexture(new Texture("canvas", bitmap));
                mIsChanging = false;
            }
        }
    }

    private void setModelMaterial(Ornament.Model model, Object3D object3D) {
        int materialId = model.getMaterialId();
        if (materialId > -1) {
            object3D.setMaterial(MaterialFactory.getMaterialById(materialId));
        }
    }

    private void setModelColor(Ornament.Model model, Object3D object3D) {
        int color = model.getColor();
        if (color != OrnamentFactory.NO_COLOR) {
            object3D.getMaterial().setColor(color);
        }
    }

    private void handleObjectPicking(Ornament.Model model, Object3D object3D) {
        if (model.isNeedObjectPick()) {
            if (mPicker == null) {
                mPicker = new ObjectColorPicker(this);
                mPicker.setOnObjectPickedListener(this);
                Log.i("somebodyluo", "handleObjectPicking: " + model.getName());
            }
            mPicker.registerObject(object3D);
        }
    }

    private void handleStreamingTexture(Ornament.Model model) throws ATexture.TextureException {
        if (model.isNeedStreaming()) {
            Object3D streamingModel;
            int modelType = model.getStreamingModelType();
            float modelWidth = model.getStreamingModelWidth();
            float modelHeight = model.getStreamingModelHeight();
            int modelSegmentsW = model.getStreamingModelSegmentsW();
            int modelSegmentsH = model.getStreamingModelSegmentsH();
            switch (modelType) {
                case Ornament.Model.STREAMING_PLANE_MODEL:
                    streamingModel = new Plane(modelWidth, modelHeight, modelSegmentsW, modelSegmentsH);
                    break;
                case Ornament.Model.STREAMING_SPHERE_MODEL:
                    streamingModel = new Sphere(modelWidth, modelSegmentsW, modelSegmentsH);
                    break;
                default:
                    throw new RuntimeException("invalid streaming model");
            }

            streamingModel.setTransparent(model.isStreamingModelTransparent());
            streamingModel.setColor(0);
            streamingModel.setScale(model.getScale());
            streamingModel.setPosition(model.getStreamingOffsetX(), model.getStreamingOffsetY(),model.getStreamingOffsetZ());
            streamingModel.setRotation(model.getStreamingRotateX(), model.getStreamingRotateY(), model.getStreamingRotateZ());
            streamingModel.setRenderChildrenAsBatch(true);
            if (mStreamingTexture == null) {
                mStreamingTexture = new StreamingTexture("viewTexture", this);
            }
            mStreamingTexture.setInfluence(model.getStreamingTextureInfluence());
            Material material = new Material();
            material.setColorInfluence(model.getColorInfluence());
            try {
                material.addTexture(mStreamingTexture);
            } catch (ATexture.TextureException e) {
                e.printStackTrace();
            }

            if (model.getAlphaMapResId() > 0) {
                material.addTexture(new AlphaMapTexture("alphaMapTex", model.getAlphaMapResId()));
            }
            streamingModel.setMaterial(material);
            mContainer.addChild(streamingModel);
            mObject3DList.add(streamingModel);

            mIsStreamingViewMirror = model.isStreamingViewMirror();
        }
    }

    private Object3D loadDynamicModel(Ornament.Model model) {
        try {
            String objDir = "OpenGLDemo/txt/";
            String objName = "base_face_uv3_obj";
            LoaderOBJ parser = new LoaderOBJ(this, objDir + objName);
            parser.parse();
            Object3D object3D = parser.getParsedObject();

            object3D.setScale(model.getScale());
            object3D.setPosition(model.getOffsetX(), model.getOffsetY(), model.getOffsetZ());
            object3D.setRotation(model.getRotateX(), model.getRotateY(), model.getRotateZ());

            ATexture texture = object3D.getMaterial().getTextureList().get(0);
            object3D.getMaterial().removeTexture(texture);

            String texturePath = model.getTexturePath();
            Bitmap bitmap = BitmapUtils.decodeSampledBitmapFromFilePath(texturePath, 300, 300);
            // 调整肤色
            if (model.isNeedSkinColor()) {
                bitmap = changeSkinColor(bitmap, mSkinColor);
            }
            object3D.getMaterial().addTexture(new Texture("canvas", bitmap));
            object3D.getMaterial().enableLighting(false);

            int color = model.getColor();
            if (color != OrnamentFactory.NO_COLOR) {
                object3D.getMaterial().setColor(color);
            }

            return object3D;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private void initOrnamentParams() {
        if (mObject3DList != null && mObject3DList.size() > 0) {
            Log.i(TAG, "initOrnamentParams: mObject3DList.size() = " + mObject3DList.size());
            for (Object3D object3D : mObject3DList) {
                String modelName = mObject3DList.get(0).getName();
                if((modelName != null) && (modelName.equals("ironManTop2")))
                {
                    Log.i(TAG, "123 initOrnamentParams");
                    getCurrentScene().getCamera().setZ(5.5);	//original 重新调整视窗
//                    getCurrentCamera().setZ(105.5);
                }
                else
                {
                    getCurrentCamera().setZ(5.5);	//original
                }

                mContainer.addChild(object3D);

                Geometry3D geometry3D = object3D.getGeometry();
                mGeometry3DList.add(geometry3D);
            }

            if(mOrnamentModel.getType() == Ornament.MODEL_TYPE_POINT)
            {
                getCurrentCamera().setZ(105.5);
            }
        }

        mContainer.setTransparent(false);

        /* mContainer.setScale(1.0f, 1.0f, 1.0f); getCamera().setZ(5.5); ironMan.setScale(0.04f)这样的参数下，人脸的面积大小为11877时，刚好吻合模型。*/
        /* mContainer.setScale(1.05f, 0.9f, 0.9f); getCamera().setZ(5.5); ironMan.setScale(0.04f)这样的参数下，人脸的面积大小为10700时，刚好吻合模型。*/
        /* 修改了模型位置，mContainer.setScale(1.08f, 1.0f, 1.0f); getCamera().setZ(5.5); ironMan.setScale(0.04f)这样的参数下，人脸的面积大小为10700时，刚好吻合模型。*/
        mContainer.setScale(1.08f, 1.0f, 1.0f);
        mContainer.setRotation(0, 0, 0);
        mContainer.setPosition(0, 0, 0);

        //luoyouren: set current camera X Y
        getCurrentCamera().setX(0);
        getCurrentCamera().setY(0);
    }

    private void loadShaderPlane() {
        int vertResId = mOrnamentModel.getVertResId();
        int fragResId = mOrnamentModel.getFragResId();
        if (vertResId > 0 && fragResId > 0) {
            mMyFragmentShader = new MyFragmentShader(fragResId);

            mCustomMaterial = new Material(
                    new MyVertexShader(vertResId),
                    mMyFragmentShader);
            mCustomMaterial.enableTime(true);

            float offsetX = mOrnamentModel.getPlaneOffsetX();
            float offsetY = mOrnamentModel.getPlaneOffsetY();
            float offsetZ = mOrnamentModel.getPlaneOffsetZ();
            mShaderPlane = new Plane(5, 5, 1, 1);
            mShaderPlane.setPosition(offsetX, offsetY, offsetZ);
            mShaderPlane.setMaterial(mCustomMaterial);
            mShaderPlane.setTransparent(true);
            mContainer.addChild(mShaderPlane);
        }
    }

    private int faceIndices[][]={
            {66, 68, 123, 125, 128, 132, 135, 137},
            {57, 59, 63, 64, 110, 114, 116, 120, 124},
            {51, 53, 67, 71, 86, 90, 92, 96, 121},
            {54, 56, 65, 69},
            {35, 39, 41, 45, 49, 52, 55, 58},
            {15, 70, 122, 129, 146, 152, 158},
            {17, 61, 126, 136, 150, 156, 162},
            {139, 144},
            {141, 142, 147, 149, 151, 154},
            {153, 155, 157, 160},
            {33, 34, 37, 99, 101, 105, 107},
            {100, 103},
            {36, 60, 97, 109},
            {112, 115},
            {16, 21, 24, 27, 30, 31, 62, 106, 118},
            {40, 43, 47, 75, 77, 81, 84},
            {76, 79},
            {44, 50, 83, 94},
            {88, 91},
            {2, 6, 9, 12, 13, 46, 72, 73, 85},
            {38, 42},
            {1, 4},
            {5, 7},
            {8, 10},
            {11, 14, 159},
            {18, 19, 161},
            {20, 22},
            {23, 25},
            {26, 28},
            {3, 48},
            {29, 32},
            {80, 82},
            {74, 78},
            {93, 95},
            {87, 89},
            {98, 102},
            {104, 108},
            {117, 119},
            {111, 113},
            {140, 145},
            {143, 148},
            {127, 130},
            {131, 133},
            {134, 138},
    };

    private int[] getIndexArrayByFace(int faceIndex) {
        return faceIndices[faceIndex];
    }

    private void changePoint(FloatBuffer vertBuffer, int faceIndex, float x, float y, float z) {
        int[] indices = getIndexArrayByFace(faceIndex); //根据序号，取出某个切片面上面的点集，进行统一的坐标偏移操作
        if (indices != null) {
            int len = indices.length;
            for (int i=0; i<len; i++) {
                int index = indices[i]-1;   //为什么有那么多点要操作？
                vertBuffer.put(index * 3, x);
                vertBuffer.put(index * 3 + 1, y);
                vertBuffer.put(index * 3 + 2, z);
            }
        }
    }

    public void setDynamicPoints(List<DynamicPoint> mPoints) {
        if (!mIsChanging) {
            this.mPoints = mPoints;
        }
    }

    private Bitmap changeSkinColor(Bitmap bitmap, int skinColor) {
        if (bitmap != null) {
            Bitmap texture = bitmap.copy(Bitmap.Config.ARGB_8888, true);

            int width = texture.getWidth();
            int height = texture.getHeight();

            int skinRed = Color.red(skinColor);
            int skinGreen = Color.green(skinColor);
            int skinBlue = Color.blue(skinColor);

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = texture.getPixel(x, y);
                    int red = Color.red(pixel);
                    int green = Color.green(pixel);
                    int blue = Color.blue(pixel);

                    // TODO
                    // 将肤色与该点颜色进行混合
                    // 在Photoshop里面看，“柔光”的效果是比较合适的。 “叠加”也类似，不过画面有点过饱和
                    // 调色层在顶层并设为“柔光”，和人脸层在顶层并设为“柔光”是不同的
                    // 理想的效果是前者，但是在网上找到的“柔光”代码实现的是后者
                    // 由于没弄明白怎么改写，暂时先用“叠加”效果，然后降低饱和度
                    red = overlay(skinRed, red);
                    green = overlay(skinGreen, green);
                    blue = overlay(skinBlue, blue);

                    pixel = Color.rgb(red, green, blue);
                    texture.setPixel(x, y, pixel);
                }
            }

            // 降低饱和度
            float saturation = 0.35f;
            ColorMatrix cMatrix = new ColorMatrix();
            cMatrix.setSaturation(saturation);

            Paint paint = new Paint();
            paint.setColorFilter(new ColorMatrixColorFilter(cMatrix));

            Canvas canvas = new Canvas(texture);
            canvas.drawBitmap(texture, 0, 0, paint);

            return texture;
        }
        return null;
    }

    // 混合模式 -- 柔光
    private int softLight(int A, int B) {
        return (B < 128) ? (2 * ((A >> 1) + 64)) * (B / 255) : (255 - (2 * (255 - ((A >> 1) + 64)) * (255 - B) / 255));
    }

    // 混合模式 -- 叠加
    private int overlay(int A, int B) {
        return ((B < 128) ? (2 * A * B / 255) : (255 - 2 * (255 - A) * (255 - B) / 255));
    }

    public void getObjectAt(float x, float y) {

        Log.i("somebodyluo", "getObjectAt1");
        if (mPicker != null) {
            Log.i("somebodyluo", "getObjectAt2");
            mPicker.getObjectAt(x, y);
        }
    }
}
