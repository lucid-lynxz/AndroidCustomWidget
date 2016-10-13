package org.lynxz.customwidgetlibrary;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;

import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * Created by zxz on 2016/10/12.
 * description : 封装surfaceView和mediaPlayer 处理在断网时,小米pad上销毁时anr的问题
 * 注意: 目前只支持播放在线视频
 * VideoView销毁时,会去释放mediaPlayer,但是这一步骤经常导致anr,需要异步去释放,
 * 而系统自带的VideoView无法满足这一条
 * 另外,pause/stop/seekTo等操作也非常容易导致anr,因此这里要求有已经加载成功有缓冲后才允许执行这些操作
 */
public class BaseVideoView extends SurfaceView implements SurfaceHolder.Callback {

    private static final String TAG = "BaseVideoView";

    // all possible internal states
    private static final int STATE_ERROR = -1;
    private static final int STATE_IDLE = 0;//初始状态
    private static final int STATE_PREPARING = 1;
    private static final int STATE_PREPARED = 2;
    private static final int STATE_PLAYING = 3;
    private static final int STATE_PAUSED = 4;
    private static final int STATE_PLAYBACK_COMPLETED = 5;//正常播放结束
    private static final int STATE_RELEASING = 6;//正在异步释放中
    private static final int STATE_STOPED = 7;// 返回桌面,切换页面等

    // mCurrentState is a VideoView object's current state.
    // mTargetState is the state that a method caller intends to reach.
    // For instance, regardless the VideoView object's current state,
    // calling pause() intends to bring the object to a target state
    // of STATE_PAUSED.
    private int mCurrentState = STATE_IDLE;
    private int mTargetState = STATE_IDLE;
    private int mSeekWhenPrepared = 0;  // recording the seek position while preparing

    private SurfaceHolder mHolder;
    private MediaPlayer mPlayer;
    private String mVideoUrl;

    //监听器
    private MediaPlayer.OnPreparedListener mOnPreparedListener;
    private MediaPlayer.OnErrorListener mOnErrorListener;
    private MediaPlayer.OnCompletionListener mOnCompletionListener;

    private int mCurrentBufferPercentage = -1;//当前已缓冲进度
    // surface创建后才去初始化mediaPlayer,否则可能出现有声音无图像,黑屏
    private boolean mSurfaceCreated = false;

    // 获取视频尺寸,用于自动调整画面比例
    private int mVideoWidth;
    private int mVideoHeight;
    private Subscription mReleaseSubscription;


    public BaseVideoView(Context context) {
        this(context, null);
    }

    public BaseVideoView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BaseVideoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView();
    }

    private void initView() {
        mHolder = getHolder();
        mHolder.addCallback(this);
    }

    public void setVideoURI(Uri uri) {
        setVideoPath(uri.getPath());
    }

    /**
     * 获取视频路径信息
     */
    public String getVideoPath() {
        return mVideoUrl;
    }

    public void setVideoPath(String path) {
        //        Log.i(TAG, "setVideoPath ");
        // TODO: 2016/10/12 判断path合法性
        mVideoUrl = path;
        mTargetState = STATE_PLAYING;
        updatePlayerPath();
        //        requestLayout();
        //        invalidate();
    }

    /**
     * 跳转到指定的播放位置
     * 为避免断网等操作造成的大概率anr,这里要求有缓冲进度
     */
    public void seekTo(int msec) {
        if (isInPlaybackState() && mCurrentBufferPercentage > 0) {
            mPlayer.seekTo(msec);
            mSeekWhenPrepared = 0;
        } else {
            mSeekWhenPrepared = msec;
        }
    }

    /**
     * 开始播放
     */
    public void start() {
        //        Log.i(TAG, "start ");
        mTargetState = STATE_PLAYING;
        if (isInPlaybackState()) {
            if (mCurrentState == STATE_STOPED) {
                mCurrentState = STATE_PREPARING;
                mPlayer.prepareAsync();
            } else {
                mPlayer.start();
                mCurrentState = STATE_PLAYING;
            }
        }
    }

    /**
     * 暂停
     */
    public void pause() {
        //        Log.i(TAG, "pause ");
        mTargetState = STATE_PAUSED;
        if (isInPlaybackState() && mCurrentBufferPercentage > 0) {
            if (mPlayer.isPlaying()) {
                mPlayer.pause();
                mCurrentState = STATE_PAUSED;
            }
        }
    }

    /**
     * 停止播放
     * 不懂为啥不叫stop()...
     */
    public void stopPlayback() {
        Log.i(TAG, "stopPlayback " + mCurrentState);
        mTargetState = STATE_STOPED;
        // 这里去stop的话依然有可能anr
        // onPrepared,start()后,但是还未开始播放,此时mCurrentState=state_playing
        if (isInPlaybackState() && mCurrentBufferPercentage > 0) {
            mPlayer.stop();
            mCurrentState = STATE_STOPED;
        }
    }

    /**
     * 获取当前播放进度:毫秒
     */
    public int getCurrentPosition() {
        if (isInPlaybackState()) {
            return mPlayer.getCurrentPosition();
        }
        return 0;
    }

    /**
     * 获取视频总时长
     */
    public int getDuration() {
        if (isInPlaybackState()) {
            return mPlayer.getDuration();
        }
        return -1;
    }

    /**
     * 获取缓存进度
     */
    public int getBufferPercentage() {
        if (mPlayer != null) {
            return mCurrentBufferPercentage;
        }
        return 0;
    }

    /**
     * 当前播放器是否正在播放
     */
    public boolean isPlaying() {
        return isInPlaybackState() && mPlayer.isPlaying();
    }

    private boolean isInPlaybackState() {
        return (mPlayer != null &&
                mCurrentState != STATE_ERROR &&
                mCurrentState != STATE_IDLE &&
                mCurrentState != STATE_PREPARING &&
                mCurrentState != STATE_RELEASING);
    }

    public boolean canPause() {
        return isInPlaybackState();
    }


    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        //        Log.i(TAG, "surfaceCreated ");
        mSurfaceCreated = true;
        updatePlayerPath();

    }

    /**
     * 初始化MediaPlayer
     */
    private void initPlayer() {
        if (mPlayer == null) {
            //            Log.i(TAG, "...initPlayer ");
            mPlayer = new MediaPlayer();
            mCurrentState = STATE_IDLE;

            mPlayer.setOnBufferingUpdateListener(mBufferingUpdateListener);
            mPlayer.setOnPreparedListener(mPreparedListener);
            mPlayer.setOnVideoSizeChangedListener(mSizeChangedListener);
            mPlayer.setOnCompletionListener(mCompletionListener);
            mPlayer.setOnErrorListener(mErrorListener);
            //            mPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
            //                public void onSeekComplete(MediaPlayer m) {
            //                    Log.i(TAG, "onSeekComplete ");
            //                    m.start();
            //                }
            //            });

            mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mPlayer.setDisplay(mHolder);
            mPlayer.setScreenOnWhilePlaying(true);
        }
    }

    /**
     * 将视频地址设置到mediaPlayer中,并异步加载播放
     * 若已有播放,则release后再设置
     */
    private void updatePlayerPath() {
        //        Log.i(TAG, "updatePlayerPath ");
        releasePlayerAsync();
    }

    private void resetPlayerUrl() {
        try {
            if (!TextUtils.isEmpty(mVideoUrl) && mPlayer != null) {
                //                Log.i(TAG, "resetPlayerUrl ");
                // idle状态才能设置视频地址,之后mPlayer进入initialized状态
                if (mCurrentState == STATE_IDLE) {
                    mPlayer.setDataSource(mVideoUrl);
                    mPlayer.prepareAsync();
                    mCurrentState = STATE_PREPARING;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "resetPlayerUrl error " + e.getMessage());
            e.printStackTrace();
            mCurrentState = STATE_ERROR;
            mTargetState = STATE_ERROR;
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        //        Log.i(TAG, "surfaceChanged ");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        //        Log.i(TAG, "surfaceDestroyed ");
        mTargetState = STATE_IDLE;
        mCurrentBufferPercentage = 0;
        mSurfaceCreated = false;
        releasePlayerAsync();
    }

    /**
     * 进行异步释放mediaPlayer,这是小米pad上anr的主要问题
     */
    private void releasePlayerAsync() {
        if (mCurrentState == STATE_RELEASING) {
            Log.i(TAG, "正在releasePlayerAsync,return...");
            return;
        }

        Subscriber<Boolean> mReleaseSubscriber = new Subscriber<Boolean>() {
            @Override
            public void onCompleted() {
            }

            @Override
            public void onError(Throwable e) {
                e.printStackTrace();
                Log.e(TAG, "releasePlayerAsync... onError " + e.getMessage());
                mCurrentState = STATE_ERROR;
            }

            @Override
            public void onNext(Boolean b) {
                mCurrentState = STATE_IDLE;
                //如果用户重新设置视频链接，则在释放后重新加载
                if (mTargetState == STATE_PLAYING && mSurfaceCreated) {
                    initPlayer();
                    resetPlayerUrl();
                }
            }
        };

        mReleaseObservable.observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(mReleaseSubscriber);

    }

    Observable<Boolean> mReleaseObservable = Observable.create(new Observable.OnSubscribe<Boolean>() {
        @Override
        public void call(Subscriber<? super Boolean> subscriber) {
            try {
                mCurrentState = STATE_RELEASING;
                if (mPlayer != null && mCurrentState != STATE_IDLE) {
                    if (isInPlaybackState() && mCurrentBufferPercentage > 0) {
                        mPlayer.stop();
                    }
                    mPlayer.release();
                }
                mPlayer = null;
                subscriber.onNext(true);
                subscriber.onCompleted();
            } catch (Exception e) {
                Log.e(TAG, "mReleaseObservable Exception " + e.getMessage());
                e.printStackTrace();
                subscriber.onError(e);
            }
        }
    });

    /**
     * 比较懒,直接复制的VideoView中的内容
     * 主要就是利用 {@linkplain #mSizeChangedListener} 监听,根据视频尺寸来自适应的,不然会被强制拉伸
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        //Log.i("@@@@", "onMeasure(" + MeasureSpec.toString(widthMeasureSpec) + ", "
        //        + MeasureSpec.toString(heightMeasureSpec) + ")");

        int width = getDefaultSize(mVideoWidth, widthMeasureSpec);
        int height = getDefaultSize(mVideoHeight, heightMeasureSpec);
        if (mVideoWidth > 0 && mVideoHeight > 0) {

            int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
            int widthSpecSize = MeasureSpec.getSize(widthMeasureSpec);
            int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
            int heightSpecSize = MeasureSpec.getSize(heightMeasureSpec);

            if (widthSpecMode == MeasureSpec.EXACTLY && heightSpecMode == MeasureSpec.EXACTLY) {
                // the size is fixed
                width = widthSpecSize;
                height = heightSpecSize;

                // for compatibility, we adjust size based on aspect ratio
                if (mVideoWidth * height < width * mVideoHeight) {
                    //Log.i("@@@", "image too wide, correcting");
                    width = height * mVideoWidth / mVideoHeight;
                } else if (mVideoWidth * height > width * mVideoHeight) {
                    //Log.i("@@@", "image too tall, correcting");
                    height = width * mVideoHeight / mVideoWidth;
                }
            } else if (widthSpecMode == MeasureSpec.EXACTLY) {
                // only the width is fixed, adjust the height to match aspect ratio if possible
                width = widthSpecSize;
                height = width * mVideoHeight / mVideoWidth;
                if (heightSpecMode == MeasureSpec.AT_MOST && height > heightSpecSize) {
                    // couldn't match aspect ratio within the constraints
                    height = heightSpecSize;
                }
            } else if (heightSpecMode == MeasureSpec.EXACTLY) {
                // only the height is fixed, adjust the width to match aspect ratio if possible
                height = heightSpecSize;
                width = height * mVideoWidth / mVideoHeight;
                if (widthSpecMode == MeasureSpec.AT_MOST && width > widthSpecSize) {
                    // couldn't match aspect ratio within the constraints
                    width = widthSpecSize;
                }
            } else {
                // neither the width nor the height are fixed, try to use actual video size
                width = mVideoWidth;
                height = mVideoHeight;
                if (heightSpecMode == MeasureSpec.AT_MOST && height > heightSpecSize) {
                    // too tall, decrease both width and height
                    height = heightSpecSize;
                    width = height * mVideoWidth / mVideoHeight;
                }
                if (widthSpecMode == MeasureSpec.AT_MOST && width > widthSpecSize) {
                    // too wide, decrease both width and height
                    width = widthSpecSize;
                    height = width * mVideoHeight / mVideoWidth;
                }
            }
        } else {
            // no size yet, just adopt the given spec sizes
        }
        setMeasuredDimension(width, height);
    }

    MediaPlayer.OnVideoSizeChangedListener mSizeChangedListener =
            new MediaPlayer.OnVideoSizeChangedListener() {
                public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
                    mVideoWidth = mp.getVideoWidth();
                    mVideoHeight = mp.getVideoHeight();
                    if (mVideoWidth != 0 && mVideoHeight != 0) {
                        getHolder().setFixedSize(mVideoWidth, mVideoHeight);
                        requestLayout();
                    }
                }
            };

    private MediaPlayer.OnBufferingUpdateListener mBufferingUpdateListener =
            new MediaPlayer.OnBufferingUpdateListener() {
                public void onBufferingUpdate(MediaPlayer mp, int percent) {
                    //                    Log.i(TAG, "onBufferingUpdate " + percent);
                    // 这里有坑 o_O ,第一次回调的时候percent是100 （>﹏<）
                    // 所以 mCurrentBufferPercentage 初值设置为负值,并在这里过滤掉第一次的回调值
                    if (mCurrentBufferPercentage < 0 && percent >= 100) {
                        percent = 0;
                    }
                    mCurrentBufferPercentage = percent;
                    // seekTo()方法也有可能anr （╯－_－）╯╧╧ ,因此现在要求有缓存后才能seekTo
                    if (mSeekWhenPrepared > 0) {
                        seekTo(mSeekWhenPrepared);
                    }
                }
            };

    public void setOnPreparedListener(MediaPlayer.OnPreparedListener l) {
        mOnPreparedListener = l;
    }

    /**
     * 设置异常发生,回调接口
     */
    public void setOnErrorListener(MediaPlayer.OnErrorListener l) {
        mOnErrorListener = l;
    }

    public void setOnCompletionListener(MediaPlayer.OnCompletionListener l) {
        mOnCompletionListener = l;
    }

    MediaPlayer.OnPreparedListener mPreparedListener = new MediaPlayer.OnPreparedListener() {
        @Override
        public void onPrepared(MediaPlayer mp) {
            mCurrentState = STATE_PREPARED;

            // 设置当前surfaceView尺寸为视频尺寸,以便保持画面比例
            mVideoWidth = mp.getVideoWidth();
            mVideoHeight = mp.getVideoHeight();
            if (mVideoWidth != 0 && mVideoHeight != 0) {
                getHolder().setFixedSize(mVideoWidth, mVideoHeight);
            }

            // 初始设置时要求直接播放
            if (mTargetState == STATE_PLAYING) {
                mPlayer.start();
                mCurrentState = STATE_PLAYING;
            }

            /**
             *  TODO: 2016/10/13 这里是不是也得判断下网络啊?
             *  videoView直接就seekTo,网络异常又还未缓冲到点时应该会anr
             *  但是其实也很难避免seekTo时网络是好的,我在红米1s上测试就是断网后还要延迟一下才会收到消息
             *  这时候去拖拽进度条仍会触发seekTo
             * */
            if (mSeekWhenPrepared > 0) {
                seekTo(mSeekWhenPrepared);
            }

            // 反馈给调用者,mPlayer已经加载成功
            if (mOnPreparedListener != null) {
                mOnPreparedListener.onPrepared(mPlayer);
            }
        }
    };

    /**
     * MediaPlayer错误监听器
     */
    private MediaPlayer.OnErrorListener mErrorListener = new MediaPlayer.OnErrorListener() {
        @Override
        public boolean onError(MediaPlayer mp, int what, int extra) {
            Log.d(TAG, "Error: " + what + "," + extra);
            mCurrentState = STATE_ERROR;
            mTargetState = STATE_ERROR;
            if (mOnErrorListener != null) {
                if (mOnErrorListener.onError(mPlayer, what, extra)) {
                    return true;
                }
            }
            return false;
        }
    };

    /**
     * 播放结束后通知调用者
     */
    private MediaPlayer.OnCompletionListener mCompletionListener =
            new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer mp) {
                    mCurrentState = STATE_PLAYBACK_COMPLETED;
                    mTargetState = STATE_PLAYBACK_COMPLETED;
                    if (mOnCompletionListener != null) {
                        mOnCompletionListener.onCompletion(mPlayer);
                    }
                }
            };


}
