package android.king.signature.fragment;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.king.signature.BaseActivity;
import android.king.signature.R;
import android.king.signature.config.PenConfig;
import android.king.signature.util.BitmapUtil;
import android.king.signature.util.DisplayUtil;
import android.king.signature.util.StatusBarCompat;
import android.king.signature.util.SystemUtil;
import android.king.signature.view.CircleView;
import android.king.signature.view.PaintSettingWindow;
import android.king.signature.view.PaintView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;


/**
 * 空白手写画板
 *
 * @author king
 * @since 2018/07/10 14:20
 */
public class PaintFragment extends Fragment implements View.OnClickListener, PaintView.StepCallback {

    /**
     * 画布最大宽度
     */
    public static final int CANVAS_MAX_WIDTH = 3000;
    /**
     * 画布最大高度
     */
    public static final int CANVAS_MAX_HEIGHT = 3000;

    private ImageView mHandView; //切换 滚动/手写
    private ImageView mUndoView;
    private ImageView mRedoView;
    private ImageView mPenView;
    private ImageView mClearView;
    private CircleView mSettingView;

    private PaintView mPaintView;

    private ProgressDialog mSaveProgressDlg;
    private static final int MSG_SAVE_SUCCESS = 1;
    private static final int MSG_SAVE_FAILED = 2;

    private String mSavePath;
    private boolean hasSize = false;

    private float mWidth;
    private float mHeight;
    private float widthRate = 1.0f;
    private float heightRate = 1.0f;
    private int bgColor;
    private boolean isCrop;
    private String format;


    private PaintSettingWindow settingWindow;


    public  View view;
    public Activity mActivity;
    /** Standard activity result: operation canceled. */
    public static final int RESULT_CANCELED    = 0;
    /** Standard activity result: operation succeeded. */
    public static final int RESULT_OK           = -1;
    /** Start of user-defined activity results. */
    public static final int RESULT_FIRST_USER   = 1;



    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        view=inflater.inflate(R.layout.sign_activity_paint,container,false);
        mActivity=getActivity();
        initView();
        initData();
        return view;
    }





    protected void initView() {

        View mCancelView = view.findViewById(R.id.tv_cancel);
        View mOkView = view.findViewById(R.id.tv_ok);

        mPaintView = view.findViewById(R.id.paint_view);
        mHandView = view.findViewById(R.id.btn_hand);
        mUndoView = view.findViewById(R.id.btn_undo);
        mRedoView = view.findViewById(R.id.btn_redo);
        mPenView = view.findViewById(R.id.btn_pen);
        mClearView = view.findViewById(R.id.btn_clear);
        mSettingView = view.findViewById(R.id.btn_setting);
        mUndoView.setOnClickListener(this);
        mRedoView.setOnClickListener(this);
        mPenView.setOnClickListener(this);
        mClearView.setOnClickListener(this);
        mSettingView.setOnClickListener(this);
        mHandView.setOnClickListener(this);
        mCancelView.setOnClickListener(this);
        mOkView.setOnClickListener(this);

        mPenView.setSelected(true);
        mUndoView.setEnabled(false);
        mRedoView.setEnabled(false);
        mClearView.setEnabled(!mPaintView.isEmpty());

        mPaintView.setBackgroundColor(Color.WHITE);
        mPaintView.setStepCallback(this);

        PenConfig.PAINT_SIZE_LEVEL = PenConfig.getPaintTextLevel(mActivity);
        PenConfig.PAINT_COLOR = PenConfig.getPaintColor(mActivity);

        mSettingView.setPaintColor(PenConfig.PAINT_COLOR);
        mSettingView.setRadiusLevel(PenConfig.PAINT_SIZE_LEVEL);

        //setThemeColor(PenConfig.THEME_COLOR);
        BitmapUtil.setImage(mClearView, R.drawable.sign_ic_clear, PenConfig.THEME_COLOR);
        BitmapUtil.setImage(mPenView, R.drawable.sign_ic_pen, PenConfig.THEME_COLOR);
        BitmapUtil.setImage(mRedoView, R.drawable.sign_ic_redo, mPaintView.canRedo() ? PenConfig.THEME_COLOR : Color.LTGRAY);
        BitmapUtil.setImage(mUndoView, R.drawable.sign_ic_undo, mPaintView.canUndo() ? PenConfig.THEME_COLOR : Color.LTGRAY);
        BitmapUtil.setImage(mClearView, R.drawable.sign_ic_clear, !mPaintView.isEmpty() ? PenConfig.THEME_COLOR : Color.LTGRAY);
//        mSettingView.setOutBorderColor(PenConfig.THEME_COLOR);
        BitmapUtil.setImage(mHandView, R.drawable.sign_ic_hand, PenConfig.THEME_COLOR);

    }


    /**
     * 获取画布默认宽度
     *
     * @return
     */
    private int getResizeWidth() {
        DisplayMetrics dm = new DisplayMetrics();
        mActivity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE && dm.widthPixels < dm.heightPixels) {
            return (int) (dm.heightPixels * widthRate);
        }
        return (int) (dm.widthPixels * widthRate);
    }

    /**
     * 获取画布默认高度
     *
     * @return
     */
    private int getResizeHeight() {
        int toolBarHeight = getResources().getDimensionPixelSize(R.dimen.sign_grid_toolbar_height);
        int actionbarHeight = getResources().getDimensionPixelSize(R.dimen.sign_actionbar_height);
        int statusBarHeight = StatusBarCompat.getStatusBarHeight(mActivity);
        int otherHeight = toolBarHeight + actionbarHeight + statusBarHeight;
        DisplayMetrics dm = new DisplayMetrics();
        mActivity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE && dm.widthPixels < dm.heightPixels) {
            return (int) ((dm.widthPixels - otherHeight) * heightRate);
        }
        return (int) ((dm.heightPixels - otherHeight) * heightRate);
    }


    protected void initData() {
        isCrop = mActivity.getIntent().getBooleanExtra("crop", false);
        format = mActivity.getIntent().getStringExtra("format");
        if(format.equals("")){
            format="PNG";
        }
        bgColor = mActivity.getIntent().getIntExtra("background", Color.TRANSPARENT);
        String mInitPath = mActivity.getIntent().getStringExtra("image");
        float bitmapWidth =mActivity. getIntent().getFloatExtra("width", 1.0f);
        float bitmapHeight =mActivity. getIntent().getFloatExtra("height", 1.0f);

        if (bitmapWidth > 0 && bitmapWidth <= 1.0f) {
            widthRate = bitmapWidth;
            mWidth = getResizeWidth();
        } else {
            hasSize = true;
            mWidth = bitmapWidth;
        }
        if (bitmapHeight > 0 && bitmapHeight <= 1.0f) {
            heightRate = bitmapHeight;
            mHeight = getResizeHeight();
        } else {
            hasSize = true;
            mHeight = bitmapHeight;
        }
        if (mWidth > CANVAS_MAX_WIDTH) {
            Toast.makeText(mActivity, "画板宽度已超过" + CANVAS_MAX_WIDTH, Toast.LENGTH_LONG).show();
            mActivity.finish();
            return;
        }
        if (mHeight > CANVAS_MAX_WIDTH) {
            Toast.makeText(mActivity, "画板高度已超过" + CANVAS_MAX_WIDTH, Toast.LENGTH_LONG).show();
            mActivity.finish();
            return;
        }
        //初始画板设置
        if (!hasSize && !TextUtils.isEmpty(mInitPath)) {
            Bitmap bitmap = BitmapFactory.decodeFile(mInitPath);
            mWidth = bitmap.getWidth();
            mHeight = bitmap.getHeight();
            hasSize = true;
            if (mWidth > CANVAS_MAX_WIDTH || mHeight > CANVAS_MAX_HEIGHT) {
                bitmap = BitmapUtil.zoomImg(bitmap, CANVAS_MAX_WIDTH, CANVAS_MAX_WIDTH);
                mWidth = bitmap.getWidth();
                mHeight = bitmap.getHeight();
            }
        }
        mPaintView.init((int) mWidth, (int) mHeight, mInitPath);
        if (bgColor != Color.TRANSPARENT) {
            mPaintView.setBackgroundColor(bgColor);
        }
    }

    /**
     * 横竖屏切换
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (settingWindow != null) {
            settingWindow.dismiss();
        }

        int resizeWidth = getResizeWidth();
        int resizeHeight = getResizeHeight();
        if (mPaintView != null && !hasSize) {
            mPaintView.resize(mPaintView.getLastBitmap(), resizeWidth, resizeHeight);
        }
    }


    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.btn_setting) {
            showPaintSettingWindow();

        } else if (i == R.id.btn_hand) {
            //切换是否允许写字
            mPaintView.setFingerEnable(!mPaintView.isFingerEnable());
            if (mPaintView.isFingerEnable()) {
                BitmapUtil.setImage(mHandView, R.drawable.sign_ic_hand, PenConfig.THEME_COLOR);
            } else {
                BitmapUtil.setImage(mHandView, R.drawable.sign_ic_drag, PenConfig.THEME_COLOR);
            }

        } else if (i == R.id.btn_clear) {
            mPaintView.reset();

        } else if (i == R.id.btn_undo) {
            mPaintView.undo();

        } else if (i == R.id.btn_redo) {
            mPaintView.redo();

        } else if (i == R.id.btn_pen) {
            if (!mPaintView.isEraser()) {
                mPaintView.setPenType(PaintView.TYPE_ERASER);
                BitmapUtil.setImage(mPenView, R.drawable.sign_ic_eraser, PenConfig.THEME_COLOR);
            } else {
                mPaintView.setPenType(PaintView.TYPE_PEN);
                BitmapUtil.setImage(mPenView, R.drawable.sign_ic_pen, PenConfig.THEME_COLOR);
            }
        } else if (i == R.id.tv_ok) {
            save();

        } else if (i == R.id.tv_cancel) {
            if (!mPaintView.isEmpty()) {
                showQuitTip();
            } else {
                mActivity.setResult(RESULT_CANCELED);
                mActivity.finish();
            }
        }
    }


    public void onDestroy() {
        if (mPaintView != null) {
            mPaintView.release();
        }
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }
        super.onDestroy();
    }


    /**
     * 弹出画笔设置
     */
    private void showPaintSettingWindow() {
        settingWindow = new PaintSettingWindow(mActivity);
        settingWindow.setSettingListener(new PaintSettingWindow.OnSettingListener() {
            @Override
            public void onColorSetting(int color) {
                mPaintView.setPaintColor(color);
                mSettingView.setPaintColor(color);
            }

            @Override
            public void onSizeSetting(int index) {
                mSettingView.setRadiusLevel(index);
                mPaintView.setPaintWidth(PaintSettingWindow.PEN_SIZES[index]);
            }
        });

        View contentView = settingWindow.getContentView();
        //需要先测量，PopupWindow还未弹出时，宽高为0
        contentView.measure(SystemUtil.makeDropDownMeasureSpec(settingWindow.getWidth()),
                SystemUtil.makeDropDownMeasureSpec(settingWindow.getHeight()));

        int padding = DisplayUtil.dip2px(mActivity, 45);
        settingWindow.popAtTopRight();
        settingWindow.showAsDropDown(mSettingView, -250, -2 * padding - settingWindow.getContentView().getMeasuredHeight());
 
    }


    private void initSaveProgressDlg() {
        mSaveProgressDlg = new ProgressDialog(mActivity);
        mSaveProgressDlg.setMessage("正在保存,请稍候...");
        mSaveProgressDlg.setCancelable(false);
    }

    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_SAVE_FAILED:
                    mSaveProgressDlg.dismiss();
                    Toast.makeText(mActivity, "保存失败", Toast.LENGTH_SHORT).show();
                    break;
                case MSG_SAVE_SUCCESS:
                    mSaveProgressDlg.dismiss();
                    Intent intent = new Intent();
                    intent.putExtra(PenConfig.SAVE_PATH, mSavePath);
                    mActivity.setResult(RESULT_OK, intent);
                    mActivity.finish();
                    break;
                default:
                    break;
            }
        }
    };

    /**
     * 保存
     */
    private void save() {
        if (mPaintView.isEmpty()) {
            Toast.makeText(mActivity, "没有写入任何文字", Toast.LENGTH_SHORT).show();
            return;
        }
        //先检查是否有存储权限
        if (ContextCompat.checkSelfPermission(mActivity,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(mActivity, "没有读写存储的权限", Toast.LENGTH_SHORT).show();
            return;
        }
        if (mSaveProgressDlg == null) {
            initSaveProgressDlg();
        }
        mSaveProgressDlg.show();
        new Thread(() -> {
            try {
                Bitmap result = mPaintView.buildAreaBitmap(isCrop);
                if (PenConfig.FORMAT_JPG.equals(format) && bgColor == Color.TRANSPARENT) {
                    bgColor = Color.WHITE;
                }
                if (bgColor != Color.TRANSPARENT) {
                    result = BitmapUtil.drawBgToBitmap(result, bgColor);
                }
                if (result == null) {
                    mHandler.obtainMessage(MSG_SAVE_FAILED).sendToTarget();
                    return;
                }
                mSavePath = BitmapUtil.saveImage(mActivity, result, 100, format);
                if (mSavePath != null) {
                    mHandler.obtainMessage(MSG_SAVE_SUCCESS).sendToTarget();
                } else {
                    mHandler.obtainMessage(MSG_SAVE_FAILED).sendToTarget();
                }
            } catch (Exception e) {

            }
        }).start();

    }

    /**
     * 画布有操作
     */
    @Override
    public void onOperateStatusChanged() {
        mUndoView.setEnabled(mPaintView.canUndo());
        mRedoView.setEnabled(mPaintView.canRedo());
        mClearView.setEnabled(!mPaintView.isEmpty());

        BitmapUtil.setImage(mRedoView, R.drawable.sign_ic_redo, mPaintView.canRedo() ? PenConfig.THEME_COLOR : Color.LTGRAY);
        BitmapUtil.setImage(mUndoView, R.drawable.sign_ic_undo, mPaintView.canUndo() ? PenConfig.THEME_COLOR : Color.LTGRAY);
        BitmapUtil.setImage(mClearView, R.drawable.sign_ic_clear, !mPaintView.isEmpty() ? PenConfig.THEME_COLOR : Color.LTGRAY);

    }


    public void onBackPressed() {
        if (!mPaintView.isEmpty()) {
            showQuitTip();
        } else {
            mActivity.setResult(RESULT_CANCELED);
            mActivity.finish();
        }
    }

    /**
     * 弹出退出提示
     */
    private void showQuitTip() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
        builder.setTitle("提示")
                .setMessage("当前文字未保存，是否退出？")
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> {
                    mActivity.setResult(RESULT_CANCELED);
                    mActivity.finish();
                });
        builder.show();
    }
}
