
package youten.redo.smartextension.straight.sensor;

import youten.redo.smartextension.straight.StraightExtensionService;
import android.util.Log;

import com.sonyericsson.extras.liveware.extension.util.sensor.AccessorySensorEvent;

public class ExtensionMotion {
    public static final String POSITION_FRONT_UP = "position_front_up"; // 腕時計上面が上を向いている
    public static final String POSITION_BACK_UP = "position_back_up"; // 腕時計背面が上を向いている
    public static final String POSITION_RIGHT_UP = "position_right_up"; // 右側面が上を向いている
    public static final String POSITION_LEFT_UP = "position_left_up"; // 左側面が上を向いている
    public static final String POSITION_BOTTOM_UP = "position_bottom_up"; // 下部側面が上を向いている
    public static final String POSITION_TOP_UP = "position_top_up"; // 上部側面が上を向いている
    public static final String POSITION_UNKNOWN = "position_unknown"; // どの状態でもない

    public interface MotionListener {
        public void onPositionChanged(String fromPosition, String toPosition);

        public void onSeiken(float score);
    };

    // xyzの絶対値のいずれかの絶対値がこの値を超えている
    private static final float POSITION_IN_BORDER = 9.0f;
    // xyzの絶対値の合計値がこの値を下回ったら（落下方向に）に移動中
    private static final float MOVEMENT_SUM_MIN = 9.0f;
    // xyzの絶対値の合計値がこの値を上回ったら（他の向きに）に移動中
    private static final float MOVEMENT_SUM_MAX = 12.0f;
    private float[] mNowXYZ = new float[3];
    private String mPosition = POSITION_UNKNOWN;
    private MotionListener mListener = null;

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
        boolean moving = false;
        float sum = abs(mNowXYZ[0]) + abs(mNowXYZ[1]) + abs(mNowXYZ[2]);
        if ((sum < MOVEMENT_SUM_MIN) || (MOVEMENT_SUM_MAX > sum)) {
            moving = true;
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
        } else if (mNowXYZ[2] < -POSITION_IN_BORDER) {
            newPosition = POSITION_BACK_UP;
        }

        if (!POSITION_UNKNOWN.equals(newPosition) && !mPosition.equals(newPosition)) {
            if (mListener != null) {
                mListener.onPositionChanged(mPosition, newPosition);
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
