# vector-compat
A basic support library for `VectorDrawable` and `AnimatedVectorDrawable` introduced in Lollipop (supports api 14+ so far)

`vector-compat` provides the necessary tools to make animated icons similar to the new drawer hamburger icon that morphs to a back arrow when clicked. Any other morph animation between icons can be defined in purely in `xml` (no java code required) and the library takes care of the transformation animation

![Example](https://github.com/wnafee/vector-compat/blob/master/demo.gif)

The library will transparently fallbacks to the lollipop implementation of `VectorDrawable` and `AnimatedVectorDrawable` on api 21+ devices

## Download
Add the vector-compat dependency to your `build.gradle` file:
```groovy
dependencies {
    compile 'com.wnafee:vector-compat:1.0.0'
}
```

## Usage
`VectorDrawable` and `AnimatedVectorDrawable` in this support library can be inflated in one of 2 ways:

* Calling Static `getDrawable()` methods:
```java
//This will only inflate a drawable with <vector> as the root element
VectorDrawable.getDrawable(context, R.drawable.ic_arrow_vector);

//This will only inflate a drawable with <animated-vector> as the root element
AnimatedVectorDrawable.getDrawable(context, R.drawable.ic_arrow_to_menu_animated_vector);

// This will inflate any drawable and will auto-fallback to the lollipop implementation on api 21+ devices
MorphButton.getDrawable(context, R.drawable.any_drawable);
````

* directly from the `MorphButton` view in xml:
```xml
<!-- Insert xmlns:app="http://schemas.android.com/apk/res-auto" in your root layout element -->
<com.wnafee.vector.MorphButton
    android:id="@+id/playPauseBtn"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    app:morphStartDrawable="@drawable/ic_pause_to_play"
    app:morphEndDrawable="@drawable/ic_play_to_pause" /> 
```
#### MorphButton
`MorphButton` is a `CompoundButton` with 2 states: `MorphState.START` or `MorphState.END`. The attributes `morphStartDrawable` and `morphEndDrawable` define which drawables to use as the button background depending on the button's state. These can be any type of drawable (e.g. `BitmapDrawable`, `ColorDrawable`, `VectorDrawable`, `AnimatedVectorDrawable` etc.)

Button clicks will toggle between the drawable states. If the drawables happen to implement the [`Animatable`][1] interface (e.g. `AnimatedVectorDrawable` or `AnimationDrawable`) then `start()` will be automatically called to animate between the start and end drawables defined in xml.

 Button state can also be set manually via `setState()` methods:
```java
// transition with no animation
myMorphButton.setState(MorphState.END) 

// ... or transition with animation if drawable is Animatable
myMorphButton.setState(MorphState.START, true) 
````

## License

    Copyright 2015 Wael Nafee

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

[1]: http://developer.android.com/reference/android/graphics/drawable/Animatable.html
