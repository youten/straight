
package youten.redo.smartextension.straight.sensor;

import com.sonyericsson.extras.liveware.extension.util.sensor.AccessorySensorEvent;

public class ExtensionMotion {
    public static final String POSITION_FRONT_UP = "FRONT"; // 腕時計上面が上を向いている
    public static final String POSITION_BACK_UP = "BACK"; // 腕時計背面が上を向いている
    public static final String POSITION_RIGHT_UP = "RIGHT"; // 右側面が上を向いている
    public static final String POSITION_LEFT_UP = "LEFT"; // 左側面が上を向いている
    public static final String POSITION_BOTTOM_UP = "BOTTOM"; // 下部側面が上を向いている
    public static final String POSITION_TOP_UP = "TOP"; // 上部側面が上を向いている
    public static final String POSITION_UNKNOWN = "Unknown"; // どの状態でもない

    // Fling:FRONTからRIGHT, LEFT, BOTTOM, TOPに傾けてすぐ戻す操作
    public static final String FLING_INSIDE = "FLING IN"; // 下部側面を下に向けてすぐ戻す
    public static final String FLING_OUTSIDE = "FLING OUT"; // 上部側面を下に向けてすぐ戻す
    public static final String FLING_LEFT = "FLING LEFT"; // 左側面を下に向けてすぐ戻す
    public static final String FLING_RIGHT = "FLING RIGHT"; // 右側面を下に向けてすぐ戻す
    public static final String NO_FLING = "NO FLING"; // どのFling操作でもない

    public interface MotionListener {
        public void onPositionChanged(String fromPosition, String toPosition);

        public void onSeiken(float score);

        public void onFling(String fling);

        public void onJump();
    };

    // xyzの絶対値のいずれかの絶対値がこの値を超えている
    private static final float POSITION_IN_BORDER = 7.0f;
    private float[] mNowXYZ = new float[3];
    private String mPosition = POSITION_UNKNOWN;
    private MotionListener mListener = null;

    // fling用前回RIGHT, LEFT, BOTTOM, TOPになった時刻
    private static final long FLING_TIME_MAX = 1000; // 1000ms以内の操作であればFlingと判定
    private long mLastPreFlingTime = 0;

    // PYONPYON (ジャンプ)
    // xyzの絶対値の最大値がこの値を下回ったら（落下方向に）に移動中
    private static final float MOVE_FALLING_MAX = 5.0f;
    // xyzの絶対値の最大値がこの値を上回ったら（上り方向に）に移動中
    private static final float MOVE_JUMPING_MIN = 15.0f;

    private static final String MOVE_NONE = "NONE";
    private static final String MOVE_FALLING = "FALLING";
    private static final String MOVE_JUMPING = "JUMPING";
    private String mMoving = MOVE_NONE;
    private static final long MAX_PYONPYON_TIME_MS = 1000;
    private long mLastJumpTime = 0;

    // SEIKENスコア用X軸最低/最大スコア
    private float mMinX = 0.0f;
    private float mMaxX = 0.0f;
    private long mSeikenStartTime = 0; // 正拳開始時刻。0は正拳中ではない。
    private static final long MAX_SEIKEN_TIME_MS = 3000; //

    public ExtensionMotion(MotionListener listener) {
        mNowXYZ[0] = 0.0f;
        mNowXYZ[1] = 0.0f;
        mNowXYZ[2] = 0.0f;
        mPosition = POSITION_UNKNOWN;
        mListener = listener;
    }

    public MotionListener getListener() {
        return mListener;
    }

    public void setListener(MotionListener listener) {
        mListener = listener;
    }

    public String getPosition() {
        return mPosition;
    }

    /**
     * 発生したイベントを設定する
     * 
     * @param event
     * @return
     */
    public boolean pushEvent(AccessorySensorEvent event) {
        if ((event == null) || (event.getSensorValues() == null)
                || (event.getSensorValues().length != 3)) {
            return false;
        }
        // ローパスフィルタ http://android.ohwada.jp/archives/334
        mNowXYZ[0] = mNowXYZ[0] * 0.8f + event.getSensorValues()[0] * 0.2f;
        mNowXYZ[1] = mNowXYZ[1] * 0.8f + event.getSensorValues()[1] * 0.2f;
        mNowXYZ[2] = mNowXYZ[2] * 0.8f + event.getSensorValues()[2] * 0.2f;

        // Log.d(StraightExtensionService.LOG_TAG,
        //        String.format("x=%.1f y=%.1f z=%.1f", mNowXYZ[0], mNowXYZ[1], mNowXYZ[2]));

        // 移動判定
        float max = Math.max(Math.max(abs(mNowXYZ[0]), abs(mNowXYZ[1])), abs(mNowXYZ[2]));
        String newMove = MOVE_NONE;
        if (max < MOVE_JUMPING_MIN) {
            newMove = MOVE_JUMPING;
        } else if (max > MOVE_FALLING_MAX) {
            newMove = MOVE_FALLING;
        }
        if (!mMoving.equals(newMove)) {
            if (MOVE_JUMPING.equals(newMove)) {
                mLastJumpTime = System.currentTimeMillis();
            } else if (MOVE_FALLING.equals(newMove)) {
                long now = System.currentTimeMillis();
                if (MAX_PYONPYON_TIME_MS < (now - mLastJumpTime)) {
                    if (mListener != null) {
                        mListener.onJump();
                    }
                }
                mLastJumpTime = 0;
            }
        }

        // 正拳スコア更新
        if (mSeikenStartTime > 0) {
            if (mNowXYZ[0] < mMinX) {
                mMinX = mNowXYZ[0];
            }
            if (mMaxX < mNowXYZ[0]) {
                mMaxX = mNowXYZ[0];
            }
            long now = System.currentTimeMillis();
            if (MAX_SEIKEN_TIME_MS < (now - mSeikenStartTime)) {
                mSeikenStartTime = 0;
            }
        }

        String newPosition = POSITION_UNKNOWN;
        if (mNowXYZ[0] > POSITION_IN_BORDER) {
            newPosition = POSITION_RIGHT_UP;
        } else if (mNowXYZ[0] < -POSITION_IN_BORDER) {
            newPosition = POSITION_LEFT_UP;
        } else if (mNowXYZ[1] > POSITION_IN_BORDER) {
            newPosition = POSITION_TOP_UP;
        } else if (mNowXYZ[1] < -POSITION_IN_BORDER) {
            newPosition = POSITION_BOTTOM_UP;
        } else if (mNowXYZ[2] > POSITION_IN_BORDER) {
            newPosition = POSITION_FRONT_UP;
            if (mSeikenStartTime > 0) {
                long now = System.currentTimeMillis();
                if (MAX_SEIKEN_TIME_MS > (now - mSeikenStartTime)) {
                    if (mListener != null) {
                        mListener.onSeiken(mMaxX - mMinX);
                    }
                    mSeikenStartTime = 0;
                }
            }
        } else if (mNowXYZ[2] < -POSITION_IN_BORDER) {
            newPosition = POSITION_BACK_UP;
            mMinX = 0.0f;
            mMaxX = 0.0f;
            mSeikenStartTime = System.currentTimeMillis();
        }

        if (!POSITION_UNKNOWN.equals(newPosition) && !mPosition.equals(newPosition)) {
            if (mListener != null) {
                mListener.onPositionChanged(mPosition, newPosition);

                if (mLastPreFlingTime > 0) {
                    // RLTB -> FRONT && FLING_TIME_MAX ms以内
                    long now = System.currentTimeMillis();
                    if (newPosition.equals(POSITION_FRONT_UP)
                            && (now - mLastPreFlingTime < FLING_TIME_MAX)) {
                        if (mPosition.equals(POSITION_RIGHT_UP)) {
                            mListener.onFling(FLING_LEFT);
                        } else if (mPosition.equals(POSITION_LEFT_UP)) {
                            mListener.onFling(FLING_RIGHT);
                        } else if (mPosition.equals(POSITION_TOP_UP)) {
                            mListener.onFling(FLING_INSIDE);
                        } else if (mPosition.equals(POSITION_BOTTOM_UP)) {
                            mListener.onFling(FLING_OUTSIDE);
                        }
                    }
                    mLastPreFlingTime = 0;
                } else {
                    // FRONT -> RLTB
                    if (mPosition.equals(POSITION_FRONT_UP)) {
                        if (newPosition.equals(POSITION_RIGHT_UP)
                                || newPosition.equals(POSITION_LEFT_UP)
                                || newPosition.equals(POSITION_TOP_UP)
                                || newPosition.equals(POSITION_BOTTOM_UP)) {
                            mLastPreFlingTime = System.currentTimeMillis();
                        }
                    }
                }
            }
            mPosition = newPosition;
        }

        return true;
    }

    private float abs(float f) {
        if (f < 0) {
            return -f;
        }
        return f;
    }
}
