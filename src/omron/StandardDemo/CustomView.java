/*
 * Copyright (C) 2014-2015 OMRON Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package omron.StandardDemo;

import java.util.Locale;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import omron.HVC.HVC;
import omron.HVC.HVC_BLE;
import omron.HVC.HVC_RES;
import omron.HVC.HVC_RES.DetectionResult;
import omron.HVC.HVC_RES.FaceResult;

public class CustomView extends View
{
    private Activity mActivity = null;
    
    private int mExecuteFlag = 0;
    private HVC_BLE mHVCDevice = null;
    private HVC_RES mHVCResult = null;
    private ExecThread execThread = null;

    private float   scale;
    private int     bitmapWidth;
    private int     bitmapHeight;
    private int     nFace;
    private int     nAge;
    private int     nGender;
    private int     nYaw;
    private int     nPitch;
    private int     nRoll;
    private int     nGazeLR;
    private int     nGazeUD;
    private int     nBlinkL;
    private int     nBlinkR;
    private int     nExScore;
    private int     sBody;
    private int     xBody;
    private int     yBody;
    private int     sHand;
    private int     xHand;
    private int     yHand;
    private int     sFace;
    private int     xFace;
    private int     yFace;
    
    private String exString[] = { "", "", "", "", "" };
    private int exColor[] = { Color.rgb(240, 240, 240),
                              Color.rgb(240, 240, 0),
                              Color.rgb(255, 102, 0),
                              Color.rgb(255, 0, 255),
                              Color.rgb(0, 255, 255) };

    private boolean             isAttached;

    public static final String TAG = "StandardDemo";
    android.os.Handler handler = new android.os.Handler();

    public CustomView(Context context, HVC_BLE hvcBle, int nExec)
    {
        super(context);
        mActivity = (Activity)this.getContext();

        mHVCDevice = hvcBle;
        mExecuteFlag = nExec;

        mHVCResult = new HVC_RES();

        nFace = 0;
        nAge = 0;
        nGender = 0;
        nGazeLR = 0;
        nGazeUD = 0;
        nBlinkL = 0;
        nBlinkR = 0;
        nYaw = 0;
        nPitch = 0;
        nRoll = 0;

        sBody = -1;
        xBody = -1;
        yBody = -1;
        sHand = -1;
        xHand = -1;
        yHand = -1;
        sFace = -1;
        xFace = -1;
        yFace = -1;

        // リソースのオブジェクトを取得
        Resources res = getResources();

        exString[0] = res.getString(R.string.exp_item1);
        exString[1] = res.getString(R.string.exp_item2);
        exString[2] = res.getString(R.string.exp_item3);
        exString[3] = res.getString(R.string.exp_item4);
        exString[4] = res.getString(R.string.exp_item5);

        bitmapWidth = 1000;
        bitmapHeight = 480;
        Log.d(TAG, "bitmapWidth= " + bitmapWidth + " bitmapHeight=" + bitmapHeight);
    }

    private void GetFlemingPos(int inLength, int inYawAngle, int inPitchAngle, int inRollAngle, 
                                                        Point outYawPos, Point outPitchPos, Point outRollPos)
    {
        double pitchRadian  = (double)inPitchAngle / 180.0 * Math.PI;
        double yawRadian    = (double)inYawAngle / 180.0 * Math.PI;

        // Rollが0でPitch軸の端点座標を一度計算、Rollを補正
        double xPos         = Math.cos(yawRadian);
        double yPos         = -Math.sin(pitchRadian) * Math.sin(yawRadian);
        double slope        = Math.atan2(yPos, xPos) * 180.0 / Math.PI;
        int roll            = (int)(inRollAngle + slope);
        double rollRadian   = (double)roll / 180.0 * Math.PI;

        // 3軸の端点座標の算出
        outYawPos.x= (int)(Math.cos(pitchRadian) * Math.sin(rollRadian) * (double)inLength + 0.5);
        outYawPos.y = (int)(Math.cos(pitchRadian) * Math.cos(rollRadian) * (double)inLength + 0.5);
        outPitchPos.x = (int)((Math.cos(rollRadian) * Math.cos(yawRadian) - Math.sin(rollRadian) * Math.sin(pitchRadian) * Math.sin(yawRadian)) * (double)inLength + 0.5);
        outPitchPos.y = (int)((-Math.sin(rollRadian) * Math.cos(yawRadian) - Math.cos(rollRadian) * Math.sin(pitchRadian) * Math.sin(yawRadian)) * (double)inLength + 0.5);
        outRollPos.x = (int)((Math.sin(yawRadian) * Math.cos(rollRadian) + Math.sin(pitchRadian) * Math.cos(yawRadian) * Math.sin(rollRadian)) * (double)inLength + 0.5);
        outRollPos.y = (int)((-Math.sin(yawRadian) * Math.sin(rollRadian) + Math.sin(pitchRadian) * Math.cos(yawRadian) * Math.cos(rollRadian)) * (double)inLength + 0.5);
    }

    private void DrawFleming(Canvas canvas, Point center, int length)
    {
        Point yawPos = new Point();
        Point pitchPos = new Point();
        Point rollPos = new Point();
        GetFlemingPos(length, nYaw, nPitch, nRoll, yawPos, pitchPos, rollPos);

        // 3軸の端点座標の算出
        yawPos.x = center.x + yawPos.x;
        yawPos.y = center.y - yawPos.y;
        pitchPos.x = center.x + pitchPos.x;
        pitchPos.y = center.y - pitchPos.y;
        rollPos.x = center.x + rollPos.x;
        rollPos.y = center.y - rollPos.y;

        // ペイントオブジェクト生成
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(2.0f);

        // 描画
        paint.setColor(Color.rgb(0xff, 0xff, 0x00));
        canvas.drawLine(center.x, center.y, pitchPos.x, pitchPos.y, paint);
        paint.setColor(Color.rgb(0x00, 0xff, 0xff));
        canvas.drawLine(center.x, center.y, yawPos.x, yawPos.y, paint);
        paint.setColor(Color.rgb(0xff, 0x00, 0xff));
        canvas.drawLine(center.x, center.y, rollPos.x, rollPos.y, paint);
    }

    private void DrawGazeLine(Canvas canvas, Point leftPos, Point rightPos, int length)
    {
        Point yawPos = new Point();
        Point pitchPos = new Point();
        Point rollPos = new Point();
        GetFlemingPos(length, nGazeLR, nGazeUD, 0, yawPos, pitchPos, rollPos);

        Point leftGazePos = new Point();
        leftGazePos.x = leftPos.x + rollPos.x;
        leftGazePos.y = leftPos.y - rollPos.y;
        Point rightGazePos = new Point();
        rightGazePos.x = rightPos.x + rollPos.x;
        rightGazePos.y = rightPos.y - rollPos.y;

        // ペイントオブジェクト生成
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);

        paint.setColor(Color.rgb(0xff, 0xff, 0x00));
        canvas.drawLine(leftPos.x, leftPos.y, leftGazePos.x, leftGazePos.y, paint);
        canvas.drawLine(rightPos.x, rightPos.y, rightGazePos.x, rightGazePos.y, paint);

        // 三角矢印
        int i;
        for(i = -2; i < 2; i++){
            int j = 0;
            for(j = -2; j < 2; j++){
                canvas.drawLine(leftGazePos.x, leftGazePos.y, leftPos.x+i, leftPos.y+j, paint);
                canvas.drawLine(rightGazePos.x, rightGazePos.y, rightPos.x+i, rightPos.y+j, paint);
            }
        }
    }

    private void DrawBlink(Canvas canvas, Point leftPos, Point rightPos, int length, boolean bHV)
    {
        int openRatioL = (1000 - nBlinkL) / 10;
        int openRatioR = (1000 - nBlinkR) / 10;
        int sizeL = (int)length / 2 * openRatioL / 100;
        int sizeR = (int)length / 2 * openRatioR / 100;

        // ペイントオブジェクト生成
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.rgb(0xff, 0x00, 0x00));

        if ( bHV ) {
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRect(leftPos.x+length/2-sizeL, leftPos.y, leftPos.x+length/2, leftPos.y+4, paint);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawRect(leftPos.x, leftPos.y, leftPos.x+length/2, leftPos.y+4, paint);

            paint.setStyle(Paint.Style.FILL);
            canvas.drawRect(rightPos.x+length/2-sizeR, rightPos.y, rightPos.x+length/2, rightPos.y+4, paint);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawRect(rightPos.x, rightPos.y, rightPos.x+length/2, rightPos.y+4, paint);
        } else {
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRect(leftPos.x, leftPos.y+length/2-sizeL, leftPos.x+4, leftPos.y+length/2, paint);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawRect(leftPos.x, leftPos.y, leftPos.x+4, leftPos.y+length/2, paint);

            paint.setStyle(Paint.Style.FILL);
            canvas.drawRect(rightPos.x, leftPos.y+length/2-sizeR, rightPos.x+4, leftPos.y+length/2, paint);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawRect(rightPos.x, rightPos.y, rightPos.x+4, rightPos.y+length/2, paint);
        }
    }

    private void DrawDegree(Canvas canvas, Point scorePos, int length)
    {
        int ratio = (nExScore);
        int size = (int)length / 2 * ratio / 200;

        // ペイントオブジェクト生成
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.rgb(0xff, 0x00, 0x00));

        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(scorePos.x+length/2-size, scorePos.y, scorePos.x+length/2, scorePos.y+4, paint);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(scorePos.x, scorePos.y, scorePos.x+length/2, scorePos.y+4, paint);
    }

    private void DrawResultText(Canvas canvas)
    {
        // リソースのオブジェクトを取得
        Resources res = getResources();

        // ペイントオブジェクト生成
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(2.0f);
        paint.setTextSize(32);

        int xTop = bitmapWidth - 320;
        int xStep = 150;
        if (Locale.JAPAN.equals(Locale.getDefault())) {
            //ここに日本語の場合の処理
            xStep = 220;
        } else {
            //ここに日本語以外の場合の処理
        }
        int yTop = 32;
        int yStep = bitmapHeight/9;
        int gSize = 32;
        String text;

        text = res.getString(R.string.short_body);
        paint.setColor(Color.rgb(127, 127, 127));
        if ( (mExecuteFlag & HVC.HVC_ACTIV_BODY_DETECTION) != 0 ) {
            paint.setColor(Color.CYAN);
            if ( sBody > -1 && xBody > -1 && yBody > -1) {
                canvas.drawCircle(xTop+xStep+gSize/2, yTop-10, gSize/2, paint);
            } else {
                canvas.drawLine(xTop+xStep, yTop-10, xTop+xStep+gSize, yTop-10, paint);
            }
            paint.setColor(Color.rgb(255, 255, 255));
        }
        canvas.drawText(text, xTop, yTop, paint);
        yTop += yStep;

        text = res.getString(R.string.short_hand);
        paint.setColor(Color.rgb(127, 127, 127));
        if ( (mExecuteFlag & HVC.HVC_ACTIV_HAND_DETECTION) != 0 ) {
            paint.setColor(Color.MAGENTA);
            if ( sHand > -1 && xHand > -1 && yHand > -1) {
                canvas.drawCircle(xTop+xStep+gSize/2, yTop-10, gSize/2, paint);
            } else {
                canvas.drawLine(xTop+xStep, yTop-10, xTop+xStep+gSize, yTop-10, paint);
            }
            paint.setColor(Color.rgb(255, 255, 255));
        }
        canvas.drawText(text, xTop, yTop, paint);
        yTop += yStep;

        boolean bFace = false;
        text = res.getString(R.string.short_face);
        paint.setColor(Color.rgb(127, 127, 127));
        if ( (mExecuteFlag & HVC.HVC_ACTIV_FACE_DETECTION) != 0 ) {
            paint.setColor(Color.GREEN);
            if ( sFace > -1 && xFace > -1 && yFace > -1) {
                bFace = true;
                canvas.drawCircle(xTop+xStep+gSize/2, yTop-10, gSize/2, paint);
            } else {
                canvas.drawLine(xTop+xStep, yTop-10, xTop+xStep+gSize, yTop-10, paint);
            }
            paint.setColor(Color.rgb(255, 255, 255));
        }
        canvas.drawText(text, xTop, yTop, paint);
        yTop += yStep;

        text = res.getString(R.string.short_dir);
        paint.setColor(Color.rgb(127, 127, 127));
        if ( (mExecuteFlag & HVC.HVC_ACTIV_FACE_DIRECTION) != 0 ) {
            if ( bFace == false ) {
                canvas.drawLine(xTop+xStep, yTop-10, xTop+xStep+gSize, yTop-10, paint);
            } else {
                Point center = new Point();
                center.x = (int)(xTop+xStep+gSize/2);
                center.y = (int)(yTop-10+gSize/2);
                int length = (int)(gSize);
                DrawFleming(canvas, center, length);
            }
            paint.setColor(Color.rgb(255, 255, 255));
        }
        canvas.drawText(text, xTop, yTop, paint);
        yTop += yStep;

        text = res.getString(R.string.short_age);
        paint.setColor(Color.rgb(127, 127, 127));
        if ( (mExecuteFlag & HVC.HVC_ACTIV_AGE_ESTIMATION) != 0 ) {
            if ( bFace == false ) {
                canvas.drawLine(xTop+xStep, yTop-10, xTop+xStep+gSize, yTop-10, paint);
            } else {
                String subText;
                if ( -128 == nAge ) {
                    subText = "?";
                } else {
                    subText = String.format("%d", nAge);
                }
                paint.setColor(Color.rgb(255, 255, 255));
                canvas.drawText(subText, xTop+xStep, yTop, paint);
            }
            paint.setColor(Color.rgb(255, 255, 255));
        }
        canvas.drawText(text, xTop, yTop, paint);
        yTop += yStep;

        text = res.getString(R.string.short_gender);
        paint.setColor(Color.rgb(127, 127, 127));
        if ( (mExecuteFlag & HVC.HVC_ACTIV_GENDER_ESTIMATION) != 0 ) {
            if ( bFace == false ) {
                canvas.drawLine(xTop+xStep, yTop-10, xTop+xStep+gSize, yTop-10, paint);
            } else {
                String subText;
                if ( -128 == nGender ) {
                    subText = "?";
                    paint.setColor(Color.rgb(240, 240, 240));
                } else
                if ( 1 == nGender ) {
                    subText = res.getString(R.string.gender_item1);
                    paint.setColor(Color.rgb(246, 173, 198));
                } else {
                    subText = res.getString(R.string.gender_item2);
                    paint.setColor(Color.rgb(0, 255, 255));
                }
                canvas.drawText(subText, xTop+xStep, yTop, paint);
            }
            paint.setColor(Color.rgb(255, 255, 255));
        }
        canvas.drawText(text, xTop, yTop, paint);
        yTop += yStep;

        text = res.getString(R.string.short_gaze);
        paint.setColor(Color.rgb(127, 127, 127));
        if ( (mExecuteFlag & HVC.HVC_ACTIV_GAZE_ESTIMATION) != 0 ) {
            if ( bFace == false ) {
                canvas.drawLine(xTop+xStep, yTop-10, xTop+xStep+gSize, yTop-10, paint);
            } else {
                Point leftPos = new Point();
                leftPos.x   = (int)((xTop+xStep+gSize/2));
                leftPos.y   = (int)((yTop-10));
                Point rightPos = new Point();
                rightPos.x  = (int)((xTop+xStep+gSize*3/2));
                rightPos.y  = (int)((yTop-10));
                int length = (int)(gSize);
                DrawGazeLine(canvas, leftPos, rightPos, length);
            }
            paint.setColor(Color.rgb(255, 255, 255));
        }
        canvas.drawText(text, xTop, yTop, paint);
        yTop += yStep;

        text = res.getString(R.string.short_blink);
        paint.setColor(Color.rgb(127, 127, 127));
        if ( (mExecuteFlag & HVC.HVC_ACTIV_BLINK_ESTIMATION) != 0 ) {
            if ( bFace == false ) {
                canvas.drawLine(xTop+xStep, yTop-10, xTop+xStep+gSize, yTop-10, paint);
            } else {
                Point leftPos = new Point();
                leftPos.x   = (int)(xTop+xStep) + 30;
                leftPos.y   = (int)(yTop-10-gSize/2);
                Point rightPos = new Point();
                rightPos.x  = (int)(xTop+xStep) + 30;
                rightPos.y  = (int)(yTop-10+gSize/2);
                int length = (int)(gSize*3);
                DrawBlink(canvas, leftPos, rightPos, length, true);
            }
            paint.setColor(Color.rgb(255, 255, 255));
            if ( bFace == true ) {
                canvas.drawText("L", xTop+xStep, yTop-gSize/2, paint);
                canvas.drawText("R", xTop+xStep, yTop+gSize/2, paint);
            }
        }
        canvas.drawText(text, xTop, yTop, paint);
        yTop += yStep;

        text = res.getString(R.string.short_exp);
        paint.setColor(Color.rgb(127, 127, 127));
        if ( (mExecuteFlag & HVC.HVC_ACTIV_EXPRESSION_ESTIMATION) != 0 ) {
            if ( bFace == false ) {
                canvas.drawLine(xTop+xStep, yTop-10, xTop+xStep+gSize, yTop-10, paint);
            } else {
                String subText;
                if ( -128 == nFace ) {
                    subText = "?";
                    paint.setColor(Color.rgb(255, 0, 255));
                } else {
                    Point scorePos = new Point();
                    scorePos.x  = (int)(xTop+xStep) - 70;
                    scorePos.y  = (int)(yTop-10);
                    int length = (int)(gSize*3);
                    DrawDegree(canvas, scorePos, length);

                    subText = exString[nFace];
                    paint.setColor(exColor[nFace]);
                }
                canvas.drawText(subText, xTop+xStep, yTop, paint);
            }
            paint.setColor(Color.rgb(255, 255, 255));
        }
        canvas.drawText(text, xTop, yTop, paint);
        yTop += yStep;
    }

    // 表示時のCanvasの状態をここで処理する
    protected void onDraw(Canvas canvas)
    {
        super.onDraw(canvas);
        
        float scaleX = (float)getWidth() / (float)bitmapWidth;
        float scaleY = (float)getHeight() / (float)bitmapHeight;
        scale = scaleX > scaleY ? scaleY : scaleX;
        
        canvas.translate( ((float)getWidth() - (float)bitmapWidth * scale) / 2.0f, ((float)getHeight() - (float)bitmapHeight * scale) / 2.0f );
        canvas.scale( scale, scale );
        
        // 背景を黒にします
        canvas.drawColor(Color.BLACK);
        
        // ペイントオブジェクト生成
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.0f);
        if ( sBody > -1 && xBody > -1 && yBody > -1) {
            paint.setColor(Color.CYAN);
            canvas.drawRect(xBody - sBody/2, yBody - sBody/2, xBody + sBody/2, yBody + sBody/2, paint);
        }
        if ( sHand > -1 && xHand > -1 && yHand > -1) {
            paint.setColor(Color.MAGENTA);
            canvas.drawRect(xHand - sHand/2, yHand - sHand/2, xHand + sHand/2, yHand + sHand/2, paint);
        }

        if ( sFace > -1 && xFace > -1 && yFace > -1) {
            paint.setColor(Color.GREEN);
            canvas.drawRect(xFace - sFace/2, yFace - sFace/2, xFace + sFace/2, yFace + sFace/2, paint);

            if ( (mExecuteFlag & HVC.HVC_ACTIV_FACE_DIRECTION) != 0 ) {
                Point center = new Point();
                center.x = (int)(xFace);
                center.y = (int)(yFace);
                //int length = (int)(sFace) / 3;
                //DrawFleming(canvas, center, length);
            }
            if ( (mExecuteFlag & HVC.HVC_ACTIV_GAZE_ESTIMATION) != 0 ) {
                int width   = sFace / 4;
                int height  = sFace / 3;
                Point leftPos = new Point();
                leftPos.x   = (int)((xFace - width));
                leftPos.y   = (int)((yFace - height/2));
                Point rightPos = new Point();
                rightPos.x  = (int)((xFace + width));
                rightPos.y  = (int)((yFace - height/2));
                //int length = (int)(sFace) / 3;
                //DrawGazeLine(canvas, leftPos, rightPos, length);
            }
            if ( (mExecuteFlag & HVC.HVC_ACTIV_BLINK_ESTIMATION) != 0 ) {
                Point leftPos = new Point();
                leftPos.x   = (int)(xFace - sFace/2) - 5;
                leftPos.y   = (int)(yFace - sFace/2);
                Point rightPos = new Point();
                rightPos.x  = (int)(xFace + sFace/2) + 1;
                rightPos.y  = (int)(yFace - sFace/2);
                //int length = (int)(sFace);
                //DrawBlink(canvas, leftPos, rightPos, length, false);
            }
        }

        paint.setColor(Color.GRAY);
        canvas.drawRect(0, 0, 640, 480, paint);

        DrawResultText(canvas);
    }

    private class ExecThread extends Thread {
        @Override
        public void run()
        {
            do {
                if ( mHVCDevice.IsBusy() == false ) {
                    mHVCDevice.execute(mExecuteFlag, mHVCResult);
                }
            } while ( isAttached == true );
        }
    }

    public void onPostExecute(int nRet, byte outStatus) {
        String deviceInfo = "HVC Execute:\n";
        if ( (nRet != HVC.HVC_NORMAL) || (outStatus != 0) ) {
            // エラー処理
            deviceInfo += "Error : " + String.format("ret = %d / status = 0x%02x\n", nRet, outStatus);
        } else {
            sBody = -1;
            xBody = -1;
            yBody = -1;
            deviceInfo += "Body Detect = " + String.format("%d\n", mHVCResult.body.size());
            for (DetectionResult bdResult : mHVCResult.body) {
                int size = sBody = bdResult.size;
                int posX = xBody = bdResult.posX;
                int posY = yBody = bdResult.posY;
                int conf = bdResult.confidence;
                deviceInfo += String.format("size = %d, x = %d, y = %d, conf = %d\n", size, posX, posY, conf);
            }
            sHand = -1;
            xHand = -1;
            yHand = -1;
            deviceInfo += "Hand Detect = " + String.format("%d\n", mHVCResult.hand.size());
            for (DetectionResult hdResult : mHVCResult.hand) {
                int size = sHand = hdResult.size;
                int posX = xHand = hdResult.posX;
                int posY = yHand = hdResult.posY;
                int conf = hdResult.confidence;
                deviceInfo += String.format("size = %d, x = %d, y = %d, conf = %d\n", size, posX, posY, conf);
            }
            sFace = -1;
            xFace = -1;
            yFace = -1;
            deviceInfo += "Face Detect = " + String.format("%d\n", mHVCResult.face.size());
            for (FaceResult fdResult : mHVCResult.face) {
                int size = sFace = fdResult.size;
                int posX = xFace = fdResult.posX;
                int posY = yFace = fdResult.posY;
                int conf = fdResult.confidence;
                deviceInfo += String.format("size = %d, x = %d, y = %d, conf = %d\n", size, posX, posY, conf);
                if ( fdResult.dir.confidence > -1 ) {
                    deviceInfo += String.format("direction : yaw = %d, pitchx = %d, roll = %d, conf = %d\n", 
                                        fdResult.dir.yaw, fdResult.dir.pitch, fdResult.dir.roll, fdResult.dir.confidence);
                    nYaw = -fdResult.dir.yaw;
                    nPitch = fdResult.dir.pitch;
                    nRoll = -fdResult.dir.roll;
                }
                if ( fdResult.age.confidence > -1 ) {
                    deviceInfo += String.format("age : age = %d, conf = %d\n", 
                                        fdResult.age.age, fdResult.age.confidence);
                    nAge = fdResult.age.age;
                }
                if ( fdResult.gen.confidence > -1 ) {
                    deviceInfo += String.format("gender : gender = %d, confidence = %d\n", 
                                        fdResult.gen.gender, fdResult.gen.confidence);
                    if ( fdResult.gen.gender == HVC.HVC_GEN_MALE ) {
                        nGender = 0;
                    } else {
                        nGender = 1;
                    }
                }
                if ( fdResult.gaze.gazeLR >= -90 && fdResult.gaze.gazeLR <= 90 ) {
                    deviceInfo += String.format("gaze : LR = %d, UD = %d\n", 
                                        fdResult.gaze.gazeLR, fdResult.gaze.gazeUD);
                    nGazeLR = - fdResult.gaze.gazeLR;
                    nGazeUD = fdResult.gaze.gazeUD;
                }
                if ( fdResult.blink.ratioL > -1 ) {
                    deviceInfo += String.format("blink : ratioL = %d, ratioR = %d\n", 
                                        fdResult.blink.ratioL, fdResult.blink.ratioR);
                    nBlinkR = fdResult.blink.ratioL;
                    nBlinkL = fdResult.blink.ratioR;
                }
                if ( fdResult.exp.score > -1 ) {
                    deviceInfo += String.format("expression : expression = %d, score = %d, degree = %d\n", 
                                        fdResult.exp.expression, fdResult.exp.score, fdResult.exp.degree);
                    if ( fdResult.exp.expression <= 0 ) {
                        nFace = 0;
                        nExScore = 0;
                    } else {
                        nFace = fdResult.exp.expression - 1;
                        nExScore = fdResult.exp.degree + 100;
                    }
                }
                break;
            }
        }
        Log.d(TAG, deviceInfo);

        handler.post(new Runnable() {
            @Override
            public void run() {
                if( isAttached )
                {
                    invalidate();
                }
            }
        });
    }

    public void showToast(final String str) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                // トースト表示
                Toast.makeText(mActivity, str, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // WindowにAttachされた時の処理
    protected void onAttachedToWindow()
    {
        isAttached = true;

        execThread = new ExecThread();
        execThread.start();

        super.onAttachedToWindow();
    }

    // WindowからDetachされた時の処理
    protected void onDetachedFromWindow()
    {
        isAttached = false;
        super.onDetachedFromWindow();
    }
}
