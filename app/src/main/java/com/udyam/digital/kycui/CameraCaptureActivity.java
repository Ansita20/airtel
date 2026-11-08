package com.udyam.digital.kycui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;
import com.udyam.digital.R;

import java.io.File;

/**
 * Fallback when Google Play services (and so the ML Kit document scanner) are missing: CameraX
 * capture into the app's private cache with a document-shaped guide frame. The caller runs the
 * quality gate, reads, encrypts and deletes the file.
 */
public class CameraCaptureActivity extends AppCompatActivity {

    /** Intent extra: true for an ID card (card-shaped frame), else an A4 page. */
    public static final String EXTRA_CARD = "card";

    private ImageCapture capture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // FLAG_SECURE comes from SecureScreens (app-wide policy).
        FrameLayout root = new FrameLayout(this);
        PreviewView preview = new PreviewView(this);
        root.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        root.addView(new GuideFrame(this, getIntent().getBooleanExtra(EXTRA_CARD, false)), new FrameLayout.LayoutParams(-1, -1));
        TextView hint = new TextView(this);
        hint.setText(R.string.camera_guide);
        hint.setTextColor(Color.WHITE);
        hint.setShadowLayer(4, 0, 1, Color.BLACK);
        hint.setGravity(Gravity.CENTER);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        hint.setPadding(pad, pad * 2, pad, 0);
        root.addView(hint, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        MaterialButton shoot = new MaterialButton(this);
        shoot.setText(R.string.camera_capture);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = (int) (32 * getResources().getDisplayMetrics().density);
        root.addView(shoot, lp);
        setContentView(root);

        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview p = new Preview.Builder().build();
                p.setSurfaceProvider(preview.getSurfaceProvider());
                capture = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build();
                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, p, capture);
            } catch (Exception e) {
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                finish();
            }
        }, ContextCompat.getMainExecutor(this));

        shoot.setOnClickListener(v -> {
            if (capture == null) return;
            File f = new File(getCacheDir(), "kyc_capture.jpg");
            capture.takePicture(new ImageCapture.OutputFileOptions.Builder(f).build(), ContextCompat.getMainExecutor(this),
                    new ImageCapture.OnImageSavedCallback() {
                        @Override public void onImageSaved(ImageCapture.OutputFileResults r) {
                            setResult(RESULT_OK, new Intent().setData(Uri.fromFile(f)));
                            finish();
                        }

                        @Override public void onError(ImageCaptureException e) {
                            Toast.makeText(CameraCaptureActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
        });
    }

    /** Dims everything outside a document-shaped window and draws its corners. */
    static final class GuideFrame extends View {
        private final boolean card;
        private final Paint dim = new Paint(), corner = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        GuideFrame(Context c, boolean card) {
            super(c);
            this.card = card;
            dim.setColor(0x99000000);
            corner.setColor(Color.WHITE);
            corner.setStyle(Paint.Style.STROKE);
            corner.setStrokeWidth(6 * c.getResources().getDisplayMetrics().density);
            corner.setStrokeCap(Paint.Cap.ROUND);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            // Portrait A4 (1 : 1.414) or a landscape ID card (1.586 : 1), 86% of the width.
            float fw = w * 0.86f, fh = card ? fw / 1.586f : fw * 1.414f;
            if (fh > h * 0.72f) { fh = h * 0.72f; fw = card ? fh * 1.586f : fh / 1.414f; }
            RectF r = new RectF((w - fw) / 2, (h - fh) / 2, (w + fw) / 2, (h + fh) / 2);
            path.reset();
            path.setFillType(Path.FillType.EVEN_ODD);
            path.addRect(0, 0, w, h, Path.Direction.CW);
            path.addRoundRect(r, 24, 24, Path.Direction.CW);
            canvas.drawPath(path, dim);
            float len = Math.min(fw, fh) * 0.12f;
            float[][] pts = {{r.left, r.top, 1, 1}, {r.right, r.top, -1, 1}, {r.left, r.bottom, 1, -1}, {r.right, r.bottom, -1, -1}};
            for (float[] p : pts) {
                canvas.drawLine(p[0], p[1], p[0] + p[2] * len, p[1], corner);
                canvas.drawLine(p[0], p[1], p[0], p[1] + p[3] * len, corner);
            }
        }
    }
}
