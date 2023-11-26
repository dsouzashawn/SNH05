package com.example.origami;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private static final int NUM_CLASSES = 4;
    TextView result, confidence;
    ImageView imageView;
    Button picture;
    int imageSize = 224;
    Interpreter tflite; // TensorFlow Lite Interpreter

    private ActivityResultLauncher<Intent> cameraLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        result = findViewById(R.id.result);
        confidence = findViewById(R.id.confidence);
        imageView = findViewById(R.id.imageView);
        picture = findViewById(R.id.button);

        picture.setOnClickListener(this::onClick);

        // Initialize the TensorFlow Lite interpreter
        try {
            tflite = new Interpreter(loadModelFile());
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Initialize ActivityResultLauncher
        cameraLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Bitmap image = (Bitmap) Objects.requireNonNull(result.getData().getExtras()).get("data");
                        if (image != null) {
                            // Process the captured image
                            handleCapturedImage(image);
                        }
                    }
                });
    }

    // Load the TensorFlow Lite model file
    private MappedByteBuffer loadModelFile() throws IOException {
        // Replace "your_model.tflite" with the actual model filename
        AssetFileDescriptor fileDescriptor = getApplicationContext().getAssets().openFd("origami.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public void classifyImage(Bitmap image) {
        // Your classification logic here...
        if (tflite != null) {
            // Create inputs for the model
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3);
            byteBuffer.order(ByteOrder.nativeOrder());
            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 224, 224, 3}, DataType.FLOAT32);

            // Preprocess the image data
            int[] intValues = new int[imageSize * imageSize];
            image.getPixels(intValues, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
            int pixel = 0;
            for (int i = 0; i < imageSize; i++) {
                for (int j = 0; j < imageSize; j++) {
                    int val = intValues[pixel++];
                    byteBuffer.putFloat(((val >> 16) & 0xFF) * (1.f / 255.f));
                    byteBuffer.putFloat(((val >> 8) & 0xFF) * (1.f / 255.f));
                    byteBuffer.putFloat((val & 0xFF) * (1.f / 255.f));
                }
            }
            inputFeature0.loadBuffer(byteBuffer);

            // Run inference
            float[][] output = new float[1][NUM_CLASSES];
            tflite.run(inputFeature0.getBuffer(), output);

            // Process the inference output
            int maxPos = 0;

            // Display results
            String[] classes = new String[]{"Boat", "Flower", "Sphere", "Heart"};
            float maxConfidence = output[0][0];
            for (int i = 1; i < classes.length; i++) {
                if (output[0][i] > maxConfidence) {
                    maxConfidence = output[0][i];
                    maxPos = i;
                }
            }

            result.setText(classes[maxPos]);
            StringBuilder s = new StringBuilder(" ");
            for (int i = 0; i < classes.length; i++) {
                s.append(String.format("%s: %.1f%%\n", classes[i], output[0][i] * 100));
            }
            confidence.setText(s.toString());
            openVideoBasedOnClassification(MainActivity.this,classes[maxPos]);
        }
    }

    @Override
    protected void onDestroy() {
        if (tflite != null) {
            tflite.close();
        }
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK) {
            Bitmap image = (Bitmap) Objects.requireNonNull(data.getExtras()).get("data");
            assert image != null;
            int dimension = Math.min(image.getWidth(), image.getHeight());
            image = ThumbnailUtils.extractThumbnail(image, dimension, dimension);
            imageView.setImageBitmap(image);

            image = Bitmap.createScaledBitmap(image, imageSize, imageSize, false);
            classifyImage(image);
        }
    }

    private void onClick(View view) {
        // Launch camera if we have permission
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraLauncher.launch(cameraIntent);
        } else {
            // Request camera permission if we don't have it.
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 100);
        }
    }

    private void handleCapturedImage(Bitmap image) {
        // Process the captured image
        imageView.setImageBitmap(image);
        image = Bitmap.createScaledBitmap(image, imageSize, imageSize, false);
        classifyImage(image);
    }
    public void openVideoBasedOnClassification(Context context,String className) {
        String url = "";

        switch (className) {
            case "Boat":
                url = "https://youtu.be/u4asF-z7APc?si=kdWn_prKbSkIH2tK";
                break;
            case "Sphere":
                url = "https://youtu.be/plaz_0SRksU?si=pQr765emHdyiqLvh";
                break;
            case "Heart":
                url = "https://youtu.be/Z-BIL6p1Te8?si=dhisaQhhNCEdv12D";
                break;
            case "Flower":
                url = "https://youtu.be/NN7LReW2JUY?si=1fQ5tlNkeTzRg8fl";
                break;
            default:
                // Default URL or action if none of the cases match
                break;
        }

        if (!url.isEmpty()) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            context.startActivity(intent);
        }
    }

}
