package main.osu;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.*;

import androidx.preference.PreferenceManager;
import androidx.core.content.PermissionChecker;

import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;

import android.widget.Toast;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.reco1l.GameEngine;
import com.reco1l.management.InputManager;
import com.reco1l.management.music.MusicManager;
import com.reco1l.ui.scenes.intro.IntroScene;
import com.reco1l.ui.base.FragmentPlatform;
import com.reco1l.ui.custom.Dialog;
import com.reco1l.tables.DialogTable;
import com.reco1l.utils.Logging;
import com.reco1l.utils.execution.ScheduledTask;

import org.anddev.andengine.engine.Engine;
import org.anddev.andengine.engine.camera.Camera;
import org.anddev.andengine.engine.camera.SmoothCamera;
import org.anddev.andengine.engine.options.EngineOptions;
import org.anddev.andengine.engine.options.resolutionpolicy.RatioResolutionPolicy;
import org.anddev.andengine.entity.scene.Scene;
import org.anddev.andengine.extension.input.touch.controller.MultiTouch;
import org.anddev.andengine.extension.input.touch.controller.MultiTouchController;
import org.anddev.andengine.extension.input.touch.exception.MultiTouchException;
import org.anddev.andengine.opengl.view.RenderSurfaceView;
import org.anddev.andengine.sensor.accelerometer.AccelerometerData;
import org.anddev.andengine.sensor.accelerometer.IAccelerometerListener;
import org.anddev.andengine.ui.activity.BaseGameActivity;
import org.anddev.andengine.util.Debug;

import java.io.File;
import java.io.IOException;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.lingala.zip4j.ZipFile;

import main.audio.BassAudioPlayer;
import main.audio.serviceAudio.SaveServiceObject;
import main.audio.serviceAudio.SongService;
import main.osu.async.SyncTaskManager;
import main.osu.helper.FileUtils;
import main.osu.helper.StringTable;
import main.osu.online.OnlineManager;
import main.osu.scoring.Replay;
import main.osu.scoring.StatisticV2;
import com.rimu.BuildConfig;
import com.rimu.R;

public class MainActivity extends BaseGameActivity implements
        IAccelerometerListener {

    public static MainActivity instance;

    public static SongService songService;
    public ServiceConnection connection;
    private PowerManager.WakeLock wakeLock = null;
    private String beatmapToAdd = null;
    private SaveServiceObject saveServiceObject;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private FirebaseAnalytics analytics;
    private FirebaseCrashlytics crashlytics;
    private boolean willReplay = false;
    private static boolean activityVisible = true;
    private boolean autoclickerDialogShown = false;

    public MainActivity() {
        instance = this;
    }

    @Override
    public Engine onLoadEngine() {
        if (!checkPermissions()) {
            return null;
        }
        analytics = FirebaseAnalytics.getInstance(this);
        crashlytics = FirebaseCrashlytics.getInstance();
        Config.loadConfig(this);
        initialGameDirectory();
        //Debug.setDebugLevel(Debug.DebugLevel.NONE);
        StringTable.setContext(this);
        ToastLogger.init(this);
        SyncTaskManager.getInstance().init(this);
        main.osu.helper.InputManager.setContext(this);
        OnlineManager.getInstance().Init(getApplicationContext());
        crashlytics.setUserId(Config.getOnlineDeviceID());

        final DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        double screenInches = Math.sqrt(Math.pow(dm.heightPixels, 2) + Math.pow(dm.widthPixels, 2)) / (dm.density * 160.0f);
        Debug.i("screen inches: " + screenInches);
        Config.setScaleMultiplier((float) ((11 - 5.2450170716245195) / 5));

        final PowerManager manager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = manager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK,
                "osudroid:osu");

        Camera mCamera = new SmoothCamera(0, 0, Config.getRES_WIDTH(),
                Config.getRES_HEIGHT(), 0, 1800, 1);
        final EngineOptions opt = new EngineOptions(true,
                null, new RatioResolutionPolicy(
                Config.getRES_WIDTH(), Config.getRES_HEIGHT()),
                mCamera);
        opt.setNeedsMusic(true);
        opt.setNeedsSound(true);
        opt.getRenderOptions().disableExtensionVertexBufferObjects();
        opt.getTouchOptions().enableRunOnUpdateThread();
        final Engine engine = new GameEngine(opt);
        try {
            if (MultiTouch.isSupported(this)) {
                engine.setTouchController(new MultiTouchController());
            } else {
                ToastLogger.showText(
                        StringTable.get(R.string.message_error_multitouch),
                        false);
            }
        } catch (final MultiTouchException e) {
            ToastLogger.showText(
                    StringTable.get(R.string.message_error_multitouch),
                    false);
        }
        GlobalManager.getInstance().setCamera(mCamera);
        GlobalManager.getInstance().setEngine(engine);
        GlobalManager.getInstance().setMainActivity(this);
        return GlobalManager.getInstance().getEngine();
    }

    private void initialGameDirectory() {
        File dir = new File(Config.getBeatmapPath());
        // Creating Osu directory if it doesn't exist
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                Config.setBeatmapPath(Config.getCorePath() + "Songs/");
                dir = new File(Config.getBeatmapPath());
                if (!(dir.exists() || dir.mkdirs())) {
                    ToastLogger.showText(StringTable.format(
                            R.string.message_error_createdir, dir.getPath()),
                            true);
                } else {
                    final SharedPreferences prefs = PreferenceManager
                            .getDefaultSharedPreferences(this);
                    final SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("directory", dir.getPath());
                    editor.commit();
                }

            }
            final File nomedia = new File(dir.getParentFile(), ".nomedia");
            try {
                nomedia.createNewFile();
            } catch (final IOException e) {
                Debug.e("LibraryManager: " + e.getMessage(), e);
            }
        }

        final File skinDir = new File(Config.getCorePath() + "/Skin");
        // Creating Osu/Skin directory if it doesn't exist
        if (!skinDir.exists()) {
            skinDir.mkdirs();
        }
    }

    @Override
    public void onLoadResources() {
        ResourceManager.getInstance().Init(mEngine, this);
        BassAudioPlayer.initDevice();
    }

    @Override
    public Scene onLoadScene() {
        return new IntroScene();
    }

    @Override
    public void onLoadComplete() {
        new AsyncTaskLoader().execute(new OsuAsyncCallback() {
            public void run() {
                GlobalManager.getInstance().init();
                analytics.logEvent(FirebaseAnalytics.Event.APP_OPEN, null);
                Config.loadSkins();
                checkNewSkins();
                checkNewBeatmaps();
                if (!LibraryManager.getInstance().loadLibraryCache(MainActivity.this, true)) {
                    LibraryManager.getInstance().scanLibrary(MainActivity.this);
                    System.gc();
                }
            }

            public void onComplete() {
                GlobalManager.getInstance().getEngine().setScene(GlobalManager.getInstance().getMainScene());
                availableInternalMemory();
                initAccessibilityDetector();
                if (willReplay) {
                    watchReplay(beatmapToAdd);
                    willReplay = false;
                }
            }
        });
    }
    /*
    Accuracy isn't the best, but it's sufficient enough
    to determine whether storage is low or not
     */
    private void availableInternalMemory() {
        DecimalFormat df = new DecimalFormat("#.##");
        df.setRoundingMode(RoundingMode.HALF_EVEN);

        double availableMemory;
        double minMem = 1073741824D; //1 GiB = 1073741824 bytes
        File internal = Environment.getDataDirectory();
        StatFs stat = new StatFs(internal.getPath());
        availableMemory = (double) stat.getAvailableBytes();
        String toastMessage = String.format(StringTable.get(R.string.message_low_storage_space), df.format(availableMemory / minMem));
        if(availableMemory < 0.5 * minMem) { //I set 512MiB as a minimum
            Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show();
        }
        Debug.i("Free Space: " + df.format(availableMemory / minMem));
    }

    @Override
    protected void onSetContentView() {
        this.mRenderSurfaceView = new RenderSurfaceView(this);
        if(Config.isUseDither()) {
            this.mRenderSurfaceView.setEGLConfigChooser(8,8,8,8,24,0);
            this.mRenderSurfaceView.getHolder().setFormat(PixelFormat.RGBA_8888);
        } else {
            this.mRenderSurfaceView.setEGLConfigChooser(true);
        }
        this.mRenderSurfaceView.setRenderer(this.mEngine);

        FragmentPlatform.instance.onSetContentView(this, mRenderSurfaceView);
    }

    public void checkNewBeatmaps() {
        final File mainDir = new File(Config.getCorePath());
        if (beatmapToAdd != null) {
            File file = new File(beatmapToAdd);
            if (file.getName().toLowerCase().endsWith(".osz")) {
                /*ToastLogger.showText(
                        StringTable.get(R.string.message_lib_importing),
                        false);*/

                if(FileUtils.extractZip(beatmapToAdd, Config.getBeatmapPath())) {
                    String folderName = beatmapToAdd.substring(0, beatmapToAdd.length() - 4);
                    // We have imported the beatmap!
                    /*ToastLogger.showText(
                            StringTable.format(R.string.message_lib_imported, folderName),
                            true);*/
                }

                // LibraryManager.getInstance().sort();
                LibraryManager.getInstance().savetoCache(MainActivity.this);
            } else if (file.getName().endsWith(".odr")) {
                willReplay = true;
            }
        } else if (mainDir.exists() && mainDir.isDirectory()) {
            File[] filelist = FileUtils.listFiles(mainDir, ".osz");
            final ArrayList<String> beatmaps = new ArrayList<String>();
            for (final File file : filelist) {
                ZipFile zip = new ZipFile(file);
                if(zip.isValidZipFile()) {
                    beatmaps.add(file.getPath());
                }
            }

            File beatmapDir = new File(Config.getBeatmapPath());
            if (beatmapDir.exists()
                    && beatmapDir.isDirectory()) {
                filelist = FileUtils.listFiles(beatmapDir, ".osz");
                for (final File file : filelist) {
                    ZipFile zip = new ZipFile(file);
                    if(zip.isValidZipFile()) {
                        beatmaps.add(file.getPath());
                    }
                }
            }

            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (Config.isSCAN_DOWNLOAD()
                    && downloadDir.exists()
                    && downloadDir.isDirectory()) {
                filelist = FileUtils.listFiles(downloadDir, ".osz");
                for (final File file : filelist) {
                    ZipFile zip = new ZipFile(file);
                    if(zip.isValidZipFile()) {
                        beatmaps.add(file.getPath());
                    }
                }
            }

            if (beatmaps.size() > 0) {
                // final boolean deleteOsz = Config.isDELETE_OSZ();
                // Config.setDELETE_OSZ(true);
                /*ToastLogger.showText(StringTable.format(
                        R.string.message_lib_importing_several,
                        beatmaps.size()), false);*/
                for (final String beatmap : beatmaps) {
                    if(FileUtils.extractZip(beatmap, Config.getBeatmapPath())) {
                        String folderName = beatmap.substring(0, beatmap.length() - 4);
                        // We have imported the beatmap!
                        /*ToastLogger.showText(
                                StringTable.format(R.string.message_lib_imported, folderName),
                                true);*/
                    }
                }
                // Config.setDELETE_OSZ(deleteOsz);

                // LibraryManager.getInstance().sort();
                LibraryManager.getInstance().savetoCache(MainActivity.this);
            }
        }
    }

    public void checkNewSkins() {

        final ArrayList<String> skins = new ArrayList<>();

        // Scanning skin directory
        final File skinDir = new File(Config.getSkinTopPath());

        if (skinDir.exists() && skinDir.isDirectory()) {
            final File[] files = FileUtils.listFiles(skinDir, ".osk");

            for (final File file : files) {
                ZipFile zip = new ZipFile(file);
                if(zip.isValidZipFile()) {
                    skins.add(file.getPath());
                }
            }
        }

        // Scanning download directory
        final File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

        if (Config.isSCAN_DOWNLOAD()
                && downloadDir.exists()
                && downloadDir.isDirectory()) {
            final File[] files = FileUtils.listFiles(downloadDir, ".osk");

            for (final File file : files) {
                ZipFile zip = new ZipFile(file);
                if(zip.isValidZipFile()) {
                    skins.add(file.getPath());
                }
            }
        }

        if (skins.size() > 0) {
            ToastLogger.showText(StringTable.format(
                    R.string.message_skin_importing_several,
                    skins.size()), false);

            for (final String skin : skins) {
                if (FileUtils.extractZip(skin, Config.getSkinTopPath())) {
                    String folderName = skin.substring(0, skin.length() - 4);
                    // We have imported the skin!
                    ToastLogger.showText(
                            StringTable.format(R.string.message_lib_imported, folderName),
                            true);
                    Config.addSkin(folderName.substring(folderName.lastIndexOf("/") + 1), skin);
                }
            }
        }
    }

    public Handler getHandler() {
        return handler;
    }

    public FirebaseAnalytics getAnalytics() {
        return analytics;
    }

    public PowerManager.WakeLock getWakeLock() {
        return wakeLock;
    }

    public static boolean isActivityVisible() {
        return activityVisible;
    }

    @Override
    protected void onCreate(Bundle pSavedInstanceState) {
        super.onCreate(pSavedInstanceState);
        if (this.mEngine == null) {
            return;
        }

        if (BuildConfig.DEBUG) {
            Logging.logcat();
        }
        onBeginBindService();
    }

    public void onBeginBindService() {
        if (connection == null && songService == null) {
            connection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    songService = ((SongService.ReturnBindObject) service).getObject();
                    saveServiceObject = (SaveServiceObject) getApplication();
                    saveServiceObject.setSongService(songService);
                    GlobalManager.getInstance().setSongService(songService);
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {

                }

            };

            bindService(new Intent(MainActivity.this, SongService.class), connection, BIND_AUTO_CREATE);
        }
        GlobalManager.getInstance().setSongService(songService);
        GlobalManager.getInstance().setSaveServiceObject(saveServiceObject);
    }

    @Override
    protected void onStart() {
//        this.enableAccelerometerSensor(this);
        if (getIntent().getAction() != null
                && getIntent().getAction().equals(Intent.ACTION_VIEW)) {
            if (ContentResolver.SCHEME_FILE.equals(getIntent().getData().getScheme())) {
                beatmapToAdd = getIntent().getData().getPath();
            }
            if (BuildConfig.DEBUG) {
                System.out.println(getIntent());
                System.out.println(getIntent().getData().getEncodedPath());
            }
        }
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (this.mEngine == null) {
            return;
        }
        activityVisible = true;
        if (GlobalManager.getInstance().getEngine() != null && GlobalManager.getInstance().getGameScene() != null
                && GlobalManager.getInstance().getEngine().getScene() == GlobalManager.getInstance().getGameScene().getScene()) {
            GlobalManager.getInstance().getEngine().getTextureManager().reloadTextures();
        }
        if (GlobalManager.getInstance().getMainScene() != null) {
            if (songService != null && Build.VERSION.SDK_INT > 10) {
                if (songService.hideNotification()) {
                    if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
                }
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        activityVisible = false;
        if (this.mEngine == null) {
            return;
        }
        if (GlobalManager.getInstance().getEngine() != null && GlobalManager.getInstance().getGameScene() != null
                && GlobalManager.getInstance().getEngine().getScene() == GlobalManager.getInstance().getGameScene().getScene()) {
            SpritePool.getInstance().purge();
            GlobalManager.getInstance().getGameScene().pause();
        }
        if (GlobalManager.getInstance().getMainScene() != null) {
            if (songService != null && LibraryManager.getInstance().getBeatmap() != null) {
                songService.showNotification();

                if (wakeLock == null) {
                    PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
                    wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "osudroid:MainActivity");
                }
                wakeLock.acquire();
            } else {
                if (songService != null) {
                    songService.pause();
                }
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        activityVisible = false;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (this.mEngine == null) {
            return;
        }
        if (GlobalManager.getInstance().getEngine() != null
                && GlobalManager.getInstance().getGameScene() != null
                && !hasFocus
                && GlobalManager.getInstance().getEngine().getScene() == GlobalManager.getInstance().getGameScene().getScene()) {
            if (!GlobalManager.getInstance().getGameScene().isPaused()) {
                GlobalManager.getInstance().getGameScene().pause();
            }
        }
        if (hasFocus && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && Config.isHideNaviBar()) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    public void onAccelerometerChanged(final AccelerometerData arg0) {
        if (this.mEngine == null) {
            return;
        }
        if (GlobalManager.getInstance().getCamera().getRotation() == 0 && arg0.getY() < -5) {
            GlobalManager.getInstance().getCamera().setRotation(180);
        } else if (GlobalManager.getInstance().getCamera().getRotation() == 180 && arg0.getY() > 5) {
            GlobalManager.getInstance().getCamera().setRotation(0);
        }
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        if (this.mEngine == null) {
            return false;
        }

        if (GlobalManager.getInstance().getEngine() == null) {
            return super.onKeyDown(keyCode, event);
        }

        return InputManager.instance.handleKey(keyCode, event.getAction());
    }

    private void initAccessibilityDetector() {
        ScheduledExecutorService scheduledExecutorService =
            Executors.newSingleThreadScheduledExecutor();
        scheduledExecutorService
            .scheduleAtFixedRate(() -> {
                AccessibilityManager manager = (AccessibilityManager)
                    getSystemService(Context.ACCESSIBILITY_SERVICE);
                List<AccessibilityServiceInfo> activeServices = new ArrayList<AccessibilityServiceInfo>(
                    manager.getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_ALL_MASK));

                for(AccessibilityServiceInfo activeService : activeServices) {
                     int capabilities = activeService.getCapabilities();
                    if((AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES & capabilities)
                            == AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES) {
                        if(!autoclickerDialogShown && activityVisible) {
                            new Dialog(DialogTable.auto_clicker()).show();
                            autoclickerDialogShown = true;
                        }
                    }
                }
            }, 0, 1, TimeUnit.SECONDS);
    }

    public long getVersionCode() {
        long versionCode = 0;
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(
                getPackageName(), 0);
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                versionCode = packageInfo.getLongVersionCode();
            }else {
                versionCode = packageInfo.versionCode;
            }
        } catch (PackageManager.NameNotFoundException e) {
            Debug.e("PackageManager: " + e.getMessage(), e);
        }
        return versionCode;
    }

    public float getRefreshRate() {
        return ((WindowManager) getSystemService(Context.WINDOW_SERVICE))
            .getDefaultDisplay()
            .getRefreshRate();
    }

    private boolean checkPermissions() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                Environment.isExternalStorageManager()) {
            return true;
        }else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
                PermissionChecker.checkCallingOrSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PermissionChecker.PERMISSION_GRANTED) {
            return true;
        } else {
            Intent grantPermission = new Intent(this, PermissionActivity.class);
            startActivity(grantPermission);
            finish();
            return false;
        }
    }

    //--------------------------------------------------------------------------------------------//

    private void watchReplay(String path) {
        Replay replay = new Replay();

        if (!replay.loadInfo(path) || replay.replayVersion < 3) {
            return;
        }

        StatisticV2 stat = replay.getStat();
        TrackInfo track = LibraryManager.getInstance().findTrackByFileNameAndMD5(replay.getMapFile(), replay.getMd5());

        if (track != null) {
            MusicManager.instance.change(track);
            GlobalManager.getInstance().getScoring().load(stat, track, path, true);
        }
    }

    public void exit() {
        if(GlobalManager.getInstance().getEngine().getScene() == GlobalManager.getInstance().getGameScene().getScene()) {
            GlobalManager.getInstance().getGameScene().quit();
        }
        GlobalManager.getInstance().getEngine().setScene(GlobalManager.getInstance().getMainScene());

        PowerManager.WakeLock wakeLock = getWakeLock();
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        GlobalManager.getInstance().getMainScene().onExit();

        ScheduledTask.of(() -> {
            if (songService != null) {
                unbindService(connection);
                stopService(new Intent(this, SongService.class));
            }
            finish();
        }, 3000);
    }
}