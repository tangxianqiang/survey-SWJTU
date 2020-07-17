package com.doodle;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.doodle.core.IDoodle;
import com.doodle.core.IDoodleColor;
import com.doodle.core.IDoodleItem;
import com.doodle.core.IDoodleItemListener;
import com.doodle.core.IDoodlePen;
import com.doodle.core.IDoodleSelectableItem;
import com.doodle.core.IDoodleShape;
import com.doodle.core.IDoodleTouchDetector;
import com.doodle.util.ImageUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class DowerLayout extends FrameLayout implements View.OnClickListener { public final static int DEFAULT_MOSAIC_SIZE = 20; // 默认马赛克大小
    public final static int DEFAULT_COPY_SIZE = 20; // 默认仿制大小
    public final static int DEFAULT_TEXT_SIZE = 18; // 默认文字大小
    public final static int DEFAULT_BITMAP_SIZE = 80; // 默认贴图大小
    public static final int RESULT_ERROR = -111; // 出现错误
    public static final String KEY_PARAMS = "key_doodle_params";
    public static final String KEY_IMAGE_PATH = "key_image_path";

    private String mImagePath;
    private FrameLayout mFrameLayout;
    private IDoodle mDoodle;
    private DoodleView mDoodleView;
    private TextView mPaintSizeView;
    private View mBtnHidePanel, mSettingsPanel;
    private View mSelectedEditContainer;
    private TextView mItemScaleTextView;
    private View mBtnColor, mColorContainer;
    private SeekBar mEditSizeSeekBar;
    private View mShapeContainer, mPenContainer, mSizeContainer;
    private View mBtnUndo;
    private View mMosaicMenu;
    private View mEditBtn;
    private View mRedoBtn;

    private AlphaAnimation mViewShowAnimation, mViewHideAnimation; // view隐藏和显示时用到的渐变动画

    private DoodleParams mDoodleParams;

    // 触摸屏幕超过一定时间才判断为需要隐藏设置面板
    private Runnable mHideDelayRunnable;
    // 触摸屏幕超过一定时间才判断为需要显示设置面板
    private Runnable mShowDelayRunnable;

    private DoodleOnTouchGestureListener mTouchGestureListener;
    private Map<IDoodlePen, Float> mPenSizeMap = new HashMap<>(); //保存每个画笔对应的最新大小

    private int mMosaicLevel = -1;

    private ValueAnimator mRotateAnimator;

    private View rootView;

    public DowerLayout(@NonNull Context context) {
        this(context,null);
    }

    public DowerLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs,0);
    }

    public DowerLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context,attrs,defStyleAttr);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr) {
        // 涂鸦参数
        DoodleParams params = new DoodleParams();
        params.mIsFullScreen = true;
        // 图片路径
        params.mImagePath = "/storage/emulated/0/cn.ctnma.telemedicinecloud.debug/放行的扣分标准.jpg";
        // 初始画笔大小
        params.mPaintUnitSize = DoodleView.DEFAULT_SIZE;
        // 画笔颜色
        params.mPaintColor = Color.RED;
        // 是否支持缩放item
        params.mSupportScaleItem = true;
        mDoodleParams = params;
        if (mDoodleParams == null) {
            return;
        }

        mImagePath = mDoodleParams.mImagePath;
        if (mImagePath == null) {
            return;
        }
        Bitmap bitmap = ImageUtils.createBitmapFromPath(mImagePath, this.getContext());
        if (bitmap == null) {
            Log.i("tang","null bitmap ");
            return;
        }

        initView(context,bitmap);
        addView(rootView,new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));
    }

    private void initView(Context context,Bitmap bitmap) {
        rootView = LayoutInflater.from(context).inflate(R.layout.view_doodle_layout,null,false);
        mFrameLayout = rootView.findViewById(R.id.doodle_container);

        mDoodle = mDoodleView = new DoodleViewWrapper(getContext(), bitmap, mDoodleParams.mOptimizeDrawing, new IDoodleListener() {
            @Override
            public void onSaved(IDoodle doodle, Bitmap bitmap, Runnable callback) { // 保存图片为jpg格式
            }

            public void onError(int i, String msg) {

            }

            @Override
            public void onReady(IDoodle doodle) {
                mEditSizeSeekBar.setMax(Math.min(mDoodleView.getWidth(), mDoodleView.getHeight()));

                float size = mDoodleParams.mPaintUnitSize > 0 ? mDoodleParams.mPaintUnitSize * mDoodle.getUnitSize() : 0;
                if (size <= 0) {
                    size = mDoodleParams.mPaintPixelSize > 0 ? mDoodleParams.mPaintPixelSize : mDoodle.getSize();
                }
                // 设置初始值
                mDoodle.setSize(size);
                // 选择画笔
                mDoodle.setPen(DoodlePen.BRUSH);
                mDoodle.setShape(DoodleShape.HAND_WRITE);
                mDoodle.setColor(new DoodleColor(mDoodleParams.mPaintColor));
                mDoodle.setZoomerScale(mDoodleParams.mZoomerScale);
                mTouchGestureListener.setSupportScaleItem(mDoodleParams.mSupportScaleItem);

                // 每个画笔的初始值
                mPenSizeMap.put(DoodlePen.BRUSH, mDoodle.getSize());
                mPenSizeMap.put(DoodlePen.MOSAIC, DEFAULT_MOSAIC_SIZE * mDoodle.getUnitSize());
                mPenSizeMap.put(DoodlePen.COPY, DEFAULT_COPY_SIZE * mDoodle.getUnitSize());
                mPenSizeMap.put(DoodlePen.ERASER, mDoodle.getSize());
                mPenSizeMap.put(DoodlePen.TEXT, DEFAULT_TEXT_SIZE * mDoodle.getUnitSize());
                mPenSizeMap.put(DoodlePen.BITMAP, DEFAULT_BITMAP_SIZE * mDoodle.getUnitSize());
            }
        });

        mTouchGestureListener = new DoodleOnTouchGestureListener(mDoodleView, new DoodleOnTouchGestureListener.ISelectionListener() {
            // save states before being selected
            IDoodlePen mLastPen = null;
            IDoodleColor mLastColor = null;
            Float mSize = null;

            IDoodleItemListener mIDoodleItemListener = new IDoodleItemListener() {
                @Override
                public void onPropertyChanged(int property) {
                    if (mTouchGestureListener.getSelectedItem() == null) {
                        return;
                    }
                    if (property == IDoodleItemListener.PROPERTY_SCALE) {
                        mItemScaleTextView.setText(
                                (int) (mTouchGestureListener.getSelectedItem().getScale() * 100 + 0.5f) + "%");
                    }
                }
            };

            @Override
            public void onSelectedItem(IDoodle doodle, IDoodleSelectableItem selectableItem, boolean selected) {
                if (selected) {
                    if (mLastPen == null) {
                        mLastPen = mDoodle.getPen();
                    }
                    if (mLastColor == null) {
                        mLastColor = mDoodle.getColor();
                    }
                    if (mSize == null) {
                        mSize = mDoodle.getSize();
                    }
                    mDoodleView.setEditMode(true);
                    mDoodle.setPen(selectableItem.getPen());
                    mDoodle.setColor(selectableItem.getColor());
                    mDoodle.setSize(selectableItem.getSize());
                    mEditSizeSeekBar.setProgress((int) selectableItem.getSize());
                    mSelectedEditContainer.setVisibility(View.VISIBLE);
                    mSizeContainer.setVisibility(View.VISIBLE);
                    mItemScaleTextView.setText((int) (selectableItem.getScale() * 100 + 0.5f) + "%");
                    selectableItem.addItemListener(mIDoodleItemListener);
                } else {
                    selectableItem.removeItemListener(mIDoodleItemListener);

                    if (mTouchGestureListener.getSelectedItem() == null) { // nothing is selected. 当前没有选中任何一个item
                        if (mLastPen != null) {
                            mDoodle.setPen(mLastPen);
                            mLastPen = null;
                        }
                        if (mLastColor != null) {
                            mDoodle.setColor(mLastColor);
                            mLastColor = null;
                        }
                        if (mSize != null) {
                            mDoodle.setSize(mSize);
                            mSize = null;
                        }
                        mSelectedEditContainer.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onCreateSelectableItem(IDoodle doodle, float x, float y) {
                if (mDoodle.getPen() == DoodlePen.TEXT) {
                    createDoodleText(null, x, y);
                } else if (mDoodle.getPen() == DoodlePen.BITMAP) {
                    createDoodleBitmap(null, x, y);
                }
            }
        }) {
            @Override
            public void setSupportScaleItem(boolean supportScaleItem) {
                super.setSupportScaleItem(supportScaleItem);
                if (supportScaleItem) {
                    mItemScaleTextView.setVisibility(View.VISIBLE);
                } else {
                    mItemScaleTextView.setVisibility(View.GONE);
                }
            }
        };
        IDoodleTouchDetector detector = new DoodleTouchDetector(getContext(), mTouchGestureListener);
        mDoodleView.setDefaultTouchDetector(detector);

        mDoodle.setIsDrawableOutside(mDoodleParams.mIsDrawableOutside);
        mFrameLayout.addView(mDoodleView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        mDoodle.setDoodleMinScale(mDoodleParams.mMinScale);
        mDoodle.setDoodleMaxScale(mDoodleParams.mMaxScale);

        initViewAction();

        setOnClickListener();
    }

    public void setOnClickListener(){
        rootView.findViewById(R.id.iv_pen_hand).setOnClickListener(this);
        rootView.findViewById(R.id.iv_pen_mosaic).setOnClickListener(this);
        rootView.findViewById(R.id.iv_pen_eraser).setOnClickListener(this);
        rootView.findViewById(R.id.iv_pen_text).setOnClickListener(this);
        rootView.findViewById(R.id.iv_pen_bitmap).setOnClickListener(this);
        rootView.findViewById(R.id.iv_brush_edit).setOnClickListener(this);
        rootView.findViewById(R.id.iv_undo).setOnClickListener(this);
        rootView.findViewById(R.id.fl_set_color_container).setOnClickListener(this);
        rootView.findViewById(R.id.iv_hide_panel).setOnClickListener(this);
        rootView.findViewById(R.id.iv_save).setOnClickListener(this);
        rootView.findViewById(R.id.iv_back).setOnClickListener(this);
        rootView.findViewById(R.id.doodle_selectable_edit).setOnClickListener(this);
        rootView.findViewById(R.id.doodle_selectable_remove).setOnClickListener(this);
        rootView.findViewById(R.id.doodle_selectable_top).setOnClickListener(this);
        rootView.findViewById(R.id.doodle_selectable_bottom).setOnClickListener(this);
        rootView.findViewById(R.id.iv_hand_write).setOnClickListener(this);
        rootView.findViewById(R.id.iv_arrowhead).setOnClickListener(this);
        rootView.findViewById(R.id.iv_line).setOnClickListener(this);
        rootView.findViewById(R.id.iv_ring).setOnClickListener(this);
        rootView.findViewById(R.id.iv_circle).setOnClickListener(this);
        rootView.findViewById(R.id.iv_rect).setOnClickListener(this);
        rootView.findViewById(R.id.iv_fill_rect).setOnClickListener(this);
        rootView.findViewById(R.id.btn_mosaic_level1).setOnClickListener(this);
        rootView.findViewById(R.id.btn_mosaic_level2).setOnClickListener(this);
        rootView.findViewById(R.id.btn_mosaic_level3).setOnClickListener(this);
        rootView.findViewById(R.id.iv_redo).setOnClickListener(this);
        rootView.findViewById(R.id.iv_edit_mode).setOnClickListener(this);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initViewAction() {
        mBtnUndo = rootView.findViewById(R.id.iv_undo);
        mBtnUndo.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return true;
            }
        });
        mSelectedEditContainer = rootView.findViewById(R.id.doodle_selectable_edit_container);
        mSelectedEditContainer.setVisibility(View.GONE);
        mItemScaleTextView = rootView.findViewById(R.id.item_scale);
        mItemScaleTextView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mTouchGestureListener.getSelectedItem() != null) {
                    mTouchGestureListener.getSelectedItem().setScale(1);
                }
                return true;
            }
        });

        mSettingsPanel = rootView.findViewById(R.id.doodle_panel);
        mBtnHidePanel = rootView.findViewById(R.id.iv_hide_panel);
        mPaintSizeView = rootView.findViewById(R.id.paint_size_text);
        mShapeContainer = rootView.findViewById(R.id.ll_shape_container);
        mPenContainer = rootView.findViewById(R.id.ll_pen_container);
        mSizeContainer = rootView.findViewById(R.id.size_container);
        mMosaicMenu = rootView.findViewById(R.id.mosaic_menu);
        mEditBtn = rootView.findViewById(R.id.doodle_selectable_edit);
        mRedoBtn = rootView.findViewById(R.id.iv_redo);

        mBtnColor = rootView.findViewById(R.id.iv_color_set);
        mColorContainer = rootView.findViewById(R.id.fl_set_color_container);
        mEditSizeSeekBar = rootView.findViewById(R.id.doodle_seekbar_size);
        mEditSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress <= 0) {
                    mEditSizeSeekBar.setProgress(1);
                    return;
                }
                if ((int) mDoodle.getSize() == progress) {
                    return;
                }
                mDoodle.setSize(progress);
                if (mTouchGestureListener.getSelectedItem() != null) {
                    mTouchGestureListener.getSelectedItem().setSize(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        mDoodleView.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // 隐藏设置面板
                if (!mBtnHidePanel.isSelected()  // 设置面板没有被隐藏
                        && mDoodleParams.mChangePanelVisibilityDelay > 0) {
                    switch (event.getAction() & MotionEvent.ACTION_MASK) {
                        case MotionEvent.ACTION_DOWN:
                            mSettingsPanel.removeCallbacks(mHideDelayRunnable);
                            mSettingsPanel.removeCallbacks(mShowDelayRunnable);
                            //触摸屏幕超过一定时间才判断为需要隐藏设置面板
                            mSettingsPanel.postDelayed(mHideDelayRunnable, mDoodleParams.mChangePanelVisibilityDelay);
                            break;
                        case MotionEvent.ACTION_CANCEL:
                        case MotionEvent.ACTION_UP:
                            mSettingsPanel.removeCallbacks(mHideDelayRunnable);
                            mSettingsPanel.removeCallbacks(mShowDelayRunnable);
                            //离开屏幕超过一定时间才判断为需要显示设置面板
                            mSettingsPanel.postDelayed(mShowDelayRunnable, mDoodleParams.mChangePanelVisibilityDelay);
                            break;
                    }
                }

                return false;
            }
        });

        // 长按标题栏显示原图
//        rootView.findViewById(R.id.doodle_txt_title).setOnTouchListener(new View.OnTouchListener() {
//            @Override
//            public boolean onTouch(View v, MotionEvent event) {
//                switch (event.getAction() & MotionEvent.ACTION_MASK) {
//                    case MotionEvent.ACTION_DOWN:
//                        v.setPressed(true);
//                        mDoodle.setShowOriginal(true);
//                        break;
//                    case MotionEvent.ACTION_UP:
//                    case MotionEvent.ACTION_CANCEL:
//                        v.setPressed(false);
//                        mDoodle.setShowOriginal(false);
//                        break;
//                }
//                return true;
//            }
//        });

        mViewShowAnimation = new AlphaAnimation(0, 1);
        mViewShowAnimation.setDuration(150);
        mViewHideAnimation = new AlphaAnimation(1, 0);
        mViewHideAnimation.setDuration(150);
        mHideDelayRunnable = new Runnable() {
            public void run() {
                hideView(mSettingsPanel);
            }

        };
        mShowDelayRunnable = new Runnable() {
            public void run() {
                showView(mSettingsPanel);
            }
        };
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.iv_pen_hand) {
            mDoodle.setPen(DoodlePen.BRUSH);
        }else if(v.getId() == R.id.iv_edit_mode){
            mDoodleView.setEditMode(!mDoodleView.isEditMode());
            showBarLayout();
        } else if (v.getId() == R.id.iv_pen_mosaic) {
            mDoodle.setPen(DoodlePen.MOSAIC);
        } else if (v.getId() == R.id.iv_pen_eraser) {
            mDoodle.setPen(DoodlePen.ERASER);
        } else if (v.getId() == R.id.iv_pen_text) {
            mDoodle.setPen(DoodlePen.TEXT);
        } else if (v.getId() == R.id.iv_pen_bitmap) {
            mDoodle.setPen(DoodlePen.BITMAP);
        } else if (v.getId() == R.id.iv_brush_edit) {
            hideBarLayout();
            mDoodleView.setEditMode(!mDoodleView.isEditMode());
        } else if (v.getId() == R.id.iv_undo) {
            mDoodle.undo();
        } else if (v.getId() == R.id.fl_set_color_container) {
//            DoodleColor color = null;
//            if (mDoodle.getColor() instanceof DoodleColor) {
//                color = (DoodleColor) mDoodle.getColor();
//            }
//            if (color == null) {
//                return;
//            }
//            if (!(DoodleParams.getDialogInterceptor() != null
//                    && DoodleParams.getDialogInterceptor().onShow(getContext(), mDoodle, DoodleParams.DialogType.COLOR_PICKER))) {
//                boolean fullScreen = (getContext().getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0;
//                int themeId;
//                if (fullScreen) {
//                    themeId = android.R.style.Theme_Translucent_NoTitleBar_Fullscreen;
//                } else {
//                    themeId = android.R.style.Theme_Translucent_NoTitleBar;
//                }
//                new ColorPickerDialog(DoodleActivity.this,
//                        new ColorPickerDialog.OnColorChangedListener() {
//                            public void colorChanged(int color, int size) {
//                                mDoodle.setColor(new DoodleColor(color));
//                                mDoodle.setSize(size);
//                            }
//
//                            @Override
//                            public void colorChanged(Drawable color, int size) {
//                                Bitmap bitmap = ImageUtils.getBitmapFromDrawable(color);
//                                mDoodle.setColor(new DoodleColor(bitmap));
//                                mDoodle.setSize(size);
//                            }
//                        }, themeId).show(mDoodleView, mBtnColor.getBackground(), Math.min(mDoodleView.getWidth(), mDoodleView.getHeight()));
//            }
        } else if (v.getId() == R.id.iv_hide_panel) {
            mSettingsPanel.removeCallbacks(mHideDelayRunnable);
            mSettingsPanel.removeCallbacks(mShowDelayRunnable);
            v.setSelected(!v.isSelected());
            if (!mBtnHidePanel.isSelected()) {
                showView(mSettingsPanel);
                ((ImageView)v).setImageResource(R.drawable.ic_arrow_down);
            } else {
                hideView(mSettingsPanel);
                ((ImageView)v).setImageResource(R.drawable.ic_arrow_up);
            }
        } else if (v.getId() == R.id.iv_save) {
            mDoodle.save();
        } else if (v.getId() == R.id.iv_back) {
            if (mDoodle.getAllItem() == null || mDoodle.getItemCount() == 0) {
                return;
            }
//            if (!(DoodleParams.getDialogInterceptor() != null
//                    && DoodleParams.getDialogInterceptor().onShow(DoodleActivity.this, mDoodle, DoodleParams.DialogType.SAVE))) {
//                DialogController.showMsgDialog(DoodleActivity.this, getString(R.string.doodle_saving_picture), null, getString(R.string.doodle_cancel),
//                        getString(R.string.doodle_save), new View.OnClickListener() {
//                            @Override
//                            public void onClick(View v) {
//                                mDoodle.save();
//                            }
//                        }, new View.OnClickListener() {
//                            @Override
//                            public void onClick(View v) {
//                                finish();
//                            }
//                        });
//            }
        } else if (v.getId() == R.id.doodle_selectable_edit) {
            if (mTouchGestureListener.getSelectedItem() instanceof DoodleText) {
                createDoodleText((DoodleText) mTouchGestureListener.getSelectedItem(), -1, -1);
            } else if (mTouchGestureListener.getSelectedItem() instanceof DoodleBitmap) {
                createDoodleBitmap((DoodleBitmap) mTouchGestureListener.getSelectedItem(), -1, -1);
            }
        } else if (v.getId() == R.id.doodle_selectable_remove) {
            mDoodle.removeItem(mTouchGestureListener.getSelectedItem());
            mTouchGestureListener.setSelectedItem(null);
        } else if (v.getId() == R.id.doodle_selectable_top) {
            mDoodle.topItem(mTouchGestureListener.getSelectedItem());
        } else if (v.getId() == R.id.doodle_selectable_bottom) {
            mDoodle.bottomItem(mTouchGestureListener.getSelectedItem());
        } else if (v.getId() == R.id.iv_hand_write) {
            mDoodle.setShape(DoodleShape.HAND_WRITE);
        } else if (v.getId() == R.id.iv_arrowhead) {
            mDoodle.setShape(DoodleShape.ARROW);
        } else if (v.getId() == R.id.iv_line) {
            mDoodle.setShape(DoodleShape.LINE);
        } else if (v.getId() == R.id.iv_ring) {
            mDoodle.setShape(DoodleShape.HOLLOW_CIRCLE);
        } else if (v.getId() == R.id.iv_circle) {
            mDoodle.setShape(DoodleShape.FILL_CIRCLE);
        } else if (v.getId() == R.id.iv_rect) {
            mDoodle.setShape(DoodleShape.HOLLOW_RECT);
        } else if (v.getId() == R.id.iv_fill_rect) {
            mDoodle.setShape(DoodleShape.FILL_RECT);
        } else if (v.getId() == R.id.btn_mosaic_level1) {
            if (v.isSelected()) {
                return;
            }

            mMosaicLevel = DoodlePath.MOSAIC_LEVEL_1;
            mDoodle.setColor(DoodlePath.getMosaicColor(mDoodle, mMosaicLevel));
            v.setSelected(true);
            mMosaicMenu.findViewById(R.id.btn_mosaic_level2).setSelected(false);
            mMosaicMenu.findViewById(R.id.btn_mosaic_level3).setSelected(false);
            if (mTouchGestureListener.getSelectedItem() != null) {
                mTouchGestureListener.getSelectedItem().setColor(mDoodle.getColor().copy());
            }
        } else if (v.getId() == R.id.btn_mosaic_level2) {
            if (v.isSelected()) {
                return;
            }

            mMosaicLevel = DoodlePath.MOSAIC_LEVEL_2;
            mDoodle.setColor(DoodlePath.getMosaicColor(mDoodle, mMosaicLevel));
            v.setSelected(true);
            mMosaicMenu.findViewById(R.id.btn_mosaic_level1).setSelected(false);
            mMosaicMenu.findViewById(R.id.btn_mosaic_level3).setSelected(false);
            if (mTouchGestureListener.getSelectedItem() != null) {
                mTouchGestureListener.getSelectedItem().setColor(mDoodle.getColor().copy());
            }
        } else if (v.getId() == R.id.btn_mosaic_level3) {
            if (v.isSelected()) {
                return;
            }

            mMosaicLevel = DoodlePath.MOSAIC_LEVEL_3;
            mDoodle.setColor(DoodlePath.getMosaicColor(mDoodle, mMosaicLevel));
            v.setSelected(true);
            mMosaicMenu.findViewById(R.id.btn_mosaic_level1).setSelected(false);
            mMosaicMenu.findViewById(R.id.btn_mosaic_level2).setSelected(false);
            if (mTouchGestureListener.getSelectedItem() != null) {
                mTouchGestureListener.getSelectedItem().setColor(mDoodle.getColor().copy());
            }
        } else if (v.getId() == R.id.iv_redo) {
            if (!mDoodle.redo(1)) {
                mRedoBtn.setVisibility(View.GONE);
            }
        }
    }

    private void hideBarLayout() {
        rootView.findViewById(R.id.iv_save).setVisibility(View.GONE);
        rootView.findViewById(R.id.iv_hide_panel).setVisibility(View.GONE);
        rootView.findViewById(R.id.iv_dismiss).setVisibility(View.GONE);
        rootView.findViewById(R.id.iv_edit_mode).setVisibility(View.VISIBLE);
    }

    private void showBarLayout() {
        rootView.findViewById(R.id.iv_save).setVisibility(View.VISIBLE);
        rootView.findViewById(R.id.iv_hide_panel).setVisibility(View.VISIBLE);
        rootView.findViewById(R.id.iv_dismiss).setVisibility(View.VISIBLE);
        rootView.findViewById(R.id.iv_edit_mode).setVisibility(View.GONE);
    }

    private void showView(View view) {
        if (view.getVisibility() == View.VISIBLE) {
            return;
        }

        view.clearAnimation();
        view.startAnimation(mViewShowAnimation);
        view.setVisibility(View.VISIBLE);
    }

    private void hideView(View view) {
        if (view.getVisibility() != View.VISIBLE) {
            return;
        }
        view.clearAnimation();
        view.startAnimation(mViewHideAnimation);
        view.setVisibility(View.GONE);
    }

    private void createDoodleText(final DoodleText doodleText, final float x, final float y) {
//        if (isFinishing()) {
//            return;
//        }
//
//        DialogController.showInputTextDialog(this, doodleText == null ? null : doodleText.getText(), new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                String text = (v.getTag() + "").trim();
//                if (TextUtils.isEmpty(text)) {
//                    return;
//                }
//                if (doodleText == null) {
//                    IDoodleSelectableItem item = new DoodleText(mDoodle, text, mDoodle.getSize(), mDoodle.getColor().copy(), x, y);
//                    mDoodle.addItem(item);
//                    mTouchGestureListener.setSelectedItem(item);
//                } else {
//                    doodleText.setText(text);
//                }
//                mDoodle.refresh();
//            }
//        }, null);
//        if (doodleText == null) {
//            mSettingsPanel.removeCallbacks(mHideDelayRunnable);
//        }
    }

    private void createDoodleBitmap(final DoodleBitmap doodleBitmap, final float x, final float y) {
//        DialogController.showSelectImageDialog(this, new ImageSelectorView.ImageSelectorListener() {
//            @Override
//            public void onCancel() {
//            }
//
//            @Override
//            public void onEnter(List<String> pathList) {
//                Bitmap bitmap = ImageUtils.createBitmapFromPath(pathList.get(0), mDoodleView.getWidth() / 4, mDoodleView.getHeight() / 4);
//
//                if (doodleBitmap == null) {
//                    IDoodleSelectableItem item = new DoodleBitmap(mDoodle, bitmap, mDoodle.getSize(), x, y);
//                    mDoodle.addItem(item);
//                    mTouchGestureListener.setSelectedItem(item);
//                } else {
//                    doodleBitmap.setBitmap(bitmap);
//                }
//                mDoodle.refresh();
//            }
//        });
    }

    private boolean canChangeColor(IDoodlePen pen) {
        return pen != DoodlePen.ERASER
                && pen != DoodlePen.BITMAP
                && pen != DoodlePen.COPY
                && pen != DoodlePen.MOSAIC;
    }

    private class DoodleViewWrapper extends DoodleView {

        public DoodleViewWrapper(Context context, Bitmap bitmap, boolean optimizeDrawing, IDoodleListener listener) {
            super(context, bitmap, optimizeDrawing, listener);
        }

        private Map<IDoodlePen, Integer> mBtnPenIds = new HashMap<>();

        {
            mBtnPenIds.put(DoodlePen.BRUSH, R.id.iv_pen_hand);
            mBtnPenIds.put(DoodlePen.MOSAIC, R.id.iv_pen_mosaic);
            mBtnPenIds.put(DoodlePen.ERASER, R.id.iv_pen_eraser);
            mBtnPenIds.put(DoodlePen.TEXT, R.id.iv_pen_text);
            mBtnPenIds.put(DoodlePen.BITMAP, R.id.iv_pen_bitmap);
        }

        @Override
        public void setPen(IDoodlePen pen) {
            IDoodlePen oldPen = getPen();
            super.setPen(pen);

            mMosaicMenu.setVisibility(GONE);
            mEditBtn.setVisibility(View.GONE); // edit btn
            if (pen == DoodlePen.BITMAP || pen == DoodlePen.TEXT) {
                mEditBtn.setVisibility(View.VISIBLE); // edit btn
                mShapeContainer.setVisibility(GONE);
                if (pen == DoodlePen.BITMAP) {
                    mColorContainer.setVisibility(GONE);
                } else {
                    mColorContainer.setVisibility(VISIBLE);
                }
            } else if (pen == DoodlePen.MOSAIC) {
                mMosaicMenu.setVisibility(VISIBLE);
                mShapeContainer.setVisibility(VISIBLE);
                mColorContainer.setVisibility(GONE);
            } else {
                mShapeContainer.setVisibility(VISIBLE);
                if (pen == DoodlePen.COPY || pen == DoodlePen.ERASER) {
                    mColorContainer.setVisibility(GONE);
                } else {
                    mColorContainer.setVisibility(VISIBLE);
                }
            }
            setSingleSelected(mBtnPenIds.values(), mBtnPenIds.get(pen));

            if (mTouchGestureListener.getSelectedItem() == null) {
                mPenSizeMap.put(oldPen, getSize()); // save
                Float size = mPenSizeMap.get(pen); // restore
                if (size != null) {
                    mDoodle.setSize(size);
                }
                if (isEditMode()) {
                    mShapeContainer.setVisibility(GONE);
                    mColorContainer.setVisibility(GONE);
                    mMosaicMenu.setVisibility(GONE);
                }
            } else {
                mShapeContainer.setVisibility(GONE);
                return;
            }

            if (pen == DoodlePen.BRUSH) {
                Drawable colorBg = mBtnColor.getBackground();
                if (colorBg instanceof ColorDrawable) {
                    mDoodle.setColor(new DoodleColor(((ColorDrawable) colorBg).getColor()));
                } else {
                    mDoodle.setColor(new DoodleColor(((BitmapDrawable) colorBg).getBitmap()));
                }
            } else if (pen == DoodlePen.MOSAIC) {
                if (mMosaicLevel <= 0) {
                    mMosaicMenu.findViewById(R.id.btn_mosaic_level2).performClick();
                } else {
                    mDoodle.setColor(DoodlePath.getMosaicColor(mDoodle, mMosaicLevel));
                }
            } else if (pen == DoodlePen.COPY) {

            } else if (pen == DoodlePen.ERASER) {

            } else if (pen == DoodlePen.TEXT) {
                Drawable colorBg = mBtnColor.getBackground();
                if (colorBg instanceof ColorDrawable) {
                    mDoodle.setColor(new DoodleColor(((ColorDrawable) colorBg).getColor()));
                } else {
                    mDoodle.setColor(new DoodleColor(((BitmapDrawable) colorBg).getBitmap()));
                }
            } else if (pen == DoodlePen.BITMAP) {
                Drawable colorBg = mBtnColor.getBackground();
                if (colorBg instanceof ColorDrawable) {
                    mDoodle.setColor(new DoodleColor(((ColorDrawable) colorBg).getColor()));
                } else {
                    mDoodle.setColor(new DoodleColor(((BitmapDrawable) colorBg).getBitmap()));
                }
            }
        }

        private Map<IDoodleShape, Integer> mBtnShapeIds = new HashMap<>();

        {
            mBtnShapeIds.put(DoodleShape.HAND_WRITE, R.id.iv_hand_write);
            mBtnShapeIds.put(DoodleShape.ARROW, R.id.iv_arrowhead);
            mBtnShapeIds.put(DoodleShape.LINE, R.id.iv_line);
            mBtnShapeIds.put(DoodleShape.HOLLOW_CIRCLE, R.id.iv_ring);
            mBtnShapeIds.put(DoodleShape.FILL_CIRCLE, R.id.iv_circle);
            mBtnShapeIds.put(DoodleShape.HOLLOW_RECT, R.id.iv_rect);
            mBtnShapeIds.put(DoodleShape.FILL_RECT, R.id.iv_fill_rect);

        }

        @Override
        public void setShape(IDoodleShape shape) {
            super.setShape(shape);
            setSingleSelected(mBtnShapeIds.values(), mBtnShapeIds.get(shape));
        }

        TextView mPaintSizeView = rootView.findViewById(R.id.paint_size_text);

        @Override
        public void setSize(float paintSize) {
            super.setSize(paintSize);
            mEditSizeSeekBar.setProgress((int) paintSize);
            mPaintSizeView.setText("" + (int) paintSize);

            if (mTouchGestureListener.getSelectedItem() != null) {
                mTouchGestureListener.getSelectedItem().setSize(getSize());
            }
        }

        @Override
        public void setColor(IDoodleColor color) {
            IDoodlePen pen = getPen();
            super.setColor(color);

            DoodleColor doodleColor = null;
            if (color instanceof DoodleColor) {
                doodleColor = (DoodleColor) color;
            }
            if (doodleColor != null
                    && canChangeColor(pen)) {
                if (doodleColor.getType() == DoodleColor.Type.COLOR) {
                    mBtnColor.setBackgroundColor(doodleColor.getColor());
                } else if (doodleColor.getType() == DoodleColor.Type.BITMAP) {
                    mBtnColor.setBackgroundDrawable(new BitmapDrawable(doodleColor.getBitmap()));
                }

                if (mTouchGestureListener.getSelectedItem() != null) {
                    mTouchGestureListener.getSelectedItem().setColor(getColor().copy());
                }
            }

            if (doodleColor != null && pen == DoodlePen.MOSAIC
                    && doodleColor.getLevel() != mMosaicLevel) {
                switch (doodleColor.getLevel()) {
                    case DoodlePath.MOSAIC_LEVEL_1:
                        rootView.findViewById(R.id.btn_mosaic_level1).performClick();
                        break;
                    case DoodlePath.MOSAIC_LEVEL_2:
                        rootView.findViewById(R.id.btn_mosaic_level2).performClick();
                        break;
                    case DoodlePath.MOSAIC_LEVEL_3:
                        rootView.findViewById(R.id.btn_mosaic_level3).performClick();
                        break;
                }
            }
        }

        @Override
        public void enableZoomer(boolean enable) {
            super.enableZoomer(enable);
            if (enable) {
                Toast.makeText(getContext(), "x" + mDoodleParams.mZoomerScale, Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public boolean undo() {
            mTouchGestureListener.setSelectedItem(null);
            boolean res = super.undo();
            if (getRedoItemCount() > 0) {
                mRedoBtn.setVisibility(VISIBLE);
            } else {
                mRedoBtn.setVisibility(GONE);
            }
            return res;
        }

        @Override
        public void clear() {
            super.clear();
            mTouchGestureListener.setSelectedItem(null);
            mRedoBtn.setVisibility(GONE);
        }

        @Override
        public void addItem(IDoodleItem item) {
            super.addItem(item);
            if (getRedoItemCount() > 0) {
                mRedoBtn.setVisibility(VISIBLE);
            } else {
                mRedoBtn.setVisibility(GONE);
            }
        }

        View mBtnEditMode = rootView.findViewById(R.id.iv_brush_edit);
        Boolean mLastIsDrawableOutside = null;

        @Override
        public void setEditMode(boolean editMode) {
            if (editMode == isEditMode()) {
                return;
            }

            super.setEditMode(editMode);
            mBtnEditMode.setSelected(editMode);
            if (editMode) {
                Toast.makeText(getContext(), R.string.doodle_edit_mode, Toast.LENGTH_SHORT).show();
                mLastIsDrawableOutside = mDoodle.isDrawableOutside(); // save
                mDoodle.setIsDrawableOutside(true);
                mPenContainer.setVisibility(GONE);
                mShapeContainer.setVisibility(GONE);
                mSizeContainer.setVisibility(GONE);
                mColorContainer.setVisibility(GONE);
                mBtnUndo.setVisibility(GONE);
                mMosaicMenu.setVisibility(GONE);
            } else {
                if (mLastIsDrawableOutside != null) { // restore
                    mDoodle.setIsDrawableOutside(mLastIsDrawableOutside);
                }
                mTouchGestureListener.center(); // center picture
                if (mTouchGestureListener.getSelectedItem() == null) { // restore
                    setPen(getPen());
                }
                mTouchGestureListener.setSelectedItem(null);
                mPenContainer.setVisibility(VISIBLE);
                mSizeContainer.setVisibility(VISIBLE);
                mBtnUndo.setVisibility(VISIBLE);
            }
        }

        private void setSingleSelected(Collection<Integer> ids, int selectedId) {
            for (int id : ids) {
                if (id == selectedId) {
                    rootView.findViewById(id).setSelected(true);
                } else {
                    rootView.findViewById(id).setSelected(false);
                }
            }
        }
    }
}