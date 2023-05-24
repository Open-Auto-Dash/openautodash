# Open AutoDash #

This app is intended for use in a vehicle as a replacement or adition to the existing dash. Works on most android tablets with Android 8 or newer. This is part of a project that I am doing in my spare time to add a more feature rich and high tech experience to my 2010 Ford Fusion SEL. 

![alt text](https://enterpriseworld.ca/openautodash.jpg)

## Features
- Phone Key <br>
Yup, you can... ah will be able to use your android phone as a key to start and operate your car. An ignition spoofing device is required such as the all famouse [EvoAll](https://fortin.ca/en/products/evo-series/evo-all/). (Hopefully soon, not tested and proven yet. The phone app [Open AutoDash client for Android](https://github.com/Open-Auto-Dash/OpenAutoClientAndroid) is also part of the project and will act as authentication, and remote start, control of many vehicle functions. Raise, lower windows, Check location, schedual remote start routines, and so on. So far it is just an empty shell. 🟠 
- Navigation
    - Speed ✅ (GPS Based)
    - Speed Limit ✅ (Overpass API)
    - Maps  ✅  (Google Maps)
    - Seach Places  🟠
    - Navigation  🔴
    - ADSB In Receiver (Show aircrafts on map) ➕ 🔴
    
- Utilities
    - Dark/Light Mode ✅ (Follows system settings)
    - Current external temperature ✅ (openweathermap.org)
    - Shows LTE signal strength of connected LTE WIFI stick.✅  Sold seperatly (Huawei E8372)
    - Dashcam, front and rear continuous recording. 🔴
    - Reply to messages and view notifications. 🔴
    
- Entertainment
    - Spotify Intigration ✅ (Requires Spotify App installed on device, with Premium account)
    - Local MP3 Player 🔴
    - FM/AM Radio ➕ 🔴
    - SDR Radio, HAM, GMRS, FRS Radio ➕ 🔴
    - Hands Free Calling 🟠
    
    
    
✅ Implimented <br>
🟡 Implimented, but may not work properly <br>
🟠 Coming soon, in development. <br>
🔴 The idea is there, but haven't worked on it. <br>
➕ Requires external pereferal kit.

## External Pereferal Kit
An external pereferal kit will be made available once I have the design finalized and working. This will be a circuit board that interfaces with your tablet via USB. Containing the SDR Radios for HAM, GMRS, ADSB. Bluetooth sink radio for handsfree calling. ODB2 interface with the vehicle for telemetry data and who knows what else I or you might think of doing. 

## IT'S OPEN SOURCED 🎉🎊🥳
Let's band together and make the coolest car infotainment system out there. This project is open sourced and free for anyone to use or reiterate as they please. 
- Download, isntall Android Studio
- Open and sellect project from version control
- paste link to this project and click "clone"
- Enjoy.

For the google maps and temperature to work you will have to edit the file named "api_keys.xml" in the res/values folder and enter your API key. 
```
<resources>
    <string name="google_maps_key" templateMergeStrategy="preserve" translatable="false">YOUR_KEY_HERE</string>
    <string name="open_weather_map_key" translatable="false">YOUR_KEY_HERE</string>
</resources>
```
To generate a key, go to https://console.cloud.google.com/google/maps-apis/ and generate your free Google Maps API key and enter it in the "YOUR_KEY_HERE" field in the previously mentioned file. For OpenWeatherMap go to https://home.openweathermap.org/users/sign_up and create a key.
