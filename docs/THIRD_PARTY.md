# Third-party components (face unlock)

* **MobileFaceNet.tflite** (app/src/main/assets): from github.com/syaringan357/Android-MobileFaceNet-MTCNN-FaceAntiSpoofing
  (MIT License, (c) 2019 syaringan357), which is based on the MobileFaceNet paper and github.com/sirius-ai/MobileFaceNet_TF.
  5.2 MB, input 112x112 RGB (batch of 2), output 192 numbers.
* **ML Kit Face Detection** (Google) finds faces, head angle and eye-open probability.
* **TensorFlow Lite** runs the model on the phone.
* **CameraX** reads the front camera.
All processing happens on the phone. Face data = a few lists of numbers in private app storage; no photo is kept, nothing is uploaded.
