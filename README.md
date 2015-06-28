# Sonogram
Experimental Android 2D sonogram using chirped sonar

## Features
* Output signal is a chirped/frequency modulated mono signal
* Input is recorded via the 2x microphones found on most phones 
* Signal processing uses GPU based convolution with GLSL shaders
* Triangulation uses the baseline/distance between microphones
* Stereo sonogram and linear histogram views

## Development
* Clone the [mikljohansson/android-core](https://github.com/mikljohansson/android-core) library into the parent folder
* Uses [Android Studio](http://developer.android.com/tools/studio/index.html) for development
