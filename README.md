# Open AutoDash #

This app is intended for use in a vehicle as a replacement or adition to the existing dash. Works on most android tablets with Android 8 or newer. This is part of a project that I am doing in my spare time to add a more feature rich and high tech experience to my 2010 Ford Fusion SEL. 

This project will be open sourced and free for anyone to use or reiterate as they please. 
- Download Android Studio
- Open and sellect project from version control
- paste link to this project and click "clone"
- Enjoy.

For the google maps to work you will have to edit the file named "google_maps_key.xml" in the res/values folder and enter your API key. 
```
<resources>
    <string name="google_maps_key" templateMergeStrategy="preserve" translatable="false">YOUR_KEY_HERE</string>
</resources>
```
To generate a key, go to https://console.cloud.google.com/google/maps-apis/ and generate your free Google Maps API key and enter it in the "YOUR_KEY_HERE" field in the previously mentioned file.
